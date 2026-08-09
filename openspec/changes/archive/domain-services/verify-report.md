# Verify Report: Domain Services (`domain-services`)

## Overview

Change: `domain-services`
Verified against: `proposal.md`, `specs/domain-service/spec.md`, `design.md`, `tasks.md`, `apply-progress.md`
Date: 2026-08-09
Verifier: sdd-verify sub-agent (mode `project`, canonical root `openspec/`)
Build environment: Windows PowerShell, `.\mvnw.cmd clean verify`, Java 25.0.2, Spring Boot 4.0.7

**Result: PASS.** All 15 requirements (R1–R15) are satisfied with concrete code/test evidence; all 18 tasks (T1–T18) are complete; `mvnw.cmd clean verify` → `BUILD SUCCESS`, 116/116 tests (0 failures, 0 errors, 0 skipped), exit code 0. 0 CRITICAL, 1 WARNING (artifact consistency, see Findings), 0 SUGGESTIONS.

---

## Requirement Traceability

| Req ID | Requirement | Status | Evidence |
|---|---|---|---|
| R1 | Real base package `com.lucalzt.mctranslator.domain.service`, exact 8 public types (7 + `package-info.java`), never `com.mctranslator` | PASS | 8 main files under `src/main/java/com/lucalzt/mctranslator/domain/service/` (verified by enumeration); grep `package com\.mctranslator\b` over `src/main/java` → 0 matches; grep `^public (record\|final class\|class\|interface\|enum) ` → exactly 7 public types (`MaskedText`, `VariableMasker`, `VariableUnmasker`, `UnmaskResult`, `GlossaryTermMatch`, `ScalingDecision`, `ScalingHeuristic`) |
| R2 | Zero framework imports; only JDK + `org.jspecify.*` + `com.lucalzt.mctranslator.domain.*` | PASS | Grep `import (org\.springframework\|jakarta)` over the package → 0 matches; exhaustive allowlist grep `^import (?!java\.\|org\.jspecify\.\|com\.lucalzt\.mctranslator\.)` → 0 matches. Per-file imports verified by inspection (JDK + `domain.model` only) |
| R3 | `@NullMarked` `package-info.java` with package-role Javadoc; no `@Nullable` elements | PASS | `package-info.java` lines 1–15: Javadoc block first, then `@org.jspecify.annotations.NullMarked`, then `package` declaration; grep `@Nullable` over the package → 0 matches (Javadoc prose avoids the literal token) |
| R4 | Javadoc on every public type and public member | PASS | All 8 main files read in full: every record/class, accessor, method and constructor carries Javadoc summary + `@param`/`@return`/`@throws` where applicable (e.g. `MaskedText.java:8–30`, `UnmaskResult.java:6–34`, `ScalingHeuristic.java:13–53` incl. the 6-rule precedence table, `ScalingDecision.java:8–34` rule-to-engine mapping) |
| R5 | `MaskedText` record VO: null/blank validation, token-set invariant, defensive copy | PASS | `MaskedText.java:41–74` compact constructor: `maskedText` null→NPE / blank→IAE; `variables` null / null element→NPE, blank element→IAE; token-set invariant via `boolean[] seen` + counter (`__VAR_(\d+)__` scan, `MaskedText.java:33`), `NumberFormatException`→IAE guard; `List.copyOf` defensive copy. `MaskedTextTest` 12/12 green (incl. out-of-range `__VAR_2__`, duplicate `__VAR_0__`, missing index, defensive copy, equality) |
| R6 | `VariableMasker`: pinned 3-branch alternation, first-occurrence numbering, no dedup, no-leakage, non-masked set left literal | PASS | `VariableMasker.java:50–51` `VARIABLE_PATTERN = "__VAR_(\\d+)__\|%(?:\\d+\\$)?[sdf]\|\\{(\\d+)(?:,[^}]*)?\\}"` (pinned order literal\|printf\|MessageFormat); `mask()` (lines 67–90): NPE/IAE validation, `find()`+`appendReplacement` positional tokens, verbatim `variables` entries no dedup, content-based branch detection (D1); non-masked set (`%%`, `%S`, `%x`, `%10.2f`, `§a`, JSON braces) left literal by construction; documented `%%s` limitation + re-entrancy note. `VariableMaskerTest` 9/9 green (all 8 R6 scenarios) |
| R7 | Round-trip fidelity; full-index token matching (`__VAR_(\d+)__`) | PASS | `VariableUnmasker.java:48` `TOKEN_PATTERN = "__VAR_(\\d+)__"` (greedy `\d+`, mandatory trailing `__` — partial match of `__VAR_1__` in `__VAR_10__` structurally impossible). `VariableRoundTripTest` 3/3 green: identity round trip, literal-token no-leakage (`"Use __VAR_3__"` restored exactly), 11-entry full-index test |
| R8 | `VariableUnmasker` best-effort + `UnmaskResult` discrepancy contract | PASS | `VariableUnmasker.java:58–96`: NPE on null args; restore by index with `Matcher.quoteReplacement` (D8, mandatory for `%1$s`); `N ≥ n` left verbatim + reported (M6); `seen[]`/`TreeSet`/`maxSeen` → missing/unmatched/reordered; `parseTokenIndex` clamps overflow to `MAX_VALUE` (never throws). `UnmaskResult.java:44–50`: null checks + `List.copyOf`, no sortedness/dedup re-validation (D2). Tests 9/9 (`VariableUnmaskerTest`, incl. missing `[1]`, unmatched `[9]` dedup, reordered, duplicate-not-reordered, `$`-variable quoting pin) + 6/6 (`UnmaskResultTest`) |
| R9 | `ScalingHeuristic` 6-rule precedence table, first match wins, masked-text-only evaluation, H7/H8 pins | PASS | `ScalingHeuristic.java:89–131` `suggest(JsonPath, String maskedText, List<GlossaryEntry>)` (H1): rules 1–6 in order, lazy per-rule (D14); rule 5 LORE guard → falls to rule 6 FAST (pinned equivalence, never LORE→PRECISE); empty glossary → rule 2 vacuous false, rule 5 fires on ≤8 words (`matchedRule` 5); thresholds 30/8 compile-time named constants (H8); whole-word case-sensitive matching with `Pattern.quote` (D9/H7, `matchGlossaryTerms` lines 162–171). `ScalingHeuristicTest` 22/22 green (all R9 scenarios, incl. corrected rule-6 10-word fall-through, LORE guard, empty glossary, masked-text evaluation) |
| R10 | `ScalingDecision` record: engine/rule-range/list validation, documented value contract | PASS | `ScalingDecision.java:45–52`: `engine` null→NPE, `matchedRule` 1..6→IAE, list null→NPE, `List.copyOf`; value contract documented in Javadoc, NOT re-validated (D7); D3 full-match-list note. `ScalingDecisionTest` 9/9 green (rule 0/7→IAE, null components, defensive copy, heuristic-produced consistency 1–6) |
| R11 | `GlossaryTermMatch` record VO | PASS | `GlossaryTermMatch.java:27–33`: `term` null→NPE, blank→IAE, `classification` null→NPE; equality by both components. `GlossaryTermMatchTest` 5/5 green |
| R12 | Word-counting contract on masked text | PASS | `ScalingHeuristic.java:142–148` package-private `static countWords` (D4): `strip()`, empty→0 guard (required: `"".split("\\s+")` → `[""]`), else `split("\\s+").length`. Direct tests green: `"Hello, world!"`=2, `"..."`=1, `"A __VAR_0__ with __VAR_1__"`=4, `"   "`=0 (in `ScalingHeuristicTest`) |
| R13 | Strict TDD red-first, JUnit 6 + AssertJ, no Spring context | PASS | Git history: 8 test-before-code pairs (aaa53e3→78791ac, 24cbe06→101bdfa, af6ec0e→f00f6d7, e729b35→f6f24e3, 180b856→a979fa5, c97191d→e41c53c, 9ae85d4→7c487f9; T9 composition pin 2afadca green-first per contract). Red states were compilation failures of not-yet-existing types (apply-progress TDD table). Domain tests import only `org.junit.jupiter.*` + `org.assertj.*` (grep over `src/test/.../domain/service/*.java` for `org.springframework`/`jakarta` → 0 matches). 75 domain tests, all green |
| R14 | `domain.model` 9-type inventory untouched; port contracts unchanged | PASS | Grep `MaskedText\|GlossaryTermMatch\|CacheKey` over `domain/model/*.java` → 0 matches; `domain/model` enumerates exactly 9 public types (GlossaryEntry, GlossaryEntryClassification, JsonPath, LanguageCode, ModpackId, TranslationEngineType, TranslationKey, TranslationResult, TranslationStatus); `git diff -- src/main/java/com/lucalzt/mctranslator/domain/port/out` → empty |
| R15 | Clean verify passes with exit code 0 | PASS | `.\mvnw.cmd clean verify` → `BUILD SUCCESS`, **Tests run: 116, Failures: 0, Errors: 0, Skipped: 0**; verified exit code 0 via `.\mvnw.cmd -q verify` → `$LASTEXITCODE = 0`. Breakdown: 75 domain.service (new) + 38 domain.model + 2 `TranslateCommandTests` + 1 `MctranslatorApplicationTests` = 116, matching apply-progress exactly |

---

## Task Status

| Task | Status | Evidence |
|---|---|---|
| T1 `MaskedTextTest` (RED) | complete | Commit aaa53e3; red state = 26 "cannot find symbol: MaskedText" compile errors (apply-progress) |
| T2 `MaskedText` (GREEN) | complete | Commit 78791ac; `-Dtest=MaskedTextTest` 12/12 green |
| T3 `UnmaskResultTest` (RED) | complete | Commit 24cbe06; red = "cannot find symbol: UnmaskResult" |
| T4 `UnmaskResult` (GREEN) | complete | Commit 101bdfa; 6/6 green; Slice A gate `clean verify` 59/59 exit 0 |
| T5 `VariableMaskerTest` (RED) | complete | Commit af6ec0e; red = 4 compile errors |
| T6 `VariableMasker` (GREEN) | complete | Commit f00f6d7; 9/9 green; Slice B gate 68/68 exit 0 |
| T7 `VariableUnmaskerTest` (RED) | complete | Commit e729b35; red = 3 compile errors |
| T8 `VariableUnmasker` (GREEN) | complete | Commit f6f24e3; 9/9 green |
| T9 `VariableRoundTripTest` (green-first composition pin) | complete | Commit 2afadca; 3/3 green; Slice C gate 80/80 exit 0 |
| T10 `GlossaryTermMatchTest` (RED) | complete | Commit 180b856; red = 14 compile errors |
| T11 `GlossaryTermMatch` (GREEN) | complete | Commit a979fa5; 5/5 green |
| T12 `ScalingDecisionTest` (RED) | complete | Commit c97191d; red = "cannot find symbol: ScalingDecision" |
| T13 `ScalingDecision` (GREEN) | complete | Commit e41c53c; 9/9 green; Slice D gate 94/94 exit 0 |
| T14 `ScalingHeuristicTest` (RED) | complete | Commit 9ae85d4; red = 7 compile errors |
| T15 `ScalingHeuristic` (GREEN) | complete | Commit 7c487f9; 22/22 green (rule-6 example corrected during green, deviation 15); Slice E gate 116/116 exit 0 |
| T16 `package-info.java` `@NullMarked` | complete | Commits 5fb1571 + b6f62ad (Javadoc reword); `-q compile` exit 0 |
| T17 Structural greps R1/R2/R3/R14 | complete | Independently re-run in this verify: all pass with zero offending matches (see Checks Run); token regex literal `__VAR_(\d+)__` present exactly 3 times (MaskedText:33, VariableMasker:51, VariableUnmasker:48) |
| T18 Full build gate | complete | `.\mvnw.cmd clean verify` → `BUILD SUCCESS`, 116/116, exit 0 (re-run in this verify) |

All 18 tasks verified complete by this phase (git history + green gates + independent re-run of T17/T18).

---

## Checks Run

- [x] **Build** — `.\mvnw.cmd clean verify` → `BUILD SUCCESS` (exit 0, re-confirmed with `.\mvnw.cmd -q verify` → `$LASTEXITCODE = 0`)
- [x] **Unit tests** — **116 run / 116 passed / 0 failed / 0 errors / 0 skipped** (75 new `domain.service` + 38 `domain.model` + 2 `TranslateCommandTests` + 1 `MctranslatorApplicationTests`)
- [x] **Static analysis (structural greps, PowerShell `Select-String` — `rg` not installed)**:
  - R1: 8 files under `domain/service/`; `package com\.mctranslator\b` → 0; public types exactly 7
  - R2: `import (org\.springframework|jakarta)` → 0; exhaustive allowlist → 0
  - R3: `@Nullable` → 0
  - R14: `MaskedText|GlossaryTermMatch|CacheKey` in `domain/model` → 0; model inventory exactly 9; `git diff` on `domain/port/out` → empty
  - Token format strictness: compiled literal `__VAR_(\d+)__` at MaskedText.java:33, VariableUnmasker.java:48, and as the first alternation branch of VariableMasker.java:51 (exactly 3, per apply-progress deviation 20 escaping: the PowerShell pattern needs `\\\\` to match two literal backslashes)
- [x] **Manual probes** — full source inspection of all 8 main files + test-count enumeration per class (5+12+9+22+6+9+3+9 = 75)

Pre-existing, non-blocking observations (unchanged by this change): Spring Shell `NonInteractiveShellRunner` ERROR log and Mockito/byte-buddy self-attach warnings in `TranslateCommandTests` (documented in apply-progress deviation 21).

---

## Findings

### CRITICAL
None.

### WARNING

1. **`tasks.md` checkboxes were never flipped to `[x]` (status-artifact consistency).** `openspec/changes/domain-services/tasks.md` still lists all 18 tasks as `- [ ]` (unchecked: 18, checked: 0), while `apply-progress.md` records all 18 as `- [x]` complete. Every other change in this repo (archived and live: `domain-models` 22/22, `domain-output-ports` 4/4, `add-github-actions-ci-pipeline` 5/5) marks its `tasks.md` boxes `[x]` on completion. Per the sdd-status-contract (§4), `taskStatus` is computed from the tasks artifact (`allComplete = pending == 0`): a strict status resolution would compute `pending = 18 → allComplete = false → applyState = incomplete → nextRecommended = apply`, misdirecting the pipeline away from `archive` and contradicting the verified implementation state. **Impact**: status resolution / archive gate false negative; no impact on implementation correctness (all 18 tasks are demonstrably complete: git history, green gates, this report). **Fix (mechanical, before archive)**: flip T1–T18 to `- [x]` in `tasks.md`, or explicitly record that `apply-progress.md` is the canonical completion ledger for this change. Trace: T1–T18, sdd-status-contract §4.

### SUGGESTION
None.

---

## Contradictions

None. The spec-internal contradiction found during apply (deviation 15: the R9 "Rule 6 — default fall-through" scenario pinned `"A plain sentence"` — 3 words — which is unsatisfiable under the binding empty-glossary literal reading, because any ≤8-word text fires rule 5) was **corrected in all three artifacts and the current files are consistent** (verified, not re-flagged):

- `spec.md:324` — `"This sentence has exactly ten plain words written here today"` (10 words, > 8 and ≤ 30) → FAST/6
- `tasks.md:344` — same 10-word text
- `design.md:379` — same 10-word text, with "corrected example — see apply-progress deviation 15" note
- No remnants of the old `"A plain sentence"` example in any artifact; the only mention is the explanatory comment in `ScalingHeuristicTest.java:194–197` documenting why the correction was needed
- The rule-6 test (`defaultFallThroughToRule6`) uses `words(10)` and is green; the empty-glossary `"Hi"` → FAST/5 (spec.md:316–318) and LORE-guard → FAST/6 pins are consistent across spec/design/implementation

The implementation follows the binding "Resolved in Spec" literal reading (rule 5 wins on short text with empty glossary, `matchedRule` 5), and the 10-word correction restores rule 6 coverage via the genuine default path. No spec correction remains pending.

---

## Strict TDD Findings

Per `strict-tdd-verify.md` (strict TDD detected — `openspec/config.yaml` `rules.tasks`):

1. **Test-before-code**: `verified` — git history shows each test commit strictly preceding its implementation commit for all 8 capability pairs (T1→T2, T3→T4, T5→T6, T7→T8, T10→T11, T12→T13, T14→T15); T9 (composition pin) green-first per the design A5 contract; T16–T18 are gate tasks.
2. **Failure legitimacy**: `verified` — red states were compilation failures of the not-yet-existing types ("cannot find symbol: MaskedText/UnmaskResult/VariableMasker/VariableUnmasker/GlossaryTermMatch/ScalingDecision/ScalingHeuristic"), the expected missing-behavior reason, not broken tooling (apply-progress TDD loop table).
3. **Green**: `verified` — the full suite passes: 116/116, exit 0.
4. **Refactor safety**: `verified` — the only post-green changes were documentation/test-example corrections (package-info Javadoc reword b6f62ad; rule-6 test example correction during T15 green, deviation 15); no behavior-changing refactor; suite green after each.

---

## Native Validation

`native_validation: skipped (binary unavailable)` — no native SDD validator is installed in this environment; status resolved manually per `sdd-status-contract.md`.

---

## Verdict

**status: PASS**

- Critical issues: **0** (required for archive — satisfied)
- Warnings: **1** (W1, tasks.md checkboxes — non-blocking for implementation correctness, must be resolved before archive)
- Requirement coverage: R1–R15 all PASS with concrete evidence
- Task completion: T1–T18 all complete with verification evidence
- Runtime gate: `BUILD SUCCESS`, 116/116 tests, exit code 0
- Summary: The `domain-services` change is fully and correctly implemented. All 8 main sources match design D1–D14 and spec R1–R15; structural greps (R1/R2/R3/R14) are clean; the strict-TDD red→green discipline is verifiable in git history; the build gate passes with the exact expected 116/116 test count. The only action before archive is the mechanical tasks.md checkbox flip (W1) so the manual status resolution computes `allComplete` correctly.
