# Design: Domain Models & Value Objects (`domain-models`)

## Executive Summary

This design specifies the pure-Java domain model of the translation pipeline: exactly 9 public types in
`com.lucalzt.mctranslator.domain.model` (`TranslationKey`, `TranslationResult`, `GlossaryEntry` records;
`JsonPath`, `LanguageCode`, `ModpackId` value objects; `TranslationStatus`, `TranslationEngineType`,
`GlossaryEntryClassification` enums), a `@NullMarked` `package-info.java`, the `org.jspecify:jspecify` compile
dependency (pinned to **1.0.1**, the latest stable 1.x verified on Maven Central on 2026-07-30), and JUnit 6 +
AssertJ unit tests written red-first. All validation lives in record compact constructors using only JDK
exceptions (`NullPointerException` for nulls, `IllegalArgumentException` for invalid values), so invalid states
fail fast while the package keeps zero framework imports. Every design element traces to a resolved product
decision (proposal decisions 1–9) or a specification requirement (spec R1–R19); anything not pinned by the spec
was resolved to the simplest option and flagged in §4.

---

## 1. Scope Anchoring

- **In scope**: the 9 public types of R3, `package-info.java` (R4), Javadoc on all public API (R5), the jspecify
  dependency (R6), unit tests (R18), and the `mvnw.cmd clean verify` gate (R19).
- **Explicitly out of scope** (Non-Goals): `MaskedText`, `GlossaryTermMatch`, `CacheKey` (each owned by the
  domain-services or ports change — R3 scenario 2); `domain/port/*`, `domain/service/*`, `application/*`,
  `infrastructure/*`; JSON extraction/collection; any output document model (R17 — the per-key output unit is
  `TranslationResult`); configuration plumbing for the target language.

---

## 2. Architecture & Component Design

### 2.1 Package layout

```
src/main/java/com/lucalzt/mctranslator/
└── domain/
    └── model/                                  ← pure Java, zero framework (R2)
        ├── package-info.java                   ← @NullMarked + package role Javadoc (R4)
        ├── JsonPath.java                       ← record VO (R7)
        ├── LanguageCode.java                   ← record VO (R8)
        ├── ModpackId.java                      ← record VO (R9)
        ├── TranslationKey.java                 ← record (R10)
        ├── TranslationStatus.java              ← enum (R11)
        ├── TranslationEngineType.java          ← enum (R12)
        ├── TranslationResult.java              ← record (R13, R17)
        ├── GlossaryEntry.java                  ← record (R14)
        └── GlossaryEntryClassification.java    ← enum (R15)
```

Test mirror (R18):

```
src/test/java/com/lucalzt/mctranslator/domain/model/
├── JsonPathTest.java
├── LanguageCodeTest.java
├── ModpackIdTest.java
├── TranslationKeyTest.java
├── TranslationStatusTest.java
├── TranslationEngineTypeTest.java
├── TranslationResultTest.java
├── GlossaryEntryTest.java
└── GlossaryEntryClassificationTest.java
```

### 2.2 Rationale

- **Hexagonal domain-layer purity** (`hexagonal-architecture` skill): `domain/model` holds entities, value
  objects, and aggregates as pure Java; the package has zero knowledge of ports, adapters, Spring, or `jakarta`.
  Allowed imports are restricted to the JDK (`java.*`, `java.lang.*`) and `org.jspecify.annotations.*` (R2).
- **Single flat package**: the 9 types are small, high-cohesion, and always consumed together by the pipeline
  (cache lookup, masking, `ScalingHeuristic`, engines, report). Splitting into `domain/model/vo`, `…/enum`, or
  `…/record` sub-packages would add ceremony without separating concerns and would make the R3 "exactly these 9
  public types" inventory harder to verify. The record/VO/enum distinction is a kind-of distinction, not a
  package boundary.
- **Typed identities, no raw strings** (`domain-driven-design` skill): `TranslationKey` references `JsonPath`,
  `LanguageCode`, `ModpackId` value objects rather than bare `String`/`Enum`-less fields; `GlossaryEntry`
  carries the typed `GlossaryEntryClassification`. This matches the project standard
  (`development-standards.md` §3: "IDs tipados (value objects), no `Long`/`String` sueltos").
- **No new public types beyond the inventory** (R3): no custom exception classes, no DTOs, no output model.
  Rejection uses JDK exceptions (decision D7).

### 2.3 Nullability policy (R4)

`package-info.java` carries `@org.jspecify.annotations.NullMarked` plus a Javadoc description of the package
role (pure domain layer of the translation pipeline, zero framework dependencies). Under `@NullMarked`, every
type and member is `@NonNull` by default; the **only** `@Nullable` elements in the package are the
`TranslationResult` components `engine` and `warning` (R13), declared via the record component annotation so
the annotation propagates to the accessor, constructor parameter, and field.

---

## 3. Data Model

All records are immutable with value-based `equals`/`hashCode` derived from all components (R16). Validation
lives in compact constructors and runs at construction time (fail fast). Record `equals`/`hashCode` are
generated by the language and are correct by construction for single-purpose VOs; tests pin the behavior (R16
scenario).

### 3.1 `JsonPath` — record VO (R7)

- **Declaration**: `public record JsonPath(String value)`.
- **Invariants**:
  - `value` non-null → `NullPointerException` (`Objects.requireNonNull`).
  - `value` not blank (`isBlank()`) → `IllegalArgumentException`.
  - No empty segments — rejected structurally: leading dot (`value.startsWith(".")`), trailing dot
    (`value.endsWith(".")`), or consecutive dots (`value.contains("..")`) → `IllegalArgumentException`.
    These three predicates exactly cover the spec scenarios `""`, `"   "`, `".quest"`, `"quest."`,
    `"quest..description"` and are the simplest direct mechanism. (An equivalent check — split on `.` and
    reject any empty segment — is not used because it is more machinery for the same outcome.)
- **Accessor**: `value()` exposes the raw dot-separated path string (R7).
- **Behavior**: `boolean startsWith(String... segments)` returns `true` iff the path has at least as many
  segments as the given arguments and the leading segments equal them, in order, **case-sensitively**. The
  stored value is split on `.` (guaranteed non-empty segments by validation) and the first
  `segments.length` elements are compared with `equals`. Wildcards are never part of a path value — the `*`
  in the docs (`quest.description.*`, `lore.*`, …) is shorthand for "and everything below" (R7).
- **Method contract (tightened beyond the spec, see D8)**: `segments` array and each element must be non-null
  (`NullPointerException`); blank elements are rejected (`IllegalArgumentException`, they can never equal a
  stored segment because stored segments are never blank); empty varargs `startsWith()` returns `true`
  (vacuous prefix).
- **Equality**: record-derived, by the canonical string (D1).

### 3.2 `LanguageCode` — record VO (R8)

- **Declaration**: `public record LanguageCode(String value)`.
- **Invariants**: `value` non-null → `NullPointerException`; `value.isBlank()` → `IllegalArgumentException`.
- **No format restriction, no normalization** (D6): engine language identifiers vary (`es`, `en_US`,
  `spa_Latn`) and case is significant — the code is stored exactly as provided. Reading the target language
  from configuration is application-layer work (decision 1, Non-Goals).
- **Equality**: by value.

### 3.3 `ModpackId` — record VO (R9)

- **Declaration**: `public record ModpackId(String name, String version)`.
- **Invariants**: `name` and `version` each non-null → `NullPointerException`; each non-blank →
  `IllegalArgumentException`.
- **No trimming** (D6): components are stored exactly as sourced; whitespace normalization is an adapter
  concern. Participates in `TranslationKey` identity (decision 2).
- **Equality**: by both components.

### 3.4 `TranslationKey` — record (R10)

- **Declaration**:
  `public record TranslationKey(JsonPath path, String originalText, LanguageCode targetLanguage, ModpackId modpack)`.
- **Invariants**: any of the four components `null` → `NullPointerException`;
  `originalText.isBlank()` → `IllegalArgumentException` (the modeled boundary of the deferred extraction
  edge-case policy; full empty/whitespace-only leaf policy lives in the extraction change — Non-Goals).
- **Identity**: all four components (decision 2). The deferred `CacheKey` will derive from this semantic
  basis (path + original text + language + modpack) — see T3.
- **Equality**: by all four components.

### 3.5 `TranslationStatus` — enum (R11)

Exactly five constants, in spec order, no payload (D3):

```
CACHE_HIT, TRANSLATED_FAST, TRANSLATED_PRECISE, DEGRADED_TO_FAST, FALLBACK_TO_ORIGINAL
```

Each constant gets Javadoc (R5). The enum is the outcome *category* used by metrics (§7 of the implementation
strategy: % cache vs fast vs precise, fallback counts); human-readable detail is carried per instance in
`TranslationResult.warning`.

### 3.6 `TranslationEngineType` — enum (R12)

Exactly two constants: `FAST`, `PRECISE`. Plain enum; no serialization mapping yet (D5). It is the
`ScalingHeuristic` decision output and the `TranslationResult` engine metadata (decision 4).

### 3.7 `TranslationResult` — record (R13, R17)

- **Declaration**:

```java
public record TranslationResult(
        TranslationKey key,
        String translatedText,
        TranslationStatus status,
        @Nullable TranslationEngineType engine,   // the only @Nullable elements in the package (R4)
        @Nullable String warning,
        Duration duration)
```

- **Invariants** (exactly the R13 list — nothing more): `key`, `translatedText`, `status`, `duration` non-null
  → `NullPointerException`; `duration.isNegative()` → `IllegalArgumentException`. Zero duration is allowed
  (cache hits can be ~instant). `engine` and `warning` are **not** null-checked — they are `@Nullable` by
  design (R4).
- **Nullability contract** (documented in Javadoc; enforced by tests, not by the constructor — D4):

  | Status | `engine` | `warning` |
  |---|---|---|
  | `CACHE_HIT` | `null` (no engine ran) | absent |
  | `TRANSLATED_FAST` | `FAST` | absent |
  | `TRANSLATED_PRECISE` | `PRECISE` | absent |
  | `DEGRADED_TO_FAST` | `FAST` | message about the precise-engine fallback |
  | `FALLBACK_TO_ORIGINAL` | `null` (no engine succeeded) | message about total failure |

- **Behavioral contract** (decision 5): when `status == FALLBACK_TO_ORIGINAL`, the result represents total
  failure — `translatedText` equals `key.originalText()` and `warning` carries the failure message.
- **Factories**: none (D2). Construction is via the canonical constructor exactly as the spec scenarios do.
- **Output role**: `TranslationResult` is the per-key output unit; the report consumes
  `List<TranslationResult>` (decision 8, R17). No output document model exists (R17 scenario).
- **Duration**: `java.time.Duration` (JDK type; keeps the package JDK-only).

### 3.8 `GlossaryEntry` — record (R14)

- **Declaration**:
  `public record GlossaryEntry(String term, String translation, GlossaryEntryClassification classification)`.
- **Invariants**: any component `null` → `NullPointerException`; `term` or `translation` blank →
  `IllegalArgumentException`.
- **Equality**: by all three components.

### 3.9 `GlossaryEntryClassification` — enum (R15)

Exactly three constants: `AMBIGUOUS`, `LORE`, `PLAIN`. Plain enum; drives heuristic rules 2/5 (decision 3).

### 3.10 `package-info.java` (R4)

```java
@org.jspecify.annotations.NullMarked
/**
 * Pure domain model of the translation pipeline ...
 */
package com.lucalzt.mctranslator.domain.model;
```

Contains the `@NullMarked` annotation and a Javadoc package description stating the role (pure domain layer,
zero framework dependencies, only `@Nullable` elements are `TranslationResult.engine`/`warning`).

---

## 4. Design Decisions & Alternatives

| ID | Decision point | Alternatives considered | Chosen | Rationale / trace |
|---|---|---|---|---|
| D1 | `JsonPath` representation | (a) raw `String` component only; (b) `List<String> segments` component; (c) `String` component + cached segments field | **(a) raw `String` component** | R7 defines the VO as "wrapping a … path string" with an accessor returning the raw path; string equality is the canonical identity; segmentation is a deterministic derivation (`split(".")`, safe because validation forbids empty segments). (b) adds aliasing/immutability ceremony and makes equality depend on list contents; (c) uses a non-component field invisible to `equals`/`hashCode` — premature optimization for a heuristic that runs once per key. |
| D2 | `TranslationResult` factory vs canonical constructor | (a) canonical only; (b) passthrough `fromKey(...)`; (c) per-status semantic factories (`cacheHit`, `translatedFast`, `translatedPrecise`, `degradedToFast`, `fallbackToOriginal`) | **(a) canonical only** | R13 pins the exact components and the exact validation list; the spec scenarios construct via the canonical constructor (including `CACHE_HIT` with null engine). (b) adds no value. (c) would add 5 public members, more Javadoc/tests, and lock the engine/warning per-status semantics before the cache-port contract lands (spec risk 5 explicitly defers engine-on-cache-hit semantics). "Simplest option" per the design rules; the behavioral contract is documented in Javadoc and tested (R18). |
| D3 | `TranslationStatus` message vs separate warning | (a) plain enum + `@Nullable String warning` on the result; (b) enum with embedded static message | **(a) plain enum + warning field** | R11 enumerates exactly 5 constants with no fields; the warning text is per-instance and dynamic (engine-specific), so a static enum message would be wrong and would tempt callers to parse messages. Status groups metrics (R11 scenarios, §7 metrics); `warning` is the human-readable detail (R13). |
| D4 | Per-status consistency enforcement (engine/warning presence) | (a) enforce in constructor; (b) document + test only | **(b) document + test** | R13's scenario requires `CACHE_HIT` with null engine to *succeed*; enforcing full per-status shape would reject legitimate future combinations (e.g., a cache hit recording which engine produced the entry — spec risk 5). Constructor validates exactly the R13 list; Javadoc documents the expected shape; R18 tests pin each of the five outcomes. |
| D5 | Enum design | plain enums vs enums with codes/payload | **plain enums** | No serialization contract exists yet; the report/cache layers will map enum → representation when they land (Non-Goals). Payload now would be speculative. |
| D6 | String normalization | trim/lowercase vs store as-is | **store as-is (no normalization)** | `LanguageCode` must not lowercase (`spa_Latn` is case-significant); `ModpackId` identity must be exactly what the source provided; `JsonPath` must not alter the canonical path. Normalization belongs to adapters/ACL (hexagonal skill), not the domain. Flag: `" MyMod "` and `"MyMod"` are different `ModpackId`s by design. |
| D7 | Exception types | JDK exceptions vs custom domain exceptions | **JDK `NullPointerException` / `IllegalArgumentException`** | The spec scenarios pin NPE/IAE per type. Custom exceptions would be new *public types* → violates R3's exact 9-type inventory. |
| D8 | `JsonPath` method surface | accessor + `startsWith` only vs also `matches(...)` / `segments()` | **accessor + `startsWith` only** | Heuristic rules 1/4/5 are all *prefix* rules (`quest.description.*`, `lore.*`, …), fully served by `startsWith(String...)` (R7). `matches`/`segments` would be speculative API. Method-level guard (null/blank varargs → exception; empty varargs → `true`) is a small tightening to keep the contract unambiguous ("cero ambigüedad" rule, proposal acceptance "every VO rejects invalid input"). |
| D9 | Javadoc enforcement | code-review check vs build-gated `maven-javadoc-plugin` | **code-review check (+ optional `mvnw.cmd javadoc:javadoc`)** | Proposal acceptance criterion says "Javadoc present … (code review check)". Adding the plugin would expand the pom diff and could fail the build on doclint warnings — out of scope. |

---

## 5. Sequencing (strict TDD — informs `tasks`)

Per `openspec/config.yaml` (`rules.tasks`: "las primeras tareas de cada capability deben escribir los tests …
en rojo antes del código de producción") and R18, capabilities are ordered by dependency:

1. **Cap 0 — Build plumbing**: add `org.jspecify:jspecify` to `pom.xml` (enables `@NullMarked` at compile time).
2. **Cap A — VOs** (no interdependencies): `JsonPath`, `LanguageCode`, `ModpackId` — write test classes red
   first (compilation failure of the not-yet-existing type counts as red), then implement, then green.
3. **Cap B — Enums**: `TranslationEngineType`, `TranslationStatus`, `GlossaryEntryClassification` — red → green.
4. **Cap C — `TranslationKey`** (depends on Cap A VOs): red → green.
5. **Cap D — `GlossaryEntry`** (depends on Cap B classification): red → green.
6. **Cap E — `TranslationResult`** (depends on Caps A/B/C): red → green.
7. **Cap F — Closing gates**: `package-info.java` (`@NullMarked`), structural greps (R1/R2/R3 scenarios:
   package placement, zero framework imports, exact 9-type inventory, deferred VOs absent), then
   `mvnw.cmd clean verify` (R19).

---

## 6. Testing Strategy (JUnit 6 + AssertJ, red-first, no Spring context)

Pure unit tests per `spring-boot-testing` skill ("business logic in service? → plain JUnit, no Spring
context") and R18. One test class per type; `@Test` + `@DisplayName` + AssertJ `assertThat`, matching repo
conventions. Coverage order per skill: happy path → edge cases → validation rejection.

| Test class | Spec coverage | Cases |
|---|---|---|
| `JsonPathTest` | R7 (all 4 scenarios), R16 | `null` → NPE; `""`, `"   "`, `".quest"`, `"quest."`, `"quest..description"` → IAE; happy path + accessor; `startsWith` prefixes `(quest,description)`→true on `quest.description.task1`, `(item)`→true on `item.sword`, exact-prefix `(quest,advancement)`→true on `quest.advancement` (equal-length exact prefix, per the corrected spec scenario), meaningful negative `(quest,description)`→false on `quest.advancement` (heuristic rule 1); case-sensitivity (`Quest.description` vs `quest` → false); equality/hashCode (equal values equal + equal hashes; different value not equal); method contract (empty varargs → true; null varargs/element → NPE; blank segment → IAE) |
| `LanguageCodeTest` | R8 | `null` → NPE; `"   "` → IAE; `"es"`, `"spa_Latn"` accepted + accessor; equality |
| `ModpackIdTest` | R9 | `null` name/version → NPE; blank name/version → IAE; happy path; equality by both components (equal pair equal + same hash; different version not equal) |
| `TranslationKeyTest` | R10 | `null` path / `null` language / `null` modpack → NPE; `""` and `"   "` originalText → IAE; happy path; equality across all four components (differing language → not equal) |
| `TranslationStatusTest` | R11 | `values()` equals exactly `{CACHE_HIT, TRANSLATED_FAST, TRANSLATED_PRECISE, DEGRADED_TO_FAST, FALLBACK_TO_ORIGINAL}` in order |
| `TranslationEngineTypeTest` | R12 | `values()` equals exactly `{FAST, PRECISE}` |
| `TranslationResultTest` | R13 (3 scenarios), R11 scenario 2, R17, R16 | `null` key / translatedText / status / duration → NPE; negative duration → IAE; zero duration accepted; **total-failure scenario** (`FALLBACK_TO_ORIGINAL`, translatedText `"Hello"` == `key.originalText()`, warning non-null, engine null); **cache-hit scenario** (`CACHE_HIT`, engine null, all other fields preserved); **five-outcomes test** (construct a result for each of the five statuses, assert status preserved — R11 scenario 2 / R18); per-status engine/warning shape (D4); equality across components |
| `GlossaryEntryTest` | R14 | `null` term / translation / classification → NPE; blank term / translation → IAE; happy path; equality by all three components |
| `GlossaryEntryClassificationTest` | R15 | `values()` equals exactly `{AMBIGUOUS, LORE, PLAIN}` |

**Structural checks** (verification-time greps, per the spec scenarios "Grep check"/"enumerated" — not JUnit):
- R1: all domain types declared under `src/main/java/com/lucalzt/mctranslator/domain/model/`, none under any `com.mctranslator` package.
- R2: `grep` over the package sources for `import org.springframework` / `import jakarta` / any non-JDK, non-jspecify import → zero matches.
- R3: public types enumerate to exactly the 9; `MaskedText` / `GlossaryTermMatch` / `CacheKey` absent from the package sources.

---

## 7. Build & Verification Integration

### 7.1 `pom.xml` change (R6)

Add to `<dependencies>` (compile scope is Maven's default):

```xml
<dependency>
    <groupId>org.jspecify</groupId>
    <artifactId>jspecify</artifactId>
    <version>1.0.1</version>
</dependency>
```

- **Version pin**: `1.0.1` is the latest stable 1.x (verified on Maven Central, released 2026-07-30;
  annotation-only artifact, Apache-2.0, zero transitive dependencies). The explicit version guards against
  parent-managed drift and satisfies "latest stable 1.x". Today the dependency is absent from `pom.xml`, so it
  is added once, not duplicated (R6).
- **Verification**: `mvnw.cmd -q dependency:tree` shows `org.jspecify:jspecify:1.0.1` in the compile-scope
  tree and the build compiles with the nullness annotations resolving without warnings (R6 scenario).

### 7.2 Build gate (R19)

`mvnw.cmd clean verify` (Windows) / `./mvnw clean verify` (Linux/macOS) must pass with zero test failures,
zero compilation errors, exit code 0. CI (existing GitHub Actions workflow) runs the same command.

### 7.3 Java 25 constraints

Only stable language features are used (records, enums, `Objects.requireNonNull`, `String.isBlank`,
`java.time.Duration`). **No String Templates** (preview/rework risk in a GraalVM native build — proposal risk;
`tech-stack.md` targets GraalVM Native Image).

---

## 8. Threat Matrix

Security by default (`sdd-design` reference: every High impact needs mitigation or an explicit accepted-risk
note; no threat dismissed without a reason).

| ID | Asset | Threat | Impact | Likelihood | Mitigation | Trace |
|---|---|---|---|---|---|---|
| T1 | Domain values in memory | **Unbounded input sizes / validation DoS**: a malicious or malformed modpack supplies huge `originalText` or path strings; keys hold full text, amplifying memory and future cache-key hashing. | Medium | Low | **Accepted risk with follow-up**: length bounding is deliberately deferred to the extraction change — the spec pins only blank-rejection for `originalText` (R10) and structural path validation (R7); adding max lengths now could reject legitimate long lore text. Recorded as an explicit requirement for the extraction change: adapters MUST cap leaf sizes (configurable max text length, max path depth) before constructing keys. | R10, R17 |
| T2 | Text/path payloads | **Injection via text content** (SQL/HTML/shell): stored strings reinterpreted as code or queries. | Low | Low | The domain never interprets text — values are opaque payloads consumed by engines and the report (R2 zero framework deps make this structural). SQL construction belongs to the future `SqliteCacheAdapter`, which MUST use parameterized JDBC (`PreparedStatement`); noted in the ports/cache change. Domain responsibility ends at storage and equality. | R2 |
| T3 | Cache identity | **Path-form ambiguity → cache-key collisions/misses**: non-canonical paths would make derived cache keys ambiguous. | Medium | Low | `JsonPath` validation guarantees a canonical dot-separated form (no empty segments, no leading/trailing dots), so `value()` is an unambiguous hash basis. The future `CacheKey` MUST derive from the VO components (`path.value()`, `originalText`, `targetLanguage`, `modpack`) — never from re-splitting or re-concatenating raw strings (decision 2 basis, spec risk 5). | R7, R10 |
| T4 | Logs/report output | **Log/report forging** via embedded control characters (newlines) in `warning`/text values. | Low | Low | Out of domain scope; the future report/log writer must escape or quote free-form values. Documented here so the writer change picks it up. | R13 |
| T5 | Constructor contracts | **Null-safety defects**: callers pass null where forbidden; `@NullMarked` is documentation, not enforced by javac (spec risk 6). | Medium | Medium | `Objects.requireNonNull` on every non-null component (fail fast → NPE); `@Nullable` only on `engine`/`warning` (R4); unit tests cover every rejection path (R18). Runtime rejection is the enforceable contract. | R4, R13, R18 |
| T6 | Supply chain | **Compromised/wrong annotation dependency** breaking the build or the nullness contract. | Low | Low | Pin exact `org.jspecify:jspecify:1.0.1` (latest stable 1.x, Maven Central, annotation-only, zero transitive deps); verify with `dependency:tree` + `mvnw.cmd clean verify` (R6, R19). | R6, R19 |
| T7 | Path semantics | **Path traversal-style abuse**: `..` segments or absolute paths leaking into filesystem/resource lookups downstream. | Low | Low | Validation rejects consecutive dots (a `..` segment is structurally impossible), plus leading/trailing dots — an absolute or parent-relative path cannot be represented in the VO. The domain never maps `JsonPath` to the filesystem. | R7 |

No High-impact row lacks a mitigation or explicit accepted-risk note. All Medium rows carry mitigations plus
recorded follow-ups (T1 extraction change; T3 cache contract).

---

## 9. Requirement Traceability

| Req | Mechanism (section) |
|---|---|
| R1 — real base package | §2.1 layout under `com.lucalzt.mctranslator.domain.model`; structural check §6 / §7 |
| R2 — zero framework imports | §2.2 import policy; grep check §6 |
| R3 — exact 9 public types | §2.1 inventory; structural check §6; D7 (no custom exceptions) |
| R4 — `@NullMarked` package-info | §2.3, §3.10 |
| R5 — Javadoc on all public API | §3 (per type) + D9 enforcement |
| R6 — jspecify compile dependency | §7.1 (pinned 1.0.1) |
| R7 — `JsonPath` VO | §3.1 (validation, accessor, `startsWith`) + D1/D8 |
| R8 — `LanguageCode` VO | §3.2 + D6 |
| R9 — `ModpackId` VO | §3.3 + D6 |
| R10 — `TranslationKey` identity | §3.4 |
| R11 — `TranslationStatus` enum | §3.5 + D3 |
| R12 — `TranslationEngineType` enum | §3.6 + D5 |
| R13 — `TranslationResult` rich metadata | §3.7 (nullability + behavioral contracts) + D2/D4 |
| R14 — `GlossaryEntry` record | §3.8 |
| R15 — `GlossaryEntryClassification` enum | §3.9 |
| R16 — value equality/immutability | §3 (all records) |
| R17 — string-leaf rule / no output model | §3.7 output role; §1 scope |
| R18 — JUnit 6 + AssertJ red-first tests | §6 |
| R19 — clean verify gate | §7.2 |

---

## 10. Scope Guards & Flags for Later Phases

- **Deferred VOs must not appear** (§1): `MaskedText`, `GlossaryTermMatch` → domain-services change;
  `CacheKey` → ports change (decision 2 pins its semantic basis). Grep check in §6.
- **Extraction edge cases** (empty/whitespace-only leaf policy, `%s`-only text): only the `TranslationKey`
  blank-text invariant is modeled (R10); the full policy is pinned in the extraction change (Non-Goals). The
  size-limit follow-up from T1 belongs there too.
- **No output document model** (R17): the report consumes `List<TranslationResult>`; output JSON substitution
  is a later writer change.
- **Flag for `tasks` phase — review budget**: this change is ~19 small files (9 main sources + `package-info`
  + 9 test classes), estimated ≈ **+750–900 added lines**, which will likely exceed the 400-line review budget
  (`sdd-phase-common.md` §E). `sdd-tasks` MUST forecast chained PRs (natural slices: Cap 0–B = pom + VOs +
  enums; Cap C–D = `TranslationKey` + `GlossaryEntry`; Cap E–F = `TranslationResult` + `package-info` + gates).
- **Flag for `apply` phase**: red-first per capability; compilation failure of the not-yet-existing type is the
  red state for Cap A (strict TDD rule, config.yaml).
