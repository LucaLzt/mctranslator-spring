# Verify Report: Domain Models & Value Objects (`domain-models`)

## Overview

- **Change**: `domain-models`
- **Verified against**: `proposal.md`, `specs/domain-model/spec.md` (+ `spec.yaml`), `design.md`, `tasks.md`
- **Date**: 2026-08-08
- **Verifier**: `sdd-verify` executor (big-pickle)
- **Result**: **PASS** — 0 CRITICAL, 1 WARNING, 2 SUGGESTION. Final gate `.\mvnw.cmd clean verify` → `BUILD SUCCESS`, exit code 0, 41/41 tests green.

---

## Requirement Traceability

| Req ID | Requirement | Status | Evidence |
|---|---|---|---|
| R1 | Real base package `com.lucalzt.mctranslator.domain.model`; no `com.mctranslator` | **PASS** | All 10 files under `src/main/java/com/lucalzt/mctranslator/domain/model/` (package-info + 9 types); repo Grep `com\.mctranslator` over `src/main` → 0 matches; every source declares `package com.lucalzt.mctranslator.domain.model;` |
| R2 | Zero framework imports in the domain package | **PASS** | Grep `^import` over the package → only `java.time.Duration` (`TranslationResult.java:3`), `java.util.Objects` (5 files), `org.jspecify.annotations.Nullable` (`TranslationResult.java:6`). No `org.springframework.*`, no `jakarta.*` |
| R3 | Exactly 9 public types; deferred VOs `MaskedText`, `GlossaryTermMatch`, `CacheKey` absent | **PASS** | Grep `public (record\|class\|enum\|interface)` → exactly 9 (3 records `TranslationKey`/`TranslationResult`/`GlossaryEntry`, 3 VOs `JsonPath`/`LanguageCode`/`ModpackId`, 3 enums `TranslationStatus`/`TranslationEngineType`/`GlossaryEntryClassification`); grep `MaskedText\|GlossaryTermMatch\|CacheKey` over package → 0 matches |
| R4 | `package-info.java` with `@NullMarked` + package role Javadoc; `@Nullable` only on `TranslationResult` engine/warning | **PASS** | `package-info.java:12` `@org.jspecify.annotations.NullMarked` + Javadoc (lines 1–11) stating pure domain role; grep `@Nullable` → only `TranslationResult.java:42` (record components), `:97`, `:109` (accessor overrides) |
| R5 | Javadoc on all public types and members | **PASS** | Manual review of all 9 types: each record/enum has type Javadoc, each compact constructor and accessor has Javadoc with `@param`/`@return`/`@throws`, each enum constant has Javadoc; summaries end with a period |
| R6 | `org.jspecify:jspecify` compile-scope dependency | **PASS** | `pom.xml:24–28` declares `org.jspecify:jspecify` `1.0.1` exactly once; `mvnw.cmd dependency:tree -Dincludes=org.jspecify:jspecify` → `org.jspecify:jspecify:jar:1.0.1:compile`; `clean verify` compiles without warnings |
| R7 | `JsonPath` VO: validation (null NPE; blank/empty segments IAE), raw accessor, `startsWith(String...)` exact-prefix semantics | **PASS** | `JsonPath.java:24–32` compact ctor (requireNonNull → NPE; `isBlank()`/leading dot/trailing dot/`contains("..")` → IAE); `value()` accessor; `startsWith` (lines 61–77) splits on `.` and compares leading segments case-sensitively, empty varargs → true, null varargs/element → NPE, blank segment → IAE. Tests: `JsonPathTest` 10/10 (incl. all five invalid values of the R7 scenario, prefix matching, case-sensitivity, method-contract guards). **Spec scenario corrected** — see Contradictions |
| R8 | `LanguageCode` VO: non-null, non-blank, no format restriction/normalization | **PASS** | `LanguageCode.java:23–28` (requireNonNull → NPE; `isBlank()` → IAE; no normalization); tests `LanguageCodeTest` 4/4 (`es`, `spa_Latn` accepted as-is; null → NPE; blank → IAE; equality) |
| R9 | `ModpackId` VO: name + version, non-null/non-blank, equality by both components | **PASS** | `ModpackId.java:25–34` (both components requireNonNull → NPE; blank → IAE; no trimming); tests `ModpackIdTest` 4/4 (nulls, blanks, equality by both components) |
| R10 | `TranslationKey` record: identity = path + originalText + targetLanguage + modpack; any null → NPE; blank originalText → IAE | **PASS** | `TranslationKey.java:26–34` (all 4 components requireNonNull → NPE; `originalText.isBlank()` → IAE); tests `TranslationKeyTest` 4/4 (null path/language/modpack → NPE; `""`/`"   "` originalText → IAE; equality across all 4 components, differing language → not equal) |
| R11 | `TranslationStatus` enum: exactly 5 constants, all outcomes representable | **PASS** | `TranslationStatus.java:10–25` — exactly `CACHE_HIT, TRANSLATED_FAST, TRANSLATED_PRECISE, DEGRADED_TO_FAST, FALLBACK_TO_ORIGINAL` in spec order, no payload; `TranslationStatusTest` `containsExactly`; `TranslationResultTest.fiveOutcomesAreRepresentable` constructs a result per status and asserts preservation |
| R12 | `TranslationEngineType` enum: exactly `FAST`, `PRECISE` | **PASS** | `TranslationEngineType.java:9–15`; `TranslationEngineTypeTest` `containsExactly(FAST, PRECISE)` |
| R13 | `TranslationResult` record: key/translatedText/status/duration non-null (NPE); negative duration IAE (zero allowed); `@Nullable` engine/warning per-status contract; `FALLBACK_TO_ORIGINAL` behavioral contract | **PASS** | `TranslationResult.java:41–58` (6 components; ctor validates exactly the R13 list; engine/warning not null-checked); Javadoc documents the per-status table and the fallback contract (lines 8–39); `@Nullable` on record components + accessor overrides. Tests `TranslationResultTest` 9/9: null key/translatedText/status/duration → NPE; negative duration → IAE; zero duration accepted; total-failure (`translatedText == key.originalText()`, warning non-null, engine null); cache-hit (null engine, others preserved); per-status engine/warning shape; equality |
| R14 | `GlossaryEntry` record: term/translation/classification, nulls NPE, blanks IAE, equality by all 3 components | **PASS** | `GlossaryEntry.java:25–35` (all components requireNonNull → NPE; blank term/translation → IAE); tests `GlossaryEntryTest` 4/4 |
| R15 | `GlossaryEntryClassification` enum: exactly `AMBIGUOUS`, `LORE`, `PLAIN` | **PASS** | `GlossaryEntryClassification.java:9–18`; `GlossaryEntryClassificationTest` `containsExactly` |
| R16 | Value equality and immutability for all records | **PASS** | All 6 records are Java records (components final, no mutators, record-generated `equals`/`hashCode` over all components); equality + hash-code tests in every record test class (`JsonPathTest`, `LanguageCodeTest`, `ModpackIdTest`, `TranslationKeyTest`, `TranslationResultTest`, `GlossaryEntryTest`) |
| R17 | String-leaf rule stated; no output document model; per-key output unit is `TranslationResult` | **PASS** | Grep `OutputDocument\|TranslationDocument\|JsonTree` over `src/main` → 0 matches; public-type inventory is exactly the 9 types of R3; `TranslationResult` is the only per-key output type (design §3.7, decision 8); string-leaf rule documented in `TranslationKey` Javadoc and spec prose (extraction logic deferred per Non-Goals) |
| R18 | Unit tests in `src/test/java/com/lucalzt/mctranslator/domain/model/`, JUnit 6 + AssertJ, no Spring context, red-first, covering validation/equality/outcomes | **PASS** | 9 test classes in the mirror package (38 domain tests); grep for `org.springframework\|@SpringBootTest\|@ExtendWith\|Mockito` over the test dir → 0 matches; imports are `org.junit.jupiter` + `org.assertj` only; red-first evidenced by 10 test→feat commit pairs in `git log` (see Strict TDD Findings); coverage includes validation rejection, value equality, `JsonPath` prefix matching, and one test per `TranslationStatus` outcome |
| R19 | `mvnw.cmd clean verify` passes, zero failures, exit code 0 | **PASS** | Re-run on 2026-08-08: `.\mvnw.cmd clean verify` → `BUILD SUCCESS`; `Tests run: 41, Failures: 0, Errors: 0, Skipped: 0` (38 domain + 2 `TranslateCommandTests` + 1 `MctranslatorApplicationTests`); jar repackage OK |

**Traceability completeness**: all 19 requirements mapped to code/tests/probes; zero `EVIDENCE-MISSING` rows.

---

## Task Status

| Task | Status | Evidence |
|---|---|---|
| T1 pom jspecify 1.0.1 | complete | `pom.xml:24–28`; `dependency:tree` shows `jspecify:jar:1.0.1:compile`; commit `b6d0bdd` |
| T2 `JsonPathTest` (red) | complete | Test exists; red state = compilation failure of missing type (per tasks.md criteria); commit `a59bb82`; apply-progress records FAILED targeted run |
| T3 `JsonPath` (green) | complete | 10/10 green; commit `b34d6ff` |
| T4 `LanguageCodeTest` (red) | complete | commit `4ecac82`; red confirmed |
| T5 `LanguageCode` (green) | complete | 4/4 green; commit `010a8a0` |
| T6 `ModpackIdTest` (red) | complete | commit `17b2a8b`; red confirmed |
| T7 `ModpackId` (green) | complete | 4/4 green; slice-A partial gate green (21/21); commit `afeee45` |
| T8 `TranslationStatusTest` (red) | complete | commit `57a7b5c`; red confirmed |
| T9 `TranslationStatus` (green) | complete | 1/1 green; commit `b991dbc` |
| T10 `TranslationEngineTypeTest` (red) | complete | commit `2a423a2`; red confirmed |
| T11 `TranslationEngineType` (green) | complete | 1/1 green; commit `888ff0e` |
| T12 `GlossaryEntryClassificationTest` (red) | complete | commit `9d8a2a8`; red confirmed |
| T13 `GlossaryEntryClassification` (green) | complete | 1/1 green; Slice A gate green (24/24); commit `5e5c92e` |
| T14 `TranslationKeyTest` (red) | complete | commit `27ae611`; red confirmed |
| T15 `TranslationKey` (green) | complete | 4/4 green; commit `d3335a6` |
| T16 `GlossaryEntryTest` (red) | complete | commit `175f933`; red confirmed |
| T17 `GlossaryEntry` (green) | complete | 4/4 green; Slice B gate green (32/32); commit `d3a0959` |
| T18 `TranslationResultTest` (red) | complete | commit `d88f9ea`; red confirmed |
| T19 `TranslationResult` (green) | complete | 9/9 green; commit `29a7429` |
| T20 `package-info.java` `@NullMarked` | complete | File present with `@NullMarked` + role Javadoc; `-q compile` OK; commit `bb203ec` |
| T21 structural greps (R1/R2/R3/deferred VOs) | complete | Independently re-run during this verify: R1/R2/R3/R17 all pass (see traceability) |
| T22 full build gate | complete | **Independently re-run during this verify**: `.\mvnw.cmd clean verify` → `BUILD SUCCESS`, exit 0, 41/41 |

All 22/22 tasks verified complete; the three chained-PR gates (Slice A 24/24, Slice B 32/32, Slice C/final 41/41) are corroborated by the final re-run.

---

## Checks Run

- [x] Build: `.\mvnw.cmd clean verify` → `BUILD SUCCESS`, exit code 0 (2026-08-08T00:44:23-03:00; 11.8 s)
- [x] Unit tests: 41 run / 41 passed / 0 failed / 0 skipped (9 domain test classes = 38 tests, no Spring context)
- [x] Integration/context tests (pre-existing): `TranslateCommandTests` 2/2, `MctranslatorApplicationTests` 1/1 — pass (expected pre-existing `NonInteractiveShellRunner` ERROR log and Mockito self-attach warning are not failures)
- [x] Static analysis (structural greps, re-run this verify):
  - `^import` over package → JDK + jspecify only (R2)
  - `public (record|class|enum|interface)` → exactly 9 types (R3)
  - `MaskedText|GlossaryTermMatch|CacheKey` → 0 (R3, R17)
  - `com\.mctranslator` over `src/main` → 0 (R1)
  - `@Nullable` → only `TranslationResult` engine/warning (R4)
  - output-document names → 0 (R17)
- [x] Dependency probe: `mvnw.cmd dependency:tree "-Dincludes=org.jspecify:jspecify"` → `org.jspecify:jspecify:jar:1.0.1:compile` (R6)
- [ ] Manual runtime probes: not applicable (pure-Java model, no I/O behavior)

---

## Contradictions

1. **R7 `JsonPath.startsWith` spec scenario (corrected — documented spec fix, not an implementation defect)**:
   The original scenario table expected `("quest","advancement")` on `"quest.advancement"` → `false`, which is
   impossible under the normative prose (R7 + design §3.1: `startsWith` returns `true` for equal-length exact
   prefixes; the sibling case in the same scenario — `("item")` on `"item.sword"` — is also equal-length and
   expects `true`; no consistent rule makes both hold). The implementation follows the prose (exact-prefix →
   `true`, pinned by `JsonPathTest.matchesLeadingSegmentsInOrder`), with the meaningful negative case
   `("quest","description")` on `"quest.advancement"` → `false` (matches heuristic rule 1 `quest.description.*`,
   pinned by `JsonPathTest.rejectsMismatchingSegments`). **The spec scenario has been corrected to match the
   implemented contract** (`specs/domain-model/spec.md`, "Prefix matching" scenario): the negative case now reads
   `("quest","description")` on `"quest.advancement"` → `false`, and the exact-prefix positive
   `("quest","advancement")` → `true` is stated explicitly. This was agreed with the user before verification.
2. **Residual documentation staleness (WARNING W1)**: `design.md` §6 test-table row and `tasks.md` T2 still
   carry the original contradictory wording (`("quest","advancement")` → `false` on `"quest.advancement"`).
   These are planning-phase records superseded by the corrected spec; `apply-progress.md` deviation 1 documents
   the deviation. No code or test impact. **Resolution**: sync the wording during `/sdd-archive`.
3. No implementation-vs-spec contradictions remain after the correction.

---

## Strict TDD Findings

`openspec/config.yaml` → `rules.tasks` requires strict TDD ("tests red before production code"). Evidence from
`git log` (20 commits) and `apply-progress.md`:

- 10 red→green pairs, one per testable capability, each with a `test(domain)` commit followed by the `feat(domain)`
  commit: `JsonPath` (a59bb82→b34d6ff), `LanguageCode` (4ecac82→010a8a0), `ModpackId` (17b2a8b→afeee45),
  `TranslationStatus` (57a7b5c→b991dbc), `TranslationEngineType` (2a423a2→888ff0e),
  `GlossaryEntryClassification` (9d8a2a8→5e5c92e), `TranslationKey` (27ae611→d3335a6),
  `GlossaryEntry` (175f933→d3a0959), `TranslationResult` (d88f9ea→29a7429), plus the `package-info` close
  (bb203ec) and the pom dependency (b6d0bdd).
- Red states were **compilation failures** of the not-yet-existing types ("cannot find symbol"), which
  `tasks.md` T2/T4/… criteria explicitly count as red — compliant.
- Per-task red states leave the tree red between pairs by design (stacked-to-main chain); each slice gate ran on
  the green head (24/24, 32/32, 41/41) — confirmed by the final re-run.
- **PASS** — strict-TDD loop is evidenced end-to-end.

---

## Native Validation

- `native_validation: skipped (binary unavailable)` — no native SDD validator installed; verification is manual
  per `sdd-phase-common.md`.

---

## Findings

### CRITICAL (0)

None. The only known contradiction (R7 scenario) was corrected in the spec and is reported above as a documented
spec fix; no unaddressed CRITICAL remains.

### WARNING (1)

- **W1 — Stale R7 wording in `design.md` §6 and `tasks.md` T2**: both still describe the contradictory
  `("quest","advancement")` → `false` expectation. Documentation-only (planning records superseded by the
  corrected spec and by `apply-progress.md` deviation 1); zero code/test impact. Resolve by syncing the wording
  during `/sdd-archive`.

### SUGGESTION (2)

- **S1 — `design.md` §3.10 snippet order**: the design snippet shows `@NullMarked` before the package Javadoc;
  the implementation uses the required package-info order (Javadoc first, annotation after). No semantic impact
  (`apply-progress.md` note 6); sync the snippet during archive.
- **S2 — Javadoc not build-gated**: per design decision D9, Javadoc completeness relies on code review; there is
  no `maven-javadoc-plugin` gate. Consider an optional `mvnw.cmd javadoc:javadoc` check for future changes.

---

## Verdict

- **status**: **PASS**
- **criticalIssues**: 0 (archive gate satisfied)
- **Summary**: The `domain-models` change is fully verified. All 19 requirements trace to concrete code, tests,
  and probes with no missing evidence; all 22 tasks are complete; the independent re-run of the final gate
  `.\mvnw.cmd clean verify` → `BUILD SUCCESS` (41/41 tests, exit 0). Structural gates hold: exactly 9 public
  types + `package-info.java` in `com.lucalzt.mctranslator.domain.model`, zero framework imports, deferred VOs
  absent, `@Nullable` limited to `TranslationResult.engine`/`warning`. The single known contradiction (R7
  scenario) was corrected in the spec to match the implemented exact-prefix contract (documented spec fix). One
  documentation-only WARNING (design/tasks stale wording) and two SUGGESTIONs remain; none blocks archive.
