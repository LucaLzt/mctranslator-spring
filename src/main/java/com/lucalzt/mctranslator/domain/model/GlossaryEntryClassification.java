package com.lucalzt.mctranslator.domain.model;

/**
 * Classification of a glossary entry, driving heuristic rules 2 and 5.
 *
 * <p>Plain enum, no payload: the classification is assigned by the glossary
 * adapter and consumed by the {@code ScalingHeuristic}.
 */
public enum GlossaryEntryClassification {

	/** Entry whose term is ambiguous and must always go to the precise engine. */
	AMBIGUOUS,

	/** Entry containing lore text, typically routed to the fast engine. */
	LORE,

	/** Regular plain entry, routed by the heuristic default. */
	PLAIN
}
