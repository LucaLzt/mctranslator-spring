package com.lucalzt.mctranslator.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TranslationResult}, covering validation rejection (R13),
 * the total-failure and cache-hit scenarios (R13), the five-outcomes requirement
 * (R11 scenario 2), the per-status engine/warning nullability contract (R13, D4)
 * and equality by all components (R16). Pure JUnit 6 + AssertJ, no Spring
 * context.
 */
class TranslationResultTest {

	private static final JsonPath PATH = new JsonPath("quest.description.task1");
	private static final String ORIGINAL_TEXT = "Complete the quest";
	private static final LanguageCode LANGUAGE = new LanguageCode("es");
	private static final ModpackId MODPACK = new ModpackId("BetterMC", "1.21.1");
	private static final TranslationKey KEY = new TranslationKey(PATH, ORIGINAL_TEXT, LANGUAGE, MODPACK);
	private static final String TRANSLATED_TEXT = "Completa la mision";
	private static final Duration DURATION = Duration.ofMillis(42);

	@Test
	@DisplayName("Accepts a valid result and exposes all six components via accessors")
	void acceptsValidResult() {
		TranslationResult result = new TranslationResult(KEY, TRANSLATED_TEXT, TranslationStatus.TRANSLATED_FAST,
				TranslationEngineType.FAST, null, DURATION);

		assertThat(result.key()).isEqualTo(KEY);
		assertThat(result.translatedText()).isEqualTo(TRANSLATED_TEXT);
		assertThat(result.status()).isEqualTo(TranslationStatus.TRANSLATED_FAST);
		assertThat(result.engine()).isEqualTo(TranslationEngineType.FAST);
		assertThat(result.warning()).isNull();
		assertThat(result.duration()).isEqualTo(DURATION);
	}

	@Test
	@DisplayName("Rejects a null key, translatedText, status or duration with NullPointerException")
	void rejectsNullComponents() {
		assertThatThrownBy(() -> new TranslationResult(null, TRANSLATED_TEXT, TranslationStatus.TRANSLATED_FAST,
				TranslationEngineType.FAST, null, DURATION)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new TranslationResult(KEY, null, TranslationStatus.TRANSLATED_FAST,
				TranslationEngineType.FAST, null, DURATION)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new TranslationResult(KEY, TRANSLATED_TEXT, null, TranslationEngineType.FAST, null,
				DURATION)).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new TranslationResult(KEY, TRANSLATED_TEXT, TranslationStatus.TRANSLATED_FAST,
				TranslationEngineType.FAST, null, null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Rejects a negative duration with IllegalArgumentException")
	void rejectsNegativeDuration() {
		assertThatThrownBy(() -> new TranslationResult(KEY, TRANSLATED_TEXT, TranslationStatus.CACHE_HIT, null, null,
				Duration.ofMillis(-1))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Accepts a zero duration (cache hits can be instant)")
	void acceptsZeroDuration() {
		TranslationResult result = new TranslationResult(KEY, TRANSLATED_TEXT, TranslationStatus.CACHE_HIT, null, null,
				Duration.ZERO);

		assertThat(result.duration()).isZero();
	}

	@Test
	@DisplayName("Total failure keeps the original text and carries a warning (R13 scenario 2)")
	void totalFailureReturnsOriginalTextWithWarning() {
		TranslationKey fallbackKey = new TranslationKey(PATH, "Hello", LANGUAGE, MODPACK);
		TranslationResult result = new TranslationResult(fallbackKey, "Hello", TranslationStatus.FALLBACK_TO_ORIGINAL,
				null, "both engines failed", DURATION);

		assertThat(result.translatedText()).isEqualTo(fallbackKey.originalText());
		assertThat(result.warning()).isNotBlank();
		assertThat(result.engine()).isNull();
	}

	@Test
	@DisplayName("Cache hit allows a null engine and preserves the other components (R13 scenario 3)")
	void cacheHitAllowsNullEngine() {
		TranslationResult result = new TranslationResult(KEY, TRANSLATED_TEXT, TranslationStatus.CACHE_HIT, null, null,
				DURATION);

		assertThat(result.engine()).isNull();
		assertThat(result.key()).isEqualTo(KEY);
		assertThat(result.status()).isEqualTo(TranslationStatus.CACHE_HIT);
		assertThat(result.translatedText()).isEqualTo(TRANSLATED_TEXT);
		assertThat(result.duration()).isEqualTo(DURATION);
	}

	@Test
	@DisplayName("All five statuses are representable and preserved by the result (R11 scenario 2)")
	void fiveOutcomesAreRepresentable() {
		for (TranslationStatus status : TranslationStatus.values()) {
			TranslationResult result = new TranslationResult(KEY, TRANSLATED_TEXT, status, null, null, DURATION);

			assertThat(result.status()).isEqualTo(status);
		}
	}

	@Test
	@DisplayName("Engine and warning follow the per-status nullability contract (R13, D4)")
	void perStatusEngineAndWarningShape() {
		TranslationResult cacheHit = new TranslationResult(KEY, TRANSLATED_TEXT, TranslationStatus.CACHE_HIT, null,
				null, DURATION);
		assertThat(cacheHit.engine()).isNull();
		assertThat(cacheHit.warning()).isNull();

		TranslationResult fast = new TranslationResult(KEY, TRANSLATED_TEXT, TranslationStatus.TRANSLATED_FAST,
				TranslationEngineType.FAST, null, DURATION);
		assertThat(fast.engine()).isEqualTo(TranslationEngineType.FAST);
		assertThat(fast.warning()).isNull();

		TranslationResult precise = new TranslationResult(KEY, TRANSLATED_TEXT, TranslationStatus.TRANSLATED_PRECISE,
				TranslationEngineType.PRECISE, null, DURATION);
		assertThat(precise.engine()).isEqualTo(TranslationEngineType.PRECISE);
		assertThat(precise.warning()).isNull();

		TranslationResult degraded = new TranslationResult(KEY, TRANSLATED_TEXT, TranslationStatus.DEGRADED_TO_FAST,
				TranslationEngineType.FAST, "precise engine failed", DURATION);
		assertThat(degraded.engine()).isEqualTo(TranslationEngineType.FAST);
		assertThat(degraded.warning()).isNotBlank();

		TranslationResult fallback = new TranslationResult(KEY, TRANSLATED_TEXT, TranslationStatus.FALLBACK_TO_ORIGINAL,
				null, "both engines failed", DURATION);
		assertThat(fallback.engine()).isNull();
		assertThat(fallback.warning()).isNotBlank();
	}

	@Test
	@DisplayName("Equality is by all six components with consistent hash codes")
	void equalityIsByAllComponents() {
		TranslationResult first = new TranslationResult(KEY, TRANSLATED_TEXT, TranslationStatus.TRANSLATED_FAST,
				TranslationEngineType.FAST, null, DURATION);
		TranslationResult second = new TranslationResult(KEY, TRANSLATED_TEXT, TranslationStatus.TRANSLATED_FAST,
				TranslationEngineType.FAST, null, DURATION);
		TranslationResult differentText = new TranslationResult(KEY, "otra traduccion",
				TranslationStatus.TRANSLATED_FAST, TranslationEngineType.FAST, null, DURATION);

		assertThat(first).isEqualTo(second);
		assertThat(first.hashCode()).isEqualTo(second.hashCode());
		assertThat(first).isNotEqualTo(differentText);
	}
}
