package com.lucalzt.mctranslator.domain.service;

import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable value object carrying the round-trip state of mask → unmask.
 *
 * <p>Holds the masked text and the ordered list of original variable
 * substrings. The pinned correspondence is {@code __VAR_N__ ↔ variables.get(N)}:
 * the token index {@code N} occurring in {@code maskedText} identifies the
 * variable at position {@code N} of {@code variables}.
 *
 * <p>Validated at construction time: {@code maskedText} must be non-null and
 * non-blank; {@code variables} must be non-null with non-null, non-blank
 * elements; and the token-set invariant must hold — the token indices occurring
 * in {@code maskedText} (matched with the full-index token regex
 * {@code __VAR_(\d+)__}) must be exactly {@code {0, 1, …, n-1}} where
 * {@code n = variables.size()}, each index occurring exactly once. Textual
 * order of the tokens is not validated: restoration is by index regardless.
 *
 * <p>The {@code variables} list is defensively copied at construction, so the
 * instance is unaffected by later mutation of the caller's list and the
 * accessor-returned list is immutable. Equality is by both components.
 *
 * @param maskedText the masked text containing the {@code __VAR_N__} tokens, never {@code null} or blank
 * @param variables  the ordered original variable substrings, never {@code null}, elements never {@code null} or blank
 */
public record MaskedText(String maskedText, List<String> variables) {

	private static final Pattern TOKEN_PATTERN = Pattern.compile("__VAR_(\\d+)__");

	/**
	 * Compact constructor enforcing the record invariants.
	 *
	 * @throws NullPointerException     if {@code maskedText} is {@code null}, or {@code variables} is {@code null}, or any element of {@code variables} is {@code null}
	 * @throws IllegalArgumentException if {@code maskedText} is blank, or any element of {@code variables} is blank, or the token indices occurring in {@code maskedText} are not exactly {@code {0, 1, …, n-1}} with each index occurring exactly once
	 */
	public MaskedText {
		Objects.requireNonNull(maskedText, "maskedText must not be null");
		if (maskedText.isBlank()) {
			throw new IllegalArgumentException("maskedText must not be blank");
		}
		Objects.requireNonNull(variables, "variables must not be null");
		int n = variables.size();
		for (String variable : variables) {
			Objects.requireNonNull(variable, "variables elements must not be null");
			if (variable.isBlank()) {
				throw new IllegalArgumentException("variables elements must not be blank");
			}
		}
		boolean[] seen = new boolean[n];
		int matchCount = 0;
		Matcher matcher = TOKEN_PATTERN.matcher(maskedText);
		while (matcher.find()) {
			int index;
			try {
				index = Integer.parseInt(matcher.group(1));
			} catch (NumberFormatException e) {
				throw new IllegalArgumentException("token indices must be exactly 0..n-1, each exactly once", e);
			}
			if (index >= n || seen[index]) {
				throw new IllegalArgumentException("token indices must be exactly 0..n-1, each exactly once");
			}
			seen[index] = true;
			matchCount++;
		}
		if (matchCount != n) {
			throw new IllegalArgumentException("token indices must be exactly 0..n-1, each exactly once");
		}
		variables = List.copyOf(variables);
	}

	/**
	 * Returns the masked text containing the {@code __VAR_N__} tokens.
	 *
	 * @return the masked text
	 */
	@Override
	public String maskedText() {
		return maskedText;
	}

	/**
	 * Returns the ordered original variable substrings as an immutable,
	 * defensively copied list.
	 *
	 * <p>The pinned correspondence is {@code __VAR_N__ ↔ variables.get(N)}: the
	 * token index {@code N} inside {@link #maskedText()} identifies the variable
	 * at position {@code N} of the returned list.
	 *
	 * @return the immutable variable list
	 */
	@Override
	public List<String> variables() {
		return variables;
	}
}
