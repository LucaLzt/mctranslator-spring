package com.lucalzt.mctranslator.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Composition pin for the mask → unmask round trip (R7), exercising
 * {@link VariableMasker} and {@link VariableUnmasker} together. Authored
 * after both services exist (design A5 depends on A3+A4): every scenario must
 * be green on first run — a red here is a defect in the responsible service.
 * Pure JUnit 6 + AssertJ, no Spring context.
 */
class VariableRoundTripTest {

	@Test
	@DisplayName("Identity round trip restores the exact original with no discrepancies")
	void identityRoundTrip() {
		String original = "HP: %s, Cost: %1$d, Value: {0,number}";
		MaskedText masked = new VariableMasker().mask(original);

		UnmaskResult result = new VariableUnmasker().unmask(masked, masked.maskedText());

		assertThat(result.restoredText()).isEqualTo(original);
		assertThat(result.missingTokenIndices()).isEmpty();
		assertThat(result.unmatchedTokenIndices()).isEmpty();
		assertThat(result.reordered()).isFalse();
	}

	@Test
	@DisplayName("Literal-token round trip has no leakage")
	void literalTokenRoundTripHasNoLeakage() {
		MaskedText masked = new VariableMasker().mask("Use __VAR_3__");

		UnmaskResult result = new VariableUnmasker().unmask(masked, masked.maskedText());

		assertThat(result.restoredText()).isEqualTo("Use __VAR_3__");
		assertThat(result.missingTokenIndices()).isEmpty();
		assertThat(result.unmatchedTokenIndices()).isEmpty();
		assertThat(result.reordered()).isFalse();
	}

	@Test
	@DisplayName("Full-index tokens never partially match")
	void fullIndexTokensNeverPartiallyMatch() {
		List<String> variables = List.of("v0", "v1", "v2", "v3", "v4", "v5", "v6", "v7", "v8", "v9", "v10");
		MaskedText masked = new MaskedText(
				"__VAR_0__ __VAR_1__ __VAR_2__ __VAR_3__ __VAR_4__ __VAR_5__ __VAR_6__ __VAR_7__ __VAR_8__ __VAR_9__ __VAR_10__",
				variables);

		UnmaskResult result = new VariableUnmasker().unmask(masked, "A __VAR_10__ B __VAR_1__");

		assertThat(result.restoredText()).isEqualTo("A " + variables.get(10) + " B " + variables.get(1));
		assertThat(result.unmatchedTokenIndices()).isEmpty();
	}
}
