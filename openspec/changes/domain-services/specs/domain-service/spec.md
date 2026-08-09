# Specification: Domain Services (`domain-services`)

## Goal

Create the pure-Java domain services of the translation pipeline in `com.lucalzt.mctranslator.domain.service` — `VariableMasker` / `VariableUnmasker` (protecting printf and MessageFormat variables with positional `__VAR_N__` tokens and restoring them best-effort) and `ScalingHeuristic` (the exact 6-rule precedence table deciding FAST vs PRECISE per key) — together with the deferred value objects `MaskedText` and `GlossaryTermMatch` and the decision/unmask result records, all 100% framework-free, `@NullMarked`, Javadoc-covered, and pinned by red-first JUnit 6 + AssertJ unit tests.

---

## Resolved in Spec (open items from the proposal)

Every open item that the proposal left for spec/design is resolved below with the simplest consistent option. These resolutions are binding for design and tasks; they are flagged here so no requirement is silently changed later.

| Open item | Resolution (binding) | Rationale |
|---|---|---|
| M3 remainder — printf regex coverage | In scope (masked): `%s`, `%d`, `%f` and positional `%<index>$<conv>` for `conv ∈ {s,d,f}` (any positive digit index, e.g. `%1$s`, `%2$d`, `%10$f`). Pattern pinned: `%(?:\d+\$)?[sdf]`. **Not masked (left literal, documented non-goal):** `%%` (printf escape for a literal `%`), case variants `%S`/`%D`/`%F`, other conversions `%x`/`%o`/`%e`/…, and flags/width/precision forms such as `%10.2f`, `%-10s`. | The user-confirmed decision 1 enumerates exactly `%s`, `%d`, `%f`, `%1$s`; the `(?:...)?` positional group and `[sdf]` conversion set are the direct, conservative realization. Width/precision forms fail the pattern (`%` not immediately followed by an optional `N$` and one conversion char) and are therefore left literal — matching the decision-1 examples. Case variants and other conversions are deliberately excluded: under-masking is recoverable (left-literal text round-trips), while over-masking risks the engine dropping a non-variable. |
| M3 remainder — MessageFormat coverage | In scope (masked): every indexed MessageFormat form matched by `\{(\d+)(?:,[^}]*)?\}`, i.e. bare `{0}`, format variants `{0,number}`, `{0,date}`, `{0,number,integer}`, and ChoiceFormat ranges `{0,choice,0#zero|1#one}`. JSON braces (`{ "key": ... }`), unbalanced braces (`{0`), and non-digit braces (`{bar}`) are NOT matched. | Decision 1 pins `{0}` and `{0,number}` "incl. indexed/format variants"; the `(?:,[^}]*)?` group covers all `,type[,style]` suffixes naturally, including choice ranges. Requiring a digit immediately after `{` prevents false masking of JSON objects. |
| M3 remainder — Minecraft `\u00a7` section-sign codes | **Non-goal.** `§a`, `\u00a7a`, and any section-sign formatting code are NOT variables and are NEVER masked (left literal, round-trip identity). | Formatting codes carry no substitution semantics; masking them would only add round-trip surface. Documented as non-goal, never a variable family. |
| M3 remainder — `%%s` adjacency edge | Accepted v1 limitation: the masker does not special-case printf escapes; a literal `%%` is not masked, but a `%s` immediately following an escaped `%%` (e.g. `100%%s`) IS masked. Documented limitation, not a requirement. | The conservative pattern has no escape-awareness; handling it would complicate the pattern set for a vanishingly rare real-world string. Recorded for the empirical tuning phase. |
| H1 — heuristic input shape | **Pinned contract:** `ScalingDecision suggest(JsonPath path, String maskedText, List<GlossaryEntry> glossary)`. The heuristic receives the path and the **masked** text as separate values — NOT a `TranslationKey`. It accepts a bare `String` masked text, NOT a `MaskedText` (the two services stay contract-decoupled). Word counting (rules 3/5) and glossary matching (rules 2/5) ALWAYS operate on `maskedText`. | Explore H1 recommends `(JsonPath path, String maskedText, List<GlossaryEntry>)`; a bare `String` avoids tying the heuristic's contract to the masking VO. Evaluation on masked text matches the documented flow (mask → heuristic → engine → unmask, `implementation-strategy.md` §5). |
| H7 — glossary matching mode | **Pinned:** whole-word, case-sensitive matching. A term matches iff it occurs in `maskedText` as a contiguous substring bounded on both sides by non-word characters, where word characters are ASCII letters, digits and underscore (Java regex `\b` semantics with the term `Pattern.quote`d). Multi-word terms match as the contiguous phrase with word boundaries at both ends. One `GlossaryTermMatch` per glossary **entry** whose term matched (no dedup by term). Matching is evaluated on the masked text, so masked tokens never contain glossary terms (their indices are interior to `__VAR_N__`). | Whole-word prevents false positives such as term `iron` matching inside `irony`; case-sensitive is the simplest deterministic option with no normalization surprises. Word-boundary semantics also keep masked tokens (`__VAR_0__`) from matching letter terms, because `_` is a word character. Case-folding and occurrence/position reporting are deferred tuning (non-goals). |
| H8 — thresholds & shape | `ScalingHeuristic` is a **stateless** service; the thresholds 30 and 8 are compile-time named constants in the class. No configuration plumbing, no instance state. | Docs call the table "punto de partida" to be adjusted empirically; constants keep the domain service pure and trivially testable. Configuration is a non-goal. |
| M8 remainder — discrepancy contract | **Pinned:** `record UnmaskResult(String restoredText, List<Integer> missingTokenIndices, List<Integer> unmatchedTokenIndices, boolean reordered)` — a single flat result record. `missingTokenIndices`: sorted ascending, indices `0..n-1` (n = number of masked variables) that occur in the masked text but not in the translated text. `unmatchedTokenIndices`: sorted ascending, deduplicated, indices `>= n` that occur in the translated text with no corresponding variable. `reordered`: `true` iff the sequence of token indices in the translated text (in order of appearance, ALL tokens — matched or unmatched) is not monotonically non-decreasing (i.e. contains an inversion). | Decision 3 requires the API to expose missing/unmatched/reordered discrepancies for application-layer `[WARN]` emission. A flat record is the minimal unambiguous shape; sorted/deduplicated lists make the contract deterministic and testable. Duplicated tokens are NOT a discrepancy (M7: duplicates restore the same original twice). |
| M6 remainder — extra-token rendering | **Pinned:** unmatched tokens (`__VAR_N__` with `N >= n`) are left **verbatim** in `restoredText`. They are never replaced with the empty string and never cause an exception. | Best-effort philosophy: replacing with `""` silently destroys engine output; leaving the token literal preserves information so the application layer can warn and/or post-process. A translation that legitimately contains a token-shaped string is preserved as-is. |
| H6 remainder — punctuation in word counting | **Pinned:** every whitespace-separated token counts as one word, including punctuation-only tokens (e.g. `...` counts as 1). No filtering, no punctuation stripping. | Simplest consistent option; the docs' rule is a coarse length estimator. Masked variables are single tokens, so the estimate is unaffected by variable density. |
| MaskedText token-set invariant | **Pinned:** `MaskedText` validates in its compact constructor that the token indices occurring in `maskedText` (via the full-index token regex `__VAR_(\d+)__`) are exactly `{0, 1, …, n-1}` with n = `variables.size()`, each occurring exactly once; otherwise `IllegalArgumentException`. | "Cero ambigüedad" in VO contracts (`config.yaml`): the round-trip correspondence `__VAR_N__ ↔ variables.get(N)` is the VO's core invariant, cheap to enforce with one regex scan, and makes misuse of the VO impossible. `mask()` trivially satisfies it. Textual order of tokens is NOT validated (restoration is by index regardless). |
| Empty-glossary rule 5 (proposal H2 wording) | **Pinned — literal reading of decision 2:** with an empty glossary, rule 2 never fires (no AMBIGUOUS term can be detected — vacuous false), but rule 5 DOES fire when the text is ≤ 8 words (the "no LORE term detected" clause is vacuously true). `matchedRule` is 5; engine is FAST. | The proposal's H2 note ("rules 2/5 vacuously don't match → falls to rules 3/4/6") conflicts with the literal rule-5 table for the empty-glossary case. User decision 2 states rule 5 matches when text ≤ 8 words AND no LORE terms detected — with no glossary, no LORE is detected, so the AND holds. Engine outcome (FAST) is identical under both readings; only `matchedRule` differs (5 vs 6). The literal reading is pinned; the acceptance-criterion phrasing "rules 2/5 don't match" in the proposal is superseded by Requirement 9's scenarios. |

---

## ADDED Requirements

No MODIFIED or REMOVED requirements exist in this change: it creates a brand-new package (`domain.service`) and must NOT alter the archived `domain-models` spec (9-type inventory, R3) nor the `domain-output-ports` spec.

### Requirement 1: Package Placement in the Real Base Package

All in-scope types MUST be declared in the package `com.lucalzt.mctranslator.domain.service` (source path `src/main/java/com/lucalzt/mctranslator/domain/service/`), which is currently absent and is created by this change. The `com.mctranslator` package written in `docs/architecture/implementation-strategy.md` MUST NOT be used. The in-scope public types are exactly: `package-info.java`, `MaskedText`, `VariableMasker`, `VariableUnmasker`, `UnmaskResult`, `GlossaryTermMatch`, `ScalingHeuristic`, and `ScalingDecision`.

#### Scenario: Types live in the real package
- **Given** the change is implemented
- **When** the source tree under `src/main/java/com/lucalzt/mctranslator/domain/service/` is inspected
- **Then** every in-scope type is declared in `com.lucalzt.mctranslator.domain.service` and no type exists under any `com.mctranslator` package.

#### Scenario: No type leaks into the pinned model package
- **Given** the implemented change
- **When** the public types of `com.lucalzt.mctranslator.domain.model` are enumerated
- **Then** the set is still exactly the 9 types pinned by the archived `domain-models` spec (Requirement 3) — `MaskedText` and `GlossaryTermMatch` do NOT appear there.

---

### Requirement 2: Zero Framework Imports in the Domain Service Package

Source files in `com.lucalzt.mctranslator.domain.service` MUST NOT import from `org.springframework.*`, `jakarta.*`, or any other framework/library package. Allowed imports are limited to the JDK (`java.*`, `java.lang.*`), the sibling packages under `com.lucalzt.mctranslator.domain.*` (model, port/out), and `org.jspecify.annotations.*` (nullness annotations only). The package is 100% pure Java.

#### Scenario: Grep check for framework imports
- **Given** the implemented `domain.service` package
- **When** a grep over its source files searches for `import org.springframework`, `import jakarta`, or any other non-JDK, non-jspecify, non-`com.lucalzt.mctranslator` import
- **Then** no matches are found and the build compiles the package without framework dependencies.

---

### Requirement 3: JSpecify @NullMarked Package

The package MUST contain a `package-info.java` annotated with `@org.jspecify.annotations.NullMarked` and a Javadoc package description stating the package's role (pure domain services of the translation pipeline: masking, unmasking, and scaling heuristic; zero framework dependencies), mirroring the `domain.model` conventions. The package has NO `@Nullable` elements: every public API is non-null by default and returns non-null values.

#### Scenario: Package is null-marked
- **Given** the implemented `domain.service` package
- **When** `package-info.java` is inspected
- **Then** it carries the `@NullMarked` annotation and a Javadoc description of the package role.

#### Scenario: No nullable elements
- **Given** the implemented `domain.service` package
- **When** a grep searches for `@Nullable` in its source files
- **Then** no matches are found.

---

### Requirement 4: Javadoc on All Public API

Every public type and every public member (record accessors, methods, record components, constants) MUST carry Javadoc: a summary sentence ending with a period, plus `@param` / `@return` / `@throws` tags where applicable, following the `java-docs` skill. In particular, the token correspondence `__VAR_N__ ↔ variables.get(N)` of `MaskedText`, the discrepancy semantics of `UnmaskResult`, the rule-to-engine mapping of `ScalingDecision`, and the 6-rule precedence table of `ScalingHeuristic` MUST be documented in their type/member Javadoc.

#### Scenario: Javadoc completeness check
- **Given** the implemented `domain.service` package
- **When** a review enumerates all public types and public members
- **Then** each one has a Javadoc comment (summary + applicable tags) and no public member is undocumented.

---

### Requirement 5: MaskedText Value Object

`MaskedText` MUST be an immutable record `MaskedText(String maskedText, List<String> variables)` in `domain.service` — the deferred artifact that carries the round-trip state of mask→unmask, with the pinned correspondence `__VAR_N__ ↔ variables.get(N)`.

Its compact constructor MUST enforce:
- `maskedText` non-null (throws `NullPointerException`) and non-blank (throws `IllegalArgumentException`);
- `variables` non-null, its elements non-null (throws `NullPointerException`) and non-blank (throws `IllegalArgumentException`);
- the token-set invariant: the token indices occurring in `maskedText` (matched with the full-index token regex `__VAR_(\d+)__`) are exactly `{0, 1, …, n-1}` where n = `variables.size()`, each index occurring exactly once (any violation throws `IllegalArgumentException`).

The `variables` list MUST be defensively copied on construction (`List.copyOf` semantics: the returned list is immutable and the VO is unaffected by later mutation of the caller's list). Equality is by both components.

#### Scenario: Rejects null and blank maskedText
- **Given** the `MaskedText` record
- **When** constructed with a `null` `maskedText` or with a blank `maskedText` (`""` or `"   "`)
- **Then** a `NullPointerException` (null) or `IllegalArgumentException` (blank) is thrown.

#### Scenario: Rejects null or blank variables
- **Given** the `MaskedText` record
- **When** constructed with a `null` `variables` list, a list containing a `null` element, or a list containing a blank element
- **Then** a `NullPointerException` (null list/element) or `IllegalArgumentException` (blank element) is thrown.

#### Scenario: Rejects an inconsistent token set
- **Given** the `MaskedText` record
- **When** constructed with `("foo __VAR_2__ bar", List.of("a"))` (index 2 outside `{0}`), or with `("foo __VAR_0__ __VAR_0__", List.of("a"))` (index 0 repeated)
- **Then** an `IllegalArgumentException` is thrown.

#### Scenario: Accepts a valid mask state
- **Given** `maskedText` = `"Use __VAR_0__ and __VAR_1__"` and `variables` = `List.of("%s", "{0,number}")`
- **When** the record is constructed
- **Then** construction succeeds, the accessors return the exact values, and `__VAR_0__` ↔ `"%s"`, `__VAR_1__` ↔ `"{0,number}"`.

#### Scenario: Defensive copy of the variables list
- **Given** a caller-owned mutable `List<String>` passed to the constructor
- **When** the caller later mutates that list
- **Then** the `MaskedText` instance is unaffected and the accessor-returned list is immutable.

---

### Requirement 6: VariableMasker

`VariableMasker` MUST be a stateless domain service exposing `MaskedText mask(String text)`. It MUST reject a `null` argument (throws `NullPointerException`) and a blank argument — empty or whitespace-only (throws `IllegalArgumentException`).

Masking behavior (pinned patterns, single precompiled `Pattern`, JDK-only):
- The variable-detection pattern is the alternation, in this order: literal token `__VAR_(\d+)__` | printf `%(?:\d+\$)?[sdf]` | MessageFormat `\{(\d+)(?:,[^}]*)?\}`.
- Every occurrence of any matched pattern is replaced by a positional token `__VAR_<k>__` where k is the occurrence index in **first-occurrence order** (0-based); the `variables` list of the returned `MaskedText` records each matched original text verbatim, one entry per occurrence (no dedup: two occurrences of `%s` produce two entries).
- A literal `__VAR_N__` substring in the source text is protected as a regular variable (matched by the first alternation branch), so no token leakage occurs: the masked output never contains a `__VAR_N__` token that does not correspond to a protected variable, and original literals are restored exactly.
- NOT masked (left literal in the output, round-trip identity): `%%`, case variants (`%S`, `%D`, `%F`), other printf conversions (`%x`, `%o`, `%e`, `%g`, `%b`, `%c`, `%h`, `%n`), printf flags/width/precision forms (`%10.2f`, `%-10s`, `%2$10d`), and Minecraft `\u00a7` section-sign formatting codes (`§a`, `\u00a7a`).
- JSON braces and unbalanced braces are not masked: `{` followed by a non-digit, or digits without a closing `}`, produce no match.

#### Scenario: Masks each pinned printf family
- **Given** the texts `"HP: %s"`, `"Damage: %d"`, `"Ratio: %f"`, `"Hello %1$s"`, `"Cost %2$d"`, `"Slot %10$s"`
- **When** `mask` is invoked on each
- **Then** each variable occurrence is replaced by the next positional token (`__VAR_0__`, …) and the `variables` list holds the exact original substrings (`"%s"`, `"%d"`, `"%f"`, `"%1$s"`, `"%2$d"`, `"%10$s"`).

#### Scenario: Masks each pinned MessageFormat family
- **Given** the texts `"Value {0}"`, `"Amount {1,number}"`, `"Day {0,date,full}"`, `"Pick {0,choice,0#zero|1#one}"`, `"N {12,number,integer}"`
- **When** `mask` is invoked on each
- **Then** each `{...}` occurrence is replaced by a positional token and the `variables` list holds the exact original substrings (e.g. `"{0,choice,0#zero|1#one}"`).

#### Scenario: First-occurrence order and no dedup
- **Given** the text `"A %s and %s and {0}"`
- **When** `mask` is invoked
- **Then** the masked text is `"A __VAR_0__ and __VAR_1__ and __VAR_2__"` and `variables` equals `List.of("%s", "%s", "{0}")` (two entries for the two `%s` occurrences).

#### Scenario: Literal token is protected, no leakage
- **Given** the text `"Use __VAR_3__ now"`
- **When** `mask` is invoked
- **Then** the masked text is `"Use __VAR_0__ now"` and `variables` equals `List.of("__VAR_3__")` — the literal is a protected variable, and every `__VAR_N__` in the output corresponds to one protected variable.

#### Scenario: Non-variable patterns are left literal
- **Given** the text `"100%% complete, %S var, %x hex, %10.2f wide, §a colored"`
- **When** `mask` is invoked
- **Then** the masked text is identical to the input (no tokens produced, `variables` is empty) — none of `%%`, `%S`, `%x`, `%10.2f`, `§a` is treated as a variable.

#### Scenario: JSON braces are not masked
- **Given** the text `"JSON { \"key\": \"value\" } and text {0}"`
- **When** `mask` is invoked
- **Then** the JSON braces are left literal, the `{0}` is replaced by `__VAR_0__`, and `variables` equals `List.of("{0}")`.

#### Scenario: Rejects null and blank input
- **Given** the `VariableMasker`
- **When** `mask` is invoked with `null` or with `""` / `"   "`
- **Then** a `NullPointerException` (null) or `IllegalArgumentException` (blank) is thrown.

---

### Requirement 7: Round-Trip Fidelity

The mask→unmask round trip MUST be lossless for the protected variable set: applying the unmasker to a `MaskedText` whose translated text is the masked text itself restores the EXACT original input string. Full-index token matching MUST be used so that `__VAR_1__` never matches a prefix of `__VAR_10__`: the token regex is `__VAR_(\d+)__` with the index captured greedily as one number, and a token is only recognized when the trailing `__` is present (in `__VAR_10__` the `0` belongs to the index, so no `__VAR_1__` substring exists to match).

#### Scenario: Identity round trip
- **Given** the original text `"HP: %s, Cost: %1$d, Value: {0,number}"` and its `MaskedText` produced by `mask`
- **When** `unmask` is invoked with that `MaskedText` and the masked text as the translated text
- **Then** the restored text equals the exact original string and the result carries no discrepancies.

#### Scenario: Literal-token round trip has no leakage
- **Given** the original text `"Use __VAR_3__"` and its `MaskedText` produced by `mask`
- **When** `unmask` is invoked with that `MaskedText` and the masked text
- **Then** the restored text equals `"Use __VAR_3__"` exactly (the original literal, not a masked token).

#### Scenario: Full-index tokens never partially match
- **Given** a `MaskedText` whose variables list has 11 entries (indices 0..10) and the translated text `"A __VAR_10__ B __VAR_1__"`
- **When** `unmask` is invoked
- **Then** `__VAR_10__` is restored from `variables.get(10)` and `__VAR_1__` from `variables.get(1)` — no partial/prefix restoration of `__VAR_1` from `__VAR_10`.

---

### Requirement 8: VariableUnmasker

`VariableUnmasker` MUST be a stateless domain service exposing `UnmaskResult unmask(MaskedText masked, String translatedText)`. It MUST reject a `null` `masked` or a `null` `translatedText` (throws `NullPointerException`). It MUST be best-effort per decision 3: it MUST NOT throw merely because the translation lacks, adds, or reorders variables.

Restoration behavior:
- Tokens are restored **by token index**: every `__VAR_N__` in `translatedText` with `0 ≤ N < n` (n = `masked.variables().size()`) is replaced by `masked.variables().get(N)`. Reordered tokens restore correctly because each token carries its own index; a duplicated token restores the same original at each occurrence.
- Unmatched tokens (`__VAR_N__` with `N ≥ n`) are left **verbatim** in the restored text and reported as discrepancies (M6 resolution).
- The result record MUST be `UnmaskResult(String restoredText, List<Integer> missingTokenIndices, List<Integer> unmatchedTokenIndices, boolean reordered)` with:
  - `missingTokenIndices` — sorted ascending list of indices `N` (`0 ≤ N < n`) that occur in the masked text's token set but do NOT occur in `translatedText`; empty when every variable survived the translation;
  - `unmatchedTokenIndices` — sorted ascending, deduplicated list of indices `N ≥ n` occurring in `translatedText`; empty when no out-of-range token appears;
  - `reordered` — `true` iff the sequence of token indices in `translatedText` (in order of appearance, considering ALL tokens — matched and unmatched) is not monotonically non-decreasing (i.e. contains an inversion relative to the canonical first-occurrence order); duplicates do not by themselves imply reordering.

#### Scenario: Missing token never throws and is reported
- **Given** a `MaskedText` with `variables` = `List.of("%s", "%d")` and the translated text `"Hello __VAR_0__"`
- **When** `unmask` is invoked
- **Then** no exception is thrown, `restoredText` is `"Hello %s"`, `missingTokenIndices` equals `[1]`, and `unmatchedTokenIndices` is empty.

#### Scenario: Unmatched token is left verbatim and reported
- **Given** a `MaskedText` with `variables` = `List.of("%s")` and the translated text `"Hi __VAR_0__ and __VAR_9__ and __VAR_9__"`
- **When** `unmask` is invoked
- **Then** no exception is thrown, `restoredText` is `"Hi %s and __VAR_9__ and __VAR_9__"`, `unmatchedTokenIndices` equals `[9]` (deduplicated, sorted), and `missingTokenIndices` is empty.

#### Scenario: Reordered tokens restore by index and are reported
- **Given** a `MaskedText` with `variables` = `List.of("%s", "%d")` and the translated text `"Second __VAR_1__ first __VAR_0__"`
- **When** `unmask` is invoked
- **Then** no exception is thrown, `restoredText` is `"Second %d first %s"`, `reordered` is `true`, and both discrepancy lists are empty.

#### Scenario: Duplicated token restores the same original twice, not reordered
- **Given** a `MaskedText` with `variables` = `List.of("{0}")` and the translated text `"A __VAR_0__ B __VAR_0__"`
- **When** `unmask` is invoked
- **Then** `restoredText` is `"A {0} B {0}"`, `reordered` is `false`, and both discrepancy lists are empty.

#### Scenario: Perfect translation yields no discrepancies
- **Given** a `MaskedText` with `variables` = `List.of("%s", "{0}")` and the translated text `"X __VAR_0__ Y __VAR_1__"`
- **When** `unmask` is invoked
- **Then** `restoredText` is `"X %s Y {0}"`, `missingTokenIndices` and `unmatchedTokenIndices` are empty, and `reordered` is `false`.

#### Scenario: Rejects null arguments
- **Given** the `VariableUnmasker`
- **When** `unmask` is invoked with a `null` `MaskedText` or a `null` `translatedText`
- **Then** a `NullPointerException` is thrown.

---

### Requirement 9: ScalingHeuristic

`ScalingHeuristic` MUST be a stateless domain service exposing `ScalingDecision suggest(JsonPath path, String maskedText, List<GlossaryEntry> glossary)` (H1 pin). It MUST reject `null` arguments (throws `NullPointerException`). The masked text is the ONLY text the heuristic evaluates: word counting (rules 3/5) and glossary matching (rules 2/5) ALWAYS operate on `maskedText`, never on raw/unmasked text.

The exact 6-rule precedence table MUST be evaluated **in order — first match wins** (resolves conflicts; `JsonPath.startsWith(String...)` is the path matcher):

| Order | Condition (evaluated on `maskedText` and `path`) | Engine |
|---|---|---|
| 1 | `path.startsWith("quest","description")` OR `path.startsWith("lore")` OR `path.startsWith("advancement")` | PRECISE |
| 2 | at least one glossary term classified `AMBIGUOUS` is detected in `maskedText` | PRECISE |
| 3 | word count of `maskedText` is strictly greater than 30 | PRECISE |
| 4 | `path.startsWith("item")` OR `path.startsWith("block")` OR `path.startsWith("entity")` OR `path.startsWith("gui")` | FAST |
| 5 | word count of `maskedText` is ≤ 8 AND no glossary term classified `LORE` is detected in `maskedText` | FAST |
| 6 | default — any other case | FAST |

Boundary semantics (H6 pin): `> 30` is strict (exactly 30 words → rule 3 does NOT match); `≤ 8` is inclusive (exactly 8 words → rule 5 is a candidate).

Glossary matching mode (H7 pin): whole-word, case-sensitive. A glossary term matches `maskedText` iff it occurs as a contiguous substring bounded on both sides by non-word characters, where word characters are ASCII letters, digits and underscore (Java `\b` semantics with `Pattern.quote(term)`); multi-word terms match as the contiguous phrase with word boundaries at both ends. One `GlossaryTermMatch` per matching entry (term + its classification), with the pinned exact-match contract of Requirement 11.

Empty-glossary behavior (literal reading of decision 2, flagged in "Resolved in Spec"): rule 2 never fires (no AMBIGUOUS term can be detected); rule 5 DOES fire when the word count is ≤ 8 (the "no LORE term detected" clause is vacuously true).

Rule 5 LORE guard (decision 2): rule 5 matches ONLY when the text is ≤ 8 words AND no LORE-classified term is detected. When the text is ≤ 8 words but a LORE term IS detected, rule 5 does NOT match and evaluation falls to rule 6 → FAST. The LORE guard is a testable clause; its outcome-equivalence with rule 6 is explicitly pinned by a scenario.

Thresholds 30 and 8 are compile-time named constants in the class (H8 pin); no configuration plumbing exists.

#### Scenario: Rule 1 — lore path decides PRECISE
- **Given** `path` = `"quest.description.task1"` and `maskedText` = `"Short text"` (≤ 8 words)
- **When** `suggest` is invoked
- **Then** the decision is PRECISE with `matchedRule` = 1 — rule 1 wins over rule 5 on a short conflicting text.

#### Scenario: Rule 1 — lore and advancement prefixes
- **Given** `path` = `"lore.story"` and `path` = `"advancement.husbandry.root"`
- **When** `suggest` is invoked for each
- **Then** both decisions are PRECISE with `matchedRule` = 1.

#### Scenario: Rule 2 — AMBIGUOUS term decides PRECISE
- **Given** `maskedText` = `"The ancient temple lies beyond"`, `path` = `"item.artifact"`, and a glossary containing an entry with term `"ancient temple"` classified `AMBIGUOUS`
- **When** `suggest` is invoked
- **Then** the decision is PRECISE with `matchedRule` = 2 and `glossaryMatches` contains one match whose term is `"ancient temple"` and classification is `AMBIGUOUS` — rule 2 wins over rule 4.

#### Scenario: Rule 2 — whole-word, case-sensitive matching
- **Given** `maskedText` = `"Iron is common; irony is not"` and a glossary with an `AMBIGUOUS` entry for term `"iron"` and another `AMBIGUOUS` entry for term `"Iron"`
- **When** `suggest` is invoked
- **Then** the decision is PRECISE with `matchedRule` = 2 (the term `"Iron"` matches at the word boundary; `"iron"` does NOT match inside `"irony"`), and `glossaryMatches` contains exactly one match for the `"Iron"` entry — `"iron"` inside `"irony"` is not detected.

#### Scenario: Rule 3 — strict > 30 words
- **Given** `maskedText` with exactly 30 words and `path` = `"gui.menu"` (a rule-4 path)
- **When** `suggest` is invoked
- **Then** the decision is FAST with `matchedRule` = 4 (rule 3 does NOT match at exactly 30 words).
- **And given** `maskedText` with 31 words and `path` = `"item.sword"`
- **When** `suggest` is invoked
- **Then** the decision is PRECISE with `matchedRule` = 3 — rule 3 wins over rule 4 on a long conflicting text.

#### Scenario: Rule 4 — fast paths
- **Given** `path` equal to `"item.sword"`, `"block.stone"`, `"entity.zombie"`, or `"gui.container"` with a short masked text
- **When** `suggest` is invoked
- **Then** the decision is FAST with `matchedRule` = 4.

#### Scenario: Rule 5 — short text with no LORE term
- **Given** `maskedText` = `"Hello there"` (2 words), `path` = `"random.key"`, and a glossary containing only `PLAIN` entries whose terms do not appear in the text
- **When** `suggest` is invoked
- **Then** the decision is FAST with `matchedRule` = 5.

#### Scenario: Rule 5 — inclusive ≤ 8 words
- **Given** `maskedText` with exactly 8 words, `path` = `"random.key"`, and a glossary with no detected terms
- **When** `suggest` is invoked
- **Then** the decision is FAST with `matchedRule` = 5 (exactly 8 words is a candidate).

#### Scenario: Rule 5 LORE guard — LORE present falls to rule 6 (pinned equivalence)
- **Given** `maskedText` = `"The dark lord rises"` (4 words, ≤ 8), `path` = `"random.key"`, and a glossary containing an entry with term `"dark lord"` classified `LORE`
- **When** `suggest` is invoked
- **Then** the decision is FAST with `matchedRule` = 6 — rule 5 does NOT match because a LORE term was detected, and rule 6 (default) produces the SAME engine outcome (FAST) that rule 5 would have produced; this outcome-equivalence is pinned by this scenario and must not be "fixed" into a LORE → PRECISE rule.
- **And given** the same text with a glossary where the `"dark lord"` entry is classified `PLAIN`
- **When** `suggest` is invoked
- **Then** the decision is FAST with `matchedRule` = 5 (no LORE term detected).

#### Scenario: Empty glossary — rule 2 vacuous, rule 5 fires on short text
- **Given** an empty glossary and `maskedText` = `"Hi"` (1 word) with `path` = `"random.key"`
- **When** `suggest` is invoked
- **Then** rule 2 does not match (no AMBIGUOUS term can exist) and the decision is FAST with `matchedRule` = 5 — the "no LORE detected" clause is vacuously true.
- **And given** an empty glossary and `maskedText` with 40 words
- **When** `suggest` is invoked
- **Then** the decision is PRECISE with `matchedRule` = 3.

#### Scenario: Rule 6 — default fall-through
- **Given** `maskedText` = `"This sentence has exactly ten plain words written here today"` (10 words, > 8 and ≤ 30), `path` = `"some.other.key"`, and an empty glossary
- **When** `suggest` is invoked
- **Then** the decision is FAST with `matchedRule` = 6 — rule 5 is blocked (10 words > 8) and rule 3 is not reached (10 words ≤ 30), so control falls through to the default rule 6.

#### Scenario: Evaluation on masked text
- **Given** the masked text `"A __VAR_0__ with __VAR_1__ detail"` whose raw (unmasked) form contains 40 words
- **When** `suggest` is invoked
- **Then** the word count is computed on the MASKED text (the two tokens count as two words), never on the raw text.

#### Scenario: Rejects null arguments
- **Given** the `ScalingHeuristic`
- **When** `suggest` is invoked with a `null` `path`, a `null` `maskedText`, or a `null` `glossary`
- **Then** a `NullPointerException` is thrown.

---

### Requirement 10: ScalingDecision Record

`ScalingDecision` MUST be an immutable record `ScalingDecision(TranslationEngineType engine, int matchedRule, List<GlossaryTermMatch> glossaryMatches)` in `domain.service` — the decision-record output of `ScalingHeuristic` (decision 4; a bare `TranslationEngineType` is only ever a trivial projection of this record).

Its compact constructor MUST enforce:
- `engine` non-null (throws `NullPointerException`);
- `matchedRule` in the range 1..6 inclusive (throws `IllegalArgumentException` otherwise);
- `glossaryMatches` non-null and its elements non-null (throws `NullPointerException`); the list is defensively copied (`List.copyOf` semantics).

Contract of the values produced by the heuristic (documented in Javadoc, guaranteed by Requirement 9, NOT re-validated in the constructor):
- `glossaryMatches` is non-empty exactly when `matchedRule` is 2 or 5; it is empty for rules 1, 3, 4 and 6.
- For `matchedRule` = 2, `glossaryMatches` contains at least one entry classified `AMBIGUOUS`.
- For `matchedRule` = 5, `glossaryMatches` contains NO entry classified `LORE` (it may be empty or contain only `PLAIN` entries).
- The engine is consistent with the rule: rules 1–3 → `PRECISE`, rules 4–6 → `FAST`.

#### Scenario: Rejects invalid rule numbers
- **Given** the `ScalingDecision` record
- **When** constructed with `matchedRule` = 0 or 7
- **Then** an `IllegalArgumentException` is thrown.

#### Scenario: Rejects null components
- **Given** the `ScalingDecision` record
- **When** constructed with a `null` `engine`, a `null` `glossaryMatches` list, or a list containing a `null` element
- **Then** a `NullPointerException` is thrown.

#### Scenario: Heuristic-produced decisions satisfy the contract
- **Given** the `ScalingHeuristic` and the scenarios of Requirement 9
- **When** `suggest` is invoked for each rule outcome
- **Then** every returned `ScalingDecision` has `matchedRule` in 1..6, engine consistent with the rule mapping, and `glossaryMatches` non-empty only for rules 2 and 5 (with the rule-2 AMBIGUOUS / rule-5 no-LORE guarantees above).

---

### Requirement 11: GlossaryTermMatch Value Object

`GlossaryTermMatch` MUST be an immutable record `GlossaryTermMatch(String term, GlossaryEntryClassification classification)` in `domain.service` — the deferred artifact representing the output of `ScalingHeuristic` rule 2/5 glossary matching. `term` is the source-language term of the matched glossary entry (verbatim); `classification` is the entry's classification.

Its compact constructor MUST reject:
- `null` `term` or `null` `classification` (throws `NullPointerException`);
- blank `term` (throws `IllegalArgumentException`).

Equality is by value (both components).

#### Scenario: Rejects null and blank term
- **Given** the `GlossaryTermMatch` record
- **When** constructed with a `null` `term`, a blank `term`, or a `null` `classification`
- **Then** a `NullPointerException` (null) or `IllegalArgumentException` (blank) is thrown.

#### Scenario: Value equality
- **Given** two `GlossaryTermMatch` instances with equal term and classification and one differing in classification
- **When** compared with `equals`/`hashCode`
- **Then** the equal instances are equal with equal hash codes and the differing one is not equal.

---

### Requirement 12: Word Counting Contract

Word counting MUST count, on the MASKED text only, the number of non-empty substrings produced by splitting on whitespace: word count = `maskedText.strip().split("\\s+")` length, and 0 when the text is blank/whitespace-only after stripping. Every whitespace-separated token counts as one word, including a `__VAR_N__` token (always exactly one word) and punctuation-only tokens (e.g. `...` counts as 1). No filtering, no punctuation stripping.

#### Scenario: Basic counting
- **Given** `maskedText` = `"Hello, world!"` and `maskedText` = `"..."`
- **When** the count is computed
- **Then** the counts are 2 and 1 respectively.

#### Scenario: Masked token counts as one word
- **Given** `maskedText` = `"A __VAR_0__ with __VAR_1__"`
- **When** the count is computed
- **Then** the count is 4 (each token is one word; tokens never inflate the `> 30` / `≤ 8` counts).

#### Scenario: Blank text counts as zero
- **Given** `maskedText` = `"   "`
- **When** the count is computed
- **Then** the count is 0.

---

### Requirement 13: Strict TDD — Red-First Unit Tests

Unit tests MUST live in `src/test/java/com/lucalzt/mctranslator/domain/service/`, use JUnit 6 + AssertJ, and run with NO Spring context. Following the project's strict-TDD rule (`openspec/config.yaml` → `rules.tasks`), the test files MUST be written red-first (before the corresponding production code; a compilation failure of a not-yet-existing type counts as red), per capability slice — masking capability first (`MaskedText`, `VariableMasker`, `VariableUnmasker`), then heuristic capability (`GlossaryTermMatch`, `ScalingDecision`, `ScalingHeuristic`) — and MUST cover: validation rejection (nulls, blanks, token-set invariant, rule range), value equality, round-trip fidelity, token full-index matching, best-effort unmask edge cases (missing/unmatched/reordered/duplicate), one scenario per heuristic rule including the pinned rule-5 LORE equivalence, word-count boundaries, and the empty-glossary behavior.

#### Scenario: Red-first test suite runs without a Spring context
- **Given** the implemented `domain.service` package and its test sources
- **When** `mvnw.cmd test` is executed
- **Then** the JUnit 6 + AssertJ tests run without a Spring context and cover the required behaviors listed above.

---

### Requirement 14: Pinned domain.model Inventory Untouched

This change MUST NOT modify the archived `domain-models` inventory (spec Requirement 3) nor the `domain-output-ports` contracts. The new `MaskedText` / `GlossaryTermMatch` types are created ONLY in `domain.service`; they MUST NOT be introduced in `domain.model`, and no public type there may be added, removed, or renamed. No port interface (`GlossaryPort`, `TranslationEnginePort`, `TranslationCachePort`) and no port method may be changed by this change.

#### Scenario: Deferred VOs absent from the model package
- **Given** the implemented change
- **When** a grep for `MaskedText` and `GlossaryTermMatch` (and the ports-owned `CacheKey`) is run over `src/main/java/com/lucalzt/mctranslator/domain/model/`
- **Then** no matches are found and the model package still enumerates exactly its 9 pinned public types.

#### Scenario: Port contracts unchanged
- **Given** the implemented change
- **When** the source of `GlossaryPort`, `TranslationEnginePort`, and `TranslationCachePort` is inspected
- **Then** their method signatures are exactly as pinned by the archived `domain-output-ports` spec.

---

### Requirement 15: Clean Verify Passes

`mvnw.cmd clean verify` MUST pass on Windows (and `./mvnw clean verify` on Linux/macOS) with zero test failures, zero compilation errors, and exit code 0.

#### Scenario: Full build succeeds
- **Given** the implemented change
- **When** `mvnw.cmd clean verify` is executed
- **Then** the build compiles, all tests (existing and new) pass, and the command exits with status 0.

---

## Non-Goals

- **Precise-engine flag-off behavior and `[WARN]` emission** — the heuristic has no flag and always evaluates; the flag override and `[WARN]` diagnostics belong to the future application-layer `GlossaryAwareTranslator` (decision 5, H4).
- **Application-layer orchestration** — `GlossaryAwareTranslator` and any use-case wiring (cache → mask → heuristic → engine → unmask → persist) are a later change.
- **Glossary loading via `GlossaryPort`** — `ScalingHeuristic` receives `List<GlossaryEntry>` as a value; no port is consumed inside `domain.service`.
- **Cache/ports integration** — `TranslationCachePort` / `TranslationEnginePort` usage and the masked-text handoff to the engine (S2) are out of scope; port contracts stay untouched.
- **The engines themselves** — NLLB/ONNX and llama adapters, lifecycle, configuration.
- **Any change to the `domain.model` inventory** — the pinned 9-type set stays exactly as-is.
- **Infrastructure** — Spring Shell commands, adapters, SQLite, configuration plumbing, logging.
- **Extraction edge cases** — empty/whitespace-only leaf policy and `%s`-only text handling remain with the extraction change.
- **Not masked by `VariableMasker` (left literal, round-trip identity)** — `%%`; case variants `%S`/`%D`/`%F`; printf conversions beyond `[sdf]` (`%x`, `%o`, `%e`, `%g`, `%b`, `%c`, `%h`, `%n`); printf flags/width/precision forms (`%10.2f`, `%-10s`, `%2$10d`); Minecraft `\u00a7` section-sign formatting codes (`§a`, `\u00a7a`) — formatting codes are NOT variables.
- **Glossary matching extensions** — case-folded matching, substring matching, occurrence/position reporting in `GlossaryTermMatch` (H7 tuning, deferred to the empirical phase).
- **Threshold configuration** — the 30/8 word thresholds are compile-time constants; empirical tuning and configuration are future work (H8).
- **Variable deduplication** — masking is occurrence-based; identical variables produce distinct tokens and entries.
- **Unmasker failure modes** — `VariableUnmasker` never throws because a translation lacks, adds, reorders, or duplicates variables; malformed data is reported via `UnmaskResult` discrepancies, never exceptions.

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| Pattern-set coverage (R6) under-masks real modpack strings (`%10.2f`, `%x`, `%S` left literal) | Placeholders left unmasked may be altered by engines in the final JSON | Conservative documented-first set pinned in Requirement 6 with per-pattern scenarios; left-literal patterns round-trip with identity; the set is an explicit hook for the empirical tuning phase |
| Rule 5 LORE guard outcome-equivalence (decision 2) misread as "LORE → PRECISE" | Future readers "fix" the table unilaterally, changing pipeline behavior | The equivalence is pinned by an explicit scenario in Requirement 9; the resolution note documents the literal reading |
| `%%s` adjacency edge (`100%%s` masks the trailing `%s`) | Rare placeholder mis-masking in contrived printf text | Documented as an accepted v1 limitation in "Resolved in Spec"; captured for the empirical phase |
| Token collision / partial match (`__VAR_1` vs `__VAR_10`; literal `__VAR_N__` in source) | Wrong restorations in final JSON | Full-index token regex (`__VAR_(\d+)__`) + no-leakage protection pinned in Requirements 5/6/7 with dedicated scenarios |
| Discrepancies ignored by the future application layer | Silent quality degradation when variables are lost | `UnmaskResult` discrepancies are a first-class API output (Requirement 8); the proposal mandates the application layer consume them for `[WARN]` |
| Heuristic input text ambiguity (masked vs raw) | Word counts and glossary matches computed on the wrong text → wrong engine | Requirement 9 pins masked-text-only evaluation with a dedicated scenario |
| Over-masking of JSON braces | JSON objects wrongly tokenized | The MessageFormat pattern requires a digit after `{`; pinned by the JSON-braces scenario in Requirement 6 |
| Empty-glossary rule-5 reading diverges from proposal wording | `matchedRule` differs (5 vs 6) from an alternative reading | Engine outcome is identical (FAST); the literal reading is pinned and flagged in "Resolved in Spec" with rationale |
| Scope creep into application layer (flag-off, WARN, engine handoff) | Change grows beyond domain/services | Explicit Non-Goals (decision 5, S2); the heuristic has no flag by construction |
| Review workload (≈ 10–13 files, strict TDD red-first) | Exceeds the 400-line review budget | Tasks phase MUST forecast chained PRs sliced by capability (masking; heuristic; gates) per S3 |
| Java 25 / GraalVM constraints | Preview features (e.g. String Templates) break the native build | Only stable features: records, enums, precompiled `Pattern` (as in `domain-models` design §7.3) |
| `MaskedText` strong token-set invariant rejects hand-built VOs | Direct VO construction is stricter than some callers expect | Invariant is trivially satisfied by `mask()` (the only producer); scenarios pin the rejection behavior |

---

## Traceability to the Proposal

| Proposal element | Spec requirement(s) |
|---|---|
| Purpose — package `domain.service` with maskers, heuristic, deferred VOs | R1, R3, R5, R11 |
| Approach items 2–6 (MaskedText, mask, unmask + discrepancies, suggest + decision, GlossaryTermMatch) | R5, R6, R7, R8, R9, R10, R11 |
| Decision 1 — printf + MessageFormat variable families | R6 |
| Decision 2 — rule 5 literal table + LORE guard equivalence | R9 (LORE-guard scenario), "Resolved in Spec" |
| Decision 3 — best-effort unmask + discrepancy exposure, never throws | R8 |
| Decision 4 — decision record output | R9, R10 |
| Decision 5 — precise-engine flag-off out of scope | Non-Goals |
| Defaults — VO placement in `domain.service`; token collision/no-leakage; word counting on masked text | R5, R6, R7, R9, R12, R14 |
| Acceptance criteria 1, 3–10 | R15, R6–R12, R14 |
| Open items M3, H1, H7, H8, M8, M6, H6, S3 | "Resolved in Spec" table; R6, R8, R9, R12, R13 |
| Strict TDD (config.yaml `rules.tasks`) | R13, R15 |
