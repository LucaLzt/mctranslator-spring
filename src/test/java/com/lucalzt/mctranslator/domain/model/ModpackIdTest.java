package com.lucalzt.mctranslator.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ModpackId}, covering validation rejection (R9) and
 * value equality (R16). Pure JUnit 6 + AssertJ, no Spring context.
 */
class ModpackIdTest {

	@Test
	@DisplayName("Accepts a valid name and version and exposes them via accessors")
	void acceptsValidModpackId() {
		ModpackId modpack = new ModpackId("BetterMC", "1.21.1");

		assertThat(modpack.name()).isEqualTo("BetterMC");
		assertThat(modpack.version()).isEqualTo("1.21.1");
	}

	@Test
	@DisplayName("Rejects a null name or a null version with NullPointerException")
	void rejectsNullComponents() {
		assertThatThrownBy(() -> new ModpackId(null, "1.21.1")).isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new ModpackId("BetterMC", null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Rejects a blank name or a blank version with IllegalArgumentException")
	void rejectsBlankComponents() {
		assertThatThrownBy(() -> new ModpackId("   ", "1.21.1")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ModpackId("BetterMC", "")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Equality is by both components with consistent hash codes")
	void equalityIsByBothComponents() {
		ModpackId first = new ModpackId("BetterMC", "1.21.1");
		ModpackId second = new ModpackId("BetterMC", "1.21.1");
		ModpackId differentVersion = new ModpackId("BetterMC", "1.20.4");

		assertThat(first).isEqualTo(second);
		assertThat(first.hashCode()).isEqualTo(second.hashCode());
		assertThat(first).isNotEqualTo(differentVersion);
	}
}
