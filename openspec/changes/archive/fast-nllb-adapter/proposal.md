# Proposal: Fast NLLB-200 ONNX Adapter (`fast-nllb-adapter`)

## Status
- **Status**: done

## Purpose
Implement `FastNllbAdapter` using `com.microsoft.onnxruntime` 1.26.0 and the local NLLB-200 (600M) distilled ONNX model assets in `models/nllb/`. This provides high-performance, locally executed machine translation for Minecraft translation keys (`FAST` engine type) without requiring external Python/HTTP services or cloud APIs, maintaining zero framework coupling in the domain via Hexagonal Architecture (`TranslationEnginePort`).

## Approach
1. **Dependencies & Configuration**: Add `com.microsoft.onnxruntime:onnxruntime` (1.26.0) and Hugging Face tokenizers dependencies to `pom.xml`. Configure model paths (`models/nllb/`) via Spring Boot configuration properties.
2. **Adapter Implementation (`FastNllbAdapter`)**:
   - Implement `TranslationEnginePort` in package `com.lucalzt.mctranslator.infrastructure.adapter.out.nllb`.
   - Load tokenizer (`tokenizer.json` / SentencePiece) and ONNX sessions (`encoder_model_quantized.onnx`, `decoder_model_merged_quantized.onnx`).
   - Implement language code mapping (e.g. `es` -> `spa_Latn`, `en` -> `eng_Latn`).
   - Implement inference execution pipeline: text encoding -> encoder session -> autoregressive decoder session -> text decoding.
3. **Testing**: Add unit and integration tests under `src/test/java/com/lucalzt/mctranslator/infrastructure/adapter/out/nllb/` ensuring robust error handling and resource cleanup.

## Scope
### In-Scope
- Maven dependencies for ONNX Runtime 1.26.0 and tokenizers.
- Configuration properties for NLLB model directory and inference parameters.
- `FastNllbAdapter` implementing `TranslationEnginePort`.
- Language code mapping utilities for NLLB-200 (e.g., `spa_Latn`, `eng_Latn`, etc.).
- Unit tests and integration tests for translation execution.

### Non-Goals
- Training or fine-tuning NLLB models.
- Implementing the `PRECISE` translation engine (which uses API or alternative model).
- External REST/gRPC microservice wrappers for ONNX inference.

## Rollback Plan
If integration issues arise (e.g., native library loading failures, memory issues, or performance regressions):
1. Revert changes to `pom.xml` and configuration files.
2. Remove package `com.lucalzt.mctranslator.infrastructure.adapter.out.nllb`.
3. Fall back to existing stub or alternative implementation of `TranslationEnginePort`.
