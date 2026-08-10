# Specification: Fast NLLB-200 ONNX Adapter (`fast-nllb-adapter`)

## Goal
Implement `FastNllbAdapter` adhering to `TranslationEnginePort` using `com.microsoft.onnxruntime` (1.26.0) and local NLLB-200 (600M) distilled ONNX model assets in `models/nllb/`, providing high-performance, locally executed machine translation for Minecraft translation keys (`FAST` engine type) with zero framework coupling in the domain.

## Requirements

### Requirement 1: Dependencies & Configuration Properties
- **REQ-1.1**: The project `pom.xml` MUST include `com.microsoft.onnxruntime:onnxruntime` (version 1.26.0) and required tokenizers dependencies.
- **REQ-1.2**: Spring Boot configuration properties (e.g. under `mctranslator.engine.nllb`) MUST define the model assets root directory (defaulting to `models/nllb/`) and inference parameters.

### Requirement 2: Model & Tokenizer Initialization
- **REQ-2.1**: The adapter MUST load the SentencePiece tokenizer (`tokenizer.json`) and ONNX sessions (`encoder_model_quantized.onnx` and `decoder_model_merged_quantized.onnx`) from the configured model directory.
- **REQ-2.2**: If model assets or tokenizer files are missing or malformed, the adapter MUST throw a descriptive initialization exception rather than failing silently.

### Requirement 3: Language Code Mapping for NLLB-200
- **REQ-3.1**: The adapter MUST map ISO language codes (e.g. `es`, `en`, `fr`, `de`, `pt`, `ru`, `zh`, etc.) to their corresponding NLLB-200 language tags (`spa_Latn`, `eng_Latn`, `fra_Latn`, `deu_Latn`, `por_Latn`, `rus_Cyrl`, `zho_Hans`, etc.).
- **REQ-3.2**: When an unsupported language code is encountered, the adapter MUST throw an `IllegalArgumentException` with clear diagnostic information.

### Requirement 4: Translation Execution Pipeline (`TranslationEnginePort`)
- **REQ-4.1**: The adapter class MUST implement the outbound domain port `TranslationEnginePort` (`com.lucalzt.mctranslator.domain.port.out.TranslationEnginePort`).
- **REQ-4.2**: When `engineType` is `TranslationEngineType.FAST`, the adapter MUST execute text tokenization, encoder inference session, autoregressive decoder inference session, and detokenization.
- **REQ-4.3**: The translate method MUST return a valid `TranslationResult` containing the translated text string, `TranslationEngineType.FAST`, and success status.

### Requirement 5: Resource Management & Hexagonal Architecture
- **REQ-5.1**: The adapter MUST implement proper lifecycle cleanup (closing ONNX `OrtSession` and `OrtEnvironment` instances via `@PreDestroy`) to prevent native memory leaks.
- **REQ-5.2**: The adapter implementation and mapping utilities MUST reside in package `com.lucalzt.mctranslator.infrastructure.adapter.out.nllb` respecting Hexagonal Architecture boundaries.

## Non-Goals
- Training, fine-tuning, or downloading NLLB model files automatically at runtime from Hugging Face if local assets are absent.
- Implementing the `PRECISE` translation engine.
- Exposing ONNX inference via external REST or gRPC microservices.

## Scenarios

### Scenario: Successful Fast Translation Execution (`en` to `es`)
- **Given** valid NLLB-200 ONNX model assets present in `models/nllb/`, source text `"Hello World"`, source language `en`, target language `es`, and engine type `FAST`
- **When** `TranslationEnginePort.translate(...)` is invoked
- **Then** the adapter tokenizes input, runs ONNX encoder and decoder sessions, and returns a `TranslationResult` containing the translated text (e.g. `"¡Hola Mundo!"`), engine type `FAST`, and SUCCESS status.

### Scenario: Missing Model Files Initialization Error
- **Given** NLLB-200 model files are missing from the configured model directory
- **When** `FastNllbAdapter` is initialized
- **Then** an exception is thrown detailing the missing asset files.

### Scenario: Language Code Mapping Validation
- **Given** ISO language code `es`
- **When** mapped for NLLB-200 target specification
- **Then** it correctly resolves to `spa_Latn`.

## Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Native ONNX runtime library loading failure (DLL/so loading error) | Application fails to start or crashes on inference | Use official `com.microsoft.onnxruntime:onnxruntime` 1.26.0 which bundles native binaries for major platforms; test in CI. |
| Native memory leaks from ONNX sessions | Out-of-memory errors in long-running CLI sessions | Explicitly implement `@PreDestroy` to close `OrtSession` and `OrtEnvironment` instances. |
| High CPU inference latency | Slow batch translation for large modpacks | Utilize quantized ONNX models (`*_quantized.onnx`) and efficient batched tokenization/generation where applicable. |

## Operational Note — Integration Test Memory Spike (9 GB)

**Main error**: Every run of `FastNllbAdapterIntegrationTest` (with `-Dmctranslator.it.nllb=true`) peaked at roughly 9–10 GB of RAM, even though the model assets are only ~850 MB on disk.

**Root cause**: The test instantiated a new `FastNllbAdapter` in a `@BeforeEach` block, i.e. once per test. Each adapter loads a full ONNX session (quantized encoder + decoder) whose memory lives **off-heap in native C++** — invisible to the JVM Garbage Collector. Because `close()` was never called, the `OrtSession`/`OrtEnvironment` handles stayed open for the whole process lifetime, leaking ~1.5 GB of native memory per test (6 tests ≈ 9 GB).

**Solution (implemented)**: Share a single class-level static adapter and bound it to the JUnit class lifecycle:
- `@BeforeAll static setUpAll()` creates one adapter for the whole class; `@AfterAll static tearDownAll()` calls `close()` once (idempotent) to release all native resources.
- The lifecycle test `shouldGuardAfterCloseAndKeepCloseIdempotent` now uses its own local adapter inside a `try-finally`, so it never closes the shared instance and cannot break the remaining tests.

**Result**: peak native memory dropped from ~9–10 GB to ~1.5–3 GB and the integration suite runs in ~14 s instead of ~82 s (the model is loaded once instead of six times).
