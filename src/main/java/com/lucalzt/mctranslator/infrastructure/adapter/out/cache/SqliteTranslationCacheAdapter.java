package com.lucalzt.mctranslator.infrastructure.adapter.out.cache;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import com.lucalzt.mctranslator.domain.model.LanguageCode;
import com.lucalzt.mctranslator.domain.model.TranslationKey;
import com.lucalzt.mctranslator.domain.model.TranslationResult;
import com.lucalzt.mctranslator.domain.model.TranslationStatus;
import com.lucalzt.mctranslator.domain.port.out.TranslationCachePort;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

/**
 * SQLite implementation of {@link TranslationCachePort} using JDBC.
 *
 * <p>Persists translation results in an embedded SQLite database file with
 * automatic schema initialization and transparent degradation on storage failures.
 */
@Repository
@NullMarked
public class SqliteTranslationCacheAdapter implements TranslationCachePort {

	private static final Logger logger = LoggerFactory.getLogger(SqliteTranslationCacheAdapter.class);

	private static final String CREATE_TABLE_SQL = """
			CREATE TABLE IF NOT EXISTS translation_cache (
			    modpack_id     TEXT NOT NULL,
			    json_path      TEXT NOT NULL,
			    original_text  TEXT NOT NULL,
			    source_lang    TEXT NOT NULL,
			    target_lang    TEXT NOT NULL,
			    translated_text TEXT NOT NULL,
			    engine_type    TEXT,
			    status         TEXT NOT NULL,
			    duration_ms    INTEGER NOT NULL,
			    updated_at     INTEGER NOT NULL,
			    PRIMARY KEY (modpack_id, json_path, original_text, source_lang, target_lang)
			);
			""";

	private static final String SELECT_SQL = """
			SELECT translated_text, engine_type, status, duration_ms, updated_at 
			FROM translation_cache 
			WHERE modpack_id = ? AND json_path = ? AND original_text = ? AND source_lang = ? AND target_lang = ?
			""";

	private static final String UPSERT_SQL = """
			INSERT OR REPLACE INTO translation_cache 
			(modpack_id, json_path, original_text, source_lang, target_lang, translated_text, engine_type, status, duration_ms, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
			""";

	private final MctranslatorCacheProperties properties;
	private final String jdbcUrl;

	/**
	 * Constructs the SQLite translation cache adapter.
	 *
	 * @param properties the cache configuration properties, never {@code null}
	 */
	public SqliteTranslationCacheAdapter(MctranslatorCacheProperties properties) {
		this.properties = Objects.requireNonNull(properties, "properties must not be null");
		Path resolvedPath = properties.resolveDbPath();
		this.jdbcUrl = "jdbc:sqlite:" + resolvedPath.toAbsolutePath();
		initializeDatabase(resolvedPath);
	}

	private void initializeDatabase(Path resolvedPath) {
		try {
			Path parentDir = resolvedPath.getParent();
			if (parentDir != null && !Files.exists(parentDir)) {
				Files.createDirectories(parentDir);
			}
			try (Connection conn = DriverManager.getConnection(jdbcUrl);
					Statement stmt = conn.createStatement()) {
				stmt.execute("PRAGMA journal_mode=WAL;");
				stmt.execute("PRAGMA busy_timeout=5000;");
				stmt.execute(CREATE_TABLE_SQL);
				logger.info("Initialized SQLite translation cache at {}", resolvedPath.toAbsolutePath());
			}
		} catch (Exception e) {
			logger.warn("Failed to initialize SQLite translation cache at {}: {}", resolvedPath, e.getMessage(), e);
		}
	}

	@Override
	public Optional<TranslationResult> find(TranslationKey key, LanguageCode sourceLang, LanguageCode targetLang) {
		Objects.requireNonNull(key, "key must not be null");
		Objects.requireNonNull(sourceLang, "sourceLang must not be null");
		Objects.requireNonNull(targetLang, "targetLang must not be null");

		String modpackIdStr = key.modpack().name() + ":" + key.modpack().version();

		try (Connection conn = DriverManager.getConnection(jdbcUrl);
				PreparedStatement pstmt = conn.prepareStatement(SELECT_SQL)) {
			pstmt.setString(1, modpackIdStr);
			pstmt.setString(2, key.path().value());
			pstmt.setString(3, key.originalText());
			pstmt.setString(4, sourceLang.value());
			pstmt.setString(5, targetLang.value());

			try (ResultSet rs = pstmt.executeQuery()) {
				if (rs.next()) {
					String translatedText = rs.getString("translated_text");
					return Optional.of(new TranslationResult(
							key,
							translatedText,
							TranslationStatus.CACHE_HIT,
							null,
							null,
							Duration.ZERO
					));
				}
			}
		} catch (Exception e) {
			logger.warn("Failed to query translation cache: {}", e.getMessage(), e);
		}
		return Optional.empty();
	}

	@Override
	public void save(TranslationKey key, LanguageCode sourceLang, LanguageCode targetLang, TranslationResult result) {
		Objects.requireNonNull(key, "key must not be null");
		Objects.requireNonNull(sourceLang, "sourceLang must not be null");
		Objects.requireNonNull(targetLang, "targetLang must not be null");
		Objects.requireNonNull(result, "result must not be null");

		String modpackIdStr = key.modpack().name() + ":" + key.modpack().version();
		String engineTypeStr = result.engine() != null ? result.engine().name() : null;

		try (Connection conn = DriverManager.getConnection(jdbcUrl);
				PreparedStatement pstmt = conn.prepareStatement(UPSERT_SQL)) {
			pstmt.setString(1, modpackIdStr);
			pstmt.setString(2, key.path().value());
			pstmt.setString(3, key.originalText());
			pstmt.setString(4, sourceLang.value());
			pstmt.setString(5, targetLang.value());
			pstmt.setString(6, result.translatedText());
			pstmt.setString(7, engineTypeStr);
			pstmt.setString(8, result.status().name());
			pstmt.setLong(9, result.duration().toMillis());
			pstmt.setLong(10, System.currentTimeMillis());

			pstmt.executeUpdate();
		} catch (Exception e) {
			logger.warn("Failed to save translation cache entry: {}", e.getMessage(), e);
		}
	}
}
