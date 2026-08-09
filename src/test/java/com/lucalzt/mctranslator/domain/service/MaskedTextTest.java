package com.lucalzt.mctranslator.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MaskedText}, covering validation rejection (R5), the
 * token-set invariant, defensive copying of the variables list and value
 * equality. Pure JUnit 6 + AssertJ, no Spring context.
 */
class MaskedTextTest {

	@Test
	@DisplayName("Accepts a valid mask state, exposes accessors and pins the __VAR_N__ to variables.get(N) correspondence")
	void acceptsValidMaskState() {
		MaskedText masked = new MaskedText("Use __VAR_0__ and __VAR_1__", List.of("%s", "{0,number}"));

		assertThat(masked.maskedText()).isEqualTo("Use __VAR_0__ and __VAR_1__");
		assertThat(masked.variables()).containsExactly("%s", "{0,number}");
		assertThat(masked.variables().get(0)).isEqualTo("%s");
		assertThat(masked.variables().get(1)).isEqualTo("{0,number}");
	}

	@Test
	@DisplayName("Rejects a null maskedText with NullPointerException")
	void rejectsNullMaskedText() {
		assertThatThrownBy(() -> new MaskedText(null, List.of("%s")))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Rejects blank maskedText with IllegalArgumentException")
	void rejectsBlankMaskedText() {
		assertThatThrownBy(() -> new MaskedText("", List.of("%s")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MaskedText("   ", List.of("%s")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Rejects a null variables list or a null element with NullPointerException")
	void rejectsNullVariables() {
		assertThatThrownBy(() -> new MaskedText("Use __VAR_0__", null))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new MaskedText("Use __VAR_0__", new ArrayList<>(List.of((String) null))))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Rejects a blank variable element with IllegalArgumentException")
	void rejectsBlankVariableElement() {
		assertThatThrownBy(() -> new MaskedText("Use __VAR_0__", List.of("")))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MaskedText("Use __VAR_0__", List.of("   ")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Rejects a token index outside 0..n-1 with IllegalArgumentException")
	void rejectsOutOfRangeTokenIndex() {
		assertThatThrownBy(() -> new MaskedText("foo __VAR_2__ bar", List.of("a")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Rejects a duplicated token index with IllegalArgumentException")
	void rejectsDuplicateTokenIndex() {
		assertThatThrownBy(() -> new MaskedText("foo __VAR_0__ __VAR_0__", List.of("a")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Rejects a missing token index with IllegalArgumentException")
	void rejectsMissingTokenIndex() {
		assertThatThrownBy(() -> new MaskedText("__VAR_1__", List.of("a", "b")))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Accepts an empty token set")
	void acceptsEmptyTokenSet() {
		MaskedText masked = new MaskedText("hello", List.of());

		assertThat(masked.maskedText()).isEqualTo("hello");
		assertThat(masked.variables()).isEmpty();
	}

	@Test
	@DisplayName("Defensive copy: caller mutation after construction does not affect the instance")
	void defensiveCopyAgainstCallerMutation() {
		List<String> variables = new ArrayList<>(List.of("%s"));
		MaskedText masked = new MaskedText("Use __VAR_0__", variables);

		variables.add("{0}");

		assertThat(masked.variables()).containsExactly("%s");
	}

	@Test
	@DisplayName("Accessor-returned variables list is immutable")
	void accessorListIsImmutable() {
		MaskedText masked = new MaskedText("Use __VAR_0__", List.of("%s"));

		assertThatThrownBy(() -> masked.variables().add("{0}"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	@DisplayName("Equality is by both components with consistent hash codes")
	void equalityIsByBothComponents() {
		MaskedText first = new MaskedText("Use __VAR_0__", List.of("%s"));
		MaskedText second = new MaskedText("Use __VAR_0__", List.of("%s"));
		MaskedText differentText = new MaskedText("Use __VAR_0__ now", List.of("%s"));
		MaskedText differentVariables = new MaskedText("Use __VAR_0__", List.of("{0}"));

		assertThat(first).isEqualTo(second);
		assertThat(first.hashCode()).isEqualTo(second.hashCode());
		assertThat(first).isNotEqualTo(differentText);
		assertThat(first).isNotEqualTo(differentVariables);
	}
}
