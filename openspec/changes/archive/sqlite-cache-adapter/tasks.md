# Tasks: SQLite Cache Adapter (`sqlite-cache-adapter`)

## Task Breakdown

- [x] **T1: Implement `MctranslatorCacheProperties` Configuration Properties**
  - **Description**: Create `@ConfigurationProperties(prefix = "mctranslator.cache")` class binding configuration with `private String dbPath = "./mctranslator.db";`. Ensure relative paths are handled correctly.
  - **Criteria**: Properties bind successfully from `application.yaml`; default path `./mctranslator.db` is correctly set.
  - **Depends on**: None
  - **Spec Reference**: REQ-1.1, REQ-1.2, REQ-1.3

- [x] **T2: Implement SQLite Schema Initialization & Connection Management**
  - **Description**: Implement database connection handling, automatic file/directory creation, SQLite performance pragmas (`journal_mode=WAL`, `busy_timeout=5000`), and idempotent `CREATE TABLE IF NOT EXISTS` for `translation_cache` with composite primary key (`modpack_id`, `json_path`, `original_text`, `source_lang`, `target_lang`) and payload columns (`translated_text`, `engine_type`, `status`, `duration_ms`, `updated_at`).
  - **Criteria**: Database file and table are created automatically on first access; pragmas are correctly applied; composite primary key enforces uniqueness.
  - **Depends on**: T1
  - **Spec Reference**: REQ-3.1, REQ-3.2, REQ-3.3

- [x] **T3: Implement `SqliteTranslationCacheAdapter` conforming to `TranslationCachePort`**
  - **Description**: Implement `SqliteTranslationCacheAdapter` in `com.lucalzt.mctranslator.infrastructure.adapter.out.cache` implementing `TranslationCachePort`. Implement `find` (5-parameter composite key query mapping to `TranslationResult` with `CACHE_HIT`) and `save` (upserting via `INSERT OR REPLACE`). Wrap all operations in robust `try-catch` blocks catching `SQLException` and `Exception`, logging warnings via SLF4J and providing transparent degradation (`Optional.empty()` on lookup error, silent continuation on save error).
  - **Criteria**: Conforms to `TranslationCachePort`; uses `PreparedStatement` with parameter binding to prevent SQL injection; logs warnings on failure and degrades gracefully without throwing.
  - **Depends on**: T2
  - **Spec Reference**: REQ-2.1, REQ-2.2, REQ-2.3, REQ-4.1, REQ-4.2, REQ-4.3, REQ-4.4, REQ-5.1, REQ-5.2, REQ-5.3, REQ-5.4

- [x] **T4: Write Unit & Integration Tests for SQLite Cache Adapter**
  - **Description**: Implement `SqliteTranslationCacheAdapterTest` using JUnit 6, AssertJ, and temporary SQLite database files. Verify schema auto-initialization, cache miss returning empty, cache save and subsequent hit retrieval, composite key isolation across different languages/modpacks/paths, and transparent degradation under simulated failure conditions (e.g., closed/locked connection or error during find/save).
  - **Criteria**: All tests pass successfully (`mvnw test`); coverage includes success path, composite key uniqueness, and degradation handling.
  - **Depends on**: T3
  - **Spec Reference**: All scenarios and requirements

---

## Review Workload Forecast

Decision needed before apply: No
Chained PRs recommended: No
400-line budget risk: Low

---

## Verification Plan

1. Execute test suite: `mvnw test`
2. Verify all unit and integration tests in `SqliteTranslationCacheAdapterTest` pass successfully.
3. Verify zero domain coupling to JDBC or SQLite.
