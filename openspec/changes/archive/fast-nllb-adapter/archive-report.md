# Archive Report: fast-nllb-adapter

- **Change Name**: `fast-nllb-adapter`
- **Project**: `mctranslator-spring`
- **Store Mode**: `project` (repo `openspec/`, versioned in git)
- **Date**: 2026-08-10
- **Status**: `success`

## Executive Summary

The `fast-nllb-adapter` change is fully implemented, verified, and archived. It introduced the local FAST
translation engine: `FastNllbAdapter` (ONNX Runtime 1.26.0 + DJL HuggingFace tokenizers 0.36.0) implementing
`TranslationEnginePort`, with `NllbEngineProperties` (prefix `mctranslator.engine.nllb`,
`modelDir=models/nllb`, `maxNewTokens=128`), `NllbLanguageMapper` (10 ISO 639-1 codes → Flores-200 tags,
`IllegalArgumentException` on unsupported), eager model-asset validation with descriptive errors, an
encoder → autoregressive decoder pipeline with merged KV-cache `If`-branch handling, and idempotent
`@PreDestroy` resource cleanup. All 4 tasks (T1–T4) are complete with checkboxes marked in `tasks.md`.
The full test suite passes twice: `mvnw.cmd test` → 162 tests / 0 failures / 6 skipped (integration tests
gated off), and `mvnw.cmd test -Dmctranslator.it.nllb=true` → 162 / 0 / 0, with
`FastNllbAdapterIntegrationTest` 6/6 green in ~82 s against the real quantized checkpoint. Verify verdict:
PASS, 0 CRITICAL, 1 WARNING (W-1), 3 SUGGESTIONS (S-1..S-3). The cycle is closed; no further phases are
recommended.

## Gates Enforced

1. **Verify gate — PASS**: `verify-report.md` (2026-08-10, `sdd-verify` executor) records `status: PASS`,
   0 CRITICAL, 1 WARNING, 3 SUGGESTIONS, with all 12 requirements (REQ-1.1..REQ-5.2) traced to concrete
   implementation evidence and the full suite green twice (162/0/0 with IT enabled; 162/0/0 with 6 skipped
   with IT gated off). CRITICAL issues: none → archive gate satisfied.
2. **Task completion gate — PASS**: `tasks.md` has all 4/4 implementation checkboxes checked (`[x]`,
   T1–T4), corroborated by the Engram `apply-progress` (`sdd/fast-nllb-adapter/apply-progress`) and the
   per-task `complete` statuses in `verify-report.md`. No archive-time reconciliation was needed.
3. **Review ledger**: structurally absent in this environment (no native review authority installed) →
   archive proceeds under ordinary repository policy; no receipt to check.
4. **actionContext**: archive ops allowed.

## Artifacts Archived & Synced

- Change artifacts copied (additive-only; the active `openspec/changes/fast-nllb-adapter/` directory is
  preserved) to `openspec/changes/archive/fast-nllb-adapter/`:
  - `proposal.md` (status updated to `done`), `explore.md`, `design.md`, `tasks.md`, `verify-report.md`,
    `archive-report.md` (this report)
  - `specs/nllb-engine/spec.md`, `specs/nllb-engine/spec.yaml`
- **Spec registry sync (upward)**: approved `nllb-engine` spec copied to
  `openspec/specs/nllb-engine/spec.md` (`spec.yaml` already present, unchanged — it carries no language
  tag examples).
- **Proposal status**: `## Status: done` added to `proposal.md` following the repo convention
  (`json-glossary-adapter` / `sqlite-cache-adapter`); active and archived copies kept byte-identical.
- **Byte-identical readback**: `git diff --no-index` between source and destination trees is empty for
  both (active change dir vs archived copy; change spec vs registry spec).
- Engram mirror: `mem_save` → `sdd/fast-nllb-adapter/archive-report` (`topic_key`, upsert).

## Spec Sync / Documentation Reconciliation (S-1 — resolved)

The verify report flagged S-1: `spec.md` REQ-3.1 listed `rus_Latn`/`zho_Latn` as example NLLB-200 tags,
but the implementation and `design.md` use the correct script-specific Flores-200 tags `rus_Cyrl` and
`zho_Hans`. During this archive the spec examples were aligned — in both the change spec
(`openspec/changes/fast-nllb-adapter/specs/nllb-engine/spec.md`) and the registry copy
(`openspec/specs/nllb-engine/spec.md`) — to match the implementation and `design.md`. Documentation-only
correction; zero code/test impact. The tags remain validated against the real tokenizer at adapter init.

## Operational Note (W-1 — documented)

`models/` is git-ignored, but `FastNllbAdapter` (a `@Component`) validates and loads the model assets
eagerly in its constructor, so a clean checkout without the model assets fails application startup.
**Out-of-band provisioning requirement (assets required but NOT versioned):** the following files must be
present under the configured model directory (default `models/nllb/`) before the application starts:

- `tokenizer.json` — Hugging Face / SentencePiece tokenizer configuration and vocabulary (~16.5 MB)
- `onnx/encoder_model_quantized.onnx` — quantized NLLB-200 encoder (~399.7 MB)
- `onnx/decoder_model_merged_quantized.onnx` — quantized NLLB-200 decoder with merged KV cache (~453.5 MB)

The spec deliberately excludes automatic model download (non-goal). This report documents the requirement
for operators; a README / ops note is the recommended place to surface it to end users of a clean checkout.

## Known Follow-ups (S-2, S-3 — recorded, no code change)

- **S-2 — Constructor failure path does not close the singleton `OrtEnvironment`**: the constructor catch
  block closes the local tokenizer and sessions but omits the shared `OrtEnvironment` instance. Negligible
  in practice (startup failure aborts the JVM; the environment is a shared singleton), but passing it to
  `closeQuietly(...)` would make the failure path symmetric with `close()`. Recorded as a future
  improvement; production code intentionally not changed.
- **S-3 — No dedicated Spring binding test for `mctranslator.engine.nllb.*`**:
  `NllbEnginePropertiesTest` covers defaults/setters/resolution only; actual `@ConfigurationProperties`
  binding from external properties is exercised implicitly by the context-loading test. A small
  `@SpringBootTest` with override properties would close the gap. Recorded as a future test improvement.

## Next Recommended Actions

- Cycle closed (`next_recommended: none`). No further SDD phases for this change.
- Recommended user-facing follow-up: add the model-assets provisioning requirement (W-1) to the project
  README or an ops note so users of a clean checkout know the assets are required but not versioned.
