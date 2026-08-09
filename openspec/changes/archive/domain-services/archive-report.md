# Archive Report: domain-services

- **Change Name**: `domain-services`
- **Project**: `mctranslator-spring`
- **Store Mode**: `project` (canonical root `openspec/` inside the repository; Engram acts as automatic mirror)
- **Date**: 2026-08-09
- **Status**: `success`

## Executive Summary

The `domain-services` change has been successfully implemented, verified, and archived. It created the
pure-Java domain services of the translation pipeline in the new package
`com.lucalzt.mctranslator.domain.service` — exactly 8 main sources (7 public types + a `@NullMarked`
`package-info.java`): `MaskedText` and `VariableMasker` / `VariableUnmasker` (printf + MessageFormat
variables protected with positional `__VAR_N__` tokens and restored best-effort, with the `UnmaskResult`
discrepancy contract), `GlossaryTermMatch` and `ScalingDecision` (heuristic output records), and
`ScalingHeuristic` (the exact 6-rule FAST/PRECISE precedence table, first match wins, evaluated on masked
text). All 18 tasks (T1–T18) are complete across the 6 chained slices A–F; the final gate
`mvnw.cmd clean verify` passes with **116/116 tests** (75 new `domain.service` tests + 41 pre-existing)
and exit code 0. 17 conventional commits (7 strict red→green pairs = 14 work-unit commits, the T9
round-trip composition pin, and 2 `package-info` docs commits). The change is closed; no further phases
are recommended.

## Gates Enforced

1. **Verify gate — PASS**: `verify-report.md` (2026-08-09, `sdd-verify` executor) records
   `status: PASS` with **0 CRITICAL, 1 WARNING (W1), 0 SUGGESTIONS**, all 15 requirements R1–R15 PASS
   with code/test evidence, and the runtime gate independently re-run
   (`.\mvnw.cmd clean verify` → `BUILD SUCCESS`, 116/116 tests, 0 failures/errors/skipped, exit 0).
   CRITICAL issues: none → archive gate satisfied.
2. **Task completion gate — PASS (with archive-time mechanical reconciliation, W1)**: `tasks.md` now has
   **all 18/18 checkboxes checked (`- [x]`), T1–T18**. `verify-report.md` (W1) flagged that the boxes were
   never flipped during apply (`- [ ]` × 18) while `apply-progress.md` recorded all 18 complete. Per the
   skill's gate-2 exception, the reconciliation is **mechanical and fully proven**: `apply-progress.md`
   documents every task with red/green evidence and per-slice green gates (59/59 → 68/68 → 80/80 → 94/94 →
   116/116 → 116/116), `verify-report.md` marks all 18 tasks `complete` with commit SHAs, and the git log
   (verified in this phase) shows the corresponding test→feat pairs and green gates. The 18 boxes were
   flipped to `[x]` at archive time so the manual status resolution (`sdd-status-contract` §4) computes
   `pending = 0 → allComplete = true → nextRecommended = archive/` correctly. No implementation change was
   made; zero code/test impact.
3. **Review ledger**: structurally absent in this environment (`review-ledger-contract.md`: no native
   review authority installed → `reviewGate` is always absent) → archive proceeds under ordinary repository
   policy; there is no receipt to check.
4. **actionContext**: archive ops allowed (full-workspace).

## Spec/Design/Tasks Consistency (Deviation 15 — corrected and verified)

The spec-internal contradiction found during apply (deviation 15) is resolved in all three planning
artifacts and the files are mutually consistent (verified by grep in this phase):

- `spec.md:324` — rule-6 scenario now uses the 10-word text
  `"This sentence has exactly ten plain words written here today"` (> 8 and ≤ 30 words) → FAST/6, which is
  satisfiable under the binding empty-glossary literal reading (≤ 8 words would fire rule 5 instead).
- `tasks.md:344` — same corrected 10-word text in T14.
- `design.md:379` — same corrected example, with the note "corrected example — see apply-progress
  deviation 15".
- No remnant of the old `"A plain sentence"` example exists in any artifact; the only mention is the
  explanatory comment in `ScalingHeuristicTest.java` documenting why the correction was needed.
- The empty-glossary `"Hi"` → FAST/5 (`matchedRule` 5, rule 2 vacuous false / rule 5 vacuously true) and the
  LORE-guard → FAST/6 outcome-equivalence pins are consistent across spec, design, and implementation.

## Structural Verification (R1/R2/R3/R14 — re-run, PowerShell `Select-String`, `rg` not installed)

| Check | Result |
|---|---|
| R1 — 8 main files under `domain/service/`; no `com.mctranslator` package; exactly 7 public types | PASS (0 offending matches) |
| R2 — zero `org.springframework` / `jakarta` imports; exhaustive import allowlist (JDK + jspecify + `com.lucalzt.mctranslator` only) | PASS (0 matches) |
| R3 — no `@Nullable` in the package; `package-info.java` Javadoc-then-`@NullMarked`-then-`package` form | PASS (0 matches) |
| R14 — `MaskedText` / `GlossaryTermMatch` / `CacheKey` absent from `domain/model`; model inventory exactly 9 public types; `git diff` on `domain/port/out` empty | PASS |
| Token-format strictness — compiled literal `__VAR_(\d+)__` present exactly 3 times (`MaskedText.java:33`, `VariableMasker.java:51`, `VariableUnmasker.java:48`) | PASS |

## Commit List (verified via `git log` on `main` — this change's 17 commits)

14 work-unit commits = 7 strict red→green pairs (T1–T8, T10–T15), plus the T9 round-trip composition pin
and the two `package-info` docs commits. Repo style `type(scope): subject` throughout.

| Slice | Commits |
|---|---|
| A — Masking VOs (`MaskedText`, `UnmaskResult`) | `aaa53e3` test(domain): add MaskedTextTest (red) · `78791ac` feat(domain): implement MaskedText record · `24cbe06` test(domain): add UnmaskResultTest (red) · `101bdfa` feat(domain): implement UnmaskResult record |
| B — Masker (`VariableMasker`) | `af6ec0e` test(domain): add VariableMaskerTest (red) · `f00f6d7` feat(domain): implement VariableMasker |
| C — Unmasker + round trip (`VariableUnmasker`) | `e729b35` test(domain): add VariableUnmaskerTest (red) · `f6f24e3` feat(domain): implement VariableUnmasker · `2afadca` test(domain): add round-trip composition pin |
| D — Heuristic VOs (`GlossaryTermMatch`, `ScalingDecision`) | `180b856` test(domain): add GlossaryTermMatchTest (red) · `a979fa5` feat(domain): implement GlossaryTermMatch record · `c97191d` test(domain): add ScalingDecisionTest (red) · `e41c53c` feat(domain): implement ScalingDecision record |
| E — Heuristic (`ScalingHeuristic`) | `9ae85d4` test(domain): add ScalingHeuristicTest (red) · `7c487f9` feat(domain): implement ScalingHeuristic |
| F — Closing gates (`package-info.java`) | `5fb1571` docs(domain): add package-info with @NullMarked · `b6f62ad` docs(domain): reword package-info nullability prose |

No push and no PRs were created by this phase (per the orchestrator instruction). The change sits on
`main` as 17 commits; `openspec/` artifacts are untracked, consistent with prior changes.

## Artifacts Archived & Synced

- `archive-report.md` (this report) written to the live change directory
  `openspec/changes/domain-services/archive-report.md`.
- **Spec registry sync (upward, per the sdd-archive skill)**: the approved `domain-service` spec was copied
  mechanically (PowerShell `Copy-Item`, never modeled Read/Write copying) to
  `openspec/specs/domain-service/spec.md` + `spec.yaml` (schema 1.0 / domain `domain-service` / change
  `domain-services`), mirroring the `domain-model` / `domain-output-ports` registry layout.
  **Byte-identical readback**: `git diff --no-index` between source and destination is **empty** (passing
  evidence) for both files.
- **Archive-state marker**: an `## Archive State` record was added (additively) to
  `openspec/changes/domain-services/apply-progress.md` so the manual status resolution can trace the
  closure. No artifact file was deleted.
- **Proposal status**: per the repo convention observed in the archived `domain-models` /
  `domain-output-ports` changes, `proposal.md` has no `status` field in this repo's OpenSpec template and
  archived copies remain byte-identical to the active ones — no status edit was applied (best-effort per
  the skill; not a failure).
- **Engram mirror**: `mem_save` → `sdd/domain-services/archive-report` (`topic_key`, upsert; type
  `manual`, project `mctranslator-spring`, `capture_prompt: false`).

Note: unlike the `domain-models` archive (which copied the full change tree into
`openspec/changes/archive/domain-models/`), this phase did NOT create an `openspec/changes/archive/domain-services/`
copy — the orchestrator's explicit write scope (archive-report at the live change dir + spec sync + archive
state marker) and the "do not write any other files" deliverable cap supersede the generic OpenSpec archive
location. If full parity with the prior archive convention is desired, the orchestrator/user can add the
archive copy and a `chore(openspec)` commit (as `4e93924` did for `domain-models`).

## Follow-ups for Downstream Changes

- **Application layer (`GlossaryAwareTranslator`)**: the next change should orchestrate the documented flow
  (cache → mask → heuristic → engine → unmask → persist) consuming `MaskedText`, `ScalingDecision` and
  `UnmaskResult`: apply the precise-engine flag override and emit `[WARN]` from
  `d.engine()`/`d.matchedRule()` (decision 5) and from `r.missingTokenIndices()` /
  `r.unmatchedTokenIndices()` / `r.reordered()` (decision 3). Non-goals of this change, recorded here so the
  translator change picks them up.
- **Engine masked-text handoff (S2 constraint)**: `TranslationEnginePort.translate` still receives a
  `TranslationKey` with the unmasked `originalText`; how the pipeline feeds masked text to the engine is
  application-layer work.
- **Glossary loading**: `ScalingHeuristic` receives `List<GlossaryEntry>` as a value; glossary loading via
  `GlossaryPort` belongs to the application layer (explicit non-goal here).
- **Empirical tuning hooks** (documented, deferred): the non-masked printf/MessageFormat/Minecraft pattern
  set, the `%%s` adjacency limitation, and the 30/8 word thresholds are pinned compile-time constants
  awaiting the empirical tuning phase the docs anticipate.

## Next Recommended Actions

- Cycle closed (`next_recommended: none`). No further SDD phases apply to `domain-services`.
- Optional (orchestrator/user decision): create the `openspec/changes/archive/domain-services/` copy and a
  `chore(openspec)` commit for full parity with the `domain-models` archive convention; `openspec/` remains
  untracked until then.
- The next logical change in the pipeline is the application-layer translator orchestration
  (`GlossaryAwareTranslator`) that consumes these services.
