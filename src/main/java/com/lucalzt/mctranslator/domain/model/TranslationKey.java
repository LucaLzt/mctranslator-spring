package com.lucalzt.mctranslator.domain.model;

import java.util.Objects;

/**
 * Identity of a single translatable string leaf of a modpack file.
 *
 * <p>Immutable and validated at construction time: all four components must be
 * non-null and {@code originalText} must be non-blank. The blank-text invariant
 * models the boundary of the deferred extraction edge-case policy. Equality is
 * by all four components, forming the semantic basis of the future cache key.
 *
 * @param path           the JSON path of the leaf in the modpack file, never {@code null}
 * @param originalText   the original string leaf, never {@code null} or blank
 * @param targetLanguage the language the leaf is translated to, never {@code null}
 * @param modpack        the modpack the leaf belongs to, never {@code null}
 */
public record TranslationKey(JsonPath path, String originalText, LanguageCode targetLanguage, ModpackId modpack) {

	/**
	 * Compact constructor enforcing the identity invariants.
	 *
	 * @throws NullPointerException     if any component is {@code null}
	 * @throws IllegalArgumentException if {@code originalText} is blank
	 */
	public TranslationKey {
		Objects.requireNonNull(path, "path must not be null");
		Objects.requireNonNull(originalText, "originalText must not be null");
		Objects.requireNonNull(targetLanguage, "targetLanguage must not be null");
		Objects.requireNonNull(modpack, "modpack must not be null");
		if (originalText.isBlank()) {
			throw new IllegalArgumentException("originalText must not be blank");
		}
	}

	/**
	 * Returns the JSON path of the leaf in the modpack file.
	 *
	 * @return the JSON path
	 */
	@Override
	public JsonPath path() {
		return path;
	}

	/**
	 * Returns the original string leaf.
	 *
	 * @return the original text
	 */
	@Override
	public String originalText() {
		return originalText;
	}

	/**
	 * Returns the language the leaf is translated to.
	 *
	 * @return the target language
	 */
	@Override
	public LanguageCode targetLanguage() {
		return targetLanguage;
	}

	/**
	 * Returns the modpack the leaf belongs to.
	 *
	 * @return the modpack
	 */
	@Override
	public ModpackId modpack() {
		return modpack;
	}
}
