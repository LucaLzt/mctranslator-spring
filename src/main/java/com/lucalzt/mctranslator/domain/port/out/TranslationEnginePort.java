package com.lucalzt.mctranslator.domain.port.out;

import com.lucalzt.mctranslator.domain.model.LanguageCode;
import com.lucalzt.mctranslator.domain.model.TranslationEngineType;
import com.lucalzt.mctranslator.domain.model.TranslationKey;
import com.lucalzt.mctranslator.domain.model.TranslationResult;

import org.jspecify.annotations.NullMarked;

/**
 * Port interface for external translation engines (e.g. DeepL, Fast/Precise LLM engines).
 */
@NullMarked
public interface TranslationEnginePort {

	/**
	 * Translates a single translation key text from source language to target language using the specified engine type.
	 *
	 * @param key        the translation key and original text, never {@code null}
	 * @param sourceLang the source language code, never {@code null}
	 * @param targetLang the target language code, never {@code null}
	 * @param engineType the translation engine type, never {@code null}
	 * @return the translation result, never {@code null}
	 */
	TranslationResult translate(TranslationKey key, LanguageCode sourceLang, LanguageCode targetLang, TranslationEngineType engineType);
}
