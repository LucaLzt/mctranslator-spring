package com.lucalzt.mctranslator.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.lucalzt.mctranslator.domain.model.GlossaryEntryClassification;
import com.lucalzt.mctranslator.domain.model.TranslationEngineType;

/**
 * Unit tests for {@link ScalingDecision}, covering validation rejection (R10),
 * defensive copying of the glossary-matches list (R10) and the
 * heuristic-produced value contract for rules 1–6 (R10, documented not
 * re-validated — D7). Pure JUnit 6 + AssertJ, no Spring context.
 */
class ScalingDecisionTest {

	@Test
	@DisplayName("Accepts a valid rule-2 decision and preserves its components")
	void acceptsValidRule2Decision() {
		List<GlossaryTermMatch> matches = List.of(
				new GlossaryTermMatch("ancient temple", GlossaryEntryClassification.AMBIGUOUS),
				new GlossaryTermMatch("temple", GlossaryEntryClassification.PLAIN));

		ScalingDecision decision = new ScalingDecision(TranslationEngineType.PRECISE, 2, matches);

		assertThat(decision.engine()).isEqualTo(TranslationEngineType.PRECISE);
		assertThat(decision.matchedRule()).isEqualTo(2);
		assertThat(decision.glossaryMatches()).containsExactlyElementsOf(matches);
	}

	@Test
	@DisplayName("Accepts a valid rule-5 decision and preserves its components")
	void acceptsValidRule5Decision() {
		List<GlossaryTermMatch> matches = List.of(
				new GlossaryTermMatch("dark lord", GlossaryEntryClassification.PLAIN));

		ScalingDecision decision = new ScalingDecision(TranslationEngineType.FAST, 5, matches);

		assertThat(decision.engine()).isEqualTo(TranslationEngineType.FAST);
		assertThat(decision.matchedRule()).isEqualTo(5);
		assertThat(decision.glossaryMatches()).containsExactlyElementsOf(matches);
	}

	@Test
	@DisplayName("Rejects a matchedRule outside 1..6 with IllegalArgumentException")
	void rejectsOutOfRangeMatchedRule() {
		assertThatThrownBy(() -> new ScalingDecision(TranslationEngineType.PRECISE, 0, List.of()))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ScalingDecision(TranslationEngineType.PRECISE, 7, List.of()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Rejects a null engine with NullPointerException")
	void rejectsNullEngine() {
		assertThatThrownBy(() -> new ScalingDecision(null, 1, List.of()))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Rejects a null glossaryMatches list with NullPointerException")
	void rejectsNullGlossaryMatches() {
		assertThatThrownBy(() -> new ScalingDecision(TranslationEngineType.FAST, 1, null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Rejects a null glossary match element with NullPointerException")
	void rejectsNullGlossaryMatchElement() {
		assertThatThrownBy(() -> new ScalingDecision(TranslationEngineType.FAST, 1,
				new ArrayList<>(List.of((GlossaryTermMatch) null))))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Defensive copy: caller mutation after construction does not affect the instance")
	void defensiveCopyAgainstCallerMutation() {
		List<GlossaryTermMatch> matches = new ArrayList<>(
				List.of(new GlossaryTermMatch("ancient temple", GlossaryEntryClassification.AMBIGUOUS)));
		ScalingDecision decision = new ScalingDecision(TranslationEngineType.PRECISE, 2, matches);

		matches.add(new GlossaryTermMatch("temple", GlossaryEntryClassification.PLAIN));

		assertThat(decision.glossaryMatches()).hasSize(1);
		assertThat(decision.glossaryMatches().get(0).term()).isEqualTo("ancient temple");
	}

	@Test
	@DisplayName("Accessor-returned glossaryMatches list is immutable")
	void accessorListIsImmutable() {
		ScalingDecision decision = new ScalingDecision(TranslationEngineType.FAST, 1, List.of());

		assertThatThrownBy(() -> decision.glossaryMatches()
				.add(new GlossaryTermMatch("temple", GlossaryEntryClassification.PLAIN)))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@Test
	@DisplayName("Heuristic-produced decisions satisfy the value contract for every rule 1-6")
	void heuristicProducedDecisionsSatisfyContract() {
		for (int rule = 1; rule <= 6; rule++) {
			TranslationEngineType expectedEngine = rule <= 3
					? TranslationEngineType.PRECISE
					: TranslationEngineType.FAST;
			List<GlossaryTermMatch> matches = switch (rule) {
				case 2 -> List.of(
						new GlossaryTermMatch("ancient temple", GlossaryEntryClassification.AMBIGUOUS),
						new GlossaryTermMatch("temple", GlossaryEntryClassification.PLAIN));
				case 5 -> List.of(new GlossaryTermMatch("dark lord", GlossaryEntryClassification.PLAIN));
				default -> List.of();
			};

			ScalingDecision decision = new ScalingDecision(expectedEngine, rule, matches);

			assertThat(decision.matchedRule()).isBetween(1, 6);
			assertThat(decision.engine()).isEqualTo(expectedEngine);
			if (rule == 2 || rule == 5) {
				assertThat(decision.glossaryMatches()).isNotEmpty();
			} else {
				assertThat(decision.glossaryMatches()).isEmpty();
			}
			if (rule == 2) {
				assertThat(decision.glossaryMatches())
						.anySatisfy(match -> assertThat(match.classification())
								.isEqualTo(GlossaryEntryClassification.AMBIGUOUS));
			}
			if (rule == 5) {
				assertThat(decision.glossaryMatches())
						.noneSatisfy(match -> assertThat(match.classification())
								.isEqualTo(GlossaryEntryClassification.LORE));
			}
		}
	}
}
