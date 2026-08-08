package com.lucalzt.mctranslator.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link LanguageCode}, covering validation rejection (R8) and
 * value equality (R16). Pure JUnit 6 + AssertJ, no Spring context.
 */
class LanguageCodeTest {

	@Test
	@DisplayName("Accepts engine language codes without normalization and exposes them via value()")
	void acceptsValidCodes() {
		assertThat(new LanguageCode("es").value()).isEqualTo("es");
		assertThat(new LanguageCode("spa_Latn").value()).isEqualTo("spa_Latn");
	}

	@Test
	@DisplayName("Rejects a null code with NullPointerException")
	void rejectsNullCode() {
		assertThatThrownBy(() -> new LanguageCode(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Rejects blank codes with IllegalArgumentException")
	void rejectsBlankCode() {
		assertThatThrownBy(() -> new LanguageCode("   ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Equality is by value with consistent hash codes")
	void equalityIsByValue() {
		LanguageCode first = new LanguageCode("es");
		LanguageCode second = new LanguageCode("es");
		LanguageCode different = new LanguageCode("en_US");

		assertThat(first).isEqualTo(second);
		assertThat(first.hashCode()).isEqualTo(second.hashCode());
		assertThat(first).isNotEqualTo(different);
	}
}
