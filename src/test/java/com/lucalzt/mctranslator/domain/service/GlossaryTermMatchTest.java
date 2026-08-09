package com.lucalzt.mctranslator.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lucalzt.mctranslator.domain.model.GlossaryEntryClassification;

/**
 * Unit tests for {@link GlossaryTermMatch}, covering null/blank rejection (R11)
 * and value equality by both components (R11). Pure JUnit 6 + AssertJ, no
 * Spring context.
 */
class GlossaryTermMatchTest {

	@Test
	@DisplayName("Accepts a valid match and exposes both components")
	void acceptsValidMatch() {
		GlossaryTermMatch match = new GlossaryTermMatch("ancient temple", GlossaryEntryClassification.AMBIGUOUS);

		assertThat(match.term()).isEqualTo("ancient temple");
		assertThat(match.classification()).isEqualTo(GlossaryEntryClassification.AMBIGUOUS);
	}

	@Test
	@DisplayName("Rejects a null term with NullPointerException")
	void rejectsNullTerm() {
		assertThatThrownBy(() -> new GlossaryTermMatch(null, GlossaryEntryClassification.AMBIGUOUS))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Rejects a blank term with IllegalArgumentException")
	void rejectsBlankTerm() {
		assertThatThrownBy(() -> new GlossaryTermMatch("", GlossaryEntryClassification.AMBIGUOUS))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new GlossaryTermMatch("   ", GlossaryEntryClassification.AMBIGUOUS))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Rejects a null classification with NullPointerException")
	void rejectsNullClassification() {
		assertThatThrownBy(() -> new GlossaryTermMatch("ancient temple", null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Equality is by both components with consistent hash codes")
	void equalityIsByBothComponents() {
		GlossaryTermMatch first = new GlossaryTermMatch("iron", GlossaryEntryClassification.AMBIGUOUS);
		GlossaryTermMatch second = new GlossaryTermMatch("iron", GlossaryEntryClassification.AMBIGUOUS);
		GlossaryTermMatch differentTerm = new GlossaryTermMatch("Iron", GlossaryEntryClassification.AMBIGUOUS);
		GlossaryTermMatch differentClassification = new GlossaryTermMatch("iron", GlossaryEntryClassification.PLAIN);

		assertThat(first).isEqualTo(second);
		assertThat(first.hashCode()).isEqualTo(second.hashCode());
		assertThat(first).isNotEqualTo(differentTerm);
		assertThat(first).isNotEqualTo(differentClassification);
	}
}
