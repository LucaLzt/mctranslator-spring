# Tasks: Domain Output Ports

## Tasks

- [x] T1: Create `TranslationEnginePort` interface in `com.lucalzt.mctranslator.domain.port.out` with `translate` method. Criteria: Pure Java interface, zero framework dependencies, uses domain models (`TranslationKey`, `LanguageCode`, `TranslationResult`, `TranslationEngineType`). Requires: none.
- [x] T2: Create `GlossaryPort` interface in `com.lucalzt.mctranslator.domain.port.out` with `getTerms` method. Criteria: Pure Java interface, zero framework dependencies, uses `ModpackId`, `LanguageCode`, `GlossaryEntry`. Requires: T1.
- [x] T3: Create `TranslationCachePort` interface in `com.lucalzt.mctranslator.domain.port.out` with `find` and `save` methods. Criteria: Pure Java interface, zero framework dependencies, returns `Optional<TranslationResult>`. Requires: T2.
- [x] T4: Verify package-info and Javadoc documentation for all created ports. Criteria: Public classes and methods have correct Javadoc comments per `java-docs` skill. Requires: T3.

## Review Workload Forecast
Decision needed before apply: No
Chained PRs recommended: No
400-line budget risk: Low
