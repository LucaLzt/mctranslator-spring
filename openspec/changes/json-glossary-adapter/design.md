# Technical Design: JSON Glossary Adapter (`json-glossary-adapter`)

## Architecture & Component Breakdown

The `json-glossary-adapter` change implements the outbound port `GlossaryPort` defined in the domain layer, providing modpack translation glossaries loaded from local JSON files with synonym expansion and thread-safe in-memory caching.

### Layer Placement (Hexagonal Architecture)
- **Domain Layer (`com.lucalzt.mctranslator.domain.port.out`)**:
  - `GlossaryPort`: Outbound port interface (`List<GlossaryEntry> getTerms(ModpackId modpackId, LanguageCode sourceLang, LanguageCode targetLang)`).
  - `GlossaryEntry` & `GlossaryEntryClassification`: Pure domain value objects.
- **Infrastructure Layer (`com.lucalzt.mctranslator.infrastructure.adapter.out.glossary`)**:
  - `JsonGlossaryAdapter`: Concrete implementation of `GlossaryPort`.
  - `GlossaryFileDto` / `GlossaryEntryDto`: Jackson DTOs for parsing JSON files.

---

## Data Model & JSON Schema

### Glossary JSON File Structure (`glossary/{sourceLang}-{targetLang}.json`)
```json
[
  {
    "term": "sword",
    "translation": "espada",
    "synonyms": ["blade", "brand"],
    "classification": "PLAIN"
  }
]
```

### Parsing DTO Mapping
- `term`: `String` (required, non-blank).
- `translation`: `String` (required, non-blank).
- `synonyms`: `List<String>` (optional, defaults to empty list).
- `classification`: `String` or Enum (optional, defaults to `GlossaryEntryClassification.PLAIN` if omitted or unparseable).

---

## Synonym Expansion Logic

When parsing each raw entry from the JSON file:
1. Create the primary `GlossaryEntry` for `term -> translation` with its `classification`.
2. For each string in `synonyms`:
   - Validate that the synonym is non-blank.
   - Create an additional `GlossaryEntry` for `synonym -> translation` with the exact same `classification`.
3. Aggregate all primary and expanded synonym entries into a single `List<GlossaryEntry>` returned by the adapter.

---

## In-Memory Caching & File Resolution

### File Resolution Strategy
- Look up files from the filesystem or classpath under `glossary/{sourceLang}-{targetLang}.json` (e.g., `glossary/en-es.json`).
- If the file does not exist, log an informational/debug message and return `List.of()` gracefully without throwing exceptions.
- Enforce UTF-8 charset encoding during reading.

### Caching Strategy
- Maintain a thread-safe in-memory cache using `ConcurrentHashMap<String, List<GlossaryEntry>>` keyed by a composite key (`${modpackId}:${sourceLang}-${targetLang}`).
- Ensure thread safety during file loading (e.g., via computeIfAbsent or double-checked locking) to prevent redundant disk reads under concurrent translation requests.

---

## Threat Matrix & Security Considerations

| Threat ID | Threat Category | Description | Mitigation Mechanism |
|---|---|---|---|
| T-01 | Path Traversal | Malicious language codes containing `..` or slashes could attempt directory traversal when constructing the file path. | Validate `LanguageCode` values using strict regex and sanitize filename resolution. |
| T-02 | Denial of Service (Malformed JSON / Huge File) | Malformed or excessively large JSON files causing high CPU/memory consumption or thread blocking. | Use Jackson object mapping with timeout/size limits; catch parsing exceptions gracefully, log warnings, and return empty list. |
| T-03 | Encoding Corruption | Non-UTF-8 characters in modpack glossary files causing Mojibake or parsing crashes. | Explicitly specify `StandardCharsets.UTF_8` when reading file contents via NIO `Files.readString` or Jackson source readers. |
| T-04 | Null Pointer / Missing Fields | JSON entries missing `synonyms` or `classification` causing `NullPointerException`. | Use default field initializers in DTOs; validate via domain record compact constructors. |

---

## Observability & Logging

- **INFO Level**: Successful glossary file loading, cache population, and count of parsed/expanded entries.
- **DEBUG Level**: Cache hits for language pair lookups.
- **WARN Level**: Missing glossary file fallback or JSON parsing errors (with stack trace or message summary).
