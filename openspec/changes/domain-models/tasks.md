# Tasks: Domain Models & Value Objects (`domain-models`)

## Review Workload Forecast

Decision needed before apply: Yes
Chained PRs recommended: Yes
400-line budget risk: High

The change is ~20 files (1 pom edit + 10 main sources incl. `package-info.java` + 9 test classes),
estimated at **≈ +900 changed lines** (range 850–950; the design predicted 750–900 — the upper edge is
confirmed). The session review budget is **800 lines**, so the aggregate exceeds it and the risk is High.

**Chained-PR plan (3 slices)** keeps every PR within budget — each slice ends with a green
`mvnw.cmd clean verify` and has clean rollback:

| Slice | Scope (tasks) | Files | Est. lines | Gate |
|---|---|---|---|---|
| **A — Build + VOs + enums** | T1–T13 (pom jspecify; `JsonPath`, `LanguageCode`, `ModpackId`; `TranslationStatus`, `TranslationEngineType`, `GlossaryEntryClassification` + tests) | 13 | **≈ 470–520** | `mvnw.cmd clean verify` after T13 |
| **B — Identity records** | T14–T17 (`TranslationKey`, `GlossaryEntry` + tests) | 4 | **≈ 170–200** | `mvnw.cmd clean verify` after T17 |
| **C — Result + closing gates** | T18–T22 (`TranslationResult` + test, `package-info.java`, structural greps, final verify) | 5 | **≈ 190–230** | `mvnw.cmd clean verify` after T22 |

Slice A is the heaviest (~500). If the strict **400-line per-PR** default must be honored instead of the
800 session budget, Slice A sub-splits without changing the task list: **A1 = T1–T7** (pom + 3 VOs, ≈ 300)
and **A2 = T8–T13** (3 enums, ≈ 160). This decision belongs to the user before apply starts
(delivery strategy `ask-on-risk`) — hence `Decision needed before apply: Yes`.

## Chained PR Plan

Feature-branch chain; PR #1 targets the feature/tracker branch, child PRs target the previous PR branch;
retarget/rebase until each child diff is clean.

- **Slice A — Build plumbing + value objects + enums (T1–T13)**
  - Start: T1 (add `org.jspecify:jspecify` 1.0.1 to `pom.xml`). Finish: T13 green + `clean verify`.
  - Scope: pom dependency; 3 VOs + 3 enums with compact-constructor validation, Javadoc, red-first tests.
  - Verification: `mvnw.cmd clean verify` passes. Rollback: revert pom edit + delete the 6 types and their 6
    test classes (nothing else references them).
- **Slice B — Identity records: `TranslationKey` + `GlossaryEntry` (T14–T17)**
  - Start: T14 (red). Finish: T17 green + `clean verify`.
  - Scope: the two identity records, validated, Javadoc, red-first tests. Depends on Slice A types.
  - Verification: `mvnw.cmd clean verify` passes. Rollback: delete the 2 types + 2 test classes
    (`TranslationResult` does not exist yet in the chain, so zero dependents).
- **Slice C — `TranslationResult` + package gates (T18–T22)**
  - Start: T18 (red). Finish: T22 final `clean verify` gate.
  - Scope: `TranslationResult` with nullable engine/warning and duration validation; `package-info.java`
    (`@NullMarked`); structural greps (R1/R2/R3 + deferred VOs absent); final full build.
  - Verification: `mvnw.cmd clean verify` passes with exit code 0. Rollback: delete `TranslationResult`,
    `package-info.java` and revert any gate artifacts — chain revert = full deletion of `domain/model`.

## Task Breakdown

### Slice A — Build plumbing + VOs + enums (PR #1)

- [x] T1: Add `org.jspecify:jspecify` 1.0.1 compile-scope dependency to `pom.xml`
  - **Description**: Declare `org.jspecify:jspecify` version `1.0.1` under `<dependencies>` (compile scope is
    Maven's default) — required so `@org.jspecify.annotations.NullMarked`/`@Nullable` resolve at compile time.
    Do not duplicate if a compatible version already exists (it is absent today, confirmed).
  - **Criteria**: `pom.xml` declares the dependency exactly once, pinned to `1.0.1`; `mvnw.cmd -q
    dependency:tree` shows `org.jspecify:jspecify:1.0.1` in the compile-scope tree; `mvnw.cmd -q compile`
    succeeds with zero compilation errors.
  - **Requirements**: R6
  - **Dependencies**: None
  - **Verification**: `mvnw.cmd -q dependency:tree` then `mvnw.cmd -q compile`

- [x] T2: Write `JsonPathTest` (RED)
  - **Description**: Create `src/test/java/com/lucalzt/mctranslator/domain/model/JsonPathTest.java` (JUnit 6 +
    AssertJ, `@Test` + `@DisplayName`, no Spring context) covering R7 + R16: `null` → NPE; `""`, `"   "`,
    `".quest"`, `"quest."`, `"quest..description"` → IAE; happy path + `value()` accessor; `startsWith`
    `("quest","description")`→true on `quest.description.task1`, `("item")`→true on `item.sword`,
    exact-prefix `("quest","advancement")`→true on `quest.advancement` (equal-length exact prefix, corrected
    spec scenario), meaningful negative `("quest","description")`→false on `quest.advancement` (heuristic
    rule 1); case-sensitivity (`Quest.description` vs `quest` → false); empty varargs → true; null
    varargs/element → NPE; blank segment → IAE; equality/hashCode
    (equal values equal + equal hashes; different value not equal).
  - **Criteria**: Test class exists and compiles *against the missing `JsonPath` type only*; targeted run
    **FAILS** (red state — compilation failure of the not-yet-existing type counts as red per strict TDD).
  - **Requirements**: R7, R16
  - **Dependencies**: T1
  - **Verification**: `mvnw.cmd -Dtest=JsonPathTest test` → expected FAILURE (red)

- [x] T3: Implement `JsonPath` (GREEN)
  - **Description**: Create `src/main/java/com/lucalzt/mctranslator/domain/model/JsonPath.java` as
    `public record JsonPath(String value)` with compact-constructor validation: `Objects.requireNonNull`
    (NPE), `isBlank()` → IAE, leading dot / trailing dot / consecutive dots → IAE; accessor `value()`;
    `boolean startsWith(String... segments)` per design §3.1 (split on `.`, compare leading segments
    case-sensitively; non-null array/elements, blank elements → IAE, empty varargs → true). Javadoc on the
    record, accessor and method (R5, `java-docs` skill). JDK + jspecify imports only (R2).
  - **Criteria**: `mvnw.cmd -Dtest=JsonPathTest test` → all green; Javadoc present on all public members.
  - **Requirements**: R5, R7, R16
  - **Dependencies**: T2
  - **Verification**: `mvnw.cmd -Dtest=JsonPathTest test` (green)

- [x] T4: Write `LanguageCodeTest` (RED)
  - **Description**: Create `LanguageCodeTest.java` (JUnit 6 + AssertJ) covering R8: `null` → NPE; `"   "` →
    IAE; `"es"` and `"spa_Latn"` accepted + `value()` accessor; equality by value.
  - **Criteria**: Test class exists referencing the missing `LanguageCode`; targeted run **FAILS** (red).
  - **Requirements**: R8, R16
  - **Dependencies**: T1
  - **Verification**: `mvnw.cmd -Dtest=LanguageCodeTest test` → expected FAILURE (red)

- [x] T5: Implement `LanguageCode` (GREEN)
  - **Description**: Create `LanguageCode.java` as `public record LanguageCode(String value)` with
    compact-constructor validation: non-null (NPE), non-blank (IAE). **No format restriction, no
    normalization** (design D6). Javadoc on record + accessor (R5).
  - **Criteria**: `mvnw.cmd -Dtest=LanguageCodeTest test` → green; Javadoc present.
  - **Requirements**: R5, R8, R16
  - **Dependencies**: T4
  - **Verification**: `mvnw.cmd -Dtest=LanguageCodeTest test` (green)

- [x] T6: Write `ModpackIdTest` (RED)
  - **Description**: Create `ModpackIdTest.java` (JUnit 6 + AssertJ) covering R9: `null` name / `null` version
    → NPE; blank name / blank version → IAE; happy path; equality by both components (equal pair equal + same
    hash; different version → not equal).
  - **Criteria**: Test class exists referencing the missing `ModpackId`; targeted run **FAILS** (red).
  - **Requirements**: R9, R16
  - **Dependencies**: T1
  - **Verification**: `mvnw.cmd -Dtest=ModpackIdTest test` → expected FAILURE (red)

- [x] T7: Implement `ModpackId` (GREEN)
  - **Description**: Create `ModpackId.java` as `public record ModpackId(String name, String version)` with
    compact-constructor validation: both components non-null (NPE) and non-blank (IAE). **No trimming**
    (design D6). Javadoc on record + accessors (R5).
  - **Criteria**: `mvnw.cmd -Dtest=ModpackIdTest test` → green; Javadoc present. **Slice A partial gate**: run
    `mvnw.cmd clean verify` — full suite must pass (T3/T5/T7 types + tests green, no regressions).
  - **Requirements**: R5, R9, R16
  - **Dependencies**: T6
  - **Verification**: `mvnw.cmd -Dtest=ModpackIdTest test` (green); then `mvnw.cmd clean verify`

- [x] T8: Write `TranslationStatusTest` (RED)
  - **Description**: Create `TranslationStatusTest.java` (JUnit 6 + AssertJ) covering R11: `values()` equals
    exactly, in order, `{CACHE_HIT, TRANSLATED_FAST, TRANSLATED_PRECISE, DEGRADED_TO_FAST,
    FALLBACK_TO_ORIGINAL}`.
  - **Criteria**: Test class exists referencing the missing `TranslationStatus`; targeted run **FAILS** (red).
  - **Requirements**: R11
  - **Dependencies**: T1
  - **Verification**: `mvnw.cmd -Dtest=TranslationStatusTest test` → expected FAILURE (red)

- [x] T9: Implement `TranslationStatus` (GREEN)
  - **Description**: Create `TranslationStatus.java` as a plain enum with exactly the five constants in spec
    order, no payload (design D3): `CACHE_HIT`, `TRANSLATED_FAST`, `TRANSLATED_PRECISE`, `DEGRADED_TO_FAST`,
    `FALLBACK_TO_ORIGINAL`. Javadoc on the enum and on each constant (R5).
  - **Criteria**: `mvnw.cmd -Dtest=TranslationStatusTest test` → green; Javadoc present; no other constants.
  - **Requirements**: R5, R11
  - **Dependencies**: T8
  - **Verification**: `mvnw.cmd -Dtest=TranslationStatusTest test` (green)

- [x] T10: Write `TranslationEngineTypeTest` (RED)
  - **Description**: Create `TranslationEngineTypeTest.java` (JUnit 6 + AssertJ) covering R12: `values()`
    equals exactly `{FAST, PRECISE}` in order.
  - **Criteria**: Test class exists referencing the missing `TranslationEngineType`; targeted run **FAILS**
    (red).
  - **Requirements**: R12
  - **Dependencies**: T1
  - **Verification**: `mvnw.cmd -Dtest=TranslationEngineTypeTest test` → expected FAILURE (red)

- [x] T11: Implement `TranslationEngineType` (GREEN)
  - **Description**: Create `TranslationEngineType.java` as a plain enum with exactly two constants: `FAST`,
    `PRECISE` (design D5, no serialization mapping). Javadoc on the enum and each constant (R5).
  - **Criteria**: `mvnw.cmd -Dtest=TranslationEngineTypeTest test` → green; Javadoc present; no other
    constants.
  - **Requirements**: R5, R12
  - **Dependencies**: T10
  - **Verification**: `mvnw.cmd -Dtest=TranslationEngineTypeTest test` (green)

- [x] T12: Write `GlossaryEntryClassificationTest` (RED)
  - **Description**: Create `GlossaryEntryClassificationTest.java` (JUnit 6 + AssertJ) covering R15: `values()`
    equals exactly `{AMBIGUOUS, LORE, PLAIN}` in order.
  - **Criteria**: Test class exists referencing the missing `GlossaryEntryClassification`; targeted run
    **FAILS** (red).
  - **Requirements**: R15
  - **Dependencies**: T1
  - **Verification**: `mvnw.cmd -Dtest=GlossaryEntryClassificationTest test` → expected FAILURE (red)

- [x] T13: Implement `GlossaryEntryClassification` (GREEN)
  - **Description**: Create `GlossaryEntryClassification.java` as a plain enum with exactly three constants:
    `AMBIGUOUS`, `LORE`, `PLAIN` (design D3/D5). Javadoc on the enum and each constant (R5).
  - **Criteria**: `mvnw.cmd -Dtest=GlossaryEntryClassificationTest test` → green; Javadoc present; no other
    constants. **Slice A gate**: `mvnw.cmd clean verify` passes (all 13 slice-A tasks green).
  - **Requirements**: R5, R15
  - **Dependencies**: T12
  - **Verification**: `mvnw.cmd -Dtest=GlossaryEntryClassificationTest test` (green); then `mvnw.cmd clean verify`

### Slice B — Identity records: `TranslationKey` + `GlossaryEntry` (PR #2)

- [x] T14: Write `TranslationKeyTest` (RED)
  - **Description**: Create `TranslationKeyTest.java` (JUnit 6 + AssertJ) covering R10 + R16: `null` path /
    `null` language / `null` modpack → NPE; `originalText` `""` and `"   "` → IAE; happy path; equality
    across all four components (identical keys equal + equal hashes; differing language → not equal).
  - **Criteria**: Test class exists referencing the missing `TranslationKey`; targeted run **FAILS** (red).
  - **Requirements**: R10, R16
  - **Dependencies**: T3, T5, T7
  - **Verification**: `mvnw.cmd -Dtest=TranslationKeyTest test` → expected FAILURE (red)

- [x] T15: Implement `TranslationKey` (GREEN)
  - **Description**: Create `TranslationKey.java` as
    `public record TranslationKey(JsonPath path, String originalText, LanguageCode targetLanguage, ModpackId modpack)`
    with compact-constructor validation: any null component → NPE; `originalText.isBlank()` → IAE (design
    §3.4, the modeled boundary of the deferred extraction policy). Identity = all four components. Javadoc on
    record + accessors (R5).
  - **Criteria**: `mvnw.cmd -Dtest=TranslationKeyTest test` → green; Javadoc present; no extra public members.
  - **Requirements**: R5, R10, R16
  - **Dependencies**: T14
  - **Verification**: `mvnw.cmd -Dtest=TranslationKeyTest test` (green)

- [x] T16: Write `GlossaryEntryTest` (RED)
  - **Description**: Create `GlossaryEntryTest.java` (JUnit 6 + AssertJ) covering R14 + R16: `null` term /
    `null` translation / `null` classification → NPE; blank term / blank translation → IAE; happy path;
    equality by all three components.
  - **Criteria**: Test class exists referencing the missing `GlossaryEntry`; targeted run **FAILS** (red).
  - **Requirements**: R14, R16
  - **Dependencies**: T13
  - **Verification**: `mvnw.cmd -Dtest=GlossaryEntryTest test` → expected FAILURE (red)

- [x] T17: Implement `GlossaryEntry` (GREEN)
  - **Description**: Create `GlossaryEntry.java` as
    `public record GlossaryEntry(String term, String translation, GlossaryEntryClassification classification)`
    with compact-constructor validation: any null component → NPE; blank term/translation → IAE. Javadoc on
    record + accessors (R5). **Slice B gate**: `mvnw.cmd clean verify` passes.
  - **Criteria**: `mvnw.cmd -Dtest=GlossaryEntryTest test` → green; Javadoc present; full suite green.
  - **Requirements**: R5, R14, R16
  - **Dependencies**: T16
  - **Verification**: `mvnw.cmd -Dtest=GlossaryEntryTest test` (green); then `mvnw.cmd clean verify`

### Slice C — `TranslationResult` + package gates (PR #3)

- [x] T18: Write `TranslationResultTest` (RED)
  - **Description**: Create `TranslationResultTest.java` (JUnit 6 + AssertJ) covering R13 (3 scenarios), R11
    scenario 2, R17, R16 and design D4 shape: `null` key / translatedText / status / duration → NPE; negative
    `duration` → IAE; **zero duration accepted**; **total-failure scenario** (`FALLBACK_TO_ORIGINAL`,
    translatedText equals `key.originalText()`, non-null warning, engine null); **cache-hit scenario**
    (`CACHE_HIT`, null engine, key/status/translatedText/duration preserved); **five-outcomes test** (one
    result per status, status preserved); per-status engine/warning shape (CACHE_HIT: null/null;
    TRANSLATED_FAST: FAST/absent; TRANSLATED_PRECISE: PRECISE/absent; DEGRADED_TO_FAST: FAST/message;
    FALLBACK_TO_ORIGINAL: null/message); equality across components.
  - **Criteria**: Test class exists referencing the missing `TranslationResult`; targeted run **FAILS** (red).
  - **Requirements**: R11, R13, R16, R17
  - **Dependencies**: T15, T9, T3, T5, T7
  - **Verification**: `mvnw.cmd -Dtest=TranslationResultTest test` → expected FAILURE (red)

- [x] T19: Implement `TranslationResult` (GREEN)
  - **Description**: Create `TranslationResult.java` as a record with exactly the R13 components:
    `TranslationKey key`, `String translatedText`, `TranslationStatus status`,
    `@Nullable TranslationEngineType engine`, `@Nullable String warning`, `Duration duration`. Compact
    constructor validates exactly the R13 list: non-null key/translatedText/status/duration → NPE;
    `duration.isNegative()` → IAE (zero allowed). `engine`/`warning` are **not** null-checked (they are the
    package's only `@Nullable` elements — R4; component annotation propagates to accessor/ctor/field). Javadoc
    documents the per-status nullability table and the `FALLBACK_TO_ORIGINAL` behavioral contract (design
    §3.7, D4). No factories (D2). JDK + jspecify imports only (R2).
  - **Criteria**: `mvnw.cmd -Dtest=TranslationResultTest test` → green; Javadoc present; exactly 6 components.
  - **Requirements**: R2, R4, R5, R13, R16, R17
  - **Dependencies**: T18
  - **Verification**: `mvnw.cmd -Dtest=TranslationResultTest test` (green)

- [x] T20: Create `package-info.java` with `@NullMarked` + package Javadoc
  - **Description**: Create `src/main/java/com/lucalzt/mctranslator/domain/model/package-info.java` carrying
    `@org.jspecify.annotations.NullMarked` and a Javadoc package description stating the package role (pure
    domain layer of the translation pipeline, zero framework dependencies; the only `@Nullable` elements are
    `TranslationResult.engine` and `TranslationResult.warning`).
  - **Criteria**: File exists; `@NullMarked` annotation present; package Javadoc describes the role;
    `mvnw.cmd -q compile` succeeds with the annotation resolving.
  - **Requirements**: R4, R5
  - **Dependencies**: T19
  - **Verification**: `mvnw.cmd -q compile`; inspect the file for the annotation + Javadoc

- [x] T21: Run structural verification greps (R1 / R2 / R3 / deferred VOs absent)
  - **Description**: Run the verification-time checks (not JUnit): (a) all 9 public types + `package-info`
    live under `src/main/java/com/lucalzt/mctranslator/domain/model/` and none under any `com.mctranslator`
    package (R1); (b) grep the package sources for `import org.springframework`, `import jakarta`, or any
    non-JDK / non-jspecify import → **zero matches** (R2); (c) public types enumerate to exactly the 9 (R3),
    and `MaskedText` / `GlossaryTermMatch` / `CacheKey` are absent from the package sources (R3 scenario 2,
    R17 no output model).
  - **Criteria**: All three greps pass with zero offending matches; the 9-type inventory is exact.
  - **Requirements**: R1, R2, R3, R17
  - **Dependencies**: T3, T5, T7, T9, T11, T13, T15, T17, T19, T20
  - **Verification**: `rg "import (org\.springframework|jakarta)" src/main/java/com/lucalzt/mctranslator/domain/model` → no matches; `rg "MaskedText|GlossaryTermMatch|CacheKey" src/main/java/com/lucalzt/mctranslator/domain/model` → no matches; enumerate public types under the package

- [x] T22: Full build gate — `mvnw.cmd clean verify`
  - **Description**: Run the complete build on Windows (`mvnw.cmd clean verify`; `./mvnw clean verify` on
    Linux/macOS). All existing and new tests (JUnit 6 + AssertJ, no Spring context for the domain tests) must
    pass: zero test failures, zero compilation errors, exit code 0. **Slice C gate / final gate.**
  - **Criteria**: `BUILD SUCCESS`; exit code 0; all 9 test classes + existing project tests green.
  - **Requirements**: R18, R19
  - **Dependencies**: T21
  - **Verification**: `mvnw.cmd clean verify` → `BUILD SUCCESS` (exit 0)
