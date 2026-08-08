# Specification: Domain Models & Value Objects (`domain-models`)

## Goal

Define the pure-Java domain model for the translation pipeline in `com.lucalzt.mctranslator.domain.model` — `TranslationKey`, `TranslationResult`, `GlossaryEntry`, and the supporting value objects and enums `JsonPath`, `LanguageCode`, `ModpackId`, `TranslationStatus`, `TranslationEngineType`, `GlossaryEntryClassification` — with zero framework dependencies, compact-constructor validation, JSpecify `@NullMarked`, Javadoc, and JUnit 6 + AssertJ unit tests, so that every downstream step (cache lookup, masking, `ScalingHeuristic`, engines, report metrics) consumes unambiguous, immutable contracts.

---

## ADDED Requirements

### Requirement 1: Package Placement in the Real Base Package
All domain model types MUST be declared in the package `com.lucalzt.mctranslator.domain.model` (source path `src/main/java/com/lucalzt/mctranslator/domain/model/`). The real base package is `com.lucalzt.mctranslator` (as confirmed in `src/main/java/com/lucalzt/mctranslator/`); the `com.mctranslator` package written in `docs/architecture/implementation-strategy.md` MUST NOT be used.

#### Scenario: Types live in the real package
- **Given** the change is implemented
- **When** the source tree under `src/main/java/com/lucalzt/mctranslator/domain/model/` is inspected
- **Then** every domain model type is declared in `com.lucalzt.mctranslator.domain.model` and no domain model type exists under any `com.mctranslator` package.

---

### Requirement 2: Zero Framework Imports in the Domain Package
Source files in `com.lucalzt.mctranslator.domain.model` MUST NOT import from `org.springframework.*`, `jakarta.*`, or any other framework/library package. Allowed imports are limited to the JDK (`java.*`, `java.lang.*`) and `org.jspecify.annotations.*` (for nullness annotations only). The domain package is 100% pure Java.

#### Scenario: Grep check for framework imports
- **Given** the implemented `domain.model` package
- **When** a grep over its source files searches for `import org.springframework`, `import jakarta`, or any other non-JDK, non-jspecify import
- **Then** no matches are found and the build compiles the package without framework dependencies.

---

### Requirement 3: Exact Public Type Inventory
The package MUST define exactly these 9 public types:

- Records: `TranslationKey`, `TranslationResult`, `GlossaryEntry`
- Value objects: `JsonPath`, `LanguageCode`, `ModpackId`
- Enums: `TranslationStatus`, `TranslationEngineType`, `GlossaryEntryClassification`

No additional public types MAY be introduced. The deferred value objects `MaskedText`, `GlossaryTermMatch`, and `CacheKey` MUST NOT be created in this change (each has a named owner change: domain-services / ports).

#### Scenario: All required types present
- **Given** the implemented `domain.model` package
- **When** the public types of the package are enumerated
- **Then** the set equals exactly the 9 types listed above.

#### Scenario: Deferred value objects absent
- **Given** the implemented `domain.model` package
- **When** a search for `MaskedText`, `GlossaryTermMatch`, or `CacheKey` is run over the package sources
- **Then** no matches are found.

---

### Requirement 4: JSpecify @NullMarked Package
The package MUST contain a `package-info.java` annotated with `@org.jspecify.annotations.NullMarked` and a Javadoc package description stating the package's role (pure domain layer of the translation pipeline, zero framework dependencies). The only `@Nullable` elements in the package are the documented nullable fields of `TranslationResult` (warning, engine — see Requirement 13).

#### Scenario: Package is null-marked
- **Given** the implemented `domain.model` package
- **When** `package-info.java` is inspected
- **Then** it carries the `@NullMarked` annotation and a Javadoc description of the package role.

---

### Requirement 5: Javadoc on All Public API
Every public type and every public member (record accessors, methods, static factories, enum constants) MUST carry Javadoc: a summary sentence ending with a period, plus `@param` / `@return` / `@throws` tags where applicable, following the `java-docs` skill.

#### Scenario: Javadoc completeness check
- **Given** the implemented `domain.model` package
- **When** a review enumerates all public types and public members
- **Then** each one has a Javadoc comment (summary + applicable tags) and no public member is undocumented.

---

### Requirement 6: jspecify Compile-Scope Dependency
`pom.xml` MUST declare the dependency `org.jspecify:jspecify` (latest stable 1.x, compile scope) so that `@NullMarked` and `@Nullable` resolve at compile time. If `pom.xml` already declares a compatible version, it is not duplicated. (Today the dependency is absent from `pom.xml`.)

#### Scenario: Dependency declared and resolvable
- **Given** the updated `pom.xml`
- **When** `mvnw.cmd -q dependency:tree` is run and the build compiles
- **Then** `org.jspecify:jspecify` appears in the compile-scope dependency tree and the nullness annotations resolve without warnings.

---

### Requirement 7: JsonPath Value Object
`JsonPath` MUST be an immutable record wrapping a non-null, non-blank, dot-separated path string. Its compact constructor MUST reject:

- `null` (throws `NullPointerException`)
- blank or empty strings, and paths with empty segments — leading dot, trailing dot, or consecutive dots (throws `IllegalArgumentException`)

It MUST expose the raw path via the record accessor and a prefix-matching method `boolean startsWith(String... segments)` that returns `true` if and only if the path has at least as many segments as the given arguments and the leading segments equal them, in order, case-sensitively. This behavior enables heuristic rules 1/4/5 to match path prefixes such as `quest.description.*`, `lore.*`, `advancement.*`, `item.*`, `block.*`, `entity.*`, `gui.*` (the `*` in the docs is shorthand for "and everything below"; wildcard characters are not part of a path value).

#### Scenario: Rejects null path
- **Given** the `JsonPath` record
- **When** constructed with a `null` value
- **Then** a `NullPointerException` is thrown.

#### Scenario: Rejects blank and structurally invalid paths
- **Given** the `JsonPath` record
- **When** constructed with `""`, `"   "`, `".quest"`, `"quest."`, or `"quest..description"`
- **Then** an `IllegalArgumentException` is thrown.

#### Scenario: Prefix matching
- **Given** a `JsonPath` with value `"quest.description.task1"`, one with value `"item.sword"`, and one with value `"quest.advancement"`
- **When** `startsWith` is invoked with segments `("quest", "description")`, `("item")`, and `("quest", "description")` respectively
- **Then** the results are `true`, `true`, and `false`. The equal-length exact prefix `("quest", "advancement")` on `"quest.advancement"` returns `true` (exact-prefix semantics, per the normative prose above); the meaningful negative case pinned for heuristic rule 1 is `("quest", "description")` on a `"quest.advancement"` path → `false`.

#### Scenario: Value equality
- **Given** two `JsonPath` instances created from the same path string and one from a different string
- **When** compared with `equals`/`hashCode`
- **Then** the equal-value instances are equal with equal hash codes and the different one is not equal.

---

### Requirement 8: LanguageCode Value Object
`LanguageCode` MUST be an immutable record wrapping a non-null, non-blank language identifier string. Its compact constructor MUST reject `null` (throws `NullPointerException`) and blank strings (throws `IllegalArgumentException`). No format restriction beyond non-blank is applied (engine language codes vary, e.g. `es`, `en_US`, `spa_Latn`); reading the target language from configuration with a default (decision 1) is application-layer work and out of scope here. Equality is by value.

#### Scenario: Rejects null and blank codes
- **Given** the `LanguageCode` record
- **When** constructed with `null` or `"   "`
- **Then** a `NullPointerException` (null) or `IllegalArgumentException` (blank) is thrown.

#### Scenario: Accepts valid codes
- **Given** the `LanguageCode` record
- **When** constructed with `"es"` or `"spa_Latn"`
- **Then** the instances are created and expose the code via the accessor.

---

### Requirement 9: ModpackId Value Object
`ModpackId` MUST be an immutable record of two components — `String name` and `String version` (decision 6). Its compact constructor MUST reject `null` name, `null` version, blank name, and blank version (nulls throw `NullPointerException`; blanks throw `IllegalArgumentException`). Equality is by both components; `ModpackId` participates in `TranslationKey` identity (decision 2).

#### Scenario: Rejects invalid name/version
- **Given** the `ModpackId` record
- **When** constructed with a `null` name, a blank name, or a blank version
- **Then** an exception is thrown (`NullPointerException` for nulls, `IllegalArgumentException` for blanks).

#### Scenario: Equality by both components
- **Given** two `ModpackId` instances with equal name and version and one with a different version
- **When** compared with `equals`/`hashCode`
- **Then** the equal-component instances are equal with equal hash codes and the different one is not equal.

---

### Requirement 10: TranslationKey Record with Pinned Identity
`TranslationKey` MUST be an immutable record with exactly these identity components (decision 2): `JsonPath path`, `String originalText`, `LanguageCode targetLanguage`, `ModpackId modpack`. Its compact constructor MUST reject:

- any `null` component (throws `NullPointerException`)
- blank `originalText` — empty or whitespace-only (throws `IllegalArgumentException`)

The blank-text invariant is the modeled boundary of the deferred extraction edge-case policy (empty/whitespace-only text leaves); the full policy is pinned in the extraction change. Equality is by all four components.

#### Scenario: Rejects null identity components
- **Given** the `TranslationKey` record
- **When** constructed with a `null` `JsonPath`, `null` `LanguageCode`, or `null` `ModpackId`
- **Then** a `NullPointerException` is thrown.

#### Scenario: Rejects blank original text
- **Given** the `TranslationKey` record
- **When** constructed with `originalText` equal to `""` or `"   "`
- **Then** an `IllegalArgumentException` is thrown.

#### Scenario: Identity equality across all components
- **Given** two `TranslationKey` instances with equal path, original text, language, and modpack, and one differing only in the language
- **When** compared with `equals`/`hashCode`
- **Then** the identical instances are equal with equal hash codes and the language-differing instance is not equal.

---

### Requirement 11: TranslationStatus Enum — Five Pipeline Outcomes
`TranslationStatus` MUST be an enum with exactly these five constants (decisions 4 and 5), enabling every documented pipeline outcome to be represented:

- `CACHE_HIT`
- `TRANSLATED_FAST`
- `TRANSLATED_PRECISE`
- `DEGRADED_TO_FAST`
- `FALLBACK_TO_ORIGINAL`

No other constants MAY be added.

#### Scenario: Enum exposes exactly the five outcomes
- **Given** the `TranslationStatus` enum
- **When** its constants are enumerated
- **Then** the set equals exactly `{CACHE_HIT, TRANSLATED_FAST, TRANSLATED_PRECISE, DEGRADED_TO_FAST, FALLBACK_TO_ORIGINAL}`.

#### Scenario: Each outcome is representable in a result
- **Given** the `TranslationResult` record
- **When** a result is constructed for each of the five `TranslationStatus` constants
- **Then** construction succeeds for all five and each status is preserved by the result.

---

### Requirement 12: TranslationEngineType Enum
`TranslationEngineType` MUST be an enum with exactly two constants (decision 4): `FAST` and `PRECISE`. No other constants MAY be added.

#### Scenario: Enum exposes exactly the two engines
- **Given** the `TranslationEngineType` enum
- **When** its constants are enumerated
- **Then** the set equals exactly `{FAST, PRECISE}`.

---

### Requirement 13: TranslationResult Record with Rich Metadata
`TranslationResult` MUST be an immutable record with these components (decision 4): `TranslationKey key`, `String translatedText`, `TranslationStatus status`, `TranslationEngineType engine`, `String warning`, `Duration duration`. Its compact constructor MUST reject:

- `null` `key`, `null` `translatedText`, `null` `status`, or `null` `duration` (throws `NullPointerException`)
- a negative `duration` (throws `IllegalArgumentException`)

Nullability contract (the only `@Nullable` elements in the package):
- `warning` is `@Nullable` — set on `DEGRADED_TO_FAST` (message about the precise-engine fallback) and on `FALLBACK_TO_ORIGINAL` (message about total failure); absent otherwise.
- `engine` is `@Nullable` — set on `TRANSLATED_FAST`, `TRANSLATED_PRECISE`, and `DEGRADED_TO_FAST`; absent on `CACHE_HIT` (no engine ran) and on `FALLBACK_TO_ORIGINAL` (no engine succeeded).

Behavioral contract (decision 5): when `status` is `FALLBACK_TO_ORIGINAL`, the result represents total failure — `translatedText` equals the key's `originalText` and `warning` carries the failure message. `TranslationResult` is the per-key output unit; the report consumes `List<TranslationResult>` (decision 8).

#### Scenario: Rejects null key and negative duration
- **Given** the `TranslationResult` record
- **When** constructed with a `null` key or a negative `duration`
- **Then** a `NullPointerException` (null key) or `IllegalArgumentException` (negative duration) is thrown.

#### Scenario: Total failure outcome representable
- **Given** a `TranslationKey` with original text `"Hello"` and a `TranslationResult` constructed with status `FALLBACK_TO_ORIGINAL`, translated text `"Hello"`, and a non-null warning
- **When** the result is inspected
- **Then** `translatedText` equals the key's `originalText` and the warning is present.

#### Scenario: Nullable engine on cache hit
- **Given** a `TranslationResult` constructed with status `CACHE_HIT` and a `null` engine
- **When** the result is inspected
- **Then** construction succeeds and the engine component is `null` while key, status, translated text, and duration are preserved.

---

### Requirement 14: GlossaryEntry Record
`GlossaryEntry` MUST be an immutable record of three components — `String term`, `String translation`, `GlossaryEntryClassification classification` (decision 3). Its compact constructor MUST reject `null` term, `null` translation, `null` classification (throws `NullPointerException`), and blank term or blank translation (throws `IllegalArgumentException`). Equality is by all three components.

#### Scenario: Rejects invalid term/translation
- **Given** the `GlossaryEntry` record
- **When** constructed with a `null` term or a blank translation
- **Then** a `NullPointerException` (null) or `IllegalArgumentException` (blank) is thrown.

#### Scenario: Rejects null classification
- **Given** the `GlossaryEntry` record
- **When** constructed with a `null` classification
- **Then** a `NullPointerException` is thrown.

---

### Requirement 15: GlossaryEntryClassification Enum
`GlossaryEntryClassification` MUST be an enum with exactly three constants (decision 3), driving heuristic rules 2/5: `AMBIGUOUS`, `LORE`, `PLAIN`. No other constants MAY be added.

#### Scenario: Enum exposes exactly the three classifications
- **Given** the `GlossaryEntryClassification` enum
- **When** its constants are enumerated
- **Then** the set equals exactly `{AMBIGUOUS, LORE, PLAIN}`.

---

### Requirement 16: Value Equality and Immutability for All Records
Every record in the package (`JsonPath`, `LanguageCode`, `ModpackId`, `TranslationKey`, `TranslationResult`, `GlossaryEntry`) MUST be immutable (no mutators, no setters; all components final) and MUST implement value-based `equals`/`hashCode` derived from all components. Records with equal components are equal and share hash codes; records differing in any component are not equal.

#### Scenario: Value equality across records
- **Given** two instances of any record type in the package with identical component values, and an instance differing in one component
- **When** compared with `equals`/`hashCode`
- **Then** the identical instances are equal with equal hash codes and the differing instance is not equal.

---

### Requirement 17: String-Leaf Rule and Output Shape (Decisions 7 & 8)
The model MUST treat a `TranslationKey` as the unit for a single JSON string leaf of the modpack file; non-string leaves are preserved untouched (rule stated; extraction logic is deferred to the extraction change and is not implemented here). The change MUST NOT introduce any output document model type — the per-key output unit is `TranslationResult` and the report consumes `List<TranslationResult>`.

#### Scenario: No output document model exists
- **Given** the implemented `domain.model` package
- **When** the public types are enumerated
- **Then** no type representing an output document or JSON tree exists (the inventory is exactly the 9 types of Requirement 3), and the only per-key output type is `TranslationResult`.

---

### Requirement 18: Unit Tests — JUnit 6 + AssertJ, Red-First
Unit tests for the package MUST live in `src/test/java/com/lucalzt/mctranslator/domain/model/`, use JUnit 6 + AssertJ, and run without a Spring context. Following the project's strict-TDD rule (`openspec/config.yaml` → `rules.tasks`), the test files MUST be written red-first (before the production code) and MUST cover, for every type: validation rejection (nulls, blanks, invalid path segments, negative duration, invalid classification), value equality, and behavior (prefix matching; each of the five `TranslationStatus` outcomes represented in a `TranslationResult`).

#### Scenario: Test suite covers validation, equality, and outcomes
- **Given** the implemented `domain.model` package and its test sources
- **When** `mvnw.cmd test` is executed
- **Then** the new JUnit 6 + AssertJ tests run without a Spring context, and coverage includes validation rejection, value equality, `JsonPath` prefix matching, and one test per `TranslationStatus` outcome represented in `TranslationResult`.

---

### Requirement 19: Clean Verify Passes
`mvnw.cmd clean verify` MUST pass on Windows (and `./mvnw clean verify` on Linux/macOS) with zero test failures, zero compilation errors, and exit code 0.

#### Scenario: Full build succeeds
- **Given** the implemented change
- **When** `mvnw.cmd clean verify` is executed
- **Then** the build compiles, all tests (existing and new) pass, and the command exits with status 0.

---

## Non-Goals

- `domain/port/*` — `TranslationEnginePort`, `GlossaryPort`, `TranslationCachePort` (deferred to the ports change).
- `domain/service/*` — `ScalingHeuristic`, `VariableMasker`/`VariableUnmasker` (deferred to the domain-services change).
- `application/` — `GlossaryAwareTranslator` and any use-case orchestration.
- `infrastructure/` — adapters (CLI, nllb, llama, glossary, cache), Spring Shell commands, SQLite schema.
- Configuration plumbing that binds the target language — the `LanguageCode` VO is in scope; reading it from configuration with a default (decision 1) is application-layer work.
- JSON extraction/collection logic, and any output document model — decision 8: only `List<TranslationResult>` is needed; the output JSON keeps the input structure with substituted values, handled by a later writer change.
- Empty/whitespace-only text-leaf policy and all-variable (`%s`-only) text handling — extraction edge cases not resolved by decision 7; deferred to the extraction change. Modeled here only as the `TranslationKey` blank-text invariant.
- Deferred value objects: `MaskedText` (domain-services change), `GlossaryTermMatch` (domain-services change), `CacheKey` (ports/cache change — decision 2 pins its semantic basis: path + text + language + modpack).
- No MODIFIED or REMOVED requirements: existing code (`TranslateCommand`, application bootstrap) and CLI runtime behavior are untouched.

---

## Risks & Mitigations

- **Risk 1 (CRITICAL) — Package mismatch in docs**: `docs/architecture/implementation-strategy.md` writes `com.mctranslator`; the real base package is `com.lucalzt.mctranslator` (confirmed in `src/main/java/com/lucalzt/mctranslator/`). The real one wins; Requirement 1 pins the real package and forbids `com.mctranslator`.
- **Risk 2 — jspecify dependency absent today**: the `jspecify` dependency is not in `pom.xml`; wrong version selection would break the build. *Mitigation*: pin the latest stable 1.x (Requirement 6) and verify with `mvnw.cmd clean verify` (Requirement 19).
- **Risk 3 — Deferred VO creep**: `MaskedText`, `GlossaryTermMatch`, `CacheKey` must not slip into this change. *Mitigation*: Requirement 3 pins the exact 9-type inventory; grep-based verification included in scenarios.
- **Risk 4 — Extraction edge cases unresolved** (empty/whitespace-only text, `%s`-only text): decision 7 covers string vs non-string leaves only. *Mitigation*: modeled only as the `TranslationKey` blank-text invariant (Requirement 10); the policy is pinned in the later extraction change (Non-Goals).
- **Risk 5 — Nullable semantics of TranslationResult (engine/warning)**: docs do not resolve engine-on-cache-hit metadata (explore Q2). *Mitigation*: Requirements 13 pins `engine` and `warning` as the only `@Nullable` elements with explicit per-status semantics; to be revisited when the cache port/schema contract lands.
- **Risk 6 — JSpecify annotations are not enforced by javac** (no nullness checker configured in the build): annotations document contracts; runtime rejection remains the enforceable contract. *Mitigation*: validation is implemented as compact-constructor checks and verified by unit tests (Requirement 18).
- **Risk 7 — Over-engineering**: every in-scope type is justified by a resolved product decision or a documented pipeline step; scope decision 9 trimmed ports and services (Non-Goals).
- **Risk 8 — Java 25 feature pitfalls**: use only stable features (records, enums, sealed types); avoid String Templates (preview/rework risk in a GraalVM native build).
- **Risk 9 — Strict TDD ordering**: config.yaml requires tests red-first per capability. *Mitigation*: Requirement 18 pins red-first ordering and the `mvnw.cmd clean verify` gate (Requirement 19) enforces completion.
