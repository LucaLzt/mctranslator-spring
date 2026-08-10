# Tasks: Fast NLLB-200 ONNX Adapter (`fast-nllb-adapter`)

## Task Breakdown

- [x] **T1: Add ONNX Runtime & Tokenizers Dependencies to `pom.xml`**
  - **Description**: Add `com.microsoft.onnxruntime:onnxruntime` (version 1.26.0) and required tokenizers dependencies to `pom.xml`.
  - **Criteria**: Maven project builds successfully; ONNX Runtime classes are accessible on the classpath.
  - **Depends on**: None
  - **Spec Reference**: REQ-1.1

- [x] **T2: Implement `NllbEngineProperties` & `NllbLanguageMapper`**
  - **Description**: Create `NllbEngineProperties` in `com.lucalzt.mctranslator.infrastructure.adapter.out.nllb` bound to configuration prefix `mctranslator.engine.nllb` with `modelDir` (`models/nllb`) and `maxNewTokens` (`128`). Create `NllbLanguageMapper` to map ISO 639-1 language codes (`es`, `en`, `fr`, `de`, `pt`, `ru`, `zh`, `it`, `ja`, `ko`) to NLLB-200 Flores-200 language tags (`spa_Latn`, `eng_Latn`, etc.), throwing `IllegalArgumentException` on unsupported codes.
  - **Criteria**: Properties bind successfully; language mapper correctly maps supported codes and throws `IllegalArgumentException` for unknown/unsupported languages.
  - **Depends on**: T1
  - **Spec Reference**: REQ-1.2, REQ-3.1, REQ-3.2

- [x] **T3: Implement `FastNllbAdapter` Conforming to `TranslationEnginePort` & Resource Lifecycle**
  - **Description**: Implement `FastNllbAdapter` in `com.lucalzt.mctranslator.infrastructure.adapter.out.nllb` implementing `TranslationEnginePort`. In constructor, validate existence of `tokenizer.json`, `encoder_model_quantized.onnx`, and `decoder_model_merged_quantized.onnx` in `modelDir`, throwing descriptive `IllegalStateException` if missing. Initialize `OrtEnvironment` and `OrtSession` instances for encoder and decoder. Implement translation pipeline (`translate` method): tokenization, encoder inference, autoregressive decoder inference with KV cache and language tokens, detokenization, duration measurement, and returning `TranslationResult` with `TRANSLATED_FAST` and `FAST` engine type. Implement `@PreDestroy` `close()` method to release native ONNX sessions and environment.
  - **Criteria**: Conforms to `TranslationEnginePort`; validates model files on startup; executes local ONNX inference pipeline correctly; releases native resources cleanly on shutdown via `@PreDestroy`.
  - **Depends on**: T2
  - **Spec Reference**: REQ-2.1, REQ-2.2, REQ-4.1, REQ-4.2, REQ-4.3, REQ-5.1, REQ-5.2

- [x] **T4: Write Unit & Integration Tests for `FastNllbAdapter` Components**
  - **Description**: Implement unit and integration tests under `src/test/java/com/lucalzt/mctranslator/infrastructure/adapter/out/nllb/`. Verify language code mapping, configuration property binding, startup validation errors on missing model files, and translation execution/lifecycle cleanup behavior.
  - **Criteria**: All tests pass successfully (`mvnw test`); robust coverage across validation, mapping, and execution paths.
  - **Depends on**: T3
  - **Spec Reference**: All requirements and scenarios

---

## Review Workload Forecast

Decision needed before apply: No
Chained PRs recommended: No
400-line budget risk: Low

---

## Verification Plan

1. Execute test suite: `mvnw test`
2. Verify all unit and integration tests for `FastNllbAdapter`, `NllbLanguageMapper`, and `NllbEngineProperties` pass successfully.
3. Verify zero domain coupling to ONNX runtime or infrastructure frameworks.

---

## Apply Progress

**Status**: All tasks completed (apply phase done).

- T1 done: `onnxruntime` 1.26.0 + tokenizers deps added to `pom.xml`; project builds.
- T2 done: `NllbEngineProperties` (prefix `mctranslator.engine.nllb`, `modelDir=models/nllb`, `maxNewTokens=128`) and `NllbLanguageMapper` (10 ISO-639-1 codes -> Flores-200 tags, `IllegalArgumentException` on unsupported).
- T3 done: `FastNllbAdapter` - ONNX Runtime pipeline (tokenize -> encoder -> greedy decoder with merged KV-cache `If` branches -> detokenize), startup asset validation with descriptive exceptions, `@PreDestroy close()` releasing native resources. The decoder follows the Hugging Face NLLB convention: seeded with EOS (`decoder_start_token_id=2`), first generated token forced to the target Flores-200 tag (`forced_bos_token_id` semantics), `use_cache_branch=false` only at step 0 (full key/value compute) and `true` afterwards (decoder cache append; encoder past kept from step 0).
- T4 done: unit + integration tests under `src/test/java/.../nllb/` (mapper, properties binding, startup validation, translation/lifecycle); integration tests gated behind `-Dmctranslator.it.nllb=true` and all 6 pass against the real quantized model ("Hello World" -> "Hola Mundo").
- Full suite: `mvnw test` -> 162 tests, 0 failures, 0 errors, 6 skipped (gated integration).
