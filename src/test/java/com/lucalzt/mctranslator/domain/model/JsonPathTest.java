package com.lucalzt.mctranslator.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link JsonPath}, covering validation rejection (R7), prefix
 * matching (R7) and value equality (R16). Pure JUnit 6 + AssertJ, no Spring context.
 */
class JsonPathTest {

	@Test
	@DisplayName("Accepts a valid dot-separated path and exposes it via value()")
	void acceptsValidPath() {
		JsonPath path = new JsonPath("quest.description.task1");

		assertThat(path.value()).isEqualTo("quest.description.task1");
	}

	@Test
	@DisplayName("Rejects a null path with NullPointerException")
	void rejectsNullPath() {
		assertThatThrownBy(() -> new JsonPath(null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Rejects blank and structurally invalid paths with IllegalArgumentException")
	void rejectsBlankAndStructurallyInvalidPaths() {
		String[] invalidPaths = { "", "   ", ".quest", "quest.", "quest..description" };

		for (String invalid : invalidPaths) {
			assertThatThrownBy(() -> new JsonPath(invalid))
					.as("path '%s' must be rejected", invalid)
					.isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	@DisplayName("Matches leading segments in order, case-sensitively")
	void matchesLeadingSegmentsInOrder() {
		assertThat(new JsonPath("quest.description.task1").startsWith("quest", "description")).isTrue();
		assertThat(new JsonPath("item.sword").startsWith("item")).isTrue();
		assertThat(new JsonPath("quest.advancement").startsWith("quest", "advancement")).isTrue();
	}

	@Test
	@DisplayName("Returns false when a leading segment differs")
	void rejectsMismatchingSegments() {
		assertThat(new JsonPath("quest.advancement").startsWith("quest", "description")).isFalse();
		assertThat(new JsonPath("quest.advancement").startsWith("advancement")).isFalse();
	}

	@Test
	@DisplayName("Segment matching is case-sensitive")
	void segmentMatchingIsCaseSensitive() {
		assertThat(new JsonPath("quest.description").startsWith("Quest")).isFalse();
	}

	@Test
	@DisplayName("Returns true for empty varargs (vacuous prefix)")
	void emptyVarargsMatchesEverything() {
		assertThat(new JsonPath("quest.description").startsWith()).isTrue();
	}

	@Test
	@DisplayName("Rejects a null segments array or a null segment with NullPointerException")
	void rejectsNullSegments() {
		assertThatThrownBy(() -> new JsonPath("quest.description").startsWith((String[]) null))
				.isInstanceOf(NullPointerException.class);
		assertThatThrownBy(() -> new JsonPath("quest.description").startsWith("quest", null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	@DisplayName("Rejects blank segments with IllegalArgumentException")
	void rejectsBlankSegment() {
		assertThatThrownBy(() -> new JsonPath("quest.description").startsWith("  "))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Equality is by value with consistent hash codes")
	void equalityIsByValue() {
		JsonPath first = new JsonPath("quest.description");
		JsonPath second = new JsonPath("quest.description");
		JsonPath different = new JsonPath("item.sword");

		assertThat(first).isEqualTo(second);
		assertThat(first.hashCode()).isEqualTo(second.hashCode());
		assertThat(first).isNotEqualTo(different);
	}
}
