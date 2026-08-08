/**
 * Pure domain model of the translation pipeline.
 *
 * <p>This package holds the immutable domain types ({@code TranslationKey},
 * {@code TranslationResult}, {@code GlossaryEntry}, the value objects and the
 * enums) as 100% pure Java: zero framework dependencies, JDK and jspecify
 * imports only. The package is {@link org.jspecify.annotations.NullMarked} —
 * every type and member is non-null by default, and the only
 * {@link org.jspecify.annotations.Nullable} elements are
 * {@code TranslationResult.engine} and {@code TranslationResult.warning}.
 */
@org.jspecify.annotations.NullMarked
package com.lucalzt.mctranslator.domain.model;
