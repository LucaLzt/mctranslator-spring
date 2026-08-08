package com.lucalzt.mctranslator.domain.model;

import java.time.Duration;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Per-key output unit of the translation pipeline: the translated text plus
 * rich metadata about how it was produced.
 *
 * <p>Immutable and validated at construction time: {@code key},
 * {@code translatedText}, {@code status} and {@code duration} must be non-null
 * and {@code duration} must not be negative (zero is allowed — cache hits can
 * be instant). {@code engine} and {@code warning} are the package's only
 * {@link Nullable} elements and are not null-checked; their per-status
 * contract is:
 *
 * <table border="1">
 * <caption>Per-status engine/warning contract</caption>
 * <tr><th>Status</th><th>{@code engine}</th><th>{@code warning}</th></tr>
 * <tr><td>{@code CACHE_HIT}</td><td>{@code null} (no engine ran)</td><td>absent</td></tr>
 * <tr><td>{@code TRANSLATED_FAST}</td><td>{@code FAST}</td><td>absent</td></tr>
 * <tr><td>{@code TRANSLATED_PRECISE}</td><td>{@code PRECISE}</td><td>absent</td></tr>
 * <tr><td>{@code DEGRADED_TO_FAST}</td><td>{@code FAST}</td><td>message about the precise-engine fallback</td></tr>
 * <tr><td>{@code FALLBACK_TO_ORIGINAL}</td><td>{@code null} (no engine succeeded)</td><td>message about total failure</td></tr>
 * </table>
 *
 * <p>When {@code status} is {@code FALLBACK_TO_ORIGINAL} the result represents
 * total failure: {@code translatedText} equals {@code key.originalText()} and
 * {@code warning} carries the failure message. Equality is by all six
 * components.
 *
 * @param key            the identity of the translated leaf, never {@code null}
 * @param translatedText the translated text, or the original text on total failure, never {@code null}
 * @param status         the pipeline outcome, never {@code null}
 * @param engine         the engine that produced the translation, {@code null} on cache hit and total failure
 * @param warning        human-readable detail of a degraded or failed outcome, {@code null} otherwise
 * @param duration       the time the translation took, never {@code null} or negative
 */
public record TranslationResult(TranslationKey key, String translatedText, TranslationStatus status,
		@Nullable TranslationEngineType engine, @Nullable String warning, Duration duration) {

	/**
	 * Compact constructor enforcing the record invariants.
	 *
	 * @throws NullPointerException     if any of {@code key}, {@code translatedText}, {@code status} or {@code duration} is {@code null}
	 * @throws IllegalArgumentException if {@code duration} is negative
	 */
	public TranslationResult {
		Objects.requireNonNull(key, "key must not be null");
		Objects.requireNonNull(translatedText, "translatedText must not be null");
		Objects.requireNonNull(status, "status must not be null");
		Objects.requireNonNull(duration, "duration must not be null");
		if (duration.isNegative()) {
			throw new IllegalArgumentException("duration must not be negative");
		}
	}

	/**
	 * Returns the identity of the translated leaf.
	 *
	 * @return the key
	 */
	@Override
	public TranslationKey key() {
		return key;
	}

	/**
	 * Returns the translated text, or the original text on total failure.
	 *
	 * @return the translated text
	 */
	@Override
	public String translatedText() {
		return translatedText;
	}

	/**
	 * Returns the pipeline outcome.
	 *
	 * @return the status
	 */
	@Override
	public TranslationStatus status() {
		return status;
	}

	/**
	 * Returns the engine that produced the translation, or {@code null} on cache
	 * hit and total failure.
	 *
	 * @return the engine, possibly {@code null}
	 */
	@Override
	@Nullable
	public TranslationEngineType engine() {
		return engine;
	}

	/**
	 * Returns the human-readable detail of a degraded or failed outcome, or
	 * {@code null} otherwise.
	 *
	 * @return the warning, possibly {@code null}
	 */
	@Override
	@Nullable
	public String warning() {
		return warning;
	}

	/**
	 * Returns the time the translation took.
	 *
	 * @return the duration
	 */
	@Override
	public Duration duration() {
		return duration;
	}
}
