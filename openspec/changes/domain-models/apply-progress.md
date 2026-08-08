# Apply Progress: Domain Models & Value Objects (`domain-models`)

## Status

**Slice A (PR #1) — COMPLETE.** Tasks T1–T13 implemented, red-first, with per-task
work-unit commits. Slice A gate green: `mvnw.cmd clean verify` → `BUILD SUCCESS`,
exit code 0, 24/24 tests green (21 new domain tests + 2 `TranslateCommandTests` +
1 `MctranslatorApplicationTests`).

**Slice B (PR #2) — COMPLETE.** Tasks T14–T17 implemented, red-first, with per-task
work-unit commits. Slice B gate green: `mvnw.cmd clean verify` → `BUILD SUCCESS`,
exit code 0, 32/32 tests green (24 prior + 4 `TranslationKeyTest` + 4
`GlossaryEntryTest`).

Slice C (T18–T22) — **COMPLETE.** Tasks T18–T22 implemented, red-first, with
per-task work-unit commits. Slice C gate green: `mvnw.cmd clean verify` →
`BUILD SUCCESS`, exit code 0, **41/41 tests green** (32 prior + 9
`TranslationResultTest`). This was the last chained PR — the `domain-models`
change is fully implemented; only verify + archive phases remain.

## Run Summary

| Slice | Scope | Result |
|---|---|---|
| A | T1–T13 (pom jspecify; `JsonPath`, `LanguageCode`, `ModpackId`; `TranslationStatus`, `TranslationEngineType`, `GlossaryEntryClassification` + tests) | ✅ Done, gate green |
| B | T14–T17 (`TranslationKey`, `GlossaryEntry` + tests) | ✅ Done, gate green |
| C | T18–T22 (`TranslationResult` + test, `package-info.java` `@NullMarked`, structural greps, final `clean verify`) | ✅ Done, final gate green |

## TDD Loop (per task)

### Slice A

| Task | Red | Green | Verification |
|---|---|---|---|
| T1 pom jspecify 1.0.1 | n/a (dependency) | n/a | `dependency:tree` shows `org.jspecify:jspecify:1.0.1:compile`; `-q compile` OK |
| T2 `JsonPathTest` | ❌ cannot find symbol: JsonPath | — | `-Dtest=JsonPathTest test` FAILED (red) |
| T3 `JsonPath` | — | ✅ 10/10 | `-Dtest=JsonPathTest test` BUILD SUCCESS |
| T4 `LanguageCodeTest` | ❌ cannot find symbol: LanguageCode | — | red |
| T5 `LanguageCode` | — | ✅ 4/4 | green |
| T6 `ModpackIdTest` | ❌ cannot find symbol: ModpackId | — | red |
| T7 `ModpackId` | — | ✅ 4/4 | green + **partial gate** `clean verify` 21/21 |
| T8 `TranslationStatusTest` | ❌ cannot find symbol | — | red |
| T9 `TranslationStatus` | — | ✅ 1/1 | green |
| T10 `TranslationEngineTypeTest` | ❌ cannot find symbol | — | red |
| T11 `TranslationEngineType` | — | ✅ 1/1 | green |
| T12 `GlossaryEntryClassificationTest` | ❌ cannot find symbol | — | red |
| T13 `GlossaryEntryClassification` | — | ✅ 1/1 | green + **Slice A gate** `clean verify` 24/24, exit 0 |

### Slice B

| Task | Red | Green | Verification |
|---|---|---|---|
| T14 `TranslationKeyTest` | ❌ cannot find symbol: TranslationKey | — | `-Dtest=TranslationKeyTest test` FAILED (red) |
| T15 `TranslationKey` | — | ✅ 4/4 | `-Dtest=TranslationKeyTest test` BUILD SUCCESS |
| T16 `GlossaryEntryTest` | ❌ cannot find symbol: GlossaryEntry | — | `-Dtest=GlossaryEntryTest test` FAILED (red) |
| T17 `GlossaryEntry` | — | ✅ 4/4 | `-Dtest=GlossaryEntryTest test` green + **Slice B gate** `clean verify` 32/32, exit 0 |

### Slice C

| Task | Red | Green | Verification |
|---|---|---|---|
| T18 `TranslationResultTest` | ❌ cannot find symbol: TranslationResult | — | `-Dtest=TranslationResultTest test` FAILED (red) |
| T19 `TranslationResult` | — | ✅ 9/9 | `-Dtest=TranslationResultTest test` BUILD SUCCESS |
| T20 `package-info.java` | n/a (no behavior) | n/a | `mvnw.cmd -q compile` exit 0; file carries `@NullMarked` + package Javadoc |
| T21 structural greps | n/a (verification-only) | n/a | R1: 10 files = package-info + 9 types, zero `com.mctranslator` packages; R2: zero `org.springframework`/`jakarta` imports, all imports JDK/jspspecify; R3: zero `MaskedText`/`GlossaryTermMatch`/`CacheKey` |
| T22 full build | n/a | n/a | **Slice C / final gate** `clean verify` 41/41, exit 0 |

## Files Created/Changed (Slice A)

Main (new package `com.lucalzt.mctranslator.domain.model`):
- `src/main/java/com/lucalzt/mctranslator/domain/model/JsonPath.java` — record VO, compact-constructor validation (NPE/IAE), `startsWith(String...)` prefix matching
- `src/main/java/com/lucalzt/mctranslator/domain/model/LanguageCode.java` — record VO, NPE/IAE validation, no normalization
- `src/main/java/com/lucalzt/mctranslator/domain/model/ModpackId.java` — record VO (name, version), NPE/IAE validation, no trimming
- `src/main/java/com/lucalzt/mctranslator/domain/model/TranslationStatus.java` — enum, exactly 5 constants in spec order
- `src/main/java/com/lucalzt/mctranslator/domain/model/TranslationEngineType.java` — enum, exactly 2 constants
- `src/main/java/com/lucalzt/mctranslator/domain/model/GlossaryEntryClassification.java` — enum, exactly 3 constants

Tests:
- `src/test/java/com/lucalzt/mctranslator/domain/model/{JsonPathTest,LanguageCodeTest,ModpackIdTest,TranslationStatusTest,TranslationEngineTypeTest,GlossaryEntryClassificationTest}.java`

Changed:
- `pom.xml` — added `org.jspecify:jspecify` 1.0.1 (compile scope)

NOT created (Slice B/C scope): `TranslationKey`, `GlossaryEntry`, `TranslationResult`, `package-info.java`.

## Files Created/Changed (Slice B)

Main:
- `src/main/java/com/lucalzt/mctranslator/domain/model/TranslationKey.java` — record identity (path, originalText, targetLanguage, modpack); compact-constructor validation: any null component → NPE, blank `originalText` → IAE; Javadoc on record + 4 accessors; JDK-only import (`java.util.Objects`)
- `src/main/java/com/lucalzt/mctranslator/domain/model/GlossaryEntry.java` — record (term, translation, classification); compact-constructor validation: any null component → NPE, blank term/translation → IAE; Javadoc on record + 3 accessors; JDK-only import (`java.util.Objects`)

Tests:
- `src/test/java/com/lucalzt/mctranslator/domain/model/TranslationKeyTest.java` — 4 tests (happy path + accessors, null path/language/modpack → NPE, blank originalText → IAE, equality across all 4 components with differing language → not equal)
- `src/test/java/com/lucalzt/mctranslator/domain/model/GlossaryEntryTest.java` — 4 tests (happy path + accessors, null term/translation/classification → NPE, blank term/translation → IAE, equality by all 3 components with differing classification → not equal)

NOT created (Slice C scope): `TranslationResult`, `package-info.java`.

## Files Created/Changed (Slice C)

Main:
- `src/main/java/com/lucalzt/mctranslator/domain/model/TranslationResult.java` — record
  `(TranslationKey key, String translatedText, TranslationStatus status, @Nullable TranslationEngineType engine, @Nullable String warning, Duration duration)`; compact-constructor validation exactly per R13: non-null key/translatedText/status/duration → NPE, `duration.isNegative()` → IAE (zero allowed); `engine`/`warning` NOT null-checked (the package's only `@Nullable` elements, R4); Javadoc on record (with the per-status engine/warning table + `FALLBACK_TO_ORIGINAL` behavioral contract, design §3.7/D4), compact ctor, all 6 accessors (with explicit `@Nullable` on `engine()`/`warning()` overrides); JDK + jspecify imports only (R2)
- `src/main/java/com/lucalzt/mctranslator/domain/model/package-info.java` — `@org.jspecify.annotations.NullMarked` + package role Javadoc (R4); note: annotation placed AFTER the Javadoc (the correct package-info form) — design §3.10's snippet shows the reverse order, corrected here

Tests:
- `src/test/java/com/lucalzt/mctranslator/domain/model/TranslationResultTest.java` — 9 tests (happy path + 6 accessors; null key/translatedText/status/duration → NPE; negative duration → IAE; zero duration accepted; total-failure scenario R13 s2 — translatedText == key.originalText(), warning non-null, engine null; cache-hit scenario R13 s3 — null engine + other components preserved; five-outcomes R11 s2 — each status representable and preserved; per-status engine/warning shape D4; equality by all 6 components with hashes)

No files changed outside the package; `openspec/` artifacts remain untracked (consistent with prior slices).

## Commits (20, one per task, conventional)

```
b6d0bdd chore(deps): add jspecify 1.0.1
a59bb82 test(domain): add JsonPathTest (red)
b34d6ff feat(domain): implement JsonPath value object
4ecac82 test(domain): add LanguageCodeTest (red)
010a8a0 feat(domain): implement LanguageCode value object
17b2a8b test(domain): add ModpackIdTest (red)
afeee45 feat(domain): implement ModpackId value object
57a7b5c test(domain): add TranslationStatusTest (red)
b991dbc feat(domain): implement TranslationStatus enum
2a423a2 test(domain): add TranslationEngineTypeTest (red)
888ff0e feat(domain): implement TranslationEngineType enum
9d8a2a8 test(domain): add GlossaryEntryClassificationTest (red)
5e5c92e feat(domain): implement GlossaryEntryClassification enum
27ae611 test(domain): add TranslationKeyTest (red)
d3335a6 feat(domain): implement TranslationKey record
175f933 test(domain): add GlossaryEntryTest (red)
d3a0959 feat(domain): implement GlossaryEntry record
d88f9ea test(domain): add TranslationResultTest (red)
29a7429 feat(domain): implement TranslationResult record
bb203ec feat(domain): add @NullMarked package-info
```

## Deviations & Notes

1. **JsonPath scenario contradiction (reported as risk)**: spec R7's scenario and
   design §6 table claim `"quest.advancement".startsWith("quest", "advancement")`
   → `false`, but the normative prose (R7 + design §3.1) defines `startsWith` as
   true for equal-length exact prefixes, and the sibling case in the same scenario
   (`"item.sword"` vs `("item")`) is equal-length too and expects `true` — no
   consistent rule makes both hold. Implemented per the prose: exact-prefix → `true`;
   the meaningful negative case (`"quest.advancement"` vs `("quest","description")`,
   matching heuristic rule 1 `quest.description.*`) is pinned `false`.
2. Strict TDD red states were **compilation failures** of the not-yet-existing
   types ("cannot find symbol"), which counts as red per tasks.md criteria.
3. Per-task commits leave the tree red between the `test(domain)` and
   `feat(domain)` commits of each pair — expected for the stacked-to-main chain;
   each slice gate runs on the green head.
4. `openspec/changes/domain-models/` artifacts (spec/design/tasks/apply-progress)
   remain untracked, consistent with how the earlier phases left them; not part of
   the slice's code diff.
5. Slice B followed the exact Slice A conventions: tabs, Javadoc on record +
   compact constructor + accessors, `Objects.requireNonNull` with message, blank →
   IAE, test style `@Test` + `@DisplayName` + AssertJ with class-level Javadoc.
   `TranslationKey` rejects nulls on ALL four components (incl. `originalText`,
   per R10 "any null component" + design §3.4), and blank only on `originalText`
   (per R10 — path/language/modpack blanks are their own VOs' invariants).
6. Slice C followed the same conventions. Two notes:
   - `package-info.java` places the `@NullMarked` annotation AFTER the Javadoc
     comment (the required package-info form — a doc comment must immediately
     precede the `package` declaration); design §3.10's snippet shows the reverse
     order, which would detach the Javadoc. Corrected here; no semantic impact.
   - The record components `engine`/`warning` carry `@Nullable` (propagates to the
     implicit members); the explicitly overridden accessors `engine()`/`warning()`
     re-declare `@Nullable` explicitly — compiles cleanly with jspecify 1.0.1.
   - Javadoc uses an HTML `<table>` for the per-status engine/warning contract
     (design §3.7). No `maven-javadoc-plugin` in the build (D9), so no doclint gate.
7. `rg` (ripgrep) is NOT on PATH on this Windows machine — T21's grep checks were
   executed with the equivalent repository Grep tool; all patterns returned zero
   matches (R1/R2/R3/R17 pass).

## Build Environment Notes

- On Windows PowerShell, invoke the wrapper as `.\mvnw.cmd` (bare `mvnw.cmd` is not
  resolved). `mvnw.cmd clean verify` passes on the green head.
- Existing suite prints expected Spring Shell `NonInteractiveShellRunner` ERROR log
  and a Mockito self-attach warning — pre-existing, not failures.

## Rollback Boundary

**Slice A**: revert commits b6d0bdd..5e5c92e → clean deletion: new package (6 types
+ 6 test classes) + one dependency in `pom.xml`. Nothing else references these types.

**Slice B**: revert commits 27ae611..d3a0959 → clean deletion: `TranslationKey` +
`GlossaryEntry` (2 types) + 2 test classes. `TranslationResult` does not exist yet
in the chain, so zero dependents.

**Slice C**: revert commits d88f9ea..bb203ec → clean deletion: `TranslationResult`
+ `package-info.java` + 1 test class (new files only, zero dependents). Full-chain
revert = delete all of `domain/model` + revert the `pom.xml` jspecify line.

## Next

The `domain-models` change is fully implemented across its 3 chained PRs
(A: b6d0bdd..5e5c92e, B: 27ae611..d3a0959, C: d88f9ea..bb203ec). Remaining phases:
`/sdd-verify` (independent re-check incl. strict-TDD loop) then `/sdd-archive`.
Open items for verify/archive: the R7 `JsonPath.startsWith` spec-scenario
contradiction (deviations note 1) — spec scenario should be corrected (the
`("quest","advancement")` negative case contradicts the prose; the implemented
negative case `("quest","description")` matches heuristic rule 1).
