package com.lucalzt.mctranslator.infrastructure.adapter.out.nllb;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Startup validation tests for {@link FastNllbAdapter}.
 *
 * <p>All scenarios fail before any model is loaded (validation and first-load step), so the suite
 * stays fast and does not depend on the real local model assets.
 */
@NullMarked
class FastNllbAdapterInitTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("Should fail with a descriptive error when the tokenizer file is missing")
	void shouldFailWhenTokenizerMissing() throws IOException {
		createOnnxDirOnly();

		assertThatThrownBy(() -> newAdapter())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("tokenizer")
				.hasMessageContaining("missing or unreadable");
	}

	@Test
	@DisplayName("Should fail with a descriptive error when the encoder model is missing")
	void shouldFailWhenEncoderMissing() throws IOException {
		createTokenizerOnly();

		assertThatThrownBy(() -> newAdapter())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("encoder")
				.hasMessageContaining("missing or unreadable");
	}

	@Test
	@DisplayName("Should fail with a descriptive error when the decoder model is missing")
	void shouldFailWhenDecoderMissing() throws IOException {
		createEncoderOnly();

		assertThatThrownBy(() -> newAdapter())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("decoder")
				.hasMessageContaining("missing or unreadable");
	}

	@Test
	@DisplayName("Should fail with a descriptive error when the tokenizer file is corrupt")
	void shouldFailWhenTokenizerCorrupt() throws IOException {
		createFullLayout();
		Files.writeString(tempDir.resolve("tokenizer.json"), "this is not a tokenizer json");

		assertThatThrownBy(() -> newAdapter())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("Failed to initialize fast NLLB engine");
	}

	@Test
	@DisplayName("Should fail when the configured token budget is not positive")
	void shouldFailOnNonPositiveTokenBudget() {
		NllbEngineProperties properties = propertiesFor(tempDir);
		properties.setMaxNewTokens(0);

		assertThatThrownBy(() -> new FastNllbAdapter(properties, new NllbLanguageMapper()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("maxNewTokens must be at least 1");
	}

	@Test
	@DisplayName("Should reject null constructor arguments")
	void shouldRejectNullArguments() {
		assertThatThrownBy(() -> new FastNllbAdapter(null, new NllbLanguageMapper()))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new FastNllbAdapter(propertiesFor(tempDir), null))
				.isInstanceOf(NullPointerException.class);
	}

	private FastNllbAdapter newAdapter() {
		return new FastNllbAdapter(propertiesFor(tempDir), new NllbLanguageMapper());
	}

	private static NllbEngineProperties propertiesFor(Path dir) {
		NllbEngineProperties properties = new NllbEngineProperties();
		properties.setModelDir(dir.toString());
		return properties;
	}

	private void createOnnxDirOnly() throws IOException {
		Files.createDirectories(tempDir.resolve("onnx"));
	}

	private void createTokenizerOnly() throws IOException {
		Files.writeString(tempDir.resolve("tokenizer.json"), "{}");
		Files.createDirectories(tempDir.resolve("onnx"));
	}

	private void createEncoderOnly() throws IOException {
		createTokenizerOnly();
		Files.write(tempDir.resolve("onnx").resolve("encoder_model_quantized.onnx"), new byte[] { 1, 2, 3 });
	}

	private void createFullLayout() throws IOException {
		createEncoderOnly();
		Files.write(tempDir.resolve("onnx").resolve("decoder_model_merged_quantized.onnx"), new byte[] { 1, 2, 3 });
	}
}
