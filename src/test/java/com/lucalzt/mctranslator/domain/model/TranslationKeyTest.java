package com.lucalzt.mctranslator.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TranslationKey}, covering validation rejection (R10) and
 * identity equality across all four components (R16). Pure JUnit 6 + AssertJ, no
 * Spring context.
 */
class TranslationKeyTest {

	private static final JsonPath PATH = new JsonPath("quest.description.task1");
	private static final String ORIGINAL_TEXT = "Complete the quest";
	private static final LanguageCode LANGUAGE = new LanguageCode("es");
	private static final ModpackId MODPACK = new ModpackId("BetterMC", "1.21.1");

	@Test
	@DisplayName("Accepts valid identity components and exposes them via accessors")
	void acceptsValidKey() {
		TranslationKey key = new TranslationKey(PATH, ORIGINAL_TEXT, LANGUAGE, MODPACK);

		assertThat(key.path()).isEqualTo(PATH);
		assertThat(key.originalText()).isEqualTo(ORIGINAL_TEXT);
		assertThat(key.targetLanguage()).isEqualTo(LANGUAGE);
		assertThat(key.modpack()).isEqualTo(MODPACK);
	}

	@Test
	@DisplayName("Rejects a null path, null language or null modpack with NullPointerException")
	void rejectsNullComponents() {
		assertThatThrownBy(() -> new TranslationKey(null, ORIGINAL_TEXT, LANGUAGE, MODPACK))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new TranslationKey(PATH, ORIGINAL_TEXT, null, MODPACK))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new TranslationKey(PATH, ORIGINAL_TEXT, LANGUAGE, null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Rejects blank original text with IllegalArgumentException")
	void rejectsBlankOriginalText() {
		assertThatThrownBy(() -> new TranslationKey(PATH, "", LANGUAGE, MODPACK))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TranslationKey(PATH, "   ", LANGUAGE, MODPACK))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Equality is by all four components with consistent hash codes")
	void equalityIsByIdentity() {
		TranslationKey first = new TranslationKey(PATH, ORIGINAL_TEXT, LANGUAGE, MODPACK);
		TranslationKey second = new TranslationKey(PATH, ORIGINAL_TEXT, LANGUAGE, MODPACK);
		TranslationKey differentLanguage = new TranslationKey(PATH, ORIGINAL_TEXT, new LanguageCode("en"), MODPACK);

		assertThat(first).isEqualTo(second);
		assertThat(first.hashCode()).isEqualTo(second.hashCode());
		assertThat(first).isNotEqualTo(differentLanguage);
	}
}
