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
 * <p>Masking replaces every occurrence of the pinned escape-aware alternation
 * {@code __VAR_(\d+)__ | %(?![0-9A-Fa-f]{2}(?!\$))(?:\d+\$)?(?:[sdfnxoegbchi]|ld)%% | %(?![0-9A-Fa-f]{2}(?!\$))(?:\d+\$)?(?:[sdfnxoegbchi]|ld) | %% | \{(\d+)(?:,[^}]*)?\}}
 * — in this order: literal token | merge (conversion + escape) | conversion (v1 + F4) | standalone escape | MessageFormat
 * — with the next positional token {@code __VAR_N__} in first-occurrence order (0-based).
 * The returned {@link MaskedText} records each matched original substring verbatim in its
 * {@code variables} list, one entry per occurrence: no deduplication — two
 * occurrences of {@code %s} produce two entries.
 *
 * <p>The pinned pattern set masks: literal {@code __VAR_N__} tokens, which
 * are protected as regular variables (branch 1); printf conversions {@code %s},
 * {@code %d}, {@code %f}, F4 extended conversions {@code %n}, {@code %x}, {@code %o},
 * {@code %e}, {@code %g}, {@code %b}, {@code %c}, {@code %h}, {@code %i}, and the two-char
 * conversion {@code %ld}, with optional positional index (e.g. {@code %1$s}, {@code %2$x});
 * the standalone printf escape literal {@code %%}; merged conversion-escape tokens
 * such as {@code %s%%}, {@code %1$s%%}, {@code %d%%}; and every indexed MessageFormat form
 * {@code {N}}, {@code {N,type}}, {@code {N,type,style}}, including ChoiceFormat ranges
 * such as {@code {0,choice,0#zero|1#one}}.
 *
 * <p><b>URL guard:</b> URL percent-encoded sequences {@code %[0-9A-Fa-f]{2}}
 * (such as {@code %20}, {@code %E2}, {@code %2C}, {@code %0A}) are never masked and are
 * left literal, taking hard precedence over F4 detection (with refinement preventing
 * false positives on positional indices like {@code %10$s}).
 *
 * <p>NOT masked (left literal, round-trip identity): case variants {@code %S}/{@code %D}/
 * {@code %F}, F3 width/precision forms such as {@code %10.2f} and {@code %2$10d},
 * the non-set two-character form {@code %l} (and {@code %ls}), JSON braces, unbalanced braces,
 * named braces ({@code {p}}), and Minecraft section-sign formatting codes such as {@code §a}.
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
			"__VAR_(\\d+)__"                                               // (1) literal-token protection (v1, verbatim)
			+ "|%(?![0-9A-Fa-f]{2}(?!\\$))(?:\\d+\\$)?(?:[sdfnxoegbchi]|ld)%%"   // (2a) MERGE: conversion + escape -> ONE token
			+ "|%(?![0-9A-Fa-f]{2}(?!\\$))(?:\\d+\\$)?(?:[sdfnxoegbchi]|ld)"      // (2b) conversion: v1 + F4, optional positional index
			+ "|%%"                                                        // (2c) standalone printf escape literal
			+ "|\\{(\\d+)(?:,[^}]*)?\\}");                                 // (3) MessageFormat (v1, verbatim)

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
