package com.lucalzt.mctranslator.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VariableUnmasker}, covering all six R8 scenarios
 * (missing, unmatched, reordered, duplicated, perfect and null rejection)
 * plus the interleaved matched/unmatched ordering and the D8 quoting pin
 * (a {@code $}-containing variable round-trips verbatim). Pure JUnit 6 +
 * AssertJ, no Spring context.
 */
class VariableUnmaskerTest {

	private static UnmaskResult unmask(MaskedText masked, String translated) {
		return new VariableUnmasker().unmask(masked, translated);
	}

	@Test
	@DisplayName("Missing token never throws and is reported")
	void missingTokenNeverThrows() {
		MaskedText masked = new MaskedText("A __VAR_0__ B __VAR_1__", List.of("%s", "%d"));

		UnmaskResult result = unmask(masked, "Hello __VAR_0__");

		assertThat(result.restoredText()).isEqualTo("Hello %s");
		assertThat(result.missingTokenIndices()).containsExactly(1);
		assertThat(result.unmatchedTokenIndices()).isEmpty();
		assertThat(result.reordered()).isFalse();
	}

	@Test
	@DisplayName("Unmatched token is left verbatim and reported, deduplicated and sorted")
	void unmatchedTokenLeftVerbatimAndReported() {
		MaskedText masked = new MaskedText("__VAR_0__", List.of("%s"));

		UnmaskResult result = unmask(masked, "Hi __VAR_0__ and __VAR_9__ and __VAR_9__");

		assertThat(result.restoredText()).isEqualTo("Hi %s and __VAR_9__ and __VAR_9__");
		assertThat(result.unmatchedTokenIndices()).containsExactly(9);
		assertThat(result.missingTokenIndices()).isEmpty();
		assertThat(result.reordered()).isFalse();
	}

	@Test
	@DisplayName("Reordered tokens restore by index and are reported")
	void reorderedTokensRestoreByIndex() {
		MaskedText masked = new MaskedText("A __VAR_0__ B __VAR_1__", List.of("%s", "%d"));

		UnmaskResult result = unmask(masked, "Second __VAR_1__ first __VAR_0__");

		assertThat(result.restoredText()).isEqualTo("Second %d first %s");
		assertThat(result.reordered()).isTrue();
		assertThat(result.missingTokenIndices()).isEmpty();
		assertThat(result.unmatchedTokenIndices()).isEmpty();
	}

	@Test
	@DisplayName("Duplicated token restores the same original twice and is not reordered")
	void duplicatedTokenNotReordered() {
		MaskedText masked = new MaskedText("__VAR_0__", List.of("{0}"));

		UnmaskResult result = unmask(masked, "A __VAR_0__ B __VAR_0__");

		assertThat(result.restoredText()).isEqualTo("A {0} B {0}");
		assertThat(result.reordered()).isFalse();
		assertThat(result.missingTokenIndices()).isEmpty();
		assertThat(result.unmatchedTokenIndices()).isEmpty();
	}

	@Test
	@DisplayName("Perfect translation yields no discrepancies")
	void perfectTranslationYieldsNoDiscrepancies() {
		MaskedText masked = new MaskedText("X __VAR_0__ Y __VAR_1__", List.of("%s", "{0}"));

		UnmaskResult result = unmask(masked, "X __VAR_0__ Y __VAR_1__");

		assertThat(result.restoredText()).isEqualTo("X %s Y {0}");
		assertThat(result.missingTokenIndices()).isEmpty();
		assertThat(result.unmatchedTokenIndices()).isEmpty();
		assertThat(result.reordered()).isFalse();
	}

	@Test
	@DisplayName("Interleaved matched and unmatched tokens report the unmatched index and the inversion")
	void interleavedMatchedAndUnmatched() {
		MaskedText masked = new MaskedText("A __VAR_0__ B __VAR_1__", List.of("%s", "%d"));

		UnmaskResult result = unmask(masked, "A __VAR_0__ B __VAR_9__ C __VAR_1__");

		assertThat(result.restoredText()).isEqualTo("A %s B __VAR_9__ C %d");
		assertThat(result.reordered()).isTrue();
		assertThat(result.unmatchedTokenIndices()).containsExactly(9);
		assertThat(result.missingTokenIndices()).isEmpty();
	}

	@Test
	@DisplayName("A $-containing variable round-trips verbatim (D8 quoting pin)")
	void dollarVariableRestoredExactly() {
		MaskedText masked = new MaskedText("__VAR_0__", List.of("%1$s"));

		UnmaskResult result = unmask(masked, "Hello __VAR_0__");

		assertThat(result.restoredText()).isEqualTo("Hello %1$s");
	}

	@Test
	@DisplayName("Rejects a null MaskedText with NullPointerException")
	void rejectsNullMaskedText() {
		assertThatThrownBy(() -> new VariableUnmasker().unmask(null, "Hello"))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Rejects a null translatedText with NullPointerException")
	void rejectsNullTranslatedText() {
		MaskedText masked = new MaskedText("__VAR_0__", List.of("%s"));

		assertThatThrownBy(() -> new VariableUnmasker().unmask(masked, null))
				.isInstanceOf(NullPointerException.class);
	}
}
