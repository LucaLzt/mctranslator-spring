/**
 * Pure domain services of the translation pipeline: variable masking and
 * unmasking ({@code VariableMasker} / {@code VariableUnmasker}) and the
 * scaling heuristic ({@code ScalingHeuristic}).
 *
 * <p>This package holds the stateless domain services and the value objects /
 * records they exchange ({@code MaskedText}, {@code UnmaskResult},
 * {@code GlossaryTermMatch}, {@code ScalingDecision}) as 100% pure Java: zero
 * framework dependencies, with JDK, jspecify and
 * {@code com.lucalzt.mctranslator.domain.model} imports only. The package is
 * {@link org.jspecify.annotations.NullMarked} — every type and member is
 * non-null by default, and there are no {@code @Nullable} elements.
 */
@org.jspecify.annotations.NullMarked
package com.lucalzt.mctranslator.domain.service;
