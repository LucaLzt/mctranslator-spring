# Proposal: Domain Models & Value Objects (`domain-models`)

## Purpose

Define the pure-Java domain model for the translation pipeline: `TranslationKey`, `TranslationResult`, `GlossaryEntry` and the value objects the pipeline requires, in `com.lucalzt.mctranslator.domain.model` — with zero framework dependencies, compact-constructor validation, JSpecify `@NullMarked`, Javadoc, and JUnit 6 + AssertJ unit tests.

- **What**: A new `domain/model` package containing 3 domain classes and 6 supporting VOs/enums (see Scope), fully validated and unit-tested.
- **Why**: The pipeline (`docs/architecture/implementation-strategy.md`) has no domain model yet (the repo only has a Spring Shell bootstrap `TranslateCommand` returning "not implemented"). Every downstream step — cache lookup, masking, `ScalingHeuristic`, engines, report metrics — consumes these types. Pinning the model now with unambiguous contracts satisfies the OpenSpec "cero ambigüedad" rule for immutable value objects and avoids churn when ports/services land.

## Approach

1. Add `org.jspecify:jspecify` (compile scope) to `pom.xml` if absent — required for `@NullMarked` (currently **not** present in the project).
2. Create package `com.lucalzt.mctranslator.domain.model` (real base package — see Risks) with a `package-info.java` annotated `@NullMarked` and documenting the package's role in the domain layer.
3. Implement records/VOs per the table below with compact-constructor validation and Javadoc on the public API. Domain stays 100% pure Java: no Spring annotations, no `jakarta` imports.
4. Write unit tests (JUnit 6 + AssertJ) in `src/test/java/com/lucalzt/mctranslator/domain/model/`, **red-first** per the project's strict-TDD rule (`openspec/config.yaml` → `rules.tasks`).
5. Verify with `mvnw.cmd clean verify` (Windows) / `./mvnw clean verify`.

## Scope

### In-Scope

Package `com.lucalzt.mctranslator.domain.model`:

| Type | Kind | Justification |
|---|---|---|
| `TranslationKey` | record (immutable domain object) | Named in the card; identity = (JsonPath + original text + target language + modpack) per decision 2; the pipeline's per-key unit. Rejects blank original text. |
| `JsonPath` | VO | Identity component of `TranslationKey` (decision 2); heuristic rules 1/4/5 match path prefixes (`quest.description.*`, `lore.*`, `advancement.*`, `item.*`, `block.*`, `entity.*`, `gui.*`). Behavior (`startsWith`/`matches`) keeps the future heuristic pure. |
| `LanguageCode` | VO | Decision 1 (target language comes from config with a default) and decision 2 (participates in the key identity). |
| `ModpackId` | VO (name + version) | Decision 6; participates in the key identity (decision 2). |
| `TranslationStatus` | enum | Decisions 4 and 5; must accommodate fallback/warning. Values: `CACHE_HIT`, `TRANSLATED_FAST`, `TRANSLATED_PRECISE`, `DEGRADED_TO_FAST`, `FALLBACK_TO_ORIGINAL`. |
| `TranslationEngineType` | enum (`FAST`, `PRECISE`) | Decision 4 (engine metadata); heuristic decision output; metrics per `implementation-strategy.md` §7. |
| `TranslationResult` | record | Decision 4 (rich metadata: status, engine, warning/fallback flag, duration) + decision 8 (feeds the report as `List<TranslationResult>`). Carries the key, translated text (original text on total failure), status, engine, warning message, duration. |
| `GlossaryEntry` | record (term + translation + classification) | Named in the card; decision 3. |
| `GlossaryEntryClassification` | enum (`AMBIGUOUS`, `LORE`, `PLAIN`) | Decision 3; drives heuristic rules 2/5. |

Supporting scope:
- `org.jspecify:jspecify` dependency in `pom.xml` (compile scope).
- Javadoc on all public types + `package-info.java`.
- Unit tests for all the above (validation, value equality, behavior).

### Non-Goals (Out of Scope)

- `domain/port/*` — `TranslationEnginePort`, `GlossaryPort`, `TranslationCachePort` (deferred to the ports change).
- `domain/service/*` — `ScalingHeuristic`, `VariableMasker`/`VariableUnmasker` (deferred to the domain-services change).
- `application/` — `GlossaryAwareTranslator` and any use-case orchestration.
- `infrastructure/` — adapters (CLI, nllb, llama, glossary, cache), Spring Shell commands, SQLite schema.
- Config plumbing that binds the target language (the `LanguageCode` VO is in scope; reading it from configuration is application-layer work).
- JSON extraction/collection logic and any output document model (decision 8: only `List<TranslationResult>` is needed; the output JSON keeps the input structure with substituted values — handled by a later writer change).
- Empty/whitespace-only text-leaf policy and all-variable (`%s`-only) text handling — extraction edge cases not resolved by decision 7; deferred to the extraction change. `TranslationKey` rejects blank text as an invariant in the meantime.

## Resolved Product Decisions (requirements)

1. **Target language**: comes from configuration with a default (no CLI option for now) — implies a `LanguageCode` VO.
2. **TranslationKey identity**: (JSON path + original text + target language + modpack) — `ModpackId` and `LanguageCode` participate in the key identity.
3. **Glossary classification**: typed enum `GlossaryEntryClassification` (e.g. `AMBIGUOUS`, `LORE`, `PLAIN`).
4. **TranslationResult metadata**: rich — status, engine, warning/fallback flag, timing/duration.
5. **Total failure behavior**: fall back to the original text + warning surfaced in the report. `TranslationStatus` must accommodate fallback/warning.
6. **ModpackId**: composed of name + version.
7. **Extraction rule**: only JSON string leaves become `TranslationKey`; non-string leaves are preserved untouched (out of scope for modeling beyond stating the rule).
8. **Output shape**: output JSON keeps the input structure with substituted values; metadata/report goes separately — `List<TranslationResult>` feeds the report; no output document model in this change.
9. **Scope**: `domain/model` only — `TranslationKey`, `TranslationResult`, `GlossaryEntry` + the necessary VOs. Ports and domain services are non-goals.

## Deferred VO Rationale (explicit)

- **`MaskedText`** — DEFERRED: it is the artifact of `VariableMasker`/`Unmasker`, a domain service that is a non-goal here. It belongs to the domain-services change.
- **`GlossaryTermMatch`** — DEFERRED: it is the output of `ScalingHeuristic` rule 2/5 matching, a domain service that is a non-goal here. Belongs to the domain-services change.
- **`CacheKey`** — DEFERRED: its exact definition belongs to the `TranslationCachePort` contract / `SqliteCacheAdapter`. Note that decision 2 already pins the semantic basis (path + text + language + modpack) from which the future `CacheKey` will derive.

## Risks

- **CRITICAL — Package mismatch in docs**: `docs/architecture/implementation-strategy.md` writes `com.mctranslator`; the real base package is `com.lucalzt.mctranslator` (confirmed in `src/main/java/com/lucalzt/mctranslator/`). The real one wins; this change creates `com.lucalzt.mctranslator.domain.model`.
- **JSpecify absent**: the `jspecify` dependency is not in `pom.xml` today; this change adds it. Wrong version selection would break the build — mitigate by pinning the latest stable 1.x and verifying with `mvnw.cmd clean verify`.
- **Deferred VOs creep**: `MaskedText`, `GlossaryTermMatch`, `CacheKey` must not slip into this change; each has a named owner change (domain-services / ports) to prevent ambiguity.
- **Extraction edge cases unresolved** (empty/whitespace-only text, `%s`-only text): decision 7 only covers string vs non-string leaves. Modeled here only as the `TranslationKey` blank-text invariant; the policy is pinned in the later extraction change.
- **Over-engineering**: every in-scope VO above is justified by a resolved product decision or a documented pipeline step; the scope decision (9) already trimmed ports and services.
- **Java 25**: use only stable features (records, enums, sealed types); avoid String Templates (preview/rework risk in a GraalVM native build).

## Rollback Plan

Model-only change in a brand-new package: revert the commit(s) introducing `domain/model` and the `jspecify` dependency. No existing code references the new package, no database schema or migrations exist, and the CLI/runtime behavior is untouched — the revert is a clean deletion with zero side effects.

## Acceptance Criteria

- `mvnw.cmd clean verify` passes (Windows) / `./mvnw clean verify` on Linux/macOS, including the new unit tests.
- The `domain.model` package compiles with **zero** imports from `org.springframework`, `jakarta`, or any other framework package (verifiable by grep/build).
- Every VO/enum rejects invalid input (nulls, blank text, empty/invalid path segments, negative duration, invalid classification) via compact-constructor validation, covered by unit tests.
- Records compare by value (equality), covered by unit tests.
- `TranslationResult` can represent every documented pipeline outcome — `CACHE_HIT`, `TRANSLATED_FAST`, `TRANSLATED_PRECISE`, `DEGRADED_TO_FAST`, `FALLBACK_TO_ORIGINAL` — each covered by a test.
- `@NullMarked` present on the package, `package-info.java` written, and Javadoc present on every public type (code review check).
