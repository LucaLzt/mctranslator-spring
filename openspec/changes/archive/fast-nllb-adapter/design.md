# Design: Fast NLLB-200 ONNX Adapter (`fast-nllb-adapter`)

## Architecture & Component Boundaries

The `fast-nllb-adapter` change introduces the local execution engine for machine translation using ONNX Runtime and NLLB-200 (600M) distilled model assets. It adheres strictly to Hexagonal Architecture standards:
- **Domain Layer (`com.lucalzt.mctranslator.domain`)**: Remains pure Java with zero framework coupling. It defines the outbound port `TranslationEnginePort` and value objects (`TranslationKey`, `LanguageCode`, `TranslationResult`, `TranslationEngineType`).
- **Infrastructure Layer (`com.lucalzt.mctranslator.infrastructure.adapter.out.nllb`)**: Houses the concrete adapter implementation (`FastNllbAdapter`), configuration properties (`NllbEngineProperties`), language code mapping (`NllbLanguageMapper`), and ONNX Runtime resource lifecycle management.

### Component Diagram

```
[Domain Use Case / Scaling Heuristic]
           │
           ▼ (calls outbound port)
   [TranslationEnginePort] (domain/port/out)
           ▲
           │ implements
[FastNllbAdapter] (infrastructure/adapter/out/nllb)
  ├─ NllbEngineProperties (Configuration)
  ├─ NllbLanguageMapper (Language Code Mapping)
  ├─ HuggingFaceTokenizer / SentencePiece (Tokenization)
  └─ ONNX Runtime OrtEnvironment & OrtSessions
       ├─ encoder_model_quantized.onnx
       └─ decoder_model_merged_quantized.onnx
```

## Data Model & File Assets

The adapter relies on static local model assets placed in the configured directory (defaulting to `models/nllb/`):
- `tokenizer.json`: Hugging Face tokenizer configuration and vocabulary/merge rules (SentencePiece / BPE).
- `encoder_model_quantized.onnx`: Quantized NLLB-200 encoder model.
- `decoder_model_merged_quantized.onnx`: Quantized NLLB-200 decoder model with merged KV cache.

## Detailed Component Design

### 1. Configuration Properties (`NllbEngineProperties`)
- **Annotation**: `@Component`, `@ConfigurationProperties(prefix = "mctranslator.engine.nllb")`.
- **Properties**:
  - `String modelDir = "models/nllb"`: Root directory containing ONNX files and tokenizer.
  - `int maxNewTokens = 128`: Maximum generated tokens length.

### 2. Language Code Mapping (`NllbLanguageMapper`)
- Maps standard ISO 639-1 / language codes to NLLB-200 Flores-200 language tags:
  - `es` -> `spa_Latn`
  - `en` -> `eng_Latn`
  - `fr` -> `fra_Latn`
  - `de` -> `deu_Latn`
  - `pt` -> `por_Latn`
  - `ru` -> `rus_Latn`
  - `zh` -> `zho_Latn`
  - `it` -> `ita_Latn`
  - `ja` -> `jpn_Latn`
  - `ko` -> `kor_Latn`
- Throws `IllegalArgumentException` if an unsupported language code is requested.

### 3. Translation Execution Pipeline (`FastNllbAdapter`)
- Implements `TranslationEnginePort`.
- **Initialization**:
  - Validates existence of `tokenizer.json`, `encoder_model_quantized.onnx`, and `decoder_model_merged_quantized.onnx` in `modelDir`. Throws `IllegalStateException` if missing.
  - Initializes `OrtEnvironment` and loads `OrtSession` instances for encoder and decoder.
- **Translate Workflow**:
  1. Verify `engineType == TranslationEngineType.FAST`.
  2. Resolve source and target language NLLB tokens via `NllbLanguageMapper`.
  3. Tokenize source text into input tensor (with source language prefix and EOS suffix).
  4. Execute Encoder session to obtain encoder hidden states.
  5. Execute Decoder session autoregressively starting with target language token as decoder start token, utilizing KV cache.
  6. Detokenize generated token IDs into translated string.
  7. Measure duration and return `TranslationResult` with `TranslationStatus.TRANSLATED_FAST` and `TranslationEngineType.FAST`.

### 4. Lifecycle Management
- Annotated with `@Repository` or `@Component`.
- Implements `@PreDestroy` method to explicitly close `OrtSession` and `OrtEnvironment` instances, releasing native memory and preventing leaks.

## Requirements Traceability Matrix

| Requirement ID | Spec Requirement Summary | Design Mechanism |
|---|---|---|
| REQ-1.1 | Include ONNX Runtime 1.26.0 & tokenizers in `pom.xml` | Add `com.microsoft.onnxruntime:onnxruntime` (1.26.0) and tokenizers dependency in `pom.xml`. |
| REQ-1.2 | Configuration properties for model directory | `NllbEngineProperties` bound to `mctranslator.engine.nllb`. |
| REQ-2.1 | Load tokenizer and ONNX sessions from model directory | `FastNllbAdapter` constructor validates paths and initializes `OrtEnvironment` and `OrtSession`. |
| REQ-2.2 | Descriptive initialization exception if assets missing | Check file existence in constructor; throw `IllegalStateException` with clear diagnostics. |
| REQ-3.1 | Map ISO language codes to NLLB-200 tags | `NllbLanguageMapper` static map & lookup. |
| REQ-3.2 | Throw `IllegalArgumentException` for unsupported language | Validation check in `NllbLanguageMapper`. |
| REQ-4.1 | Implement `TranslationEnginePort` | `FastNllbAdapter implements TranslationEnginePort`. |
| REQ-4.2 | Execute tokenization, encoder, decoder, detokenization | Pipeline steps inside `translate(...)`. |
| REQ-4.3 | Return valid `TranslationResult` with FAST metadata | Return `TranslationResult` with status `TRANSLATED_FAST` and engine `FAST`. |
| REQ-5.1 | Lifecycle cleanup via `@PreDestroy` | `@PreDestroy public void close()` closing sessions & environment. |
| REQ-5.2 | Hexagonal Architecture boundaries | Package `com.lucalzt.mctranslator.infrastructure.adapter.out.nllb`. |

## Threat Matrix & Security Considerations

| Threat / Risk | Likelihood | Impact | Mitigations |
|---|---|---|---|
| **Native Memory Leak** | Medium | High (OOM crash in CLI) | Implement `@PreDestroy` cleanup closing `OrtSession` and `OrtEnvironment`. |
| **Missing Model Assets / Corruption** | Medium | High (Startup failure) | Strict validation in constructor throwing descriptive `IllegalStateException`. |
| **Path Traversal via Model Directory Config** | Low | Medium | Resolve paths relative to working directory or validate that model directory is within expected boundaries. |
| **Native Library Loading Failure (DLL/so)** | Low | High (Runtime crash) | Use official `com.microsoft.onnxruntime:onnxruntime` 1.26.0 with built-in native binaries for supported platforms. |
