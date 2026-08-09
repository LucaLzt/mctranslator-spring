package com.lucalzt.mctranslator.infrastructure.adapter.out.glossary;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.lucalzt.mctranslator.domain.model.GlossaryEntryClassification;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Data Transfer Object for parsing JSON glossary entries.
 *
 * <p>Maps JSON fields {@code term}, {@code translation}, {@code synonyms},
 * and {@code classification} to domain-friendly values with defaults for
 * optional fields.
 *
 * @param term           the source term string
 * @param translation    the translated term string
 * @param synonyms       optional list of synonym strings
 * @param classification optional classification string
 */
@NullMarked
@JsonIgnoreProperties(ignoreUnknown = true)
public record GlossaryEntryDto(
		@JsonProperty("term") String term,
		@JsonProperty("translation") String translation,
		@JsonProperty("synonyms") @Nullable List<String> synonyms,
		@JsonProperty("classification") @Nullable String classification
) {

	/**
	 * Returns synonyms list, defaulting to empty list if null.
	 *
	 * @return non-null list of synonyms
	 */
	public List<String> synonyms() {
		return synonyms != null ? synonyms : List.of();
	}

	/**
	 * Resolves the classification string into a {@link GlossaryEntryClassification},
	 * defaulting to {@link GlossaryEntryClassification#PLAIN} if null, blank, or invalid.
	 *
	 * @return resolved classification
	 */
	public GlossaryEntryClassification resolvedClassification() {
		if (classification == null || classification.isBlank()) {
			return GlossaryEntryClassification.PLAIN;
		}
		try {
			return GlossaryEntryClassification.valueOf(classification.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			return GlossaryEntryClassification.PLAIN;
		}
	}
}
