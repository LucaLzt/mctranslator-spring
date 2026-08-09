# Design: Domain Output Ports

## Architecture
- **Package**: `com.lucalzt.mctranslator.domain.port.out`
- **Interfaces**:
  1. `TranslationEnginePort`:
     - Method: `TranslationResult translate(TranslationKey key, LanguageCode sourceLang, LanguageCode targetLang, TranslationEngineType engineType)` (or similar domain types). Let's check existing domain models in `src/main/java/com/lucalzt/mctranslator/domain/model/`.
  2. `GlossaryPort`:
     - Method: `List<GlossaryEntry> getTerms(ModpackId modpackId, LanguageCode sourceLang, LanguageCode targetLang)`.
  3. `TranslationCachePort`:
     - Method: `Optional<TranslationResult> find(TranslationKey key, LanguageCode sourceLang, LanguageCode targetLang)`
     - Method: `void save(TranslationKey key, LanguageCode sourceLang, LanguageCode targetLang, TranslationResult result)`.
- **Dependencies**: Pure Java, JSpecify annotations (`@NullMarked`, `@Nullable`), zero framework dependencies.

## Data Model & Types
- Uses existing domain models: `TranslationKey`, `LanguageCode`, `TranslationResult`, `TranslationEngineType`, `ModpackId`, `GlossaryEntry`.

## Threat Matrix
| Threat | Mitigation |
|---|---|
| Null arguments passed to ports | Use JSpecify `@NullMarked` at package level and runtime null checks (`Objects.requireNonNull`). |
| Framework leakage into domain | Code review ensuring no Spring, Jakarta Persistence, or external SDK imports in `domain.port.out`. |
