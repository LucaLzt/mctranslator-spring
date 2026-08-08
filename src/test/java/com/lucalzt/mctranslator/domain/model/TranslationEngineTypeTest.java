package com.lucalzt.mctranslator.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TranslationEngineType}, pinning the exact two engine
 * types in spec order (R12). Pure JUnit 6 + AssertJ, no Spring context.
 */
class TranslationEngineTypeTest {

	@Test
	@DisplayName("Exposes exactly the two engine types in spec order")
	void exposesExactlyTheTwoEngines() {
		assertThat(TranslationEngineType.values()).containsExactly(
				TranslationEngineType.FAST,
				TranslationEngineType.PRECISE);
	}
}
