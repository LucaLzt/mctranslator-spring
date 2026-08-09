package com.lucalzt.mctranslator.domain.service;

import java.util.Objects;

import com.lucalzt.mctranslator.domain.model.GlossaryEntryClassification;

/**
 * Immutable value object representing a glossary entry whose term was matched
 * on the masked text by the {@code ScalingHeuristic} (rules 2 and 5).
 *
 * <p>{@code term} is the verbatim source-language term of the matched entry;
 * {@code classification} is the entry's classification. Validated at
 * construction time: {@code term} must be non-null and non-blank, and
 * {@code classification} must be non-null. Equality is by both components.
 *
 * @param term           the verbatim source-language term of the matched entry, never {@code null} or blank
 * @param classification the classification of the matched entry, never {@code null}
 */
public record GlossaryTermMatch(String term, GlossaryEntryClassification classification) {

	/**
	 * Compact constructor enforcing the record invariants.
	 *
	 * @throws NullPointerException     if {@code term} or {@code classification} is {@code null}
	 * @throws IllegalArgumentException if {@code term} is blank
	 */
	public GlossaryTermMatch {
		Objects.requireNonNull(term, "term must not be null");
		if (term.isBlank()) {
			throw new IllegalArgumentException("term must not be blank");
		}
		Objects.requireNonNull(classification, "classification must not be null");
	}

	/**
	 * Returns the verbatim source-language term of the matched entry.
	 *
	 * @return the term
	 */
	@Override
	public String term() {
		return term;
	}

	/**
	 * Returns the classification of the matched entry.
	 *
	 * @return the classification
	 */
	@Override
	public GlossaryEntryClassification classification() {
		return classification;
	}
}
