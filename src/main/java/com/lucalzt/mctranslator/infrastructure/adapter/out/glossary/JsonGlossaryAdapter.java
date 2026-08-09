package com.lucalzt.mctranslator.infrastructure.adapter.out.glossary;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucalzt.mctranslator.domain.model.GlossaryEntry;
import com.lucalzt.mctranslator.domain.model.GlossaryEntryClassification;
import com.lucalzt.mctranslator.domain.model.LanguageCode;
import com.lucalzt.mctranslator.domain.model.ModpackId;
import com.lucalzt.mctranslator.domain.port.out.GlossaryPort;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * JSON file implementation of {@link GlossaryPort}.
 *
 * <p>Loads, parses, and expands modpack translation glossaries from local files or
 * classpath resources matching {@code glossary/{sourceLang}-{targetLang}.json},
 * supporting synonym expansion and thread-safe in-memory caching.
 */
@Repository
@NullMarked
public class JsonGlossaryAdapter implements GlossaryPort {

	private static final Logger logger = LoggerFactory.getLogger(JsonGlossaryAdapter.class);
	private static final Pattern LANGUAGE_CODE_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

	private final ObjectMapper objectMapper;
	private final ConcurrentMap<String, List<GlossaryEntry>> cache = new ConcurrentHashMap<>();

	/**
	 * Constructs the JSON glossary adapter with a default {@link ObjectMapper}.
	 */
	public JsonGlossaryAdapter() {
		this.objectMapper = new ObjectMapper();
	}

	/**
	 * Constructs the JSON glossary adapter with a custom {@link ObjectMapper}.
	 *
	 * @param objectMapper the Jackson object mapper, never {@code null}
	 */
	public JsonGlossaryAdapter(ObjectMapper objectMapper) {
		this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
	}

	@Override
	public List<GlossaryEntry> getTerms(ModpackId modpackId, LanguageCode sourceLang, LanguageCode targetLang) {
		Objects.requireNonNull(modpackId, "modpackId must not be null");
		Objects.requireNonNull(sourceLang, "sourceLang must not be null");
		Objects.requireNonNull(targetLang, "targetLang must not be null");

		validateLanguageCode(sourceLang);
		validateLanguageCode(targetLang);

		String cacheKey = modpackId.name() + ":" + modpackId.version() + ":" + sourceLang.value() + "-" + targetLang.value();

		return cache.computeIfAbsent(cacheKey, key -> loadAndParseGlossary(sourceLang, targetLang));
	}

	private void validateLanguageCode(LanguageCode lang) {
		if (!LANGUAGE_CODE_PATTERN.matcher(lang.value()).matches()) {
			throw new IllegalArgumentException("Invalid language code format (risk of path traversal): " + lang.value());
		}
	}

	private List<GlossaryEntry> loadAndParseGlossary(LanguageCode sourceLang, LanguageCode targetLang) {
		String filename = sourceLang.value() + "-" + targetLang.value() + ".json";
		Path filePath = Path.of("glossary", filename);

		try {
			List<GlossaryEntryDto> dtos = null;

			if (Files.exists(filePath)) {
				logger.info("Loading glossary file from filesystem: {}", filePath.toAbsolutePath());
				String jsonContent = Files.readString(filePath, StandardCharsets.UTF_8);
				dtos = objectMapper.readValue(jsonContent, objectMapper.getTypeFactory().constructCollectionType(List.class, GlossaryEntryDto.class));
			} else {
				// Try classpath resource
				String classpathLocation = "glossary/" + filename;
				try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpathLocation)) {
					if (inputStream != null) {
						logger.info("Loading glossary file from classpath: {}", classpathLocation);
						dtos = objectMapper.readValue(inputStream, objectMapper.getTypeFactory().constructCollectionType(List.class, GlossaryEntryDto.class));
					}
				}
			}

			if (dtos == null) {
				logger.debug("Glossary file not found for language pair {}-{} (checked path {} and classpath glossary/{})",
						sourceLang.value(), targetLang.value(), filePath, filename);
				return List.of();
			}

			List<GlossaryEntry> entries = new ArrayList<>();
			for (GlossaryEntryDto dto : dtos) {
				try {
					if (dto.term() == null || dto.translation() == null) {
						logger.warn("Skipping glossary entry with null term or translation: {}", dto);
						continue;
					}
					GlossaryEntryClassification classification = dto.resolvedClassification();
					// Primary term entry
					entries.add(new GlossaryEntry(dto.term(), dto.translation(), classification));

					// Synonym expansion
					for (String synonym : dto.synonyms()) {
						if (synonym != null && !synonym.isBlank()) {
							entries.add(new GlossaryEntry(synonym, dto.translation(), classification));
						}
					}
				} catch (Exception e) {
					logger.warn("Failed to parse individual glossary entry {}: {}", dto, e.getMessage());
				}
			}

			logger.info("Successfully parsed and expanded {} glossary entries for language pair {}-{}",
					entries.size(), sourceLang.value(), targetLang.value());
			return List.copyOf(entries);

		} catch (Exception e) {
			logger.warn("Failed to read or parse glossary file for language pair {}-{}: {}",
					sourceLang.value(), targetLang.value(), e.getMessage(), e);
			return List.of();
		}
	}
}
