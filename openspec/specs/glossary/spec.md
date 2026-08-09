# Specification: JSON Glossary Adapter (`json-glossary-adapter`)

## Goal
Implement the `GlossaryPort` driven port in the `mctranslator-spring` application, allowing the system to read, parse, and cache in memory translation glossaries from modpack JSON files located at `glossary/{sourceLang}-{targetLang}.json`, supporting entries with `term`, `translation`, `synonyms`, and `classification` (`AMBIGUOUS`, `LORE`, `PLAIN`) with automatic synonym expansion.

## Requirements

### Requirement 1: Glossary File Path & Resolution
- **REQ-1.1**: The adapter MUST load glossary files matching the path pattern `glossary/{sourceLang}-{targetLang}.json` (e.g., `glossary/en-es.json`) from the file system or classpath.
- **REQ-1.2**: If the glossary file for a requested language pair does not exist, the adapter MUST return an empty list (`List.of()`) gracefully without throwing unhandled exceptions.

### Requirement 2: JSON Structure & Parsing
- **REQ-2.1**: The JSON glossary file MUST be structured as an array of entry objects containing `term` (String), `translation` (String), `synonyms` (List of Strings, optional), and `classification` (String representing `AMBIGUOUS`, `LORE`, or `PLAIN`).
- **REQ-2.2**: The adapter MUST use Jackson (`ObjectMapper`) to parse the JSON content correctly using UTF-8 encoding.

### Requirement 3: Synonym Expansion
- **REQ-3.1**: For any glossary entry containing one or more `synonyms`, the adapter MUST expand them into independent lookup entries pointing to the same `translation` and `classification`.
- **REQ-3.2**: Expanded synonym entries MUST be included in the returned list of glossary entries from `GlossaryPort.getTerms(...)`.

### Requirement 4: In-Memory Caching per Language Pair
- **REQ-4.1**: The adapter MUST cache parsed and expanded glossary entries in memory (e.g., using a thread-safe concurrent map or Spring Cache) keyed by language pair (and modpack context if applicable) to avoid redundant disk reads.
- **REQ-4.2**: Subsequent requests for the same glossary source/target language pair MUST return the cached entries instantly.

### Requirement 5: GlossaryPort Conformance & Hexagonal Architecture
- **REQ-5.1**: The adapter class MUST implement the outbound domain port `GlossaryPort` (`com.lucalzt.mctranslator.domain.port.out.GlossaryPort`).
- **REQ-5.2**: The adapter implementation MUST reside in the infrastructure layer (`com.lucalzt.mctranslator.infrastructure.adapter.out.glossary` or similar) respecting Hexagonal Architecture boundaries.

## Non-Goals
- Database persistence for glossaries (glossaries are static modpack files read from disk/cache).
- Remote API fetching of glossaries.
- UI or CLI command integration (handled in subsequent changes).

## Scenarios

### Scenario: Successful Glossary Loading with Synonym Expansion
- **Given** a JSON glossary file at `glossary/en-es.json` containing an entry with `term="sword"`, `translation="espada"`, `synonyms=["blade", "brand"]`, and `classification="PLAIN"`
- **When** `GlossaryPort.getTerms(modpackId, en, es)` is invoked
- **Then** the adapter returns 3 glossary entries: `sword -> espada`, `blade -> espada`, and `brand -> espada`, all classified as `PLAIN`.

### Scenario: Missing Glossary File Fallback
- **Given** no glossary file exists for language pair `en-fr` at `glossary/en-fr.json`
- **When** `GlossaryPort.getTerms(modpackId, en, fr)` is invoked
- **Then** the adapter logs an informational/debug message and returns an empty list (`List.of()`) without throwing an exception.

### Scenario: In-Memory Caching
- **Given** a glossary file loaded once for language pair `en-es`
- **When** `GlossaryPort.getTerms(...)` is invoked multiple times for the same language pair
- **Then** the file is read from disk only on the first call, and subsequent calls retrieve entries from the in-memory cache.

## Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Malformed JSON in glossary files | Parsing failure or application crash | Catch parsing exceptions, log a warning, and return an empty list gracefully. |
| Character encoding issues with special characters (diacritics, Spanish/UTF-8) | Corrupted terms or translations | Explicitly enforce UTF-8 charset when reading JSON files. |
| Missing optional fields (`synonyms`, `classification`) | NullPointerException during parsing | Provide sensible defaults (e.g., empty list for synonyms, `PLAIN` for classification if omitted). |
