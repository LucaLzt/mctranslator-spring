package com.lucalzt.mctranslator.domain.model;

import java.util.Objects;

/**
 * Value object identifying a modpack by its name and version.
 *
 * <p>Immutable and validated at construction time: both components must be
 * non-null and non-blank. Components are stored exactly as sourced (no
 * trimming) and participate in {@link com.lucalzt.mctranslator.domain.model
 * TranslationKey} identity.
 *
 * @param name    the modpack name, never {@code null} or blank
 * @param version the modpack version, never {@code null} or blank
 */
public record ModpackId(String name, String version) {

	/**
	 * Compact constructor enforcing the value object invariants.
	 *
	 * @throws NullPointerException     if {@code name} or {@code version} is
	 *                                  {@code null}
	 * @throws IllegalArgumentException if {@code name} or {@code version} is blank
	 */
	public ModpackId {
		Objects.requireNonNull(name, "name must not be null");
		Objects.requireNonNull(version, "version must not be null");
		if (name.isBlank()) {
			throw new IllegalArgumentException("name must not be blank");
		}
		if (version.isBlank()) {
			throw new IllegalArgumentException("version must not be blank");
		}
	}

	/**
	 * Returns the modpack name.
	 *
	 * @return the modpack name
	 */
	@Override
	public String name() {
		return name;
	}

	/**
	 * Returns the modpack version.
	 *
	 * @return the modpack version
	 */
	@Override
	public String version() {
		return version;
	}
}
