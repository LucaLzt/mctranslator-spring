package com.lucalzt.mctranslator.infrastructure.adapter.out.glossary;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lucalzt.mctranslator.domain.model.GlossaryEntry;
import com.lucalzt.mctranslator.domain.model.GlossaryEntryClassification;
import com.lucalzt.mctranslator.domain.model.LanguageCode;
import com.lucalzt.mctranslator.domain.model.ModpackId;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@NullMarked
class JsonGlossaryAdapterTest {

	@TempDir
	Path tempDir;

	private ObjectMapper objectMapper;

	private static final ModpackId MODPACK = new ModpackId("BetterMC", "1.21.1");
	private static final LanguageCode SOURCE_LANG = new LanguageCode("en");
	private static final LanguageCode TARGET_LANG = new LanguageCode("es");

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper();
	}

	@Test
	@DisplayName("Should successfully load glossary file and expand synonyms")
	void shouldLoadGlossaryAndExpandSynonyms() throws Exception {
		Path glossaryDir = Path.of("glossary");
		Files.createDirectories(glossaryDir);
		Path glossaryFile = glossaryDir.resolve("en-es.json");

		String jsonContent = """
				[
				  {
				    "term": "sword",
				    "translation": "espada",
				    "synonyms": ["blade", "brand"],
				    "classification": "PLAIN"
				  },
				  {
				    "term": "ancient lore",
				    "translation": "sabiduría antigua",
				    "classification": "LORE"
				  }
				]
				""";
		Files.writeString(glossaryFile, jsonContent);

		try {
			JsonGlossaryAdapter testAdapter = new JsonGlossaryAdapter(objectMapper);
			List<GlossaryEntry> entries = testAdapter.getTerms(MODPACK, SOURCE_LANG, TARGET_LANG);

			assertThat(entries).hasSize(4);
			assertThat(entries).containsExactlyInAnyOrder(
					new GlossaryEntry("sword", "espada", GlossaryEntryClassification.PLAIN),
					new GlossaryEntry("blade", "espada", GlossaryEntryClassification.PLAIN),
					new GlossaryEntry("brand", "espada", GlossaryEntryClassification.PLAIN),
					new GlossaryEntry("ancient lore", "sabiduría antigua", GlossaryEntryClassification.LORE)
			);
		} finally {
			Files.deleteIfExists(glossaryFile);
		}
	}

	@Test
	@DisplayName("Should return empty list gracefully when glossary file does not exist")
	void shouldReturnEmptyListWhenFileNotFound() {
		JsonGlossaryAdapter testAdapter = new JsonGlossaryAdapter(objectMapper);
		List<GlossaryEntry> entries = testAdapter.getTerms(MODPACK, new LanguageCode("en"), new LanguageCode("fr"));

		assertThat(entries).isEmpty();
	}

	@Test
	@DisplayName("Should cache glossary entries and avoid redundant disk reads")
	void shouldCacheGlossaryEntries() throws Exception {
		Path glossaryDir = Path.of("glossary");
		Files.createDirectories(glossaryDir);
		Path glossaryFile = glossaryDir.resolve("en-de.json");

		String jsonContent = """
				[
				  {
				    "term": "potion",
				    "translation": "Trank"
				  }
				]
				""";
		Files.writeString(glossaryFile, jsonContent);

		try {
			JsonGlossaryAdapter testAdapter = new JsonGlossaryAdapter(objectMapper);
			LanguageCode deTarget = new LanguageCode("de");

			// First call (disk read)
			List<GlossaryEntry> firstCall = testAdapter.getTerms(MODPACK, SOURCE_LANG, deTarget);
			assertThat(firstCall).hasSize(1);

			// Delete file from disk to verify caching serves from memory
			Files.deleteIfExists(glossaryFile);

			// Second call (cache hit)
			List<GlossaryEntry> secondCall = testAdapter.getTerms(MODPACK, SOURCE_LANG, deTarget);
			assertThat(secondCall).hasSize(1);
			assertThat(secondCall).isEqualTo(firstCall);
		} finally {
			Files.deleteIfExists(glossaryFile);
		}
	}

	@Test
	@DisplayName("Should handle malformed JSON gracefully by returning empty list")
	void shouldHandleMalformedJsonGracefully() throws Exception {
		Path glossaryDir = Path.of("glossary");
		Files.createDirectories(glossaryDir);
		Path glossaryFile = glossaryDir.resolve("en-it.json");

		Files.writeString(glossaryFile, "invalid json content {");

		try {
			JsonGlossaryAdapter testAdapter = new JsonGlossaryAdapter(objectMapper);
			List<GlossaryEntry> entries = testAdapter.getTerms(MODPACK, SOURCE_LANG, new LanguageCode("it"));

			assertThat(entries).isEmpty();
		} finally {
			Files.deleteIfExists(glossaryFile);
		}
	}

	@Test
	@DisplayName("Should reject language codes with path traversal characters")
	void shouldRejectPathTraversalLanguageCodes() {
		JsonGlossaryAdapter testAdapter = new JsonGlossaryAdapter(objectMapper);
		LanguageCode maliciousLang = new LanguageCode("../etc");

		assertThatThrownBy(() -> testAdapter.getTerms(MODPACK, SOURCE_LANG, maliciousLang))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
