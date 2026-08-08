package com.lucalzt.mctranslator.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GlossaryEntry}, covering validation rejection (R14) and
 * value equality by all three components (R16). Pure JUnit 6 + AssertJ, no
 * Spring context.
 */
class GlossaryEntryTest {

	private static final String TERM = "Netherite";
	private static final String TRANSLATION = "Netherita";
	private static final GlossaryEntryClassification CLASSIFICATION = GlossaryEntryClassification.PLAIN;

	@Test
	@DisplayName("Accepts a valid term, translation and classification and exposes them via accessors")
	void acceptsValidEntry() {
		GlossaryEntry entry = new GlossaryEntry(TERM, TRANSLATION, CLASSIFICATION);

		assertThat(entry.term()).isEqualTo(TERM);
		assertThat(entry.translation()).isEqualTo(TRANSLATION);
		assertThat(entry.classification()).isEqualTo(CLASSIFICATION);
	}

	@Test
	@DisplayName("Rejects a null term, null translation or null classification with NullPointerException")
	void rejectsNullComponents() {
		assertThatThrownBy(() -> new GlossaryEntry(null, TRANSLATION, CLASSIFICATION))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new GlossaryEntry(TERM, null, CLASSIFICATION))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new GlossaryEntry(TERM, TRANSLATION, null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Rejects a blank term or a blank translation with IllegalArgumentException")
	void rejectsBlankComponents() {
		assertThatThrownBy(() -> new GlossaryEntry("", TRANSLATION, CLASSIFICATION))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new GlossaryEntry(TERM, "   ", CLASSIFICATION))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Equality is by all three components with consistent hash codes")
	void equalityIsByAllComponents() {
		GlossaryEntry first = new GlossaryEntry(TERM, TRANSLATION, CLASSIFICATION);
		GlossaryEntry second = new GlossaryEntry(TERM, TRANSLATION, CLASSIFICATION);
		GlossaryEntry differentClassification = new GlossaryEntry(TERM, TRANSLATION, GlossaryEntryClassification.LORE);

		assertThat(first).isEqualTo(second);
		assertThat(first.hashCode()).isEqualTo(second.hashCode());
		assertThat(first).isNotEqualTo(differentClassification);
	}
}
