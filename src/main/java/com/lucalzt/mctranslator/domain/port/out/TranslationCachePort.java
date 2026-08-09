package com.lucalzt.mctranslator.domain.port.out;

import java.util.Optional;

import com.lucalzt.mctranslator.domain.model.LanguageCode;
import com.lucalzt.mctranslator.domain.model.TranslationKey;
import com.lucalzt.mctranslator.domain.model.TranslationResult;

import org.jspecify.annotations.NullMarked;

/**
 * Port interface for translation result caching (SQLite/Local cache).
 */
@NullMarked
public interface TranslationCachePort {

	/**
	 * Finds a cached translation result for the given translation key and language pair.
	 *
	 * @param key        the translation key, never {@code null}
	 * @param sourceLang the source language code, never {@code null}
	 * @param targetLang the target language code, never {@code null}
	 * @return an optional containing the translation result if found, or empty if not found, never {@code null}
	 */
	Optional<TranslationResult> find(TranslationKey key, LanguageCode sourceLang, LanguageCode targetLang);

	/**
	 * Saves a translation result to the cache for the given language pair.
	 *
	 * @param key        the translation key, never {@code null}
	 * @param sourceLang the source language code, never {@code null}
	 * @param targetLang the target language code, never {@code null}
	 * @param result     the translation result to save, never {@code null}
	 */
	void save(TranslationKey key, LanguageCode sourceLang, LanguageCode targetLang, TranslationResult result);
}
