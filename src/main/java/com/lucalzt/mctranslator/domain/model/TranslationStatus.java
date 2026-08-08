package com.lucalzt.mctranslator.domain.model;

/**
 * Outcome category of a single translation pipeline execution for one key.
 *
 * <p>The enum represents every documented pipeline outcome and groups metrics
 * (cache vs fast vs precise ratios, fallback counts). Human-readable detail is
 * carried per instance in {@code TranslationResult.warning}.
 */
public enum TranslationStatus {

	/** Translation served from the local cache; no engine ran. */
	CACHE_HIT,

	/** Translation produced by the fast engine. */
	TRANSLATED_FAST,

	/** Translation produced by the precise engine. */
	TRANSLATED_PRECISE,

	/** Precise engine failed; the fast engine produced the translation. */
	DEGRADED_TO_FAST,

	/** Total failure: no engine succeeded; the original text is kept. */
	FALLBACK_TO_ORIGINAL
}
