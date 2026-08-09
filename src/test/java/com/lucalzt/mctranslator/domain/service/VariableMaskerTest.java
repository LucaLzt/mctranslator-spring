package com.lucalzt.mctranslator.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VariableMasker}, covering all eight R6 scenarios: the
 * pinned printf and MessageFormat families, first-occurrence numbering without
 * deduplication, literal-token protection (no leakage), the non-masked literal
 * set, JSON braces, the combined mixed scan and null/blank rejection. Pure
 * JUnit 6 + AssertJ, no Spring context.
 */
class VariableMaskerTest {

	private static MaskedText mask(String text) {
		return new VariableMasker().mask(text);
	}

	private static void assertMasked(String input, String expectedMasked, List<String> expectedVariables) {
		MaskedText result = mask(input);
		assertThat(result.maskedText()).isEqualTo(expectedMasked);
		assertThat(result.variables()).containsExactlyElementsOf(expectedVariables);
	}

	@Test
	@DisplayName("Masks each pinned printf family to a positional token with the exact variable")
	void masksEachPinnedPrintfFamily() {
		assertMasked("HP: %s", "HP: __VAR_0__", List.of("%s"));
		assertMasked("Damage: %d", "Damage: __VAR_0__", List.of("%d"));
		assertMasked("Ratio: %f", "Ratio: __VAR_0__", List.of("%f"));
		assertMasked("Hello %1$s", "Hello __VAR_0__", List.of("%1$s"));
		assertMasked("Cost %2$d", "Cost __VAR_0__", List.of("%2$d"));
		assertMasked("Slot %10$s", "Slot __VAR_0__", List.of("%10$s"));
	}

	@Test
	@DisplayName("Masks each pinned MessageFormat family to a positional token with the exact variable")
	void masksEachPinnedMessageFormatFamily() {
		assertMasked("Value {0}", "Value __VAR_0__", List.of("{0}"));
		assertMasked("Amount {1,number}", "Amount __VAR_0__", List.of("{1,number}"));
		assertMasked("Day {0,date,full}", "Day __VAR_0__", List.of("{0,date,full}"));
		assertMasked("Pick {0,choice,0#zero|1#one}", "Pick __VAR_0__", List.of("{0,choice,0#zero|1#one}"));
		assertMasked("N {12,number,integer}", "N __VAR_0__", List.of("{12,number,integer}"));
	}

	@Test
	@DisplayName("Numbers tokens in first-occurrence order without deduplication")
	void numbersInFirstOccurrenceOrderWithoutDedup() {
		assertMasked("A %s and %s and {0}", "A __VAR_0__ and __VAR_1__ and __VAR_2__",
				List.of("%s", "%s", "{0}"));
	}

	@Test
	@DisplayName("Protects a literal __VAR_N__ token as a regular variable with no leakage")
	void protectsLiteralTokenWithoutLeakage() {
		assertMasked("Use __VAR_3__ now", "Use __VAR_0__ now", List.of("__VAR_3__"));
	}

	@Test
	@DisplayName("Leaves non-variable patterns literal with an empty variable list")
	void leavesNonVariablesLiteral() {
		assertMasked("100%% complete, %S var, %x hex, %10.2f wide, §a colored",
				"100%% complete, %S var, %x hex, %10.2f wide, §a colored", List.of());
	}

	@Test
	@DisplayName("Does not mask JSON braces, only indexed MessageFormat forms")
	void doesNotMaskJsonBraces() {
		assertMasked("JSON { \"key\": \"value\" } and text {0}",
				"JSON { \"key\": \"value\" } and text __VAR_0__", List.of("{0}"));
	}

	@Test
	@DisplayName("Scans a mixed input combining all three branches in one pass")
	void combinedMixedScan() {
		assertMasked("A %s {0} %1$s {1,number}", "A __VAR_0__ __VAR_1__ __VAR_2__ __VAR_3__",
				List.of("%s", "{0}", "%1$s", "{1,number}"));
	}

	@Test
	@DisplayName("Rejects a null text with NullPointerException")
	void rejectsNullText() {
		assertThatThrownBy(() -> new VariableMasker().mask(null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Rejects blank text with IllegalArgumentException")
	void rejectsBlankText() {
		assertThatThrownBy(() -> new VariableMasker().mask(""))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new VariableMasker().mask("   "))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
