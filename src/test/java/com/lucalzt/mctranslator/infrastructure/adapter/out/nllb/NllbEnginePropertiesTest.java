package com.lucalzt.mctranslator.infrastructure.adapter.out.nllb;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NllbEngineProperties}.
 */
@NullMarked
class NllbEnginePropertiesTest {

	@TempDir
	Path tempDir;

	@Test
	@DisplayName("Should provide documented defaults")
	void shouldProvideDefaults() {
		NllbEngineProperties properties = new NllbEngineProperties();
		assertThat(properties.getModelDir()).isEqualTo("models/nllb");
		assertThat(properties.getMaxNewTokens()).isEqualTo(128);
	}

	@Test
	@DisplayName("Should expose configured values through setters")
	void shouldExposeConfiguredValues() {
		NllbEngineProperties properties = new NllbEngineProperties();
		properties.setModelDir("custom/models");
		properties.setMaxNewTokens(64);
		assertThat(properties.getModelDir()).isEqualTo("custom/models");
		assertThat(properties.getMaxNewTokens()).isEqualTo(64);
	}

	@Test
	@DisplayName("Should resolve a relative model directory against the working directory")
	void shouldResolveRelativeModelDir() {
		NllbEngineProperties properties = new NllbEngineProperties();
		properties.setModelDir("models/nllb");
		Path resolved = properties.resolveModelDir();
		assertThat(resolved).isEqualTo(Paths.get("models/nllb"));
	}

	@Test
	@DisplayName("Should keep an absolute model directory unchanged")
	void shouldKeepAbsoluteModelDir() {
		Path absolute = tempDir.resolve("my-models").toAbsolutePath().normalize();
		NllbEngineProperties properties = new NllbEngineProperties();
		properties.setModelDir(absolute.toString());
		assertThat(properties.resolveModelDir()).isEqualTo(absolute);
	}
}
