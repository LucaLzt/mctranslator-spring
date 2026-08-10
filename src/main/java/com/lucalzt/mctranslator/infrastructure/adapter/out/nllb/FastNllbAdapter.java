package com.lucalzt.mctranslator.infrastructure.adapter.out.nllb;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.NodeInfo;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import ai.onnxruntime.ValueInfo;

import com.lucalzt.mctranslator.domain.model.LanguageCode;
import com.lucalzt.mctranslator.domain.model.TranslationEngineType;
import com.lucalzt.mctranslator.domain.model.TranslationKey;
import com.lucalzt.mctranslator.domain.model.TranslationResult;
import com.lucalzt.mctranslator.domain.model.TranslationStatus;
import com.lucalzt.mctranslator.domain.port.out.TranslationEnginePort;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * Local NLLB-200 (distilled 600M) translation engine running on ONNX Runtime.
 *
 * <p>Implements {@link TranslationEnginePort} for {@link TranslationEngineType#FAST} entirely on
 * the host machine: the model assets (a SentencePiece tokenizer file plus quantized encoder and
 * decoder ONNX graphs) are loaded from the local model directory configured in
 * {@link NllbEngineProperties} and never leave the machine — no network is involved.
 *
 * <p>Translation follows the standard NLLB sequence-to-sequence pipeline:
 * <ol>
 * <li>the source text is tokenized with the source Flores-200 language tag prepended and the EOS
 * token appended;</li>
 * <li>the encoder produces the last hidden states for the full source sequence;</li>
 * <li>a greedy autoregressive decoder loop, seeded with the EOS token (the exported model's
 * {@code decoder_start_token_id}), forcing the target Flores-200 language tag as the first
 * generated token and reusing the onnxruntime KV-cache tensors from the previous step, generates
 * tokens until EOS or the configured token budget is exhausted;</li>
 * <li>the generated token ids are decoded back to text skipping special tokens.</li>
 * </ol>
 *
 * <p>The adapter fails fast at construction time with a descriptive {@link IllegalStateException}
 * when the model assets are missing or cannot be loaded, and with an
 * {@link IllegalArgumentException} at translation time for unsupported engine types. Native
 * resources (tokenizer, ONNX sessions and environment) are released by {@link #close()}, invoked
 * automatically by Spring when the application context shuts down.
 */
@Component
@NullMarked
public class FastNllbAdapter implements TranslationEnginePort {

	private static final Logger logger = LoggerFactory.getLogger(FastNllbAdapter.class);

	/** Name of the SentencePiece tokenizer asset inside the model directory. */
	private static final String TOKENIZER_FILE = "tokenizer.json";

	/** Sub-directory of the model directory holding the ONNX graphs. */
	private static final String ONNX_SUBDIR = "onnx";

	/** File name of the quantized NLLB encoder ONNX graph. */
	private static final String ENCODER_FILE = "encoder_model_quantized.onnx";

	/** File name of the quantized NLLB decoder ONNX graph (with merged KV cache). */
	private static final String DECODER_FILE = "decoder_model_merged_quantized.onnx";

	private static final String INPUT_IDS = "input_ids";
	private static final String ATTENTION_MASK = "attention_mask";
	private static final String ENCODER_HIDDEN_STATES = "encoder_hidden_states";
	private static final String ENCODER_ATTENTION_MASK = "encoder_attention_mask";
	private static final String USE_CACHE_BRANCH = "use_cache_branch";
	private static final String PAST_PREFIX = "past_key_values.";
	private static final String PRESENT_PREFIX = "present.";
	private static final String LOGITS = "logits";
	private static final String LAST_HIDDEN_STATE = "last_hidden_state";

	/** EOS token id of the NLLB tokenizer ({@code </s>}). */
	private static final long EOS_TOKEN_ID = 2L;

	/** Batch size of every model tensor; only single-key translation is supported. */
	private static final long BATCH_SIZE = 1L;

	/** Upper bound of the encoder input sequence: model max position embeddings minus tag and EOS. */
	private static final int MAX_ENCODER_SEQUENCE_LENGTH = 1024;

	/** Fallback number of attention heads used to build the empty KV-cache tensors. */
	private static final long DEFAULT_NUM_HEADS = 16L;

	/** Fallback head dimension used to build the empty KV-cache tensors. */
	private static final long DEFAULT_HEAD_DIM = 64L;

	/** Sorts decoder cache tensor names ({@code past_key_values.N.decoder|encoder.key|value}). */
	private static final Comparator<String> PAST_NAME_COMPARATOR = (first, second) -> {
		String[] firstParts = first.split("\\.");
		String[] secondParts = second.split("\\.");
		int layerCompare = Integer.compare(Integer.parseInt(firstParts[1]), Integer.parseInt(secondParts[1]));
		if (layerCompare != 0) {
			return layerCompare;
		}
		int kindCompare = firstParts[2].compareTo(secondParts[2]);
		if (kindCompare != 0) {
			return kindCompare;
		}
		return firstParts[3].compareTo(secondParts[3]);
	};

	private final NllbEngineProperties properties;
	private final NllbLanguageMapper languageMapper;
	private final Path modelDir;
	private final int maxNewTokens;
	private final HuggingFaceTokenizer tokenizer;
	private final OrtEnvironment environment;
	private final OrtSession encoderSession;
	private final OrtSession decoderSession;
	private final Map<String, Long> languageTokenIds;
	private final List<String> pastInputNames;
	private final List<String> pastOutputNames;
	private final long[] initialPastShape;
	private final String decoderInputIdsName;
	private final String decoderEncoderHiddenStatesName;
	private final String decoderEncoderAttentionMaskName;
	private final String decoderUseCacheBranchName;
	private volatile boolean closed;

	/**
	 * Constructs the adapter, validates the model assets and loads all native resources.
	 *
	 * @param properties     the NLLB engine properties, never {@code null}
	 * @param languageMapper the ISO-639-1 to Flores-200 tag mapper, never {@code null}
	 * @throws IllegalStateException if the model assets are missing or cannot be loaded, or if the
	 *                               configured token budget is not positive
	 */
	public FastNllbAdapter(NllbEngineProperties properties, NllbLanguageMapper languageMapper) {
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
		this.languageMapper = Objects.requireNonNull(languageMapper, "languageMapper must not be null");
		this.modelDir = properties.resolveModelDir();
		this.maxNewTokens = properties.getMaxNewTokens();
		if (this.maxNewTokens < 1) {
			throw new IllegalStateException(
					"mctranslator.engine.nllb.maxNewTokens must be at least 1, got " + this.maxNewTokens);
		}
		validateModelAssets();

		HuggingFaceTokenizer localTokenizer = null;
		OrtEnvironment localEnvironment = null;
		OrtSession localEncoderSession = null;
		OrtSession localDecoderSession = null;
		try {
			localTokenizer = HuggingFaceTokenizer.newInstance(modelDir.resolve(TOKENIZER_FILE),
					Map.of("addSpecialTokens", "false", "truncation", "false", "padding", "false"));
			localEnvironment = OrtEnvironment.getEnvironment();
			localEncoderSession = createSession(localEnvironment, ENCODER_FILE);
			localDecoderSession = createSession(localEnvironment, DECODER_FILE);
			DecoderGraphInfo graphInfo = inspectDecoderGraph(localDecoderSession);
			this.tokenizer = localTokenizer;
			this.environment = localEnvironment;
			this.encoderSession = localEncoderSession;
			this.decoderSession = localDecoderSession;
			this.pastInputNames = graphInfo.pastInputNames();
			this.pastOutputNames = graphInfo.pastOutputNames();
			this.initialPastShape = graphInfo.initialPastShape();
			this.decoderInputIdsName = graphInfo.inputIdsName();
			this.decoderEncoderHiddenStatesName = graphInfo.encoderHiddenStatesName();
			this.decoderEncoderAttentionMaskName = graphInfo.encoderAttentionMaskName();
			this.decoderUseCacheBranchName = graphInfo.useCacheBranchName();
			this.languageTokenIds = resolveLanguageTokenIds();
			logger.info("Initialized fast NLLB engine from '{}': {} decoder KV-cache tensor pairs, maxNewTokens={}",
					modelDir.toAbsolutePath(), pastInputNames.size(), maxNewTokens);
		} catch (IOException | OrtException | RuntimeException e) {
			closeQuietly(localTokenizer, localEncoderSession, localDecoderSession);
			throw new IllegalStateException(
					"Failed to initialize fast NLLB engine from model directory '" + modelDir + "': " + e.getMessage(), e);
		}
	}

	/**
	 * Translates a single key with the local NLLB engine.
	 *
	 * <p>Only {@link TranslationEngineType#FAST} is accepted; any other engine type is rejected with
	 * an {@link IllegalArgumentException}. ONNX failures are wrapped in an
	 * {@link IllegalStateException} while the domain translation contract stays checked-exception free.
	 *
	 * @param key        the translation key and original text, never {@code null}
	 * @param sourceLang the source language code, never {@code null}
	 * @param targetLang the target language code, never {@code null}
	 * @param engineType the translation engine type, never {@code null}
	 * @return the translation result, never {@code null}
	 * @throws IllegalArgumentException if {@code engineType} is not {@link TranslationEngineType#FAST}
	 *                                  or a language code is unsupported
	 * @throws IllegalStateException    if the engine is closed or an ONNX inference fails
	 */
	@Override
	public TranslationResult translate(TranslationKey key, LanguageCode sourceLang, LanguageCode targetLang,
			TranslationEngineType engineType) {
		Objects.requireNonNull(key, "key must not be null");
		Objects.requireNonNull(sourceLang, "sourceLang must not be null");
		Objects.requireNonNull(targetLang, "targetLang must not be null");
		Objects.requireNonNull(engineType, "engineType must not be null");
		if (engineType != TranslationEngineType.FAST) {
			throw new IllegalArgumentException(
					"FastNllbAdapter only supports TranslationEngineType.FAST, got " + engineType);
		}
		if (closed) {
			throw new IllegalStateException("Fast NLLB engine is already closed");
		}
		long startNanos = System.nanoTime();
		try {
			String sourceTag = languageMapper.toNllbTag(sourceLang.value());
			String targetTag = languageMapper.toNllbTag(targetLang.value());
			long sourceLangId = languageTokenIds.get(sourceTag);
			long targetLangId = languageTokenIds.get(targetTag);

			long[] sourceIds = tokenizer.encode(key.originalText(), false, false).getIds();
			long[] encoderInputIds = buildEncoderInputIds(sourceLangId, sourceIds);
			long[] attentionMask = ones(encoderInputIds.length);

			OnnxTensor encoderIdsTensor = OnnxTensor.createTensor(environment, new long[][] { encoderInputIds });
			OnnxTensor encoderMaskTensor = OnnxTensor.createTensor(environment, new long[][] { attentionMask });
			try {
				try (OrtSession.Result encoderResult = encoderSession
						.run(Map.of(INPUT_IDS, encoderIdsTensor, ATTENTION_MASK, encoderMaskTensor))) {
					OnnxTensor encoderHiddenStates = findTensor(encoderResult, LAST_HIDDEN_STATE);
					List<Long> generatedIds = decode(encoderHiddenStates, targetLangId, attentionMask);
					String translatedText = tokenizer.decode(toLongArray(generatedIds), true);
					Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
					logger.debug("Translated '{}' ({} -> {}) to '{}' in {} ms",
							key.path(), sourceLang, targetLang, translatedText, duration.toMillis());
					return new TranslationResult(key, translatedText, TranslationStatus.TRANSLATED_FAST,
							TranslationEngineType.FAST, null, duration);
				}
			} finally {
				encoderIdsTensor.close();
				encoderMaskTensor.close();
			}
		} catch (OrtException e) {
			Duration duration = Duration.ofNanos(System.nanoTime() - startNanos);
			logger.warn("Fast NLLB translation failed for key '{}' after {} ms: {}",
					key.path(), duration.toMillis(), e.getMessage());
			throw new IllegalStateException(
					"Fast NLLB translation failed for key '" + key.path() + "': " + e.getMessage(), e);
		}
	}

	/**
	 * Releases all native resources: tokenizer, ONNX sessions and the ONNX environment.
	 *
	 * <p>Invoked automatically by Spring on context shutdown. Idempotent — a second call is a no-op.
	 */
	@PreDestroy
	public void close() {
		if (closed) {
			return;
		}
		closed = true;
		logger.info("Closing fast NLLB engine (tokenizer, ONNX sessions and environment)");
		closeQuietly(tokenizer, encoderSession, decoderSession, environment);
	}

	private void validateModelAssets() {
		validateFile(modelDir.resolve(TOKENIZER_FILE), "NLLB tokenizer file");
		validateFile(modelDir.resolve(ONNX_SUBDIR).resolve(ENCODER_FILE), "NLLB encoder ONNX model");
		validateFile(modelDir.resolve(ONNX_SUBDIR).resolve(DECODER_FILE), "NLLB decoder ONNX model");
	}

	private void validateFile(Path file, String description) {
		if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
			throw new IllegalStateException(
					"Fast NLLB engine cannot start: " + description + " is missing or unreadable at '" + file + "'");
		}
	}

	private OrtSession createSession(OrtEnvironment ortEnvironment, String modelFile) throws OrtException {
		Path modelPath = modelDir.resolve(ONNX_SUBDIR).resolve(modelFile);
		OrtSession.SessionOptions options = new OrtSession.SessionOptions();
		try {
			// The quantized NLLB graphs fail graph optimization at EXTENDED/ALL level
			// (QDQ pass "TransposeDQWeightsForMatMulNBits" reports a missing scale for
			// model.shared.weight_transposed_DequantizeLinear), so only basic optimizations
			// (constant folding, redundant computation elimination) are enabled.
			options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT);
			return ortEnvironment.createSession(modelPath.toString(), options);
		} finally {
			options.close();
		}
	}

	private Map<String, Long> resolveLanguageTokenIds() {
		Map<String, Long> tokenIds = new LinkedHashMap<>();
		for (String tag : languageMapper.supportedTags()) {
			long[] ids = tokenizer.encode(tag, false, false).getIds();
			if (ids.length != 1) {
				throw new IllegalStateException("NLLB language tag '" + tag
						+ "' does not map to exactly one token id in tokenizer.json (got " + ids.length + ")");
			}
			tokenIds.put(tag, ids[0]);
		}
		return tokenIds;
	}

	private DecoderGraphInfo inspectDecoderGraph(OrtSession ortDecoderSession) throws OrtException {
		Map<String, NodeInfo> inputs = ortDecoderSession.getInputInfo();
		Map<String, NodeInfo> outputs = ortDecoderSession.getOutputInfo();
		List<String> pastInputs = new ArrayList<>();
		long[] pastShape = null;
		for (Map.Entry<String, NodeInfo> entry : inputs.entrySet()) {
			String name = entry.getKey();
			if (name.startsWith(PAST_PREFIX)) {
				pastInputs.add(name);
				if (pastShape == null) {
					long[] shape = tensorShapeOf(entry.getValue());
					if (shape != null && shape.length == 4 && shape[1] > 0 && shape[3] > 0) {
						pastShape = new long[] { BATCH_SIZE, shape[1], 0L, shape[3] };
					}
				}
			}
		}
		if (pastShape == null) {
			pastShape = new long[] { BATCH_SIZE, DEFAULT_NUM_HEADS, 0L, DEFAULT_HEAD_DIM };
		}
		pastInputs.sort(PAST_NAME_COMPARATOR);
		List<String> pastOutputs = new ArrayList<>();
		for (String name : outputs.keySet()) {
			if (name.startsWith(PRESENT_PREFIX)) {
				pastOutputs.add(name);
			}
		}
		pastOutputs.sort(PAST_NAME_COMPARATOR);
		if (pastInputs.isEmpty() || pastInputs.size() != pastOutputs.size()) {
			throw new IllegalStateException("Decoder model does not expose a usable KV-cache interface: "
					+ pastInputs.size() + " 'past_key_values.*' inputs and " + pastOutputs.size()
					+ " 'present.*' outputs found in '" + DECODER_FILE + "'");
		}
		return new DecoderGraphInfo(List.copyOf(pastInputs), List.copyOf(pastOutputs), pastShape,
				INPUT_IDS, ENCODER_HIDDEN_STATES, ENCODER_ATTENTION_MASK, USE_CACHE_BRANCH);
	}

	private long[] tensorShapeOf(NodeInfo nodeInfo) {
		ValueInfo info = nodeInfo.getInfo();
		if (info instanceof TensorInfo tensorInfo) {
			return tensorInfo.getShape();
		}
		return null;
	}

	/**
	 * Runs the greedy autoregressive decoder loop over the encoder hidden states.
	 *
	 * <p>The merged decoder graph is an {@code If} node with two branches selected by the
	 * {@code use_cache_branch} input: the no-cache branch (first step) computes the full key/value
	 * tensors of every attention head, while the cache branch (subsequent steps) concatenates the
	 * self-attention key/values with the past ones and reuses the cross-attention key/values cached
	 * at the first step. The encoder key/value tensors returned at the first step are therefore kept
	 * unchanged for every following step; the encoder key/value outputs of the cache branch are empty
	 * constants and are ignored.
	 *
	 * <p>The decoder is seeded following the Hugging Face generation convention for NLLB: the first
	 * decoder input is the EOS token (the exported model's {@code decoder_start_token_id}), and the
	 * first generated token is forced to the target Flores-200 language tag (the
	 * {@code forced_bos_token_id} semantics) that the model needs to select the target language.
	 * Subsequent tokens are picked greedily by argmax over the last logits position until EOS or the
	 * configured token budget is exhausted.
	 *
	 * @param encoderHiddenStates the encoder output tensor, owned by the caller's encoder result
	 * @param targetLangId        the token id of the target Flores-200 language tag
	 * @param encoderMask         the encoder attention mask of the source sequence
	 * @return the generated token ids (the EOS seed, the forced language tag, the decoded tokens and
	 *         the trailing EOS if the decoder stopped), never {@code null}
	 * @throws OrtException if an ONNX inference or tensor operation fails
	 */
	private List<Long> decode(OnnxTensor encoderHiddenStates, long targetLangId, long[] encoderMask)
			throws OrtException {
		List<Long> generated = new ArrayList<>();
		generated.add(EOS_TOKEN_ID);
		OnnxTensor encoderAttentionMaskTensor = OnnxTensor.createTensor(environment, new long[][] { encoderMask });
		Map<String, OnnxTensor> pastTensors = createEmptyPastTensors();
		boolean useCacheBranch = false;
		try {
			for (int step = 0; step < maxNewTokens; step++) {
				OnnxTensor decoderInputIdsTensor = OnnxTensor
						.createTensor(environment, new long[][] { toLongArray(generated) });
				OnnxTensor useCacheBranchTensor = OnnxTensor.createTensor(environment, new boolean[] { useCacheBranch });
				try {
					Map<String, OnnxTensor> inputs = new LinkedHashMap<>();
					inputs.put(decoderInputIdsName, decoderInputIdsTensor);
					inputs.put(decoderEncoderHiddenStatesName, encoderHiddenStates);
					inputs.put(decoderEncoderAttentionMaskName, encoderAttentionMaskTensor);
					inputs.putAll(pastTensors);
					inputs.put(decoderUseCacheBranchName, useCacheBranchTensor);
					try (OrtSession.Result result = decoderSession.run(inputs)) {
						// First step: the model generates the target language tag as its very first
						// token (Hugging Face forced_bos_token_id), so the argmax logits are skipped.
						long nextTokenId = useCacheBranch ? argmaxLastPosition(findTensor(result, LOGITS)) : targetLangId;
						generated.add(nextTokenId);
						if (nextTokenId == EOS_TOKEN_ID) {
							break;
						}
						if (useCacheBranch) {
							// Cache path: decoder presents are real, encoder presents are empty
							// constants, so the encoder past from the first step is kept as-is.
							Map<String, OnnxTensor> nextPast = replaceDecoderPast(pastTensors, result);
							closeTensors(decoderPastOf(pastTensors));
							pastTensors = nextPast;
						} else {
							// First step, no-cache path: all presents are real, full key/values.
							Map<String, OnnxTensor> nextPast = new LinkedHashMap<>();
							for (int i = 0; i < pastOutputNames.size(); i++) {
								OnnxTensor presentTensor = findTensor(result, pastOutputNames.get(i));
								nextPast.put(pastInputNames.get(i), copyTensor(presentTensor));
							}
							closeTensors(pastTensors.values());
							pastTensors = nextPast;
						}
					}
				} finally {
					decoderInputIdsTensor.close();
					useCacheBranchTensor.close();
				}
				useCacheBranch = true;
			}
		} finally {
			encoderAttentionMaskTensor.close();
			closeTensors(pastTensors.values());
		}
		return generated;
	}

	/**
	 * Returns a copy of the given past map where every self-attention (decoder) tensor is replaced by
	 * a copy of the corresponding {@code present.*} decoder output of the last decoder run. The
	 * cross-attention (encoder) tensors are shared unchanged, as the cache branch never updates them.
	 *
	 * @param currentPast the past map consumed by the last decoder run
	 * @param result      the last decoder run result, still open
	 * @return a new past map ready for the next decoder run
	 * @throws OrtException if an ONNX tensor operation fails
	 */
	private Map<String, OnnxTensor> replaceDecoderPast(Map<String, OnnxTensor> currentPast, OrtSession.Result result)
			throws OrtException {
		Map<String, OnnxTensor> nextPast = new LinkedHashMap<>(currentPast);
		for (int i = 0; i < pastOutputNames.size(); i++) {
			String pastInputName = pastInputNames.get(i);
			if (isDecoderPastName(pastInputName)) {
				OnnxTensor presentTensor = findTensor(result, pastOutputNames.get(i));
				nextPast.put(pastInputName, copyTensor(presentTensor));
			}
		}
		return nextPast;
	}

	private boolean isDecoderPastName(String name) {
		return name.startsWith(PAST_PREFIX) && name.split("\\.")[2].equals("decoder");
	}

	private Iterable<OnnxTensor> decoderPastOf(Map<String, OnnxTensor> pastTensors) {
		List<OnnxTensor> decoderTensors = new ArrayList<>();
		for (Map.Entry<String, OnnxTensor> entry : pastTensors.entrySet()) {
			if (isDecoderPastName(entry.getKey())) {
				decoderTensors.add(entry.getValue());
			}
		}
		return decoderTensors;
	}

	private long argmaxLastPosition(OnnxTensor logitsTensor) throws OrtException {
		TensorInfo info = logitsTensor.getInfo();
		long[] shape = info.getShape();
		int sequenceLength = (int) shape[1];
		int vocabSize = (int) shape[2];
		FloatBuffer logits = logitsTensor.getFloatBuffer();
		int lastPositionOffset = (sequenceLength - 1) * vocabSize;
		long bestIndex = 0L;
		float bestScore = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < vocabSize; i++) {
			float score = logits.get(lastPositionOffset + i);
			if (score > bestScore) {
				bestScore = score;
				bestIndex = i;
			}
		}
		return bestIndex;
	}

	private OnnxTensor copyTensor(OnnxTensor source) throws OrtException {
		TensorInfo info = source.getInfo();
		long[] shape = info.getShape();
		int numElements = Math.toIntExact(info.getNumElements());
		FloatBuffer buffer = source.getFloatBuffer().duplicate();
		float[] data = new float[numElements];
		buffer.get(data);
		return OnnxTensor.createTensor(environment, FloatBuffer.wrap(data), shape);
	}

	private Map<String, OnnxTensor> createEmptyPastTensors() throws OrtException {
		Map<String, OnnxTensor> tensors = new LinkedHashMap<>();
		FloatBuffer empty = FloatBuffer.allocate(0);
		for (String name : pastInputNames) {
			tensors.put(name, OnnxTensor.createTensor(environment, empty, initialPastShape));
		}
		return tensors;
	}

	private void closeTensors(Iterable<OnnxTensor> tensors) {
		for (OnnxTensor tensor : tensors) {
			try {
				tensor.close();
			} catch (Exception e) {
				logger.warn("Failed to close ONNX tensor: {}", e.getMessage());
			}
		}
	}

	private OnnxTensor findTensor(OrtSession.Result result, String outputName) {
		for (Map.Entry<String, OnnxValue> entry : result) {
			if (entry.getKey().equals(outputName)) {
				return (OnnxTensor) entry.getValue();
			}
		}
		throw new IllegalStateException("ONNX model did not produce expected output '" + outputName + "'");
	}

	private long[] buildEncoderInputIds(long sourceLangId, long[] sourceIds) {
		int contentLength = Math.min(sourceIds.length, MAX_ENCODER_SEQUENCE_LENGTH - 2);
		long[] inputIds = new long[contentLength + 2];
		inputIds[0] = sourceLangId;
		System.arraycopy(sourceIds, 0, inputIds, 1, contentLength);
		inputIds[inputIds.length - 1] = EOS_TOKEN_ID;
		return inputIds;
	}

	private long[] ones(int length) {
		long[] mask = new long[length];
		Arrays.fill(mask, 1L);
		return mask;
	}

	private long[] toLongArray(List<Long> values) {
		long[] result = new long[values.size()];
		for (int i = 0; i < values.size(); i++) {
			result[i] = values.get(i);
		}
		return result;
	}

	private static void closeQuietly(AutoCloseable... resources) {
		for (AutoCloseable resource : resources) {
			if (resource != null) {
				try {
					resource.close();
				} catch (Exception e) {
					logger.warn("Failed to close native resource during fast NLLB engine shutdown: {}", e.getMessage());
				}
			}
		}
	}

	/**
	 * Introspected interface of the merged decoder graph: cache tensor names and shapes.
	 *
	 * @param pastInputNames               sorted {@code past_key_values.N.decoder|encoder.key|value} input names
	 * @param pastOutputNames              sorted {@code present.N.decoder|encoder.key|value} output names
	 * @param initialPastShape             shape of the empty KV-cache tensors used at step zero
	 * @param inputIdsName                 name of the {@code input_ids} input
	 * @param encoderHiddenStatesName      name of the {@code encoder_hidden_states} input
	 * @param encoderAttentionMaskName     name of the {@code encoder_attention_mask} input
	 * @param useCacheBranchName           name of the {@code use_cache_branch} input
	 */
	private record DecoderGraphInfo(List<String> pastInputNames, List<String> pastOutputNames, long[] initialPastShape,
			String inputIdsName, String encoderHiddenStatesName, String encoderAttentionMaskName,
			String useCacheBranchName) {
	}
}
