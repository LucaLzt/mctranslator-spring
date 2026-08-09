package com.lucalzt.mctranslator.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stateless domain service that restores masked variables in translated text
 * best-effort.
 *
 * <p>{@code unmask} scans the translated text for the full-index token regex
 * {@code __VAR_(\d+)__} — greedy index, mandatory trailing {@code __} — so a
 * {@code __VAR_1__} substring can never partially match {@code __VAR_10__} —
 * and restores every in-range token by its index: {@code __VAR_N__} with
 * {@code 0 ≤ N < n} ({@code n} = the number of masked variables) is replaced
 * by {@code masked.variables().get(N)}. Out-of-range tokens ({@code N ≥ n})
 * are left verbatim in the restored text and reported as unmatched.
 *
 * <p><b>Best-effort contract:</b> the unmasker never throws merely because
 * the translated text lacks, adds, reorders or duplicates tokens. Anomalies
 * are reported via the returned {@link UnmaskResult}:
 * <ul>
 *   <li>{@code missingTokenIndices} — sorted ascending indices
 *       {@code 0 ≤ N < n} present in the masked token set but absent from the
 *       translated text;</li>
 *   <li>{@code unmatchedTokenIndices} — sorted ascending, deduplicated indices
 *       {@code N ≥ n} occurring in the translated text;</li>
 *   <li>{@code reordered} — {@code true} iff the appearance-order sequence of
 *       token indices (ALL tokens, matched or unmatched) contains an inversion
 *       relative to the canonical first-occurrence order; duplicates alone
 *       never imply reordering.</li>
 * </ul>
 *
 * <p>Every replacement is quoted with {@link Matcher#quoteReplacement}:
 * variables may contain {@code $} and {@code \} characters (e.g.
 * {@code %1$s}), which {@code appendReplacement} would otherwise interpret as
 * group references or escapes, corrupting valid round trips.
 *
 * <p>The service is stateless (no instance fields) and thread-safe: the
 * precompiled {@link Pattern} is immutable and a fresh {@link Matcher} is
 * created per call.
 */
public final class VariableUnmasker {

	private static final Pattern TOKEN_PATTERN = Pattern.compile("__VAR_(\\d+)__");

	/**
	 * Restores masked variables in the translated text best-effort.
	 *
	 * @param masked         the {@link MaskedText} whose variables restore the in-range tokens, never {@code null}
	 * @param translatedText the translated text to scan for {@code __VAR_N__} tokens, never {@code null}
	 * @return the {@link UnmaskResult} carrying the restored text and the discrepancy report
	 * @throws NullPointerException if {@code masked} or {@code translatedText} is {@code null}
	 */
	public UnmaskResult unmask(MaskedText masked, String translatedText) {
		Objects.requireNonNull(masked, "masked must not be null");
		Objects.requireNonNull(translatedText, "translatedText must not be null");
		int n = masked.variables().size();
		boolean[] seen = new boolean[n];
		TreeSet<Integer> unmatched = new TreeSet<>();
		int maxSeen = -1;
		boolean reordered = false;
		StringBuilder restored = new StringBuilder();
		Matcher matcher = TOKEN_PATTERN.matcher(translatedText);
		while (matcher.find()) {
			int index = parseTokenIndex(matcher.group(1));
			if (index >= n) {
				// M6 resolution: never replaced with "" and never throws — the
				// quoted token itself is left verbatim and recorded as unmatched.
				unmatched.add(index);
				matcher.appendReplacement(restored, Matcher.quoteReplacement(matcher.group()));
			} else {
				seen[index] = true;
				// D8: quoting is mandatory — variables contain $ and \ (e.g. %1$s).
				matcher.appendReplacement(restored, Matcher.quoteReplacement(masked.variables().get(index)));
			}
			// R8: an inversion vs the running max flags reordering; a duplicate
			// (index == maxSeen) is non-decreasing and never triggers it.
			if (index < maxSeen) {
				reordered = true;
			} else {
				maxSeen = index;
			}
		}
		matcher.appendTail(restored);
		List<Integer> missing = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			if (!seen[i]) {
				missing.add(i);
			}
		}
		return new UnmaskResult(restored.toString(), missing, new ArrayList<>(unmatched), reordered);
	}

	/**
	 * Parses a token index digit run, clamping pathological values so the
	 * best-effort contract holds.
	 *
	 * <p>A digit run too long for {@code int} (e.g. inside
	 * {@code __VAR_99999999999999999999__}) is clamped to
	 * {@link Integer#MAX_VALUE}, which is always {@code ≥ n} and therefore
	 * treated as an unmatched token left verbatim — the unmasker never throws.
	 *
	 * @param digits the matched digit run
	 * @return the parsed index, clamped to {@link Integer#MAX_VALUE} on overflow
	 */
	private static int parseTokenIndex(String digits) {
		try {
			return Integer.parseInt(digits);
		} catch (NumberFormatException e) {
			return Integer.MAX_VALUE;
		}
	}
}
