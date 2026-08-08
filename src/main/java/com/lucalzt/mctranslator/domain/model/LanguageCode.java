package com.lucalzt.mctranslator.domain.model;

import java.util.Objects;

/**
 * Value object wrapping a non-null, non-blank language identifier string.
 *
 * <p>Immutable and validated at construction time: the code must be non-null
 * and non-blank. No format restriction or normalization is applied — engine
 * language identifiers vary (e.g. {@code es}, {@code en_US}, {@code spa_Latn})
 * and case is significant, so the code is stored exactly as provided.
 *
 * @param value the language identifier string, never {@code null} or blank
 */
public record LanguageCode(String value) {

	/**
	 * Compact constructor enforcing the value object invariants.
	 *
	 * @throws NullPointerException     if {@code value} is {@code null}
	 * @throws IllegalArgumentException if {@code value} is blank
	 */
	public LanguageCode {
		Objects.requireNonNull(value, "value must not be null");
		if (value.isBlank()) {
			throw new IllegalArgumentException("value must not be blank");
		}
	}

	/**
	 * Returns the language identifier string as provided.
	 *
	 * @return the language code string
	 */
	@Override
	public String value() {
		return value;
	}
}
