package com.lucalzt.mctranslator.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TranslationStatus}, pinning the exact five pipeline
 * outcomes in spec order (R11). Pure JUnit 6 + AssertJ, no Spring context.
 */
class TranslationStatusTest {

	@Test
	@DisplayName("Exposes exactly the five pipeline outcomes in spec order")
	void exposesExactlyTheFiveOutcomes() {
		assertThat(TranslationStatus.values()).containsExactly(
				TranslationStatus.CACHE_HIT,
				TranslationStatus.TRANSLATED_FAST,
				TranslationStatus.TRANSLATED_PRECISE,
				TranslationStatus.DEGRADED_TO_FAST,
				TranslationStatus.FALLBACK_TO_ORIGINAL);
	}
}
