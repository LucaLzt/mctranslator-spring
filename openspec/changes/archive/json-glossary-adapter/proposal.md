# Proposal: JSON Glossary Adapter (`json-glossary-adapter`)

## Status
- **Status**: done

## Purpose
The purpose of this change is to implement the `GlossaryPort` driven port in the `mctranslator-spring` application, allowing the system to read, parse, and cache in memory translation glossaries from modpack JSON files located at `glossary/{sourceLang}-{targetLang}.json`. 
Each JSON file contains an array of entries with `term`, `translation`, `synonyms`, and `classification` (`AMBIGUOUS`, `LORE`, `PLAIN`). Synonyms must be expanded as independent lookup entries pointing to the same translation and metadata to ensure robust glossary matching during translation.

## Approach
1. **Domain Port & Model**: Define or adapt `GlossaryPort` in the domain (`domain/port/out/GlossaryPort.java`) and rich domain models/value objects for glossary entries, source/target languages, and classification (`Classification.AMBIGUOUS`, `Classification.LORE`, `Classification.PLAIN`).
2. **Infrastructure Adapter**: Implement `JsonGlossaryAdapter` in `infrastructure/external/` or `infrastructure/persistence/` that loads files from the file system or classpath (`glossary/{sourceLang}-{targetLang}.json`), parsing them using Jackson.
3. **In-Memory Caching & Expansion**: Implement caching (e.g., concurrent map or Spring Cache) keyed by language pair, and expand synonyms into independent entries upon loading.
4. **Testing**: Write unit and slice tests using JUnit 6 and AssertJ following `spring-boot-testing` standards.

## Scope
### In Scope
- Definition of `GlossaryPort` and domain value objects for glossary entries and classifications.
- JSON file parsing for path pattern `glossary/{sourceLang}-{targetLang}.json`.
- Support for entry fields: `term`, `translation`, `synonyms`, and `classification` (`AMBIGUOUS`, `LORE`, `PLAIN`).
- Automatic expansion of `synonyms` into independent lookup entries referencing the parent entry data.
- In-memory caching per language pair to avoid repeated disk reads.
- Unit and integration tests verifying correct parsing, caching, and synonym expansion.

### Non-Goals
- Database persistence for glossaries (glossaries are static modpack files read from disk/cache).
- Remote API fetching of glossaries.
- UI or CLI command integration (that will be handled in subsequent changes).

## Rollback Plan
If issues arise during implementation:
- Revert the changes made in the `json-glossary-adapter` change branch.
- Remove the created adapter and port files if they destabilize the compilation or test suite.
- Since this is a new additive capability with no existing production dependency, reverting is clean and isolated.
