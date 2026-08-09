package com.lucalzt.mctranslator.infrastructure.adapter.out.cache;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

import com.lucalzt.mctranslator.domain.model.JsonPath;
import com.lucalzt.mctranslator.domain.model.LanguageCode;
import com.lucalzt.mctranslator.domain.model.ModpackId;
import com.lucalzt.mctranslator.domain.model.TranslationEngineType;
import com.lucalzt.mctranslator.domain.model.TranslationKey;
import com.lucalzt.mctranslator.domain.model.TranslationResult;
import com.lucalzt.mctranslator.domain.model.TranslationStatus;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

@NullMarked
class SqliteTranslationCacheAdapterTest {

	@TempDir
	Path tempDir;

	private SqliteTranslationCacheAdapter adapter;
	private MctranslatorCacheProperties properties;

	private static final ModpackId MODPACK = new ModpackId("BetterMC", "1.21.1");
	private static final JsonPath PATH = new JsonPath("quests.json#/title");
	private static final LanguageCode SOURCE_LANG = new LanguageCode("en");
	private static final LanguageCode TARGET_LANG = new LanguageCode("es");
	private static final TranslationKey KEY = new TranslationKey(PATH, "Hello World", TARGET_LANG, MODPACK);

	@BeforeEach
	void setUp() {
		Path dbFile = tempDir.resolve("test-cache.db");
		properties = new MctranslatorCacheProperties();
		properties.setDbPath(dbFile.toString());
		adapter = new SqliteTranslationCacheAdapter(properties);
	}

	@Test
	@DisplayName("Should return Optional.empty() on cache miss")
	void shouldReturnEmptyOnCacheMiss() {
		Optional<TranslationResult> result = adapter.find(KEY, SOURCE_LANG, TARGET_LANG);
		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("Should save and retrieve translation result on cache hit")
	void shouldSaveAndRetrieveCacheHit() {
		TranslationResult savedResult = new TranslationResult(
				KEY,
				"Hola Mundo",
				TranslationStatus.TRANSLATED_FAST,
				TranslationEngineType.FAST,
				null,
				Duration.ofMillis(150)
		);

		adapter.save(KEY, SOURCE_LANG, TARGET_LANG, savedResult);

		Optional<TranslationResult> found = adapter.find(KEY, SOURCE_LANG, TARGET_LANG);
		assertThat(found).isPresent();
		TranslationResult hit = found.get();
		assertThat(hit.translatedText()).isEqualTo("Hola Mundo");
		assertThat(hit.status()).isEqualTo(TranslationStatus.CACHE_HIT);
		assertThat(hit.engine()).isNull();
		assertThat(hit.warning()).isNull();
		assertThat(hit.duration()).isEqualTo(Duration.ZERO);
		assertThat(hit.key()).isEqualTo(KEY);
	}

	@Test
	@DisplayName("Should isolate cache entries by composite key components")
	void shouldIsolateCompositeKeyComponents() {
		TranslationResult resultEs = new TranslationResult(
				KEY, "Hola", TranslationStatus.TRANSLATED_FAST, TranslationEngineType.FAST, null, Duration.ofMillis(10));
		adapter.save(KEY, SOURCE_LANG, TARGET_LANG, resultEs);

		// Different target language
		LanguageCode targetFr = new LanguageCode("fr");
		TranslationKey keyFr = new TranslationKey(PATH, "Hello World", targetFr, MODPACK);
		TranslationResult resultFr = new TranslationResult(
				keyFr, "Bonjour", TranslationStatus.TRANSLATED_FAST, TranslationEngineType.FAST, null, Duration.ofMillis(10));
		adapter.save(keyFr, SOURCE_LANG, targetFr, resultFr);

		// Verify isolation
		assertThat(adapter.find(KEY, SOURCE_LANG, TARGET_LANG)).isPresent()
				.get().extracting(TranslationResult::translatedText).isEqualTo("Hola");

		assertThat(adapter.find(keyFr, SOURCE_LANG, targetFr)).isPresent()
				.get().extracting(TranslationResult::translatedText).isEqualTo("Bonjour");

		// Different modpack
		ModpackId otherModpack = new ModpackId("OtherModpack", "1.0.0");
		TranslationKey keyOtherModpack = new TranslationKey(PATH, "Hello World", TARGET_LANG, otherModpack);
		assertThat(adapter.find(keyOtherModpack, SOURCE_LANG, TARGET_LANG)).isEmpty();
	}

	@Test
	@DisplayName("Should handle transparent degradation on find error without throwing")
	void shouldDegradeGracefullyOnFindError() {
		MctranslatorCacheProperties invalidProps = new MctranslatorCacheProperties();
		invalidProps.setDbPath(tempDir.toString()); // Directory path causes SQL exception on JDBC connection/query
		SqliteTranslationCacheAdapter faultyAdapter = new SqliteTranslationCacheAdapter(invalidProps);

		Optional<TranslationResult> result = faultyAdapter.find(KEY, SOURCE_LANG, TARGET_LANG);
		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("Should handle transparent degradation on save error without throwing")
	void shouldDegradeGracefullyOnSaveError() {
		MctranslatorCacheProperties invalidProps = new MctranslatorCacheProperties();
		invalidProps.setDbPath(tempDir.toString());
		SqliteTranslationCacheAdapter faultyAdapter = new SqliteTranslationCacheAdapter(invalidProps);

		TranslationResult savedResult = new TranslationResult(
				KEY, "Hola", TranslationStatus.TRANSLATED_FAST, TranslationEngineType.FAST, null, Duration.ofMillis(10));

		faultyAdapter.save(KEY, SOURCE_LANG, TARGET_LANG, savedResult);
	}
}
