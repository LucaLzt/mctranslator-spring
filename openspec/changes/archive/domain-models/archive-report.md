# Archive Report: domain-models

- **Change Name**: `domain-models`
- **Project**: `mctranslator-spring`
- **Store Mode**: `hybrid` (repo `openspec/` canonical + disk mirror `~/.config/opencode/openspec/mctranslator-spring/`)
- **Date**: 2026-08-08
- **Status**: `success`

## Executive Summary

The `domain-models` change has been successfully implemented, verified, and archived. It introduced the
pure-Java domain model of the translation pipeline — exactly 9 public types in
`com.lucalzt.mctranslator.domain.model` (`TranslationKey`, `TranslationResult`, `GlossaryEntry` records;
`JsonPath`, `LanguageCode`, `ModpackId` value objects; `TranslationStatus`, `TranslationEngineType`,
`GlossaryEntryClassification` enums), a `@NullMarked` `package-info.java`, the `org.jspecify:jspecify`
1.0.1 compile-scope dependency, and 9 red-first JUnit 6 + AssertJ test classes (38 domain tests). All 22
tasks are complete across the 3 chained PR slices; the final gate `mvnw.cmd clean verify` passes with
41/41 tests and exit code 0. The change is closed; no further phases are recommended.

## Gates Enforced

1. **Verify gate — PASS**: `verify-report.md` (2026-08-08, `sdd-verify` executor) records `status: PASS`,
   0 CRITICAL, 1 WARNING, 2 SUGGESTION, with the final gate independently re-run
   (`.\mvnw.cmd clean verify` → `BUILD SUCCESS`, 41/41 tests, exit 0). CRITICAL issues: none → archive
   gate satisfied.
2. **Task completion gate — PASS**: `tasks.md` has all 22/22 implementation checkboxes checked (`[x]`),
   T1–T22, corroborated by `apply-progress.md` (slices A/B/C green: 24/24, 32/32, 41/41) and by the
   per-task `complete` statuses in `verify-report.md`. No archive-time reconciliation was needed.
3. **Review ledger**: structurally absent in this environment (no native review authority installed) →
   archive proceeds under ordinary repository policy; no receipt to check.
4. **actionContext**: archive ops allowed.

## Artifacts Archived & Synced

- Change artifacts copied (additive-only; the active `openspec/changes/domain-models/` directory is
  preserved) to `openspec/changes/archive/domain-models/`:
  - `proposal.md`, `explore.md`, `design.md`, `tasks.md`, `apply-progress.md`, `verify-report.md`
  - `specs/domain-model/spec.md`, `specs/domain-model/spec.yaml`
  - `archive-report.md` (this report)
- **Spec registry sync (upward)**: approved `domain-model` spec copied to
  `openspec/specs/domain-model/spec.md` + `spec.yaml`.
- **Disk mirror**: the same tree synced to `~/.config/opencode/openspec/mctranslator-spring/`
  (`changes/domain-models/`, `changes/archive/domain-models/`, `specs/domain-model/`).
- **Byte-identical readback**: `git diff --no-index` between source and destination is empty for every
  copied artifact (only expected addition: `archive-report.md` present in the archive copy).
- `proposal.md` has no `status` field in this repo's OpenSpec template, and the repo convention
  (`add-github-actions-ci-pipeline`) keeps active and archived copies byte-identical; per that convention
  no status edit was applied.
- Engram mirror: `mem_save` → `sdd/domain-models/archive-report` (`topic_key`, upsert).

## Spec Sync / Documentation Reconciliation (W1 — resolved)

The verify report flagged W1: `design.md` §6 (`JsonPathTest` row) and `tasks.md` T2 still carried the
original contradictory R7 wording (`("quest","advancement")` → `false` on `"quest.advancement"`), which
contradicts the normative prose (equal-length exact prefix → `true`) and the sibling case in the same
scenario. During this archive, both files were synced to the corrected exact-prefix semantics that the
spec (`specs/domain-model/spec.md`, "Prefix matching" scenario) and the implementation already follow:

- `"quest.description.task1".startsWith("quest","description")` → `true`
- `"item.sword".startsWith("item")` → `true`
- `"quest.advancement".startsWith("quest","advancement")` → `true` (equal-length exact prefix)
- `"quest.advancement".startsWith("quest","description")` → `false` (meaningful negative, heuristic rule 1)

Documentation-only correction; zero code/test impact. The corrected wording now matches the implemented
contract pinned by `JsonPathTest.matchesLeadingSegmentsInOrder` /
`JsonPathTest.rejectsMismatchingSegments`.

## Informational Notes (S1, S2 — recorded, no change)

- **S1 — `design.md` §3.10 snippet order**: the design snippet shows `@NullMarked` before the package
  Javadoc; the implementation uses the required package-info form (Javadoc first, annotation after).
  No semantic impact (`apply-progress.md` note 6). Kept as a design-doc record; not changed.
- **S2 — Javadoc not build-gated**: per design decision D9, Javadoc completeness relies on code review;
  there is no `maven-javadoc-plugin` gate. Consider an optional `mvnw.cmd javadoc:javadoc` check for
  future changes.

## Follow-ups for Downstream Changes

- **Extraction change (text-size bounds)**: threat-matrix row T1 records an explicit requirement — the
  extraction adapters MUST cap leaf sizes (configurable max text length, max path depth) before
  constructing `TranslationKey`s; length bounding was deliberately deferred out of this model-only
  change (spec Non-Goals; design §8 T1).
- **Ports change (`CacheKey` derivation)**: the future `CacheKey` MUST derive from the VO components
  (`JsonPath.value()`, `originalText`, `targetLanguage`, `modpack`) — never from re-splitting or
  re-concatenating raw strings (decision 2; design §8 T3, §10 scope guards). Spec risk 5 also defers the
  final `engine`-on-`CACHE_HIT` semantics until the cache port/schema contract lands.

## Next Recommended Actions

- Cycle closed (`next_recommended: none`). No open PRs or pushes from this phase.
