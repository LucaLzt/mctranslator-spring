package com.lucalzt.mctranslator.domain.port.out;

import java.util.List;

import com.lucalzt.mctranslator.domain.model.GlossaryEntry;
import com.lucalzt.mctranslator.domain.model.LanguageCode;
import com.lucalzt.mctranslator.domain.model.ModpackId;

import org.jspecify.annotations.NullMarked;

/**
 * Port interface for retrieving translation glossaries and terminology rules.
 */
@NullMarked
public interface GlossaryPort {

	/**
	 * Retrieves the list of glossary entries for a given modpack and language pair.
	 *
	 * @param modpackId  the modpack identifier, never {@code null}
	 * @param sourceLang the source language code, never {@code null}
	 * @param targetLang the target language code, never {@code null}
	 * @return a list of glossary entries, never {@code null}
	 */
	List<GlossaryEntry> getTerms(ModpackId modpackId, LanguageCode sourceLang, LanguageCode targetLang);
}
