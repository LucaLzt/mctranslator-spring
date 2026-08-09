# Design: SQLite Cache Adapter (`sqlite-cache-adapter`)

## Executive Summary

The `sqlite-cache-adapter` change introduces a robust, local SQLite persistence adapter for `TranslationCachePort` (`com.lucalzt.mctranslator.domain.port.out.TranslationCachePort`). It prevents redundant translation API calls by caching resolved translations in an embedded SQLite database file (defaulting to `./mctranslator.db` next to the application). 

Designed following Hexagonal Architecture principles, the adapter resides entirely in the infrastructure layer (`com.lucalzt.mctranslator.infrastructure.adapter.out.cache`), keeping the domain and application layers free of JDBC or SQLite dependencies. A key architectural guarantee of this adapter is **transparent degradation**: any storage, connection, query, or initialization failure is caught, logged as a warning via SLF4J, and handled gracefully (`Optional.empty()` on lookup, silent continuation on save) so that database unavailability never disrupts the translation pipeline.

---

## Architecture & Component Design

### Layer & Package Structure

Following Hexagonal Architecture and Spring Boot standards:
- **Domain Port**: `com.lucalzt.mctranslator.domain.port.out.TranslationCachePort` (driven port interface).
- **Infrastructure Adapter**: `com.lucalzt.mctranslator.infrastructure.adapter.out.cache.SqliteTranslationCacheAdapter`.
- **Configuration Properties**: `com.lucalzt.mctranslator.infrastructure.adapter.out.cache.MctranslatorCacheProperties`.

```
src/main/java/com/lucalzt/mctranslator/
├── domain/
│   └── port/out/
│       └── TranslationCachePort.java          (Driven port interface)
└── infrastructure/
    └── adapter/
        └── out/
            └── cache/
                ├── SqliteTranslationCacheAdapter.java  (JDBC SQLite implementation)
                └── MctranslatorCacheProperties.java    (Type-safe properties binding)
```

### Component Specifications

#### 1. `MctranslatorCacheProperties`
- **Annotation**: `@ConfigurationProperties(prefix = "mctranslator.cache")`
- **Field**: `private String dbPath = "./mctranslator.db";`
- **Behavior**: Binds configuration properties from `application.yaml`. Resolves relative paths against the current working directory.

#### 2. `SqliteTranslationCacheAdapter`
- **Annotations**: `@Repository` (or `@Component`), `@RequiredArgsConstructor`.
- **Dependencies**: `MctranslatorCacheProperties`.
- **Initialization**: On construction or first access, initializes the SQLite database connection, applies SQLite pragmas (WAL mode, busy timeout), and executes `CREATE TABLE IF NOT EXISTS`.
- **Methods**:
  - `Optional<TranslationResult> find(TranslationKey key, LanguageCode sourceLang, LanguageCode targetLang)`
  - `void save(TranslationKey key, LanguageCode sourceLang, LanguageCode targetLang, TranslationResult result)`

---

## Data Model & SQLite Schema

### Database File Location
- Defaults to `./mctranslator.db` in the working directory.
- Configurable via `mctranslator.cache.db-path` in `application.yaml`.
- JDBC Connection URL: `jdbc:sqlite:<resolved-path>`.

### DDL Schema (`translation_cache` table)

```sql
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
```

### Composite Primary Key
The composite primary key uniquely identifies each translation cache entry across five distinct attributes:
1. `modpack_id`: Modpack name and version identifier (`key.modpack().value()`).
2. `json_path`: JSON pointer path in the modpack file (`key.path().value()`).
3. `original_text`: Original untranslated string leaf (`key.originalText()`).
4. `source_lang`: Source language code (`sourceLang.value()`).
5. `target_lang`: Target language code (`targetLang.value()`).

### Prepared Statement Mappings

#### 1. Lookup (`find`)
- **SQL**:
  ```sql
  SELECT translated_text, engine_type, status, duration_ms, updated_at 
  FROM translation_cache 
  WHERE modpack_id = ? AND json_path = ? AND original_text = ? AND source_lang = ? AND target_lang = ?
  ```
- **Mapping**:
  - Sets parameters 1-5 from `key.modpack().value()`, `key.path().value()`, `key.originalText()`, `sourceLang.value()`, `targetLang.value()`.
  - Maps result row to `TranslationResult`:
    - `key`: reconstructed `TranslationKey`.
    - `translatedText`: string from `translated_text` column.
    - `status`: `TranslationStatus.CACHE_HIT`.
    - `engine`: `null` (per cache hit contract).
    - `warning`: `null`.
    - `duration`: `Duration.ZERO`.

#### 2. Upsert (`save`)
- **SQL (SQLite compatible `INSERT OR REPLACE`)**:
  ```sql
  INSERT OR REPLACE INTO translation_cache 
  (modpack_id, json_path, original_text, source_lang, target_lang, translated_text, engine_type, status, duration_ms, updated_at)
  VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
  ```
- **Mapping**:
  - Parameters 1-5: Composite key (`modpack_id`, `json_path`, `original_text`, `source_lang`, `target_lang`).
  - Parameters 6-10: Payload (`result.translatedText()`, `result.engine()` != null ? `result.engine().name()` : null, `result.status().name()`, `result.duration().toMillis()`, `System.currentTimeMillis()`).

---

## Robustness & Transparent Degradation

To ensure that cache infrastructure issues never abort translation runs:
1. **Try-Catch Wrapping**: Every JDBC interaction (connection establishment, DDL execution, prepared statement preparation, execution, and ResultSet iteration) is wrapped in `try-catch` blocks catching `SQLException` and general `Exception`.
2. **Lookup Degradation (`find`)**:
   - If an exception occurs, logs a warning via SLF4J (`logger.warn("Failed to query translation cache: {}", e.getMessage(), e)`).
   - Returns `Optional.empty()` immediately, allowing the pipeline to proceed with external translation engines.
3. **Save Degradation (`save`)**:
   - If an exception occurs (e.g., read-only disk, locked database, disk full), logs a warning via SLF4J (`logger.warn("Failed to save translation cache entry: {}", e.getMessage(), e)`).
   - Swallows the exception so that translation execution continues uninterrupted.
4. **SQLite Concurrency & WAL Mode**:
   - Executes pragmas on connection initialization: `PRAGMA journal_mode=WAL;` and `PRAGMA busy_timeout=5000;` to minimize `SQLITE_BUSY` lock contention across concurrent translation tasks.

---

## Observability & Logging

- **SLF4J Logger**: `private static final Logger logger = LoggerFactory.getLogger(SqliteTranslationCacheAdapter.class);`
- **Log Events**:
  - `INFO`: Database file initialization and table creation success (with resolved file path).
  - `DEBUG`: Cache hit / cache miss events (during diagnostic tracing).
  - `WARN`: Storage degradation warnings when `SQLException` occurs during `find` or `save`.

---

## Threat Matrix & Security Considerations

| Threat / Risk | Impact | Security / Robustness Mitigation |
|---|---|---|
| **SQL Injection** | Arbitrary SQL execution if untrusted keys/text contain malicious SQL fragments. | All queries use parameterized `PreparedStatement` with `setString()` / `setLong()`, completely isolating parameters from SQL syntax structure. |
| **File System Traversal / Permissions** | Unauthorized read/write or directory traversal via crafted `db-path`. | Path resolution validates target directory; relative paths default safely to application working directory. File permissions rely on standard OS file-system security. |
| **Database File Lock Contention (`SQLITE_BUSY`)** | Thread blocking or runtime failures under multi-threaded execution. | Enable SQLite Write-Ahead Logging (`PRAGMA journal_mode=WAL`) and configure busy timeout (`PRAGMA busy_timeout=5000`). |
| **Cascading Failures from Storage Errors** | Entire CLI translation run crashes due to corrupted or locked SQLite file. | **Transparent Degradation**: All DB exceptions are caught, logged at `WARN` level, and swallowed or translated to `Optional.empty()`. |

---

## Verification Plan

1. **Unit Tests (`SqliteTranslationCacheAdapterTest`)**:
   - Test schema auto-initialization on a temporary database file.
   - Test cache save and subsequent cache hit retrieval with exact composite key matching.
   - Test cache miss returning `Optional.empty()`.
   - Test composite key isolation (different languages, modpacks, or paths do not collide).
2. **Degradation Tests**:
   - Simulate `SQLException` during `find` (e.g., using closed datasource or locked/read-only conditions) and verify `Optional.empty()` is returned and warning is logged.
   - Simulate `SQLException` during `save` and verify execution continues without throwing exceptions.
