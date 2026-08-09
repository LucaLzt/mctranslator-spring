package com.lucalzt.mctranslator.domain.service;

import java.util.List;
import java.util.Objects;

/**
 * Output record of {@code VariableUnmasker.unmask}: the restored text plus the
 * discrepancy report consumed by the application layer for diagnostics.
 *
 * <p>Discrepancy semantics (the unmasker's producer contract, documented here
 * and pinned by tests — not re-validated in the constructor). With
 * {@code n = } the number of masked variables:
 * <ul>
 *   <li>{@code missingTokenIndices} — sorted ascending indices {@code N}
 *       ({@code 0 ≤ N < n}) present in the masked text's token set but absent
 *       from the translated text; empty when every variable survived the
 *       translation;</li>
 *   <li>{@code unmatchedTokenIndices} — sorted ascending, deduplicated indices
 *       {@code N ≥ n} occurring in the translated text with no corresponding
 *       variable; empty when no out-of-range token appears;</li>
 *   <li>{@code reordered} — {@code true} iff the appearance-order sequence of
 *       token indices in the translated text (ALL tokens, matched or unmatched)
 *       is not monotonically non-decreasing, i.e. contains an inversion;
 *       duplicates alone never imply reordering.</li>
 * </ul>
 *
 * <p>Both index lists are defensively copied at construction, so the instance
 * is unaffected by later mutation of the caller's lists and the
 * accessor-returned lists are immutable. Equality is by all four components.
 *
 * @param restoredText          the translated text with every in-range token restored, never {@code null}
 * @param missingTokenIndices   the missing variable indices, never {@code null}
 * @param unmatchedTokenIndices the unmatched token indices, never {@code null}
 * @param reordered             whether the token appearance order is inverted
 */
public record UnmaskResult(String restoredText, List<Integer> missingTokenIndices,
		List<Integer> unmatchedTokenIndices, boolean reordered) {

	/**
	 * Compact constructor enforcing the record invariants.
	 *
	 * @throws NullPointerException if {@code restoredText} or either index list is {@code null}
	 */
	public UnmaskResult {
		Objects.requireNonNull(restoredText, "restoredText must not be null");
		Objects.requireNonNull(missingTokenIndices, "missingTokenIndices must not be null");
		Objects.requireNonNull(unmatchedTokenIndices, "unmatchedTokenIndices must not be null");
		missingTokenIndices = List.copyOf(missingTokenIndices);
		unmatchedTokenIndices = List.copyOf(unmatchedTokenIndices);
	}

	/**
	 * Returns the restored text.
	 *
	 * @return the restored text
	 */
	@Override
	public String restoredText() {
		return restoredText;
	}

	/**
	 * Returns the sorted ascending indices {@code N} ({@code 0 ≤ N < n}) present
	 * in the masked token set but absent from the translated text, as an
	 * immutable, defensively copied list.
	 *
	 * @return the missing variable indices, empty when none
	 */
	@Override
	public List<Integer> missingTokenIndices() {
		return missingTokenIndices;
	}

	/**
	 * Returns the sorted ascending, deduplicated indices {@code N ≥ n} occurring
	 * in the translated text with no corresponding variable, as an immutable,
	 * defensively copied list.
	 *
	 * @return the unmatched token indices, empty when none
	 */
	@Override
	public List<Integer> unmatchedTokenIndices() {
		return unmatchedTokenIndices;
	}

	/**
	 * Returns whether the token appearance order is inverted.
	 *
	 * @return {@code true} iff the sequence of token indices in the translated
	 *         text is not monotonically non-decreasing
	 */
	@Override
	public boolean reordered() {
		return reordered;
	}
}
