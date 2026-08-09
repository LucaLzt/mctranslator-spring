package com.lucalzt.mctranslator.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link UnmaskResult}, covering null rejection (R8), defensive
 * copying of both discrepancy lists (R8) and value equality across the four
 * components. Pure JUnit 6 + AssertJ, no Spring context.
 */
class UnmaskResultTest {

	@Test
	@DisplayName("Accepts a valid result and exposes all four components")
	void acceptsValidResult() {
		UnmaskResult result = new UnmaskResult("Hello %s", List.of(1), List.of(9), true);

		assertThat(result.restoredText()).isEqualTo("Hello %s");
		assertThat(result.missingTokenIndices()).containsExactly(1);
		assertThat(result.unmatchedTokenIndices()).containsExactly(9);
		assertThat(result.reordered()).isTrue();
	}

	@Test
	@DisplayName("Rejects a null restoredText with NullPointerException")
	void rejectsNullRestoredText() {
		assertThatThrownBy(() -> new UnmaskResult(null, List.of(), List.of(), false))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Rejects a null discrepancy list with NullPointerException")
	void rejectsNullDiscrepancyLists() {
		assertThatThrownBy(() -> new UnmaskResult("ok", null, List.of(), false))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new UnmaskResult("ok", List.of(), null, false))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Defensive copy: caller mutation after construction does not affect the instance")
	void defensiveCopyAgainstCallerMutation() {
		List<Integer> missing = new ArrayList<>(List.of(1));
		List<Integer> unmatched = new ArrayList<>(List.of(9));
		UnmaskResult result = new UnmaskResult("ok", missing, unmatched, false);

		missing.add(2);
		unmatched.add(10);

		assertThat(result.missingTokenIndices()).containsExactly(1);
		assertThat(result.unmatchedTokenIndices()).containsExactly(9);
	}

	@Test
	@DisplayName("Accessor-returned discrepancy lists are immutable")
	void accessorListsAreImmutable() {
		UnmaskResult result = new UnmaskResult("ok", List.of(1), List.of(9), false);

		assertThatThrownBy(() -> result.missingTokenIndices().add(2))
				.isInstanceOf(UnsupportedOperationException.class);
		assertThatThrownBy(() -> result.unmatchedTokenIndices().add(10))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	@DisplayName("Equality is by all four components with consistent hash codes")
	void equalityIsByAllComponents() {
		UnmaskResult first = new UnmaskResult("Hello %s", List.of(1), List.of(), false);
		UnmaskResult second = new UnmaskResult("Hello %s", List.of(1), List.of(), false);
		UnmaskResult differentMissing = new UnmaskResult("Hello %s", List.of(2), List.of(), false);
		UnmaskResult differentUnmatched = new UnmaskResult("Hello %s", List.of(1), List.of(9), false);
		UnmaskResult differentReordered = new UnmaskResult("Hello %s", List.of(1), List.of(), true);

		assertThat(first).isEqualTo(second);
		assertThat(first.hashCode()).isEqualTo(second.hashCode());
		assertThat(first).isNotEqualTo(differentMissing);
		assertThat(first).isNotEqualTo(differentUnmatched);
		assertThat(first).isNotEqualTo(differentReordered);
	}
}
