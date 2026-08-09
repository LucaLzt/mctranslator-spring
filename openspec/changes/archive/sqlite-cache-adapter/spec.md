# Specification: SQLite Cache Adapter (`sqlite-cache-adapter`)

## Goal
Implement the robust SQLite persistence adapter for `TranslationCachePort` using JDBC (`org.xerial:sqlite-jdbc`) with automatic database initialization, composite-key caching, and transparent degradation on storage failures.

## Requirements

### Requirement 1: Configuration Properties Binding
- **REQ-1.1**: The application MUST define configuration properties (`MctranslatorCacheProperties` or equivalent) bound to prefix `mctranslator.cache`.
- **REQ-1.2**: The configuration property `db-path` MUST default to `./mctranslator.db` when not explicitly specified in `application.yaml`.
- **REQ-1.3**: Relative database paths MUST be resolved correctly relative to the current working directory / application executable location.

### Requirement 2: SQLite Cache Adapter & Domain Port Conformance
- **REQ-2.1**: The adapter class `SqliteTranslationCacheAdapter` MUST implement the outbound domain port `TranslationCachePort` (`com.lucalzt.mctranslator.domain.port.out.TranslationCachePort`).
- **REQ-2.2**: The adapter MUST be annotated appropriately (e.g. `@Repository`, `@Component`) and injected via constructor injection with `private final` fields.
- **REQ-2.3**: All adapter code MUST reside in the infrastructure layer (`com.lucalzt.mctranslator.infrastructure.persistence` or equivalent) adhering to Hexagonal Architecture.

### Requirement 3: Database Schema & Automatic Initialization
- **REQ-3.1**: On startup or upon first cache access, the adapter MUST automatically create the SQLite database file and the `translation_cache` table if they do not already exist.
- **REQ-3.2**: The `translation_cache` table MUST define a composite primary key or unique constraint consisting of:
  - `modpack_id` (TEXT)
  - `json_path` (TEXT)
  - `original_text` (TEXT)
  - `source_lang` (TEXT)
  - `target_lang` (TEXT)
- **REQ-3.3**: The table MUST store payload columns: `translated_text` (TEXT), `engine_type` (TEXT, nullable), `status` (TEXT), `duration_ms` (INTEGER), and `updated_at` (TIMESTAMP / INTEGER).

### Requirement 4: Cache Lookup and Storage (`find` and `save`)
- **REQ-4.1**: `find(TranslationKey key, LanguageCode sourceLang, LanguageCode targetLang)` MUST query the `translation_cache` using all five composite key components (`modpack`, `path`, `originalText`, `sourceLang`, `targetLang`).
- **REQ-4.2**: If a matching record is found, `find` MUST map it back to a `TranslationResult` with `TranslationStatus.CACHE_HIT`, duration set to zero, and `null` engine/warning.
- **REQ-4.3**: If no matching record exists, `find` MUST return `Optional.empty()`.
- **REQ-4.4**: `save(TranslationKey key, LanguageCode sourceLang, LanguageCode targetLang, TranslationResult result)` MUST insert or replace (upsert via `INSERT OR REPLACE` or `INSERT ... ON CONFLICT`) the cache record corresponding to the composite key.

### Requirement 5: Transparent Degradation on SQLite Errors
- **REQ-5.1**: All JDBC operations (`find` and `save`, as well as connection/initialization setup) MUST be wrapped in robust exception handling catching `SQLException` and general `Exception`.
- **REQ-5.2**: When a database access, connection, read, write, or schema error occurs, the adapter MUST log a clear warning message via SLF4J, including the error details.
- **REQ-5.3**: On lookup failure (`find`), the adapter MUST return `Optional.empty()` instead of propagating exceptions.
- **REQ-5.4**: On storage failure (`save`), the adapter MUST swallow the exception and allow translation processing to continue uninterrupted without failing the pipeline.

## Non-Goals
- Application use cases or orchestration services (e.g. translation workflow integration).
- Other outbound adapters (DeepL, LLama, Glossary readers).
- CLI command bindings or shell integration.
- Remote caching or distributed key-value stores (strictly local SQLite file cache).

## Scenarios

### Scenario: Successful Cache Miss and Subsequent Hit
- **Given** an initialized SQLite translation cache and a translation result for key `path="quests.json#/title"`, `originalText="Hello"`, `sourceLang="en"`, `targetLang="es"`, `modpack="modpack-v1"`
- **When** `find` is invoked before saving, it returns `Optional.empty()`
- **And** `save` is invoked with the translation result
- **When** `find` is invoked again with the same composite key
- **Then** it returns an `Optional` containing the `TranslationResult` with `status = CACHE_HIT`.

### Scenario: Composite Key Uniqueness and Isolation
- **Given** two cache entries differing only in one component (e.g., `targetLang="fr"` vs `targetLang="es"`, or `modpack="modpack-v1"` vs `modpack="modpack-v2"`), but sharing other text/path fields
- **When** both are saved to the cache
- **Then** `find` correctly distinguishes them and returns the exact result corresponding to the requested composite key without cross-contamination.

### Scenario: Automatic Schema Creation on Startup
- **Given** a non-existent SQLite database path configured in properties
- **When** the `SqliteTranslationCacheAdapter` initializes or performs its first database operation
- **Then** the SQLite file and `translation_cache` table are automatically created successfully.

### Scenario: Transparent Degradation on Database Connection or Read Error
- **Given** an SQLite cache adapter configured with an inaccessible/locked database or encountering a simulated `SQLException` during `find`
- **When** `find` is invoked
- **Then** a warning is logged via SLF4J and `Optional.empty()` is returned gracefully without throwing an exception or aborting execution.

### Scenario: Transparent Degradation on Database Write Error
- **Given** an SQLite cache adapter encountering a simulated `SQLException` (e.g., read-only disk or locked database) during `save`
- **When** `save` is invoked
- **Then** a warning is logged via SLF4J and the exception is swallowed, allowing the translation pipeline to continue uninterrupted.

## Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| SQLite file locking / concurrent access across threads | Potential `SQLITE_BUSY` or locking exceptions | Use proper JDBC connection management and enable SQLite WAL mode or brief retry timeouts if needed; robust try-catch handles any locking errors via transparent degradation. |
| Relative path resolution in packaged JAR or CLI execution | Database file created in unexpected working directory | Resolve relative paths explicitly against project working directory / executable location via configuration property handling. |
| Schema evolution / column mismatch in future versions | Startup failure if table schema drifts | Keep schema creation idempotent (`CREATE TABLE IF NOT EXISTS`) and design columns to be forward-compatible. |
