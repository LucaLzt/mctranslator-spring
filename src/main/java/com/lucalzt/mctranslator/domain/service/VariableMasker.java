package com.lucalzt.mctranslator.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateless domain service that protects printf and MessageFormat variables
 * with positional {@code __VAR_N__} tokens.
 *
 * <p>Masking replaces every occurrence of the pinned 3-branch alternation
 * {@code __VAR_(\d+)__ | %(?:\d+\$)?[sdf] | \{(\d+)(?:,[^}]*)?\}} — in this
 * order: literal token | printf | MessageFormat — with the next positional
 * token {@code __VAR_N__} in first-occurrence order (0-based). The returned
 * {@link MaskedText} records each matched original substring verbatim in its
 * {@code variables} list, one entry per occurrence: no deduplication — two
 * occurrences of {@code %s} produce two entries.
 *
 * <p>The pinned pattern set masks: literal {@code __VAR_N__} tokens, which
 * are protected as regular variables (branch 1), so the masked output never
 * contains a token that does not correspond to a protected variable and no
 * leakage occurs; printf conversions {@code %s}, {@code %d} and {@code %f}
 * with an optional positional index ({@code %1$s}, {@code %2$d},
 * {@code %10$s}); and every indexed MessageFormat form {@code {N}},
 * {@code {N,type}} and {@code {N,type,style}}, including ChoiceFormat ranges
 * such as {@code {0,choice,0#zero|1#one}}. NOT masked (left literal,
 * round-trip identity): {@code %%}, case variants {@code %S}/{@code %D}/
 * {@code %F}, other printf conversions, width/precision forms such as
 * {@code %10.2f} and {@code %2$10d}, JSON braces, unbalanced braces, and
 * Minecraft section-sign formatting codes such as {@code §a}.
 *
 * <p><b>Documented limitation:</b> the alternation has no escape awareness. A
 * literal {@code %%} is never masked, but a {@code %s} immediately following
 * an escaped {@code %%} (e.g. {@code 100%%s}) IS masked — an accepted v1
 * limitation, not a bug.
 *
 * <p><b>Re-entrancy note:</b> masking an already-masked string treats each
 * {@code __VAR_N__} as a literal token (first alternation branch) and
 * re-numbers it; the pipeline masks the original text exactly once. This is
 * pinned behavior, not a bug.
 *
 * <p>The service is stateless (no instance fields) and thread-safe: the
 * precompiled {@link Pattern} is immutable and a fresh {@link Matcher} is
 * created per call.
 */
public final class VariableMasker {

	private static final Pattern VARIABLE_PATTERN = Pattern.compile(
			"__VAR_(\\d+)__|%(?:\\d+\\$)?[sdf]|\\{(\\d+)(?:,[^}]*)?\\}");

	/**
	 * Masks the given text, replacing every matched variable with the next
	 * positional token {@code __VAR_N__} in first-occurrence order.
	 *
	 * <p>The token replacement {@code __VAR_N__} contains no {@code $} or
	 * {@code \} characters, so {@link Matcher#appendReplacement} needs no
	 * quoting. Non-masked patterns are left literal by construction.
	 *
	 * @param text the text to mask, never {@code null} or blank
	 * @return the {@link MaskedText} carrying the masked text and the ordered
	 *         original variable substrings (one entry per occurrence)
	 * @throws NullPointerException     if {@code text} is {@code null}
	 * @throws IllegalArgumentException if {@code text} is blank
	 */
	public MaskedText mask(String text) {
		Objects.requireNonNull(text, "text must not be null");
		if (text.isBlank()) {
			throw new IllegalArgumentException("text must not be blank");
		}
		List<String> variables = new ArrayList<>();
		StringBuilder masked = new StringBuilder();
		Matcher matcher = VARIABLE_PATTERN.matcher(text);
		int nextIndex = 0;
		while (matcher.find()) {
			// Branch classification is content-based on the whole match (D1):
			// the pinned alternation's matched prefixes are disjoint ('_' /
			// '%' / '{'), so the match text identifies the branch — literal
			// token, printf or MessageFormat — without relying on group
			// numbers. All three branches are handled identically: replace
			// with the next positional token and record the original substring
			// verbatim (no dedup).
			matcher.appendReplacement(masked, "__VAR_" + nextIndex + "__");
			variables.add(matcher.group());
			nextIndex++;
		}
		matcher.appendTail(masked);
		return new MaskedText(masked.toString(), variables);
	}
}
