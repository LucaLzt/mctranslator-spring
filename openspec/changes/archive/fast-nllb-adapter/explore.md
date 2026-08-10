# Exploration Report: Fast NLLB-200 ONNX Adapter (`fast-nllb-adapter`)

## 1. Overview & Objectives
Analyze the codebase, architecture (`hexagonal-architecture`), technology stack (`java-springboot`), and NLLB-200 ONNX model assets in `models/nllb/` to design and implement `FastNllbAdapter` implementing `TranslationEnginePort` using `com.microsoft.onnxruntime` (version 1.26.0) and Hugging Face tokenizers.

## 2. Relevant Code Paths & Architecture
- **Domain Port**: `com.lucalzt.mctranslator.domain.port.out.TranslationEnginePort` defines `translate(TranslationKey key, LanguageCode sourceLang, LanguageCode targetLang, TranslationEngineType engineType)`.
- **Domain Models**: `TranslationKey`, `TranslationResult`, `TranslationEngineType` (`FAST`, `PRECISE`), `LanguageCode`.
- **Existing Adapters**: `JsonGlossaryAdapter`, `SqliteTranslationCacheAdapter` (demonstrate structure, dependency injection, logging with SLF4J, exception handling, and `@Repository` / `@NullMarked` annotations).
- **Target Package for New Adapter**: `com.lucalzt.mctranslator.infrastructure.adapter.out.nllb`.

## 3. Model Asset Analysis (`models/nllb/`)
- **Model Architecture**: NLLB-200 Distilled 600M (`m2m_100`, sequence-to-sequence transformer).
- **Files available in `models/nllb/`**:
  - `config.json`, `generation_config.json`, `tokenizer_config.json`, `special_tokens_map.json`
  - `tokenizer.json` (Hugging Face tokenizer configuration and vocabulary)
  - `sentencepiece.bpe.model` (SentencePiece subword model)
  - `onnx/encoder_model_quantized.onnx` (Quantized encoder model)
  - `onnx/decoder_model_merged_quantized.onnx` (Quantized merged decoder model with KV-cache)

## 4. Integration Approach for ONNX Runtime & Tokenization
- **ONNX Runtime**: `com.microsoft.onnxruntime:onnxruntime` (version 1.26.0). Runs embedded within the JVM/Native process without external subprocesses.
- **Tokenization**: `ai.djl.huggingface:tokenizers` (or direct JSON/SentencePiece processing) to load `models/nllb/tokenizer.json`. `HuggingFaceTokenizer` handles encoding text to token IDs, attention masks, and decoding token IDs back to text.
- **Language Token Mapping**: NLLB-200 uses specific language tokens (e.g., `spa_Latn` for Spanish, `eng_Latn` for English). The adapter must map `LanguageCode` (e.g. `es`, `en`) to NLLB target language tokens (e.g. `spa_Latn`, `eng_Latn`) or use tokenizer special token API.
- **Inference Pipeline**:
  1. Encode source text using tokenizer.
  2. Run Encoder session (`encoder_model_quantized.onnx`) with `input_ids` and `attention_mask` to obtain encoder hidden states.
  3. Run Decoder session (`decoder_model_merged_quantized.onnx`) autoregressively starting with target language token and BOS token, up to max length or EOS token.
  4. Decode output token IDs back to translated text.

## 5. Candidate Approaches & Tradeoffs
| Approach | Pros | Cons | Recommendation |
|---|---|---|---|
| **A. Embedded ONNX Runtime + DJL Tokenizers + NLLB ONNX Sessions** | Pure embedded execution, zero external subprocesses, high performance (~100 words/s), meets hexagonal architecture and CLI design goals. | Requires careful memory management and tensor handling in ONNX Runtime Java API. | **Recommended** |
| **B. Python/External Subprocess Translation Service** | Easier Python-side setup. | Violates requirement for embedded local CLI tool without external server dependencies for fast engine. | **Rejected** |

## 6. Risks and Unknowns
- **Memory footprint**: NLLB-200 600M quantized consumes ~1.5 GB RAM during inference.
- **Native Image Compatibility**: ONNX Runtime JNI libraries need correct platform configuration for GraalVM Native Image compilation.
- **Language Code Resolution**: Robust mapping between standard language codes (`es`, `en`) and NLLB language identifiers (`spa_Latn`, `eng_Latn`).

## 7. Recommendation & Next Steps
1. Add `com.microsoft.onnxruntime:onnxruntime` (1.26.0) and `ai.djl.huggingface:tokenizers` dependencies to `pom.xml`.
2. Create `FastNllbAdapter` in `infrastructure.adapter.out.nllb` implementing `TranslationEnginePort`.
3. Implement unit and integration tests under `src/test/java/com/lucalzt/mctranslator/infrastructure/adapter/out/nllb/`.
