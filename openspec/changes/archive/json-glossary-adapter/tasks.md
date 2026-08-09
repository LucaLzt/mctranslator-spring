# Tasks: JSON Glossary Adapter (`json-glossary-adapter`)

## Overview
Implement `JsonGlossaryAdapter` implementing `GlossaryPort`, supporting JSON glossary file parsing, synonym expansion, UTF-8 encoding, secure path validation, graceful missing file handling, and thread-safe in-memory caching.

## Task List

- [x] T1: Create Infrastructure DTOs (`GlossaryEntryDto`) for Jackson JSON parsing
  - **Description**: Implement `GlossaryEntryDto` supporting `term`, `translation`, `synonyms` (List of Strings, optional), and `classification` (String, defaulting to `PLAIN`).
  - **Criteria**: Correctly maps JSON fields to DTO with robust defaults for missing optional fields and unparseable classifications.
  - **Traceability**: REQ-2.1, REQ-2.2, Risk T-04.
  - **Requires**: None.

- [x] T2: Implement `JsonGlossaryAdapter` class implementing `GlossaryPort`
  - **Description**: Create `JsonGlossaryAdapter` in `com.lucalzt.mctranslator.infrastructure.adapter.out.glossary` implementing `GlossaryPort`.
  - **Criteria**:
    - Resolves files at `glossary/{sourceLang}-{targetLang}.json` via classpath/filesystem.
    - Validates language codes to prevent path traversal (Risk T-01).
    - Uses Jackson with UTF-8 encoding (REQ-2.2, Risk T-03).
    - Gracefully handles missing files by returning `List.of()` (REQ-1.2).
    - Gracefully handles malformed JSON by logging warning and returning `List.of()` (Risk T-02).
  - **Traceability**: REQ-1.1, REQ-1.2, REQ-2.1, REQ-2.2, REQ-5.1, REQ-5.2.
  - **Requires**: T1.

- [x] T3: Implement Synonym Expansion and Caching Logic
  - **Description**: Add synonym expansion logic to expand `synonyms` into independent `GlossaryEntry` objects, and implement thread-safe in-memory caching (`ConcurrentHashMap`) keyed by `${modpackId}:${sourceLang}-{targetLang}`.
  - **Criteria**:
    - Synonyms are correctly expanded into individual `GlossaryEntry` items with identical translation and classification (REQ-3.1, REQ-3.2).
    - Caching prevents redundant disk reads on subsequent requests (REQ-4.1, REQ-4.2).
  - **Traceability**: REQ-3.1, REQ-3.2, REQ-4.1, REQ-4.2.
  - **Requires**: T2.

- [x] T4: Write Unit & Component Tests for `JsonGlossaryAdapter`
  - **Description**: Create `JsonGlossaryAdapterTest` using JUnit 6 and AssertJ following `spring-boot-testing` standards.
  - **Criteria**:
    - Tests successful loading and synonym expansion (Scenario 1).
    - Tests missing file fallback returning empty list (Scenario 2).
    - Tests in-memory caching behavior (Scenario 3).
    - Tests malformed JSON and path traversal protection.
  - **Traceability**: REQ-1.1 to REQ-5.2, Scenarios 1-3.
  - **Requires**: T3.

## Review Workload Forecast

Decision needed before apply: Yes
Chained PRs recommended: No
400-line budget risk: Low
