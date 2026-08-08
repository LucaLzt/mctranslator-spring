package com.lucalzt.mctranslator.domain.model;

/**
 * Type of translation engine selected for a key.
 *
 * <p>Plain enum, no serialization mapping: this is the {@code ScalingHeuristic}
 * decision output and the engine metadata carried by {@code TranslationResult}.
 */
public enum TranslationEngineType {

	/** Fast embedded engine (NLLB-200 via ONNX). */
	FAST,

	/** Precise external engine (Qwen3.5-4B via llama-server). */
	PRECISE
}
