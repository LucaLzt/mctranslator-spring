package com.lucalzt.mctranslator.infrastructure.adapter.out.nllb;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.NullMarked;
import org.springframework.stereotype.Component;

/**
 * Maps ISO 639-1 language codes to their NLLB-200 Flores-200 language tags.
 *
 * <p>The supported codes are the Minecraft translation targets handled by the fast engine:
 * {@code es, en, fr, de, pt, ru, zh, it, ja, ko}. The returned tags match the added tokens of the
 * bundled NLLB-200 tokenizer (e.g. {@code spa_Latn}, {@code eng_Latn}), so they can be looked up
 * directly as single token ids during tokenization.
 */
@Component
@NullMarked
public class NllbLanguageMapper {

	private static final Map<String, String> ISO_TO_FLORES = Map.ofEntries(
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

	/**
	 * Maps an ISO 639-1 language code to its NLLB-200 Flores-200 language tag.
	 *
	 * @param isoCode the ISO 639-1 language code, never {@code null} or blank
	 * @return the NLLB-200 Flores-200 language tag, never {@code null}
	 * @throws IllegalArgumentException if the code is blank, {@code null} or unsupported
	 */
	public String toNllbTag(String isoCode) {
		Objects.requireNonNull(isoCode, "isoCode must not be null");
		if (isoCode.isBlank()) {
			throw new IllegalArgumentException("Unsupported language code: language code must not be blank");
		}
		String tag = ISO_TO_FLORES.get(isoCode);
		if (tag == null) {
			throw new IllegalArgumentException(
					"Unsupported language code for fast NLLB engine: '" + isoCode
							+ "'. Supported codes are: " + String.join(", ", ISO_TO_FLORES.keySet()));
		}
		return tag;
	}

	/**
	 * Returns the set of all NLLB-200 Flores-200 language tags supported by this mapper.
	 *
	 * @return an unmodifiable set of Flores-200 tags, never {@code null}
	 */
	public Set<String> supportedTags() {
		return Set.copyOf(ISO_TO_FLORES.values());
	}
}
