package com.lucalzt.mctranslator.domain.model;

import java.util.Objects;

/**
 * Value object wrapping a canonical dot-separated JSON path string.
 *
 * <p>Immutable and validated at construction time: the path must be non-null,
 * non-blank and free of empty segments (no leading dot, trailing dot or
 * consecutive dots). The stored string is the canonical identity of the path.
 *
 * @param value the raw dot-separated path string, never {@code null} or blank
 */
public record JsonPath(String value) {

	/**
	 * Compact constructor enforcing the value object invariants.
	 *
	 * @throws NullPointerException     if {@code value} is {@code null}
	 * @throws IllegalArgumentException if {@code value} is blank or contains empty
	 *                                  segments (leading dot, trailing dot or
	 *                                  consecutive dots)
	 */
	public JsonPath {
		Objects.requireNonNull(value, "value must not be null");
		if (value.isBlank()) {
			throw new IllegalArgumentException("value must not be blank");
		}
		if (value.startsWith(".") || value.endsWith(".") || value.contains("..")) {
			throw new IllegalArgumentException("value must not contain empty segments");
		}
	}

	/**
	 * Returns the raw dot-separated path string.
	 *
	 * @return the raw path string
	 */
	@Override
	public String value() {
		return value;
	}

	/**
	 * Returns whether this path starts with the given segments, in order and
	 * case-sensitively.
	 *
	 * <p>The path is split on {@code '.'} and its leading segments are compared
	 * with the given arguments. The method returns {@code true} if and only if
	 * the path has at least as many segments as the given arguments and every
	 * leading segment equals the corresponding argument. An empty varargs list
	 * is a vacuous prefix and always returns {@code true}.
	 *
	 * @param segments the segments to match as a path prefix; each element must be
	 *                 non-null and non-blank
	 * @return {@code true} if the leading path segments equal the given arguments
	 * @throws NullPointerException     if {@code segments} or any of its elements is
	 *                                  {@code null}
	 * @throws IllegalArgumentException if any segment is blank
	 */
	public boolean startsWith(String... segments) {
		Objects.requireNonNull(segments, "segments must not be null");
		String[] pathSegments = value.split("\\.");
		if (segments.length > pathSegments.length) {
			return false;
		}
		for (int i = 0; i < segments.length; i++) {
			String segment = Objects.requireNonNull(segments[i], "segment must not be null");
			if (segment.isBlank()) {
				throw new IllegalArgumentException("segment must not be blank");
			}
			if (!pathSegments[i].equals(segment)) {
				return false;
			}
		}
		return true;
	}
}
