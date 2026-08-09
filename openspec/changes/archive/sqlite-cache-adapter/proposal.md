# Proposal: SQLite Cache Adapter (`sqlite-cache-adapter`)

## Status
- **Status**: done

## Purpose
Implement the infrastructure persistence adapter for `TranslationCachePort` using SQLite via JDBC (`org.xerial:sqlite-jdbc`). 
To prevent redundant external translation calls and improve translation pipeline performance, resolved translations are persisted in a local SQLite database file next to the executable (defaulting to `./mctranslator.db`, configurable via `application.yaml` under `mctranslator.cache.db-path`).
Crucially, the adapter must implement transparent degradation: if the database file cannot be accessed, initialized, read, or written, it logs a warning and gracefully continues without cache, ensuring pipeline execution never fails due to storage errors.

## Approach
1. **Configuration Properties**: Define configuration properties (e.g., `MctranslatorCacheProperties`) binding `mctranslator.cache.db-path` with default value `./mctranslator.db`. Handle relative paths resolved next to the executable/working directory.
2. **SQLite Cache Adapter**: Implement `SqliteTranslationCacheAdapter` conforming to the outbound domain port `TranslationCachePort` (`com.lucalzt.mctranslator.domain.port.out.TranslationCachePort`).
3. **Database Schema & Initialization**:
   - Automatically create the SQLite database file and `translation_cache` table on startup if not present.
   - Unique cache key composite primary key or unique index consisting of:
     - `modpack_id` (string / name+version)
     - `json_path` (string)
     - `original_text` (text)
     - `source_lang` (string)
     - `target_lang` (string)
   - Stored columns: translated text, engine type, status, duration ms, updated_at timestamp.
4. **Transparent Degradation**: Wrap all JDBC operations (`find` and `save`) in robust try-catch blocks catching `SQLException` (and general exceptions), logging a warning via SLF4J, returning `Optional.empty()` on cache lookup failure, and swallowing/logging on save failure so translation processing continues uninterrupted.
5. **Testing**: Write unit and integration tests using JUnit 6, AssertJ, and temporary SQLite database files to verify schema initialization, cache hit/miss semantics, composite key uniqueness, and transparent degradation on simulated DB connection failures.

## Scope

### In-Scope
- `SqliteTranslationCacheAdapter` implementing `TranslationCachePort`.
- Configuration properties binding `mctranslator.cache.db-path` (default `./mctranslator.db`).
- SQLite table schema and auto-initialization via JDBC (`translation_cache` table with composite unique key: `modpack_id`, `json_path`, `original_text`, `source_lang`, `target_lang`).
- Transparent degradation error handling (`SQLException` caught, warning logged, non-blocking execution).
- Unit and integration tests for the cache adapter and fallback behavior.

### Non-Goals (Out of Scope)
- Application use cases or orchestration services (e.g., translation workflow integration).
- Other outbound adapters (DeepL, LLama, Glossary readers).
- CLI command bindings or shell integration (deferred to command/use-case integration phase).
- Remote caching or distributed key-value stores (strictly local SQLite file cache).

## Rollback Plan
Remove the created infrastructure persistence classes (`SqliteTranslationCacheAdapter`, configuration properties, tests) and remove any `mctranslator.cache` entries from `application.yaml`. Since domain models and domain port contracts remain untouched, reverting is a clean, side-effect-free deletion.

## Acceptance Criteria
- `SqliteTranslationCacheAdapter` implements `TranslationCachePort` using JDBC SQLite.
- Database file defaults to `./mctranslator.db` next to the application and is configurable via `application.yaml`.
- Unique composite key (`modpack_id`, `json_path`, `original_text`, `source_lang`, `target_lang`) prevents duplicate entries and correctly resolves cache hits.
- Database errors (e.g., locked file, invalid path, IO failure) trigger transparent degradation: warning logged, translation pipeline continues without cache.
- Tests pass (`mvnw clean verify`).
