package com.lucalzt.mctranslator.infrastructure.adapter.out.nllb;

import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.NullMarked;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link NllbLanguageMapper}.
 */
@NullMarked
class NllbLanguageMapperTest {

	private final NllbLanguageMapper mapper = new NllbLanguageMapper();

	@Test
	@DisplayName("Should map every supported ISO 639-1 code to its Flores-200 tag")
	void shouldMapAllSupportedCodes() {
		Map<String, String> expected = Map.ofEntries(
				Map.entry("es", "spa_Latn"),
				Map.entry("en", "eng_Latn"),
				Map.entry("fr", "fra_Latn"),
				Map.entry("de", "deu_Latn"),
				Map.entry("pt", "por_Latn"),
				Map.entry("ru", "rus_Cyrl"),
				Map.entry("zh", "zho_Hans"),
				Map.entry("it", "ita_Latn"),
				Map.entry("ja", "jpn_Jpan"),
				Map.entry("ko", "kor_Hang"));
		expected.forEach((code, tag) -> assertThat(mapper.toNllbTag(code)).as("tag of %s", code).isEqualTo(tag));
	}

	@Test
	@DisplayName("Should expose the full set of supported Flores-200 tags")
	void shouldExposeSupportedTags() {
		Set<String> tags = mapper.supportedTags();
		assertThat(tags).containsExactlyInAnyOrder("spa_Latn", "eng_Latn", "fra_Latn", "deu_Latn", "por_Latn",
				"rus_Cyrl", "zho_Hans", "ita_Latn", "jpn_Jpan", "kor_Hang");
	}

	@ParameterizedTest
	@ValueSource(strings = { "xx", "en-US", "spa_Latn", "Español", "" })
	@DisplayName("Should reject unsupported or blank language codes")
	void shouldRejectUnsupportedCodes(String code) {
		assertThatThrownBy(() -> mapper.toNllbTag(code))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("Unsupported language code")
				.hasMessageContaining(code.isEmpty() ? "must not be blank" : "Supported codes are:");
	}

	@Test
	@DisplayName("Should reject null language codes")
	void shouldRejectNullCode() {
		assertThatThrownBy(() -> mapper.toNllbTag(null)).isInstanceOf(NullPointerException.class);
	}
}
