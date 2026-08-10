/**
 * Infrastructure adapters for the fast local NLLB-200 translation engine backed by ONNX Runtime.
 *
 * <p>Contains the {@code FastNllbAdapter} implementing the outbound
 * {@code TranslationEnginePort}, its configuration properties ({@code NllbEngineProperties}) and
 * the ISO-639-1 to Flores-200 language mapping ({@code NllbLanguageMapper}). All classes in this
 * package are Spring infrastructure: no domain or application code depends on them.
 */
@org.jspecify.annotations.NullMarked
package com.lucalzt.mctranslator.infrastructure.adapter.out.nllb;
