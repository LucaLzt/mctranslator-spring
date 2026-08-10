# Verify Report: Fast NLLB-200 ONNX Adapter (`fast-nllb-adapter`)

## Overview

Change: `fast-nllb-adapter`
Verified against: `specs/nllb-engine/spec.md`, `design.md`, `tasks.md`
Date: 2026-08-10
Artifact store: project (`openspec/changes/fast-nllb-adapter/`)
Test runner: `mvnw.cmd test` (Windows). Strict TDD: NOT active.

## Requirement Traceability

| Req ID | Requirement | Status | Evidence |
|---|---|---|---|
| REQ-1.1 | `pom.xml` includes `com.microsoft.onnxruntime:onnxruntime` 1.26.0 and tokenizers deps | PASS | `pom.xml:43-51` — `com.microsoft.onnxruntime:onnxruntime` 1.26.0 and `ai.djl.huggingface:tokenizers` 0.36.0; Maven build succeeds (exit 0). |
| REQ-1.2 | Config properties under `mctranslator.engine.nllb` with model dir (default `models/nllb`) and inference params | PASS | `NllbEngineProperties.java:17-24` — `@Component` + `@ConfigurationProperties(prefix = "mctranslator.engine.nllb")`, `modelDir = "models/nllb"`, `maxNewTokens = 128`; `NllbEnginePropertiesTest` (4/4 pass). |
| REQ-2.1 | Load `tokenizer.json`, `encoder_model_quantized.onnx`, `decoder_model_merged_quantized.onnx` from configured dir | PASS | `FastNllbAdapter.java:151-192` (constructor loads `HuggingFaceTokenizer`, `OrtEnvironment.getEnvironment()`, encoder+decoder `OrtSession`); assets verified present on disk: `models/nllb/tokenizer.json` (16.5 MB), `models/nllb/onnx/encoder_model_quantized.onnx` (399.7 MB), `models/nllb/onnx/decoder_model_merged_quantized.onnx` (453.5 MB). |
| REQ-2.2 | Descriptive initialization exception when assets missing/malformed | PASS | `FastNllbAdapter.java:277-288` (`validateFile` -> `IllegalStateException` "...is missing or unreadable at..."); `:187-191` wraps load failures in `IllegalStateException` with model dir context; `FastNllbAdapterInitTest` (6/6 pass: missing tokenizer/encoder/decoder, corrupt tokenizer, non-positive budget, null args). |
| REQ-3.1 | Map ISO codes (es/en/fr/de/pt/ru/zh/it/ja/ko) to NLLB-200 Flores-200 tags | PASS | `NllbLanguageMapper.java:22-32` (10 codes -> `spa_Latn`, `eng_Latn`, `fra_Latn`, `deu_Latn`, `por_Latn`, `rus_Cyrl`, `zho_Hans`, `ita_Latn`, `jpn_Jpan`, `kor_Hang`); `NllbLanguageMapperTest.shouldMapAllSupportedCodes`; every tag additionally validated as a single token id against the real tokenizer at init (`FastNllbAdapter.resolveLanguageTokenIds :305-316`). |
| REQ-3.2 | `IllegalArgumentException` with clear diagnostics for unsupported codes | PASS | `NllbLanguageMapper.java:41-53` (message lists supported codes); `NllbLanguageMapperTest.shouldRejectUnsupportedCodes` (parameterized over `xx`, `en-US`, `spa_Latn`, `Español`, ``); `FastNllbAdapterIntegrationTest.shouldRejectUnsupportedLanguageCode`. |
| REQ-4.1 | Implement `com.lucalzt.mctranslator.domain.port.out.TranslationEnginePort` | PASS | `FastNllbAdapter.java:69` — `implements TranslationEnginePort`; `TranslationEnginePort.java` untouched (empty `git diff` for `src/main/java/.../domain`). |
| REQ-4.2 | For `FAST`: tokenize -> encoder inference -> autoregressive decoder inference -> detokenize | PASS | `FastNllbAdapter.java` `translate():210-260` + `decode():388-443` (greedy loop, EOS seed, forced target Flores-200 tag, KV-cache `If`-branch handling, argmax until EOS/budget); verified end-to-end: `FastNllbAdapterIntegrationTest` 6/6 pass against the real quantized model (82.12 s), including `"Hello World"` -> coherent Spanish containing `"ola"` and Minecraft-style key with `%s`. |
| REQ-4.3 | Return valid `TranslationResult` (text, `FAST`, success status) | PASS | `FastNllbAdapter.java:246-247` — `new TranslationResult(key, translatedText, TranslationStatus.TRANSLATED_FAST, TranslationEngineType.FAST, null, duration)`; integration assertions on `status()`, `engine()`, `duration()` pass. |
| REQ-5.1 | Lifecycle cleanup via `@PreDestroy` closing `OrtSession`/`OrtEnvironment` | PASS | `FastNllbAdapter.java:267-275` — `@PreDestroy close()` idempotent via `volatile closed` flag, `closeQuietly(tokenizer, encoderSession, decoderSession, environment)`; `FastNllbAdapterIntegrationTest.shouldGuardAfterCloseAndKeepCloseIdempotent` (double close + post-close guard) passes. |
| REQ-5.2 | Adapter + mapping utilities confined to `com.lucalzt.mctranslator.infrastructure.adapter.out.nllb` | PASS | Package declarations of all classes + `package-info.java`; `git status`: new files only under `src/main/java/.../adapter/out/nllb/` and `src/test/java/.../adapter/out/nllb/`; no changes under `domain/` or `application/`. |

Status counts: PASS 12 / FAIL 0 / EVIDENCE-MISSING 0.

## Task Status

| Task | Status | Evidence |
|---|---|---|
| T1 — ONNX Runtime & tokenizers deps in `pom.xml` (REQ-1.1) | complete | `pom.xml:43-51`; build succeeds; ONNX classes resolvable (integration tests run). |
| T2 — `NllbEngineProperties` & `NllbLanguageMapper` (REQ-1.2, REQ-3.1, REQ-3.2) | complete | Both classes in `infrastructure.adapter.out.nllb`; `NllbEnginePropertiesTest` 4/4, `NllbLanguageMapperTest` 8/8 pass. |
| T3 — `FastNllbAdapter` conforming to port + resource lifecycle (REQ-2.1, REQ-2.2, REQ-4.x, REQ-5.x) | complete | Adapter implements port, validates assets, runs full pipeline; `FastNllbAdapterInitTest` 6/6, `FastNllbAdapterIntegrationTest` 6/6 pass. |
| T4 — Unit & integration tests (all requirements/scenarios) | complete | 24 NLLB-related test cases all green; full suite 162 tests, 0 failures, 0 errors (0 skipped with IT enabled). |

All task checkboxes `[x]` in `tasks.md` confirmed.

## Checks Run

- [x] Build — `mvnw.cmd test`: **BUILD SUCCESS**, exit code 0.
- [x] Unit tests (default, IT gated off): 162 run / 0 failures / 0 errors / 6 skipped (the gated integration tests).
- [x] Integration tests — `mvnw.cmd test -Dmctranslator.it.nllb=true`: 162 run / 0 failures / 0 errors / 0 skipped; `FastNllbAdapterIntegrationTest` 6/6 in 82.12 s against the real quantized model (`models/nllb/onnx/`, ~850 MB total).
- [x] Static analysis — N/A (no static analysis step configured in the project).
- [x] Manual probes — covered by the gated integration run (real ONNX inference, lifecycle close, error paths).

## Findings

### CRITICAL
- None.

### WARNING
- **W-1: Production startup depends on git-ignored model assets.** The change adds `models/` to `.gitignore`, but `FastNllbAdapter` is a `@Component` that eagerly validates and loads the model in its constructor, so a clean checkout without `models/nllb/` fails application context startup. Assets exist locally (verified) and the spec deliberately excludes auto-download (non-goal), but the provisioning requirement is only documented in the integration-test Javadoc. Recommend documenting the out-of-band provisioning step (README / ops note) before archive.

### SUGGESTION
- **S-1: Spec example tags for `ru`/`zh` differ from implementation.** `spec.md` lists `rus_Latn`/`zho_Latn` as examples, but the implementation (and `design.md`) use the correct script-specific Flores-200 tags `rus_Cyrl`/`zho_Hans` (also `jpn_Jpan`, `kor_Hang`). The spec uses "e.g."/"etc." and the tags are validated against the real tokenizer at init, so this is not a defect — align the spec examples with the actual tags to avoid confusion.
- **S-2: Constructor failure path does not close the singleton `OrtEnvironment`.** The catch block calls `closeQuietly(localTokenizer, localEncoderSession, localDecoderSession)` but omits `localEnvironment`. Negligible in practice (startup failure aborts the JVM and the environment is a shared singleton), but passing it in would make the failure path symmetric with `close()`.
- **S-3: No dedicated Spring binding test for `mctranslator.engine.nllb.*`.** `NllbEnginePropertiesTest` covers defaults/setters/resolution only; actual `@ConfigurationProperties` binding from external properties is exercised implicitly by the context-loading test (bean registered, context boots). A small `@SpringBootTest` with override properties would close the gap.

## Contradictions

- None. No spec/design/implementation contradiction found. (S-1 is a spec-example imprecision, explicitly covered by the design artifact.)

## Strict TDD Findings

- Not applicable (strict TDD not active).

## Native Validation

- `native_validation: skipped (binary unavailable)` — no native SDD validator installed.

## Verdict

status: **PASS**
Summary: All 12 requirements trace to concrete implementation evidence (code, config, tests). All 4 tasks complete. Full suite green twice: 162 tests / 0 failures / 0 errors with integration gated off (6 skipped), and 162 / 0 / 0 with `-Dmctranslator.it.nllb=true` including all 6 real-model integration tests (82 s). Architecture conformance holds (adapter confined to `infrastructure.adapter.out.nllb`, port signature untouched, zero `domain/`/`application/` changes). Resource lifecycle verified (idempotent `@PreDestroy`, tensor close discipline, post-close guard). One operational WARNING (untracked model assets required for startup) and three minor suggestions; no critical issues.
Critical issues: 0 (required for archive).
