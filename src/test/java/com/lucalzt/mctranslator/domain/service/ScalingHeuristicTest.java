package com.lucalzt.mctranslator.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lucalzt.mctranslator.domain.model.GlossaryEntry;
import com.lucalzt.mctranslator.domain.model.GlossaryEntryClassification;
import com.lucalzt.mctranslator.domain.model.JsonPath;
import com.lucalzt.mctranslator.domain.model.TranslationEngineType;

/**
 * Unit tests for {@link ScalingHeuristic}, covering the 6-rule precedence
 * table of Requirement 9 (one scenario per rule, first-match-wins), the
 * pinned rule-5 LORE guard equivalence, the empty-glossary behavior, the
 * masked-text-only evaluation, the whole-word case-sensitive glossary
 * matching (Requirement 9/H7), the word-counting contract of Requirement 12
 * and the null-rejection of Requirement 9. Pure JUnit 6 + AssertJ, no Spring
 * context.
 */
class ScalingHeuristicTest {

	private static final ScalingHeuristic HEURISTIC = new ScalingHeuristic();

	@Test
	@DisplayName("Rule 1: quest.description path decides PRECISE/1 on short text, winning over rule 5")
	void questDescriptionPathDecidesPreciseRule1() {
		ScalingDecision decision = HEURISTIC.suggest(
				new JsonPath("quest.description.task1"), "Short text", List.of());

		assertThat(decision.engine()).isEqualTo(TranslationEngineType.PRECISE);
		assertThat(decision.matchedRule()).isEqualTo(1);
		assertThat(decision.glossaryMatches()).isEmpty();
	}

	@Test
	@DisplayName("Rule 1: lore and advancement prefixes decide PRECISE/1")
	void loreAndAdvancementPathsDecidePreciseRule1() {
		ScalingDecision lore = HEURISTIC.suggest(new JsonPath("lore.story"), "Some text", List.of());
		ScalingDecision advancement = HEURISTIC.suggest(
				new JsonPath("advancement.husbandry.root"), "Some text", List.of());

		assertThat(lore.engine()).isEqualTo(TranslationEngineType.PRECISE);
		assertThat(lore.matchedRule()).isEqualTo(1);
		assertThat(advancement.engine()).isEqualTo(TranslationEngineType.PRECISE);
		assertThat(advancement.matchedRule()).isEqualTo(1);
	}

	@Test
	@DisplayName("Rule 2: AMBIGUOUS term decides PRECISE/2 with the match, winning over rule 4")
	void ambiguousTermDecidesPreciseRule2() {
		ScalingDecision decision = HEURISTIC.suggest(
				new JsonPath("item.artifact"),
				"The ancient temple lies beyond",
				List.of(entry("ancient temple", GlossaryEntryClassification.AMBIGUOUS)));

		assertThat(decision.engine()).isEqualTo(TranslationEngineType.PRECISE);
		assertThat(decision.matchedRule()).isEqualTo(2);
		assertThat(decision.glossaryMatches()).hasSize(1);
		assertThat(decision.glossaryMatches().get(0).term()).isEqualTo("ancient temple");
		assertThat(decision.glossaryMatches().get(0).classification())
				.isEqualTo(GlossaryEntryClassification.AMBIGUOUS);
	}

	@Test
	@DisplayName("Rule 2: whole-word case-sensitive matching — Iron matches, iron inside irony does not")
	void wholeWordCaseSensitiveMatching() {
		ScalingDecision decision = HEURISTIC.suggest(
				new JsonPath("some.key"),
				"Iron is common; irony is not",
				List.of(entry("iron", GlossaryEntryClassification.AMBIGUOUS),
						entry("Iron", GlossaryEntryClassification.AMBIGUOUS)));

		assertThat(decision.engine()).isEqualTo(TranslationEngineType.PRECISE);
		assertThat(decision.matchedRule()).isEqualTo(2);
		assertThat(decision.glossaryMatches()).hasSize(1);
		assertThat(decision.glossaryMatches().get(0).term()).isEqualTo("Iron");
		assertThat(decision.glossaryMatches().get(0).classification())
				.isEqualTo(GlossaryEntryClassification.AMBIGUOUS);
	}

	@Test
	@DisplayName("Rule 3: exactly 30 words does NOT match — gui.menu falls to rule 4 FAST/4")
	void exactlyThirtyWordsDoesNotTriggerRule3() {
		ScalingDecision decision = HEURISTIC.suggest(new JsonPath("gui.menu"), words(30), List.of());

		assertThat(decision.engine()).isEqualTo(TranslationEngineType.FAST);
		assertThat(decision.matchedRule()).isEqualTo(4);
	}

	@Test
	@DisplayName("Rule 3: 31 words decides PRECISE/3, winning over rule 4")
	void thirtyOneWordsTriggersRule3() {
		ScalingDecision decision = HEURISTIC.suggest(new JsonPath("item.sword"), words(31), List.of());

		assertThat(decision.engine()).isEqualTo(TranslationEngineType.PRECISE);
		assertThat(decision.matchedRule()).isEqualTo(3);
		assertThat(decision.glossaryMatches()).isEmpty();
	}

	@Test
	@DisplayName("Rule 4: item, block, entity and gui paths decide FAST/4 on short text")
	void fastPathsDecideFastRule4() {
		List<String> paths = List.of("item.sword", "block.stone", "entity.zombie", "gui.container");

		for (String path : paths) {
			ScalingDecision decision = HEURISTIC.suggest(new JsonPath(path), "Hello", List.of());

			assertThat(decision.engine()).isEqualTo(TranslationEngineType.FAST);
			assertThat(decision.matchedRule()).isEqualTo(4);
		}
	}

	@Test
	@DisplayName("Rule 5: short text with only PLAIN non-matching entries decides FAST/5")
	void shortTextWithPlainGlossaryDecidesFastRule5() {
		ScalingDecision decision = HEURISTIC.suggest(
				new JsonPath("random.key"),
				"Hello there",
				List.of(entry("stone", GlossaryEntryClassification.PLAIN),
						entry("pickaxe", GlossaryEntryClassification.PLAIN)));

		assertThat(decision.engine()).isEqualTo(TranslationEngineType.FAST);
		assertThat(decision.matchedRule()).isEqualTo(5);
		assertThat(decision.glossaryMatches()).isEmpty();
	}

	@Test
	@DisplayName("Rule 5: exactly 8 words is a candidate (inclusive threshold) — FAST/5")
	void exactlyEightWordsTriggersRule5() {
		ScalingDecision decision = HEURISTIC.suggest(new JsonPath("random.key"), words(8), List.of());

		assertThat(decision.engine()).isEqualTo(TranslationEngineType.FAST);
		assertThat(decision.matchedRule()).isEqualTo(5);
	}

	@Test
	@DisplayName("Pinned: LORE term on short text falls to rule 6 with the SAME FAST outcome")
	void loreTermFallsToRule6PinnedEquivalence() {
		ScalingDecision decision = HEURISTIC.suggest(
				new JsonPath("random.key"),
				"The dark lord rises",
				List.of(entry("dark lord", GlossaryEntryClassification.LORE)));

		assertThat(decision.engine()).isEqualTo(TranslationEngineType.FAST);
		assertThat(decision.matchedRule()).isEqualTo(6);
		assertThat(decision.glossaryMatches()).isEmpty();
	}

	@Test
	@DisplayName("Rule 5: the same text with a PLAIN dark lord entry decides FAST/5 with the match")
	void plainDarkLordMatchesRule5() {
		ScalingDecision decision = HEURISTIC.suggest(
				new JsonPath("random.key"),
				"The dark lord rises",
				List.of(entry("dark lord", GlossaryEntryClassification.PLAIN)));

		assertThat(decision.engine()).isEqualTo(TranslationEngineType.FAST);
		assertThat(decision.matchedRule()).isEqualTo(5);
		assertThat(decision.glossaryMatches()).hasSize(1);
		assertThat(decision.glossaryMatches().get(0).term()).isEqualTo("dark lord");
		assertThat(decision.glossaryMatches().get(0).classification())
				.isEqualTo(GlossaryEntryClassification.PLAIN);
	}

	@Test
	@DisplayName("Empty glossary: short text fires rule 5 (rule 2 vacuous false, no-LORE vacuously true)")
	void emptyGlossaryShortTextFiresRule5() {
		ScalingDecision decision = HEURISTIC.suggest(new JsonPath("random.key"), "Hi", List.of());

		assertThat(decision.engine()).isEqualTo(TranslationEngineType.FAST);
		assertThat(decision.matchedRule()).isEqualTo(5);
		assertThat(decision.glossaryMatches()).isEmpty();
	}

	@Test
	@DisplayName("Empty glossary: 40 words triggers rule 3 PRECISE/3")
	void emptyGlossaryLongTextTriggersRule3() {
		ScalingDecision decision = HEURISTIC.suggest(new JsonPath("random.key"), words(40), List.of());

		assertThat(decision.engine()).isEqualTo(TranslationEngineType.PRECISE);
		assertThat(decision.matchedRule()).isEqualTo(3);
		assertThat(decision.glossaryMatches()).isEmpty();
	}

	@Test
	@DisplayName("Rule 6: default fall-through on a plain path with empty glossary — FAST/6")
	void defaultFallThroughToRule6() {
		// 10 words: rule 5 (<= 8) is blocked, rule 3 (> 30) is not reached,
		// so rules 1-5 all fail and the default rule 6 decides. NOTE: the spec
		// scenario example "A plain sentence" (3 words) cannot reach rule 6 —
		// under the pinned empty-glossary literal reading, any <= 8-word text
		// fires rule 5 first; a > 8-word text is required for fall-through.
		ScalingDecision decision = HEURISTIC.suggest(
				new JsonPath("some.other.key"), words(10), List.of());

		assertThat(decision.engine()).isEqualTo(TranslationEngineType.FAST);
		assertThat(decision.matchedRule()).isEqualTo(6);
		assertThat(decision.glossaryMatches()).isEmpty();
	}

	@Test
	@DisplayName("Evaluation is on the masked text only — tokens count as words, never the raw 40 words")
	void evaluatesWordCountOnMaskedTextOnly() {
		String maskedText = "A __VAR_0__ with __VAR_1__ detail";

		// The raw (unmasked) form of this text contains 40 words; the masked
		// form counts as 5 words, so rule 3 must NOT fire on gui.menu.
		assertThat(ScalingHeuristic.countWords(maskedText)).isEqualTo(5);
		ScalingDecision decision = HEURISTIC.suggest(new JsonPath("gui.menu"), maskedText, List.of());

		assertThat(decision.engine()).isEqualTo(TranslationEngineType.FAST);
		assertThat(decision.matchedRule()).isEqualTo(4);
	}

	@Test
	@DisplayName("countWords: every whitespace-separated token counts, punctuation-only included")
	void countWordsBasicCounting() {
		assertThat(ScalingHeuristic.countWords("Hello, world!")).isEqualTo(2);
		assertThat(ScalingHeuristic.countWords("...")).isEqualTo(1);
	}

	@Test
	@DisplayName("countWords: a masked token counts as exactly one word")
	void countWordsMaskedTokenIsOneWord() {
		assertThat(ScalingHeuristic.countWords("A __VAR_0__ with __VAR_1__")).isEqualTo(4);
	}

	@Test
	@DisplayName("countWords: blank and whitespace-only text counts as zero")
	void countWordsBlankIsZero() {
		assertThat(ScalingHeuristic.countWords("   ")).isEqualTo(0);
	}

	@Test
	@DisplayName("Masked-token safety: VAR and 0 letter terms never match inside __VAR_0__")
	void maskedTokenNeverMatchesLetterTerm() {
		ScalingDecision decision = HEURISTIC.suggest(
				new JsonPath("random.key"),
				"A __VAR_0__",
				List.of(entry("VAR", GlossaryEntryClassification.AMBIGUOUS),
						entry("0", GlossaryEntryClassification.AMBIGUOUS)));

		assertThat(decision.engine()).isEqualTo(TranslationEngineType.FAST);
		assertThat(decision.matchedRule()).isEqualTo(5);
		assertThat(decision.glossaryMatches()).isEmpty();
	}

	@Test
	@DisplayName("Rejects a null path with NullPointerException")
	void rejectsNullPath() {
		assertThatThrownBy(() -> HEURISTIC.suggest(null, "text", List.of()))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Rejects a null maskedText with NullPointerException")
	void rejectsNullMaskedText() {
		assertThatThrownBy(() -> HEURISTIC.suggest(new JsonPath("some.key"), null, List.of()))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Rejects a null glossary list with NullPointerException")
	void rejectsNullGlossary() {
		assertThatThrownBy(() -> HEURISTIC.suggest(new JsonPath("some.key"), "text", null))
				.isInstanceOf(NullPointerException.class);
	}

	/**
	 * Builds a whitespace-separated string of {@code n} words.
	 *
	 * @param n the number of words, must be at least 1
	 * @return the {@code n}-word string
	 */
	private static String words(int n) {
		return "word ".repeat(n - 1) + "word";
	}

	/**
	 * Builds a glossary entry with the given term and classification; the
	 * translation is set to the term itself.
	 *
	 * @param term           the entry term
	 * @param classification the entry classification
	 * @return the glossary entry
	 */
	private static GlossaryEntry entry(String term, GlossaryEntryClassification classification) {
		return new GlossaryEntry(term, term, classification);
	}
}
