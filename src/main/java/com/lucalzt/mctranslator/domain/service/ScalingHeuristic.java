package com.lucalzt.mctranslator.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

import com.lucalzt.mctranslator.domain.model.GlossaryEntry;
import com.lucalzt.mctranslator.domain.model.GlossaryEntryClassification;
import com.lucalzt.mctranslator.domain.model.JsonPath;
import com.lucalzt.mctranslator.domain.model.TranslationEngineType;

/**
 * Stateless domain service deciding the translation engine for a key via the
 * 6-rule precedence table of the scaling heuristic.
 *
 * <p>Evaluation ALWAYS operates on the {@code maskedText} argument — word
 * counting (rules 3 and 5) and glossary matching (rules 2 and 5) never touch
 * raw or unmasked text.
 *
 * <p>The rules are evaluated in strict order, first match wins, and are lazy
 * per rule (the glossary scan runs only when rule 1 fails; the word count of
 * rule 3 runs only when rules 1–2 failed):
 *
 * <table>
 *   <caption>Rule precedence table</caption>
 *   <tr><th>Order</th><th>Condition</th><th>Engine / rule</th></tr>
 *   <tr><td>1</td><td>{@code path} starts with {@code quest.description},
 *       {@code lore} or {@code advancement}</td><td>PRECISE / 1</td></tr>
 *   <tr><td>2</td><td>at least one glossary term classified {@code AMBIGUOUS}
 *       is detected in {@code maskedText}</td><td>PRECISE / 2</td></tr>
 *   <tr><td>3</td><td>word count of {@code maskedText} is strictly greater
 *       than {@value #THRESHOLD_PRECISE_WORDS}</td><td>PRECISE / 3</td></tr>
 *   <tr><td>4</td><td>{@code path} starts with {@code item}, {@code block},
 *       {@code entity} or {@code gui}</td><td>FAST / 4</td></tr>
 *   <tr><td>5</td><td>word count of {@code maskedText} is at most
 *       {@value #THRESHOLD_FAST_WORDS} and no glossary term classified
 *       {@code LORE} is detected</td><td>FAST / 5</td></tr>
 *   <tr><td>6</td><td>default — any other case</td><td>FAST / 6</td></tr>
 * </table>
 *
 * <p>Boundary semantics: {@code > THRESHOLD_PRECISE_WORDS} is strict (exactly
 * 30 words does NOT match rule 3); {@code ≤ THRESHOLD_FAST_WORDS} is inclusive
 * (exactly 8 words qualifies for rule 5). With an empty glossary, rule 2 is
 * vacuously false and rule 5 fires on short text (the "no LORE detected"
 * clause is vacuously true). When a {@code LORE} term is detected on short
 * text, rule 5 does NOT match and control falls to rule 6, which produces the
 * same FAST engine outcome — the equivalence is pinned and must not be
 * reworked into a LORE → PRECISE rule.
 *
 * <p>The class is stateless (no instance fields); the 30/8 thresholds are
 * compile-time named constants and one glossary pattern is compiled per entry
 * per {@link #suggest} call.
 */
public final class ScalingHeuristic {

	/** Rule 3 threshold: word count must be strictly greater than this value. */
	private static final int THRESHOLD_PRECISE_WORDS = 30;

	/** Rule 5 threshold: word count must be at most this value (inclusive). */
	private static final int THRESHOLD_FAST_WORDS = 8;

	/**
	 * Creates a scaling heuristic.
	 *
	 * <p>The implicit default constructor is declared explicitly so the
	 * application layer can instantiate the service.
	 */
	public ScalingHeuristic() {
		// default public constructor
	}

	/**
	 * Suggests a translation engine decision for the given path and masked
	 * text, applying the 6-rule precedence table (first match wins).
	 *
	 * @param path       the JSON path of the key, never {@code null}
	 * @param maskedText the masked translation text, never {@code null}; the
	 *                   only text this method evaluates (word counts and
	 *                   glossary matching always operate on it)
	 * @param glossary   the glossary entries to match against, never {@code null},
	 *                   may be empty
	 * @return the decision record carrying the engine, the matched rule number
	 *         and the full glossary match list of rules 2/5 (empty for rules
	 *         1, 3, 4 and 6)
	 * @throws NullPointerException if {@code path}, {@code maskedText} or
	 *                              {@code glossary} is {@code null}
	 */
	public ScalingDecision suggest(JsonPath path, String maskedText, List<GlossaryEntry> glossary) {
		Objects.requireNonNull(path, "path must not be null");
		Objects.requireNonNull(maskedText, "maskedText must not be null");
		Objects.requireNonNull(glossary, "glossary must not be null");

		// Rule 1 — path-only, no glossary scan and no word count (D14).
		if (path.startsWith("quest", "description") || path.startsWith("lore")
				|| path.startsWith("advancement")) {
			return new ScalingDecision(TranslationEngineType.PRECISE, 1, List.of());
		}

		// One matching pass, computed lazily only if rule 1 failed (D14);
		// reused by rules 2 and 5 (D3).
		List<GlossaryTermMatch> matches = matchGlossaryTerms(maskedText, glossary);

		// Rule 2.
		if (matches.stream().anyMatch(match -> match.classification() == GlossaryEntryClassification.AMBIGUOUS)) {
			return new ScalingDecision(TranslationEngineType.PRECISE, 2, matches);
		}

		// Rule 3 — strict threshold: exactly THRESHOLD_PRECISE_WORDS does NOT match.
		if (countWords(maskedText) > THRESHOLD_PRECISE_WORDS) {
			return new ScalingDecision(TranslationEngineType.PRECISE, 3, List.of());
		}

		// Rule 4.
		if (path.startsWith("item") || path.startsWith("block") || path.startsWith("entity")
				|| path.startsWith("gui")) {
			return new ScalingDecision(TranslationEngineType.FAST, 4, List.of());
		}

		// Rule 5 — inclusive threshold and the LORE guard: a detected LORE
		// term makes rule 5 not match; control then falls to rule 6 (same FAST
		// engine outcome, pinned equivalence).
		boolean hasLore = matches.stream()
				.anyMatch(match -> match.classification() == GlossaryEntryClassification.LORE);
		if (countWords(maskedText) <= THRESHOLD_FAST_WORDS && !hasLore) {
			return new ScalingDecision(TranslationEngineType.FAST, 5, matches);
		}

		// Rule 6 — default.
		return new ScalingDecision(TranslationEngineType.FAST, 6, List.of());
	}

	/**
	 * Counts the words of the given text on the masked text only: every
	 * whitespace-separated token counts as one word, including {@code __VAR_N__}
	 * tokens and punctuation-only tokens; blank or whitespace-only text counts
	 * as zero.
	 *
	 * @param maskedText the text to count, never {@code null}
	 * @return the number of whitespace-separated tokens, 0 when blank after stripping
	 */
	static int countWords(String maskedText) {
		String stripped = maskedText.strip();
		if (stripped.isEmpty()) {
			return 0;
		}
		return stripped.split("\\s+").length;
	}

	/**
	 * Matches every glossary entry against the masked text with whole-word,
	 * case-sensitive semantics: a term matches iff it occurs as a contiguous
	 * substring bounded on both sides by non-word characters (Java {@code \b}
	 * semantics with the term {@link Pattern#quote quoted}). One pattern is
	 * compiled per entry per call; one {@link GlossaryTermMatch} is produced
	 * per matching entry (no dedup by term).
	 *
	 * @param maskedText the masked text to match against
	 * @param glossary   the glossary entries
	 * @return the full list of matches, in glossary order, possibly empty
	 */
	private static List<GlossaryTermMatch> matchGlossaryTerms(String maskedText, List<GlossaryEntry> glossary) {
		List<GlossaryTermMatch> matches = new ArrayList<>();
		for (GlossaryEntry entry : glossary) {
			Pattern pattern = Pattern.compile("\\b" + Pattern.quote(entry.term()) + "\\b");
			if (pattern.matcher(maskedText).find()) {
				matches.add(new GlossaryTermMatch(entry.term(), entry.classification()));
			}
		}
		return matches;
	}
}
