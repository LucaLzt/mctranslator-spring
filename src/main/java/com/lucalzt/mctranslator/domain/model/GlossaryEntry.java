package com.lucalzt.mctranslator.domain.model;

import java.util.Objects;

/**
 * Glossary entry mapping a term to its translation, classified for the scaling
 * heuristic.
 *
 * <p>Immutable and validated at construction time: all three components must be
 * non-null and {@code term} / {@code translation} must be non-blank. Equality
 * is by all three components.
 *
 * @param term           the source-language term, never {@code null} or blank
 * @param translation    the translated term, never {@code null} or blank
 * @param classification the entry classification, never {@code null}
 */
public record GlossaryEntry(String term, String translation, GlossaryEntryClassification classification) {

	/**
	 * Compact constructor enforcing the record invariants.
	 *
	 * @throws NullPointerException     if any component is {@code null}
	 * @throws IllegalArgumentException if {@code term} or {@code translation} is blank
	 */
	public GlossaryEntry {
		Objects.requireNonNull(term, "term must not be null");
		Objects.requireNonNull(translation, "translation must not be null");
		Objects.requireNonNull(classification, "classification must not be null");
		if (term.isBlank()) {
			throw new IllegalArgumentException("term must not be blank");
		}
		if (translation.isBlank()) {
			throw new IllegalArgumentException("translation must not be blank");
		}
	}

	/**
	 * Returns the source-language term.
	 *
	 * @return the term
	 */
	@Override
	public String term() {
		return term;
	}

	/**
	 * Returns the translated term.
	 *
	 * @return the translation
	 */
	@Override
	public String translation() {
		return translation;
	}

	/**
	 * Returns the entry classification.
	 *
	 * @return the classification
	 */
	@Override
	public GlossaryEntryClassification classification() {
		return classification;
	}
}
