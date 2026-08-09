package com.lucalzt.mctranslator.domain.service;

import java.util.List;
import java.util.Objects;

import com.lucalzt.mctranslator.domain.model.TranslationEngineType;

/**
 * Immutable output record of {@code ScalingHeuristic.suggest}: the selected
 * translation engine, the matched rule number and the glossary term matches.
 *
 * <p>Rule-to-engine mapping: rules 1–3 always yield {@code PRECISE}, rules 4–6
 * always yield {@code FAST}.
 *
 * <p>Value contract guaranteed by the heuristic, documented here and NOT
 * re-validated in the constructor:
 * <ul>
 *   <li>{@code glossaryMatches} is non-empty exactly when {@code matchedRule}
 *       is 2 or 5; it is empty for rules 1, 3, 4 and 6;</li>
 *   <li>for {@code matchedRule} = 2, {@code glossaryMatches} contains at least
 *       one entry classified {@code AMBIGUOUS} — it may carry non-AMBIGUOUS
 *       matches too, because the full match list is carried;</li>
 *   <li>for {@code matchedRule} = 5, {@code glossaryMatches} contains no entry
 *       classified {@code LORE} (it may be empty or contain only {@code PLAIN}
 *       entries).</li>
 * </ul>
 *
 * <p>The {@code glossaryMatches} list is defensively copied at construction, so
 * the instance is unaffected by later mutation of the caller's list and the
 * accessor-returned list is immutable. Equality is by all three components.
 *
 * @param engine          the selected translation engine, never {@code null}
 * @param matchedRule     the matched rule number, always in 1..6
 * @param glossaryMatches the glossary term matches (rules 2/5), never {@code null}, elements never {@code null}
 */
public record ScalingDecision(TranslationEngineType engine, int matchedRule,
		List<GlossaryTermMatch> glossaryMatches) {

	/**
	 * Compact constructor enforcing the record invariants.
	 *
	 * @throws NullPointerException     if {@code engine} is {@code null}, or {@code glossaryMatches} is {@code null}, or any element of {@code glossaryMatches} is {@code null}
	 * @throws IllegalArgumentException if {@code matchedRule} is outside 1..6
	 */
	public ScalingDecision {
		Objects.requireNonNull(engine, "engine must not be null");
		if (matchedRule < 1 || matchedRule > 6) {
			throw new IllegalArgumentException("matchedRule must be in 1..6");
		}
		Objects.requireNonNull(glossaryMatches, "glossaryMatches must not be null");
		glossaryMatches = List.copyOf(glossaryMatches);
	}

	/**
	 * Returns the selected translation engine (rules 1–3 → {@code PRECISE},
	 * rules 4–6 → {@code FAST}).
	 *
	 * @return the engine
	 */
	@Override
	public TranslationEngineType engine() {
		return engine;
	}

	/**
	 * Returns the matched rule number, always in 1..6.
	 *
	 * @return the matched rule
	 */
	@Override
	public int matchedRule() {
		return matchedRule;
	}

	/**
	 * Returns the glossary term matches produced by rules 2 and 5, as an
	 * immutable, defensively copied list.
	 *
	 * @return the glossary matches, empty when {@code matchedRule} is 1, 3, 4 or 6
	 */
	@Override
	public List<GlossaryTermMatch> glossaryMatches() {
		return glossaryMatches;
	}
}
