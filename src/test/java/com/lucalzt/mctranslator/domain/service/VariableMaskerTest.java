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
	@DisplayName("Reduced non-variable set is left literal")
	void leavesNonVariablesLiteral() {
		assertMasked("%S var, %10.2f wide, %-10s, %2$10d, %+d, %l, %ls, {p}, §a colored",
				"%S var, %10.2f wide, %-10s, %2$10d, %+d, %l, %ls, {p}, §a colored", List.of());
	}

	@Test
	@DisplayName("Masks F4 conversions as variables")
	void masksF4Conversions() {
		assertMasked("Line: %n", "Line: __VAR_0__", List.of("%n"));
		assertMasked("Hex: %x", "Hex: __VAR_0__", List.of("%x"));
		assertMasked("Oct: %o", "Oct: __VAR_0__", List.of("%o"));
		assertMasked("Sci: %e", "Sci: __VAR_0__", List.of("%e"));
		assertMasked("Gen: %g", "Gen: __VAR_0__", List.of("%g"));
		assertMasked("Flag: %b", "Flag: __VAR_0__", List.of("%b"));
		assertMasked("Char: %c", "Char: __VAR_0__", List.of("%c"));
		assertMasked("Hash: %h", "Hash: __VAR_0__", List.of("%h"));
		assertMasked("Int: %i", "Int: __VAR_0__", List.of("%i"));
		assertMasked("Long: %ld", "Long: __VAR_0__", List.of("%ld"));
	}

	@Test
	@DisplayName("Masks positional F4 conversions")
	void masksPositionalF4Conversions() {
		assertMasked("Cost %2$x and %1$ld", "Cost __VAR_0__ and __VAR_1__", List.of("%2$x", "%1$ld"));
	}

	@Test
	@DisplayName("Masks standalone printf escape as variable")
	void masksStandaloneEscape() {
		assertMasked("100%% complete", "100__VAR_0__ complete", List.of("%%"));
	}

	@Test
	@DisplayName("Masks conversion and escape merge as one token")
	void masksConversionAndEscapeMerge() {
		assertMasked("Progress: %s%%", "Progress: __VAR_0__", List.of("%s%%"));
		assertMasked("+%s%% %s", "+__VAR_0__ __VAR_1__", List.of("%s%%", "%s"));
		assertMasked("Progress: %1$s%%", "Progress: __VAR_0__", List.of("%1$s%%"));
	}

	@Test
	@DisplayName("Merge consumes exactly one conversion and one escape, fallback handles extra")
	void mergeConsumesOneAndFallbackHandlesExtra() {
		assertMasked("A %s%%%% B", "A __VAR_0____VAR_1__ B", List.of("%s%%", "%%"));
		assertMasked("100%%s", "100__VAR_0__s", List.of("%%"));
		assertMasked("%%%", "__VAR_0__%", List.of("%%"));
		assertMasked("%s%%%%", "__VAR_0____VAR_1__", List.of("%s%%", "%%"));
	}

	@Test
	@DisplayName("Scans mixed v2 input in one pass")
	void mixedV2Scan() {
		assertMasked("A %s {0} %1$s%% %n {1,number} %2$x",
				"A __VAR_0__ __VAR_1__ __VAR_2__ __VAR_3__ __VAR_4__ __VAR_5__",
				List.of("%s", "{0}", "%1$s%%", "%n", "{1,number}", "%2$x"));
	}

	@Test
	@DisplayName("URL percent-encodings are guarded and left literal")
	void urlEncodingsGuarded() {
		assertMasked("Path %20F", "Path %20F", List.of());
		assertMasked("Enc %E2", "Enc %E2", List.of());
		assertMasked("List %2C", "List %2C", List.of());
		assertMasked("Newline %0A", "Newline %0A", List.of());
		assertMasked("Click here: https://example.com/api?text=Portable%20Fluid%20Storage%2C%20Silo",
				"Click here: https://example.com/api?text=Portable%20Fluid%20Storage%2C%20Silo", List.of());
	}

	@Test
	@DisplayName("URL guard has precedence over hex-adjacent conversions")
	void guardPrecedenceOverHexAdjacent() {
		assertMasked("Rate %e2", "Rate %e2", List.of());
		assertMasked("Rate %e", "Rate __VAR_0__", List.of("%e"));
		assertMasked("Flag %b2", "Flag %b2", List.of());
		assertMasked("Flag %b", "Flag __VAR_0__", List.of("%b"));
		assertMasked("Val %c2", "Val %c2", List.of());
		assertMasked("Val %c", "Val __VAR_0__", List.of("%c"));
		assertMasked("Num %d2", "Num %d2", List.of());
		assertMasked("Num %d", "Num __VAR_0__", List.of("%d"));
	}

	@Test
	@DisplayName("URL guard does not affect escape and positional conversions")
	void guardDoesNotAffectEscapeAndPositional() {
		assertMasked("100%% done", "100__VAR_0__ done", List.of("%%"));
		assertMasked("Hello %1$s", "Hello __VAR_0__", List.of("%1$s"));
		assertMasked("Slot %10$s", "Slot __VAR_0__", List.of("%10$s"));
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
