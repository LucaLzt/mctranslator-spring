# Apply Progress: JSON Glossary Adapter (`json-glossary-adapter`)

## Status
- **Phase**: Apply
- **Status**: Success
- **Completed Tasks**: T1, T2, T3, T4 (100% complete)

## Summary of Implementation
1. **Infrastructure DTO (`GlossaryEntryDto`)**:
   - Created `GlossaryEntryDto` in `com.lucalzt.mctranslator.infrastructure.adapter.out.glossary` using Jackson annotations (`@JsonProperty`, `@JsonIgnoreProperties`).
   - Implemented safe resolution for optional fields (`synonyms` defaults to `List.of()`, `classification` defaults to `GlossaryEntryClassification.PLAIN`).
2. **Outbound Adapter (`JsonGlossaryAdapter`)**:
   - Implemented `GlossaryPort` adhering to Hexagonal Architecture boundaries.
   - Added path traversal validation on language codes via strict regex.
   - Configured UTF-8 file reading from filesystem (`glossary/{sourceLang}-{targetLang}.json`) and classpath fallback.
   - Implemented thread-safe in-memory caching using `ConcurrentHashMap` keyed by composite modpack and language pair context.
   - Added graceful error handling for missing files and malformed JSON (returning empty list `List.of()`).
3. **Tests (`JsonGlossaryAdapterTest`)**:
   - Implemented comprehensive unit/integration tests covering successful parsing, synonym expansion, missing file fallback, in-memory caching, malformed JSON handling, and path traversal protection.
   - Verified 138/138 tests passing successfully.
