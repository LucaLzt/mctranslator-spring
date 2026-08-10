package com.lucalzt.mctranslator.infrastructure.adapter.out.nllb;

import java.time.Duration;

import com.lucalzt.mctranslator.domain.model.JsonPath;
import com.lucalzt.mctranslator.domain.model.LanguageCode;
import com.lucalzt.mctranslator.domain.model.ModpackId;
import com.lucalzt.mctranslator.domain.model.TranslationEngineType;
import com.lucalzt.mctranslator.domain.model.TranslationKey;
import com.lucalzt.mctranslator.domain.model.TranslationResult;
import com.lucalzt.mctranslator.domain.model.TranslationStatus;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * End-to-end integration tests for {@link FastNllbAdapter} against the real local NLLB model assets.
 *
 * <p>These tests load the full ONNX models (roughly 850 MB) and are disabled by default. Enable them
 * manually with {@code -Dmctranslator.it.nllb=true} only when the model directory {@code models/nllb}
 * is present (see the change's {@code apply-progress.md} for how to run the one-off validation).
 */
@NullMarked
@EnabledIfSystemProperty(named = "mctranslator.it.nllb", matches = "true")
class FastNllbAdapterIntegrationTest {

	private static final ModpackId MODPACK = new ModpackId("BetterMC", "1.21.1");
	private static final JsonPath PATH = new JsonPath("lang/en_us.json#/item.minecraft.diamond");
	private static final LanguageCode SOURCE_LANG = new LanguageCode("en");
	private static final LanguageCode TARGET_LANG = new LanguageCode("es");

	private static FastNllbAdapter adapter;

	@BeforeAll
	static void setUpAll() {
		adapter = new FastNllbAdapter(new NllbEngineProperties(), new NllbLanguageMapper());
	}

	@AfterAll
	static void tearDownAll() {
		if (adapter != null) {
			adapter.close();
		}
	}

	@Test
	@DisplayName("Should translate an English key to Spanish with the fast engine")
	void shouldTranslateToSpanish() {
		TranslationKey key = key("Hello World");
		TranslationResult result = adapter.translate(key, SOURCE_LANG, TARGET_LANG, TranslationEngineType.FAST);

		assertThat(result.key()).isEqualTo(key);
		assertThat(result.translatedText()).isNotBlank();
		assertThat(result.status()).isEqualTo(TranslationStatus.TRANSLATED_FAST);
		assertThat(result.engine()).isEqualTo(TranslationEngineType.FAST);
		assertThat(result.warning()).isNull();
		assertThat(result.duration()).isGreaterThanOrEqualTo(Duration.ZERO);
	}

	@Test
	@DisplayName("Should translate a Minecraft-style key with format specifiers")
	void shouldTranslateMinecraftStyleKey() {
		TranslationResult result = adapter.translate(
				key("Craft %s on the crafting table"), SOURCE_LANG, TARGET_LANG, TranslationEngineType.FAST);

		assertThat(result.translatedText()).isNotBlank();
		assertThat(result.status()).isEqualTo(TranslationStatus.TRANSLATED_FAST);
	}

	@Test
	@DisplayName("Should reject engine types other than FAST")
	void shouldRejectNonFastEngineType() {
		assertThatThrownBy(() -> adapter.translate(key("Hello World"), SOURCE_LANG, TARGET_LANG,
				TranslationEngineType.PRECISE)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Should reject unsupported language codes")
	void shouldRejectUnsupportedLanguageCode() {
		assertThatThrownBy(() -> adapter.translate(key("Hello World"), new LanguageCode("xx"), TARGET_LANG,
				TranslationEngineType.FAST)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Should guard translation after close and keep close idempotent")
	void shouldGuardAfterCloseAndKeepCloseIdempotent() {
		FastNllbAdapter localAdapter = new FastNllbAdapter(new NllbEngineProperties(), new NllbLanguageMapper());
		try {
			localAdapter.close();
			localAdapter.close();

			assertThatThrownBy(() -> localAdapter.translate(key("Hello World"), SOURCE_LANG, TARGET_LANG,
					TranslationEngineType.FAST)).isInstanceOf(IllegalStateException.class);
		} finally {
			localAdapter.close();
		}
	}

	@Test
	@DisplayName("Should produce a coherent Spanish output for a known greeting")
	void shouldProduceCoherentSpanish() {
		TranslationResult result = adapter.translate(key("Hello World"), SOURCE_LANG, TARGET_LANG,
				TranslationEngineType.FAST);

		assertThat(result.duration()).isGreaterThanOrEqualTo(Duration.ZERO);
		assertThat(result.translatedText()).contains("ola").as("Spanish translation of 'Hello World'");
	}

	private static TranslationKey key(String text) {
		return new TranslationKey(PATH, text, TARGET_LANG, MODPACK);
	}
}
