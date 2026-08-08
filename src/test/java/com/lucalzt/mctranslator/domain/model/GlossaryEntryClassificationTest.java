package com.lucalzt.mctranslator.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link GlossaryEntryClassification}, pinning the exact three
 * classifications in spec order (R15). Pure JUnit 6 + AssertJ, no Spring context.
 */
class GlossaryEntryClassificationTest {

	@Test
	@DisplayName("Exposes exactly the three classifications in spec order")
	void exposesExactlyTheThreeClassifications() {
		assertThat(GlossaryEntryClassification.values()).containsExactly(
				GlossaryEntryClassification.AMBIGUOUS,
				GlossaryEntryClassification.LORE,
				GlossaryEntryClassification.PLAIN);
	}
}
