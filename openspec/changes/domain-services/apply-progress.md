# Apply Progress: Domain Services (`domain-services`)

## Status

**Slice A (PR #1) — COMPLETE.** Tasks T1–T4 implemented, red-first, with per-task
work-unit commits. Slice A gate green: `mvnw.cmd clean verify` → `BUILD SUCCESS`,
exit code 0, 59/59 tests green (18 new domain tests + 41 prior tests incl. the
existing `TranslateCommandTests` and `MctranslatorApplicationTests`).

**Slice B (PR #2) — COMPLETE.** Tasks T5–T6 (`VariableMasker` + test) implemented,
red-first, with per-task work-unit commits. Slice B gate green:
`mvnw.cmd clean verify` → `BUILD SUCCESS`, exit code 0, 68/68 tests green
(27 new domain tests + 41 prior tests).

**Slice C (PR #3) — COMPLETE.** Tasks T7–T9 (`VariableUnmasker` + test +
`VariableRoundTripTest`) implemented, red-first, with per-task work-unit
commits. Slice C gate green: `mvnw.cmd clean verify` → `BUILD SUCCESS`,
exit code 0, 80/80 tests green (39 new domain tests + 41 prior tests).

**Slice D (PR #4) — COMPLETE.** Tasks T10–T13 (`GlossaryTermMatch` +
`ScalingDecision` + 2 tests) implemented, red-first, with per-task work-unit
commits. Slice D gate green: `mvnw.cmd clean verify` → `BUILD SUCCESS`,
exit code 0, 94/94 tests green (53 new domain tests + 41 prior tests).

**Slice E (PR #5) — COMPLETE.** Tasks T14–T15 (`ScalingHeuristic` + test)
implemented, red-first, with per-task work-unit commits. Slice E gate green:
`mvnw.cmd clean verify` → `BUILD SUCCESS`, exit code 0, 116/116 tests green
(75 new domain tests + 41 prior tests).

**Slice F (PR #6) — COMPLETE.** Tasks T16–T18 (closing gates) implemented with
work-unit commits. T16 `package-info.java` with `@NullMarked` + package
Javadoc (Javadoc block FIRST, then the annotation, then the package
declaration); T17 structural verification greps (R1/R2/R3/R14 + token-format
strictness) all pass with zero offending matches; T18 final gate:
`mvnw.cmd clean verify` → `BUILD SUCCESS`, exit code 0, **116/116 tests green**
(75 new domain tests + 41 prior tests — unchanged count, Slice F adds no
tests). **All 18 tasks of the change are now done.**

## Task State

- [x] T1: Write `MaskedTextTest` (RED)
- [x] T2: Implement `MaskedText` (GREEN)
- [x] T3: Write `UnmaskResultTest` (RED)
- [x] T4: Implement `UnmaskResult` (GREEN)
- [x] T5: Write `VariableMaskerTest` (RED) — Slice B
- [x] T6: Implement `VariableMasker` (GREEN) — Slice B
- [x] T7: Write `VariableUnmaskerTest` (RED) — Slice C
- [x] T8: Implement `VariableUnmasker` (GREEN) — Slice C
- [x] T9: Write `VariableRoundTripTest` (R7 composition pin) — Slice C
- [x] T10: Write `GlossaryTermMatchTest` (RED) — Slice D
- [x] T11: Implement `GlossaryTermMatch` (GREEN) — Slice D
- [x] T12: Write `ScalingDecisionTest` (RED) — Slice D
- [x] T13: Implement `ScalingDecision` (GREEN) — Slice D
- [x] T14: Write `ScalingHeuristicTest` (RED) — Slice E
- [x] T15: Implement `ScalingHeuristic` (GREEN) — Slice E
- [x] T16: Create `package-info.java` with `@NullMarked` + package Javadoc — Slice F
- [x] T17: Run structural verification greps (R1 / R2 / R3 / R14) — Slice F
- [x] T18: Full build gate — `mvnw.cmd clean verify` — Slice F

## Run Summary

| Slice | Scope | Result |
|---|---|---|
| A | T1–T4 (`MaskedText`, `UnmaskResult` + 2 tests) | ✅ Done, gate green |
| B | T5–T6 (`VariableMasker` + test) | ✅ Done, gate green |
| C | T7–T9 (`VariableUnmasker` + test + round-trip pin) | ✅ Done, gate green |
| D | T10–T13 (`GlossaryTermMatch`, `ScalingDecision` + 2 tests) | ✅ Done, gate green |
| E | T14–T15 (`ScalingHeuristic` + test) | ✅ Done, gate green |
| F | T16–T18 (`package-info.java`, structural greps, final verify) | ✅ Done, **all 18 tasks complete** |

## TDD Loop (per task)

| Task | Red | Green | Verification |
|---|---|---|---|
| T1 `MaskedTextTest` | ❌ cannot find symbol: MaskedText (26 errors) | — | `-Dtest=MaskedTextTest test` FAILED (red) |
| T2 `MaskedText` | — | ✅ 12/12 | `-Dtest=MaskedTextTest test` BUILD SUCCESS |
| T3 `UnmaskResultTest` | ❌ cannot find symbol: UnmaskResult | — | `-Dtest=UnmaskResultTest test` FAILED (red) |
| T4 `UnmaskResult` | — | ✅ 6/6 | `-Dtest=UnmaskResultTest test` BUILD SUCCESS; **Slice A gate** `clean verify` 59/59, exit 0 |
| T5 `VariableMaskerTest` | ❌ cannot find symbol: VariableMasker (4 errors) | — | `-Dtest=VariableMaskerTest test` FAILED (red) |
| T6 `VariableMasker` | — | ✅ 9/9 | `-Dtest=VariableMaskerTest test` BUILD SUCCESS; **Slice B gate** `clean verify` 68/68, exit 0 |
| T7 `VariableUnmaskerTest` | ❌ cannot find symbol: VariableUnmasker (3 errors) | — | `-Dtest=VariableUnmaskerTest test` FAILED (red) |
| T8 `VariableUnmasker` | — | ✅ 9/9 | `-Dtest=VariableUnmaskerTest test` BUILD SUCCESS |
| T9 `VariableRoundTripTest` | — (composition pin, green-first) | ✅ 3/3 | `-Dtest=VariableRoundTripTest test` BUILD SUCCESS; **Slice C gate** `clean verify` 80/80, exit 0 |
| T10 `GlossaryTermMatchTest` | ❌ cannot find symbol: GlossaryTermMatch (14 errors) | — | `-Dtest=GlossaryTermMatchTest test` FAILED (red) |
| T11 `GlossaryTermMatch` | — | ✅ 5/5 | `-Dtest=GlossaryTermMatchTest test` BUILD SUCCESS |
| T12 `ScalingDecisionTest` | ❌ cannot find symbol: ScalingDecision | — | `-Dtest=ScalingDecisionTest test` FAILED (red) |
| T13 `ScalingDecision` | — | ✅ 9/9 | `-Dtest=ScalingDecisionTest test` BUILD SUCCESS; **Slice D gate** `clean verify` 94/94, exit 0 |
| T14 `ScalingHeuristicTest` | ❌ cannot find symbol: ScalingHeuristic (7 errors) | — | `-Dtest=ScalingHeuristicTest test` FAILED (red) |
| T15 `ScalingHeuristic` | — | ✅ 22/22 (1 test corrected during green — see deviation 15) | `-Dtest=ScalingHeuristicTest test` BUILD SUCCESS; **Slice E gate** `clean verify` 116/116, exit 0 |
| T16 `package-info.java` | — | ✅ `-q compile` exit 0 | `.\mvnw.cmd -q compile` BUILD SUCCESS (`@NullMarked` resolves) |
| T17 structural greps | — | ✅ R1/R2/R3/R14 zero offending matches | See "Files Created (Slice F)" + deviations 19–20 for commands and evidence |
| T18 final gate | — | ✅ 116/116 | `.\mvnw.cmd clean verify` → **BUILD SUCCESS**, exit 0, 116 tests, 0 failures/errors/skipped |

## Files Created (Slice A)

Main (new package `com.lucalzt.mctranslator.domain.service`):
- `src/main/java/com/lucalzt/mctranslator/domain/service/MaskedText.java` — record
  `MaskedText(String maskedText, List<String> variables)` (design §3.1 / R5):
  compact constructor in order — `maskedText` null → NPE, blank → IAE;
  `variables` null or any element null → NPE, blank element → IAE; **token-set
  invariant** via `private static final Pattern TOKEN_PATTERN =
  Pattern.compile("__VAR_(\\d+)__")` + `boolean[] seen` + match counter (D10):
  indices in `maskedText` must be exactly `{0, 1, …, n-1}` each exactly once,
  else IAE (textual token order NOT validated); `variables = List.copyOf(...)`
  defensive copy. Javadoc on the record (token correspondence
  `__VAR_N__ ↔ variables.get(N)`), compact constructor (`@throws` NPE/IAE) and
  both accessors. Imports: JDK only (R2). No custom exceptions (D11); canonical
  constructors only (D12). Edge: an over-long digit run in a literal token
  (`__VAR_99999999999999999999__`) is mapped from `NumberFormatException` to
  IAE, preserving the R5 "any violation → IAE" contract.
- `src/main/java/com/lucalzt/mctranslator/domain/service/UnmaskResult.java` —
  record `UnmaskResult(String restoredText, List<Integer> missingTokenIndices,
  List<Integer> unmatchedTokenIndices, boolean reordered)` (design §3.2 / R8):
  compact constructor — `restoredText` null → NPE; each list null → NPE; both
  lists `List.copyOf` defensively copied. **No validation of sortedness/dedup/
  consistency** (D2 — unmasker producer contract, documented in Javadoc and
  pinned by tests). Javadoc on the record documents the discrepancy semantics
  (missing = sorted ascending `0 ≤ N < n` absent from translated text; unmatched
  = sorted ascending deduplicated `N ≥ n` in translated text; `reordered` = token
  appearance-order sequence not monotonically non-decreasing — duplicates alone
  never imply reordering) plus `@throws` on the constructor and Javadoc on all 4
  accessors. Imports: JDK only (R2).

Tests:
- `src/test/java/com/lucalzt/mctranslator/domain/service/MaskedTextTest.java` —
  12 tests: valid mask state + accessors + `__VAR_N__ ↔ variables.get(N)`
  correspondence; null maskedText → NPE; blank maskedText → IAE; null
  variables list / null element → NPE; blank element → IAE; invariant
  out-of-range (`("foo __VAR_2__ bar", List.of("a"))`), duplicate
  (`("foo __VAR_0__ __VAR_0__", List.of("a"))`), missing index
  (`("__VAR_1__", List.of("a","b"))`) → IAE; empty token set `("hello",
  List.of())` accepted; defensive copy (caller mutation → instance unaffected;
  accessor list immutable — `assertThatThrownBy(() -> accessorList.add(...))`);
  equality/hashCode by both components.
- `src/test/java/com/lucalzt/mctranslator/domain/service/UnmaskResultTest.java` —
  6 tests: valid result preserves all 4 components; null restoredText → NPE;
  null missing/unmatched list → NPE; defensive copy of both lists (caller
  mutation → instance unaffected; returned lists immutable); equality/hashCode
  across the 4 components.

No files changed outside the package; no `pom.xml` change (jspecify 1.0.1
already present, design §10.2); `openspec/` artifacts remain untracked
(consistent with prior changes).

## Files Created (Slice B)

Main:
- `src/main/java/com/lucalzt/mctranslator/domain/service/VariableMasker.java` —
  stateless `final class` (design §5.1 / R6) with the pinned precompiled
  `private static final Pattern VARIABLE_PATTERN = Pattern.compile(
  "__VAR_(\\d+)__|%(?:\\d+\\$)?[sdf]|\\{(\\d+)(?:,[^}]*)?\\}")` (alternation
  order literal | printf | MessageFormat — D5) and no declared constructor
  (implicit public default, D13, design §2.5). `mask(String text)`:
  `Objects.requireNonNull` → NPE; `isBlank()` → IAE; single `find()` +
  `appendReplacement`/`appendTail` scan assigning the next token index k in
  first-occurrence order, replacing with the literal `"__VAR_" + k + "__"`
  (no `$`/`\` — no quoting needed, still documented), appending
  `matcher.group()` verbatim to `variables` (no dedup); returns
  `new MaskedText(masked, variables)` (invariant trivially satisfied). Branch
  detection is content-based on the whole match (D1 — prefixes `_`/`%`/`{`
  are disjoint, no group numbers); all three branches handled identically, so
  the classification is documented via a loop comment, not dead code. Javadoc
  on the class (pinned pattern set, first-occurrence numbering, no-leakage
  property, documented `%%s` adjacency limitation, re-entrancy note) and on
  `mask` (`@param`/`@return`/`@throws`). Imports: JDK only (R2).

Tests:
- `src/test/java/com/lucalzt/mctranslator/domain/service/VariableMaskerTest.java` —
  9 tests covering all eight R6 scenarios: each printf family (`%s`, `%d`,
  `%f`, `%1$s`, `%2$d`, `%10$s`) and MessageFormat family (`{0}`,
  `{1,number}`, `{0,date,full}`, `{0,choice,0#zero|1#one}`, `{12,number,integer}`)
  → positional token + exact variable; first-occurrence order + no dedup
  (`"A %s and %s and {0}"` → `__VAR_0__..__VAR_2__`, variables
  `["%s","%s","{0}"]`); literal-token protection no leakage (`"Use __VAR_3__ now"`
  → `"Use __VAR_0__ now"`, variables `["__VAR_3__"]`); non-variables left
  literal (`"100%% complete, %S var, %x hex, %10.2f wide, §a colored"` →
  unchanged, empty variables); JSON braces not masked; combined mixed scan
  (`"A %s {0} %1$s {1,number}"`); null → NPE; blank → IAE.

## Files Created (Slice C)

Main:
- `src/main/java/com/lucalzt/mctranslator/domain/service/VariableUnmasker.java` —
  stateless `final class` (design §5.2 / R8) with the pinned precompiled
  `private static final Pattern TOKEN_PATTERN = Pattern.compile("__VAR_(\\d+)__")`
  (full-index, greedy `\d+`, mandatory trailing `__` — R7 partial matches
  structurally impossible, D5) and no declared constructor (implicit public
  default, D13). `unmask(MaskedText masked, String translatedText)`:
  `Objects.requireNonNull` on both args → NPE; scans `translatedText`;
  `n = masked.variables().size()`; tracks `seen` (`boolean[]` over `0..n-1`),
  `unmatched` (`TreeSet<Integer>` of indices `N ≥ n`), `maxSeen` (running max
  → `reordered = true` as soon as a token index is strictly smaller —
  duplicates alone never trigger it); per token `__VAR_N__`: `N < n` →
  replace with `masked.variables().get(N)` wrapped in
  `Matcher.quoteReplacement(...)` (**mandatory**, D8 — variables contain
  `$`/`\` such as `%1$s`); `N ≥ n` → left verbatim (replacement = the quoted
  token itself, M6 resolution — never `""`, never throws);
  `missingTokenIndices` = ascending `0..n-1` not in `seen`; return
  `new UnmaskResult(...)`. Javadoc on the class (best-effort contract,
  restore-by-index semantics, discrepancy semantics, quoteReplacement note)
  and on `unmask` (`@param`/`@return`/`@throws`). Imports: JDK only (R2).
  Private helper `parseTokenIndex` clamps pathologically long digit runs
  (e.g. `__VAR_99999999999999999999__`) to `Integer.MAX_VALUE` so the
  best-effort "never throws" contract holds — defensive, mirrors the
  `MaskedText` deviation note 3.

Tests:
- `src/test/java/com/lucalzt/mctranslator/domain/service/VariableUnmaskerTest.java` —
  9 tests covering all six R8 scenarios + the D8 quoting pin: missing token
  never throws and is reported (`[1]`, unmatched empty); unmatched left
  verbatim and reported (`[9]` deduplicated/sorted, missing empty); reordered
  restored by index (`reordered` true, both lists empty); duplicate restores
  twice, not reordered; perfect translation → no discrepancies; interleaved
  matched/unmatched `[0,9,1]` → `reordered` true, unmatched `[9]`;
  `$`-variable (`"%1$s"`) round-trips verbatim (D8 pin); `null` masked → NPE;
  `null` translatedText → NPE.
- `src/test/java/com/lucalzt/mctranslator/domain/service/VariableRoundTripTest.java` —
  3 tests covering all three R7 scenarios (composition pin, green-first):
  identity round trip (`"HP: %s, Cost: %1$d, Value: {0,number}"` →
  unmask(mask) → exact original, no discrepancies); literal-token round trip
  no leakage (`"Use __VAR_3__"` → restored exactly, not a masked token);
  full-index tokens never partially match (11-entry `MaskedText`, translated
  `"A __VAR_10__ B __VAR_1__"` → `variables.get(10)` / `variables.get(1)`
  restored, no prefix restoration of `__VAR_1` from `__VAR_10`).

## Files Created (Slice D)

Main:
- `src/main/java/com/lucalzt/mctranslator/domain/service/GlossaryTermMatch.java` —
  record `GlossaryTermMatch(String term, GlossaryEntryClassification
  classification)` (design §3.3 / R11): compact constructor in order —
  `term` null → NPE, blank → IAE, `classification` null → NPE. Equality by
  both components (record semantics). Javadoc on the record (term = verbatim
  source-language term of the matched entry) and on both accessors (R4).
  Imports: `java.util.Objects` + `com.lucalzt.mctranslator.domain.model.GlossaryEntryClassification`
  only (R2). No factories (D12).
- `src/main/java/com/lucalzt/mctranslator/domain/service/ScalingDecision.java` —
  record `ScalingDecision(TranslationEngineType engine, int matchedRule,
  List<GlossaryTermMatch> glossaryMatches)` (design §3.4 / R10): compact
  constructor in order — `engine` null → NPE, `matchedRule` outside 1..6 →
  IAE, `glossaryMatches` null → NPE (explicit `requireNonNull`), any element
  null → NPE (from `List.copyOf`), `glossaryMatches = List.copyOf(...)`
  defensive copy. **Value contract documented in Javadoc, NOT re-validated**
  (D7): `glossaryMatches` non-empty exactly when `matchedRule` is 2 or 5;
  rule 2 ⇒ ≥ 1 `AMBIGUOUS` match (may carry non-AMBIGUOUS matches too — D3,
  the full match list); rule 5 ⇒ no `LORE` match; rules 1–3 ⇒ PRECISE, 4–6 ⇒
  FAST. Javadoc on the record (rule-to-engine mapping + D3 note) and on all 3
  accessors (R4). Imports: `java.util.List`, `java.util.Objects` +
  `com.lucalzt.mctranslator.domain.model.TranslationEngineType` only (R2).

Tests:
- `src/test/java/com/lucalzt/mctranslator/domain/service/GlossaryTermMatchTest.java` —
  5 tests: valid match preserves both components; null term → NPE; blank term
  (`""` / `"   "`) → IAE; null classification → NPE; equality/hashCode by both
  components (differing term and differing classification → not equal).
- `src/test/java/com/lucalzt/mctranslator/domain/service/ScalingDecisionTest.java` —
  9 tests: valid rule-2 shape (engine PRECISE, rule 2, AMBIGUOUS + PLAIN
  matches) and valid rule-5 shape (engine FAST, rule 5, PLAIN matches) preserve
  components; `matchedRule` 0 and 7 → IAE; null engine → NPE; null list → NPE;
  null element → NPE; defensive copy (caller mutation → instance unaffected;
  accessor list immutable); **heuristic-produced consistency** — one decision
  per rule 1–6 with `matchedRule` in 1..6, engine consistent with the rule
  mapping (1–3 PRECISE, 4–6 FAST), `glossaryMatches` non-empty only for rules
  2/5, rule-2 has ≥ 1 `AMBIGUOUS`, rule-5 has no `LORE` (documented contract
  pin, D7).

## Files Created (Slice E)

Main:
- `src/main/java/com/lucalzt/mctranslator/domain/service/ScalingHeuristic.java` —
  stateless `final class` (design §5.3 / R9, R12) with a default public
  constructor (D13) exposing `ScalingDecision suggest(JsonPath path, String
  maskedText, List<GlossaryEntry> glossary)` (H1 pin — bare `String`, NOT
  `MaskedText`). Compile-time named constants `THRESHOLD_PRECISE_WORDS = 30`
  (rule 3: strictly greater) and `THRESHOLD_FAST_WORDS = 8` (rule 5:
  inclusive) — H8, no configuration (D5). `suggest`:
  `Objects.requireNonNull` on `path`, `maskedText`, `glossary` → NPE;
  **evaluation ALWAYS on `maskedText`** (R9); word count via package-private
  `static int countWords(String)` (D4): `strip()`, empty → 0, else
  `split("\\s+").length` (the `isEmpty()` guard is required — `"".split("\\s+")`
  returns `[""]`); glossary matching via private `matchGlossaryTerms`: per
  entry `Pattern.compile("\\b" + Pattern.quote(term) + "\\b")` + `find()` on
  `maskedText` (whole-word, case-sensitive, H7/D9; `Pattern.quote` mandatory;
  one compile per entry per call — D6, stateless). **Rule evaluation in strict
  order, first match wins, lazy per-rule** (D14): 1) `path.startsWith("quest",
  "description") || startsWith("lore") || startsWith("advancement")` →
  PRECISE/1; 2) any match classified `AMBIGUOUS` (match pass computed lazily
  only if rule 1 failed) → PRECISE/2 with the FULL match list (D3); 3)
  `countWords(maskedText) > 30` → PRECISE/3; 4) `path.startsWith("item") ||
  startsWith("block") || startsWith("entity") || startsWith("gui")` → FAST/4;
  5) `countWords(maskedText) ≤ 8` AND no match classified `LORE` → FAST/5
  (empty glossary: vacuously true — pinned literal reading); 6) default →
  FAST/6. **Pinned behaviors**: the LORE guard falls to rule 6 with the SAME
  engine outcome (FAST) — NOT "fixed" into LORE→PRECISE; empty-glossary short
  text yields `matchedRule` 5. Javadoc on the class (the 6-rule precedence
  table as a Javadoc table, first-match-wins, thresholds via `{@value}`,
  masked-text-only evaluation, empty-glossary and LORE-guard pins) and on
  `suggest` (`@param`/`@return`/`@throws`), the explicit constructor and
  `countWords` (R4, java-docs). Imports: `java.util.ArrayList`,
  `java.util.List`, `java.util.Objects`, `java.util.regex.Pattern` +
  `com.lucalzt.mctranslator.domain.model.GlossaryEntry`,
  `GlossaryEntryClassification`, `JsonPath`, `TranslationEngineType` (R2). No
  jspecify imports (`@NullMarked` package-info is T16, later slice). No custom
  exceptions (D11); no instance fields (stateless, D13/H8).

Tests:
- `src/test/java/com/lucalzt/mctranslator/domain/service/ScalingHeuristicTest.java` —
  22 tests covering R9 (all scenarios), the R10 engine/rule contract and R12
  (all 3 scenarios): rule 1 (`quest.description.task1` short text → PRECISE/1
  winning over rule 5; `lore.story`, `advancement.husbandry.root` →
  PRECISE/1); rule 2 (AMBIGUOUS `"ancient temple"` on `item.artifact` →
  PRECISE/2 with the match, winning over rule 4; whole-word case-sensitive —
  `"Iron is common; irony is not"` with AMBIGUOUS `iron`/`Iron` → PRECISE/2
  with exactly the `Iron` match, `iron` inside `irony` NOT detected); rule 3
  strict (exactly 30 words on `gui.menu` → FAST/4 — rule 3 does NOT match at
  30; 31 words on `item.sword` → PRECISE/3 winning over rule 4); rule 4
  (`item.sword`, `block.stone`, `entity.zombie`, `gui.container` short →
  FAST/4); rule 5 (`"Hello there"` 2 words on `random.key` with only-PLAIN
  glossary → FAST/5 with empty matches — full-match-list semantics D3;
  exactly 8 words → FAST/5, inclusive); **LORE guard pinned** (`"The dark lord
  rises"` with LORE `"dark lord"` → FAST/6, outcome-equivalence pinned, MUST
  NOT become LORE→PRECISE; same text with PLAIN `"dark lord"` → FAST/5 with
  the match); **empty glossary** (`"Hi"` → FAST/5, rule 2 vacuous false / rule
  5 vacuously true; 40 words → PRECISE/3); **rule 6 default fall-through**
  (10-word text on `some.other.key` empty glossary → FAST/6 — see deviation
  15 for why the spec's `"A plain sentence"` example was corrected);
  **evaluation on masked text** (`"A __VAR_0__ with __VAR_1__ detail"` counts
  as 5 words, so `gui.menu` → FAST/4 — a raw 40-word form would have fired
  rule 3); **R12 direct** (`countWords("Hello, world!")` = 2,
  `countWords("...")` = 1, `countWords("A __VAR_0__ with __VAR_1__")` = 4,
  `countWords("   ")` = 0); **masked-token glossary safety** (AMBIGUOUS `VAR`
  and `0` terms never match inside `__VAR_0__` → FAST/5, empty matches); `null`
  path / `null` maskedText / `null` glossary → NPE. Helpers `words(int)` and
  `entry(String, classification)` avoid repetition (spring-boot-testing skill:
  helper methods for commonly used objects).

## Files Created (Slice F)

Main:
- `src/main/java/com/lucalzt/mctranslator/domain/service/package-info.java` —
  package Javadoc **first** (pure domain services of the translation pipeline:
  variable masking/unmasking `VariableMasker` / `VariableUnmasker` and the
  scaling heuristic `ScalingHeuristic`; the value objects / records exchanged
  `MaskedText`, `UnmaskResult`, `GlossaryTermMatch`, `ScalingDecision`; 100%
  pure Java with JDK, jspecify and `domain.model` imports only), **then**
  `@org.jspecify.annotations.NullMarked`, **then**
  `package com.lucalzt.mctranslator.domain.service;` — the correct
  package-info form, mirroring `domain/model/package-info.java` (design §3.5 /
  R3, per the `domain-models` apply-progress correction). The Javadoc states
  that no element is nullable (deliberately avoiding the literal `@Nullable`
  token — see deviation 19). T16 verification: `.\mvnw.cmd -q compile` exit 0.

### Structural grep evidence (T17 — PowerShell `Select-String`, `rg` not installed)

| Check | Command | Result |
|---|---|---|
| R1a — 8 files under `domain/service/` | `Get-ChildItem ...\domain\service\*.java \| ForEach-Object { $_.Name }` | 8 files: 7 types + `package-info.java` |
| R1b — no `com.mctranslator` package | `Get-ChildItem -Recurse -Filter *.java src\main\java \| Select-String -Pattern 'package com\.mctranslator\b'` | NO MATCHES |
| R1c — public type inventory (exactly 7) | `Select-String -Pattern '^public (record\|final class\|class\|interface\|enum) '` | 7 (`MaskedText`, `VariableMasker`, `VariableUnmasker`, `UnmaskResult`, `GlossaryTermMatch`, `ScalingDecision`, `ScalingHeuristic`) |
| R2a — framework imports | `Select-String -Pattern 'import (org\.springframework\|jakarta)'` | NO MATCHES |
| R2b — exhaustive import allowlist | `Select-String -Pattern '^import (?!java\.\|org\.jspecify\.\|com\.lucalzt\.mctranslator\.)'` | NO MATCHES |
| R3 — no `@Nullable` | `Select-String -Pattern '@Nullable'` | NO MATCHES |
| R14a — deferred VOs absent from `domain/model` | `Select-String -Pattern 'MaskedText\|GlossaryTermMatch\|CacheKey'` over `domain\model\*.java` | NO MATCHES |
| R14b — model inventory exactly 9 | `Select-String -Pattern '^public (record\|final class\|class\|interface\|enum) '` over `domain\model\*.java` | 9 public types |
| R14c — ports untouched | `git diff -- src/main/java/com/lucalzt/mctranslator/domain/port/out` | NO DIFF (EMPTY) |
| R14d — no bare `__VAR_<digits>` without closing `__` | `Select-String -Pattern '__VAR_\d+(?![\d_])'` | NO MATCHES (see deviation 20 for the naive-lookahead artifact) |
| R14e — compiled token regex exactly `__VAR_(\\d+)__` | `Select-String -Pattern '"__VAR_\(\\\\d\+\)__'` | exactly 3: `MaskedText.java:33`, `VariableMasker.java:51`, `VariableUnmasker.java:48` |

## Commits (17, one per task, conventional)

```
aaa53e3 test(domain): add MaskedTextTest (red)
78791ac feat(domain): implement MaskedText record
24cbe06 test(domain): add UnmaskResultTest (red)
101bdfa feat(domain): implement UnmaskResult record
af6ec0e test(domain): add VariableMaskerTest (red)
f00f6d7 feat(domain): implement VariableMasker
e729b35 test(domain): add VariableUnmaskerTest (red)
f6f24e3 feat(domain): implement VariableUnmasker
2afadca test(domain): add round-trip composition pin
180b856 test(domain): add GlossaryTermMatchTest (red)
a979fa5 feat(domain): implement GlossaryTermMatch record
c97191d test(domain): add ScalingDecisionTest (red)
e41c53c feat(domain): implement ScalingDecision record
9ae85d4 test(domain): add ScalingHeuristicTest (red)
7c487f9 feat(domain): implement ScalingHeuristic
5fb1571 docs(domain): add package-info with @NullMarked
b6f62ad docs(domain): reword package-info nullability prose
```

## Deviations & Notes

1. Strict TDD red states were **compilation failures** of the not-yet-existing
   types ("cannot find symbol"), which counts as red per tasks.md criteria.
2. Per-task commits leave the tree red between the `test(domain)` and
   `feat(domain)` commits of each pair — expected for the stacked chain; the
   slice gate runs on the green head.
3. `MaskedText` compact constructor guards `Integer.parseInt` against
   pathologically long digit runs inside a literal token (e.g.
   `__VAR_99999999999999999999__`): `NumberFormatException` is rethrown as
   `IllegalArgumentException` so the R5 "any violation → IAE" contract holds
   (a raw `NumberFormatException` would leak a JDK-internal error type). Not
   pinned by any test scenario; purely defensive.
4. Imports verified per R2: `MaskedText.java` imports `java.util.List`,
   `java.util.Objects`, `java.util.regex.Matcher`, `java.util.regex.Pattern`
   only; `UnmaskResult.java` imports `java.util.List`, `java.util.Objects`
   only. Zero `org.springframework`/`jakarta`/jspecify imports in this slice
   (`@NullMarked` package-info is T16, later slice).
5. `openspec/changes/domain-services/` artifacts remain untracked, consistent
   with how the earlier phases left them; not part of the slice's code diff.
6. `VariableMasker` implements D1 content-based branch detection as a
   documented loop comment (the pinned alternation's matched prefixes are
   disjoint `_` / `%` / `{`); all three branches are handled identically
   (replace + record verbatim), so the classification has no observable
   branch in the output — faithful to D1 without dead code.
7. No declared constructor: the implicit public default constructor satisfies
   D13 / design §2.5 ("no declared constructor"); the class has no instance
   fields (stateless, thread-safe per design §2.5).
8. Imports verified per R2: `VariableMasker.java` imports
   `java.util.ArrayList`, `java.util.List`, `java.util.Objects`,
   `java.util.regex.Matcher`, `java.util.regex.Pattern` only. No jspecify
   imports (`@NullMarked` package-info is T16, later slice).
9. `VariableUnmasker` private helper `parseTokenIndex` clamps a digit run too
   long for `int` (e.g. inside `__VAR_99999999999999999999__`) to
   `Integer.MAX_VALUE`, which is always `≥ n` and therefore handled as an
   unmatched token left verbatim — preserves the R8 "never throws" best-effort
   contract on pathological translated text. Not pinned by any test scenario;
   purely defensive (mirrors deviation note 3).
10. Imports verified per R2: `VariableUnmasker.java` imports
    `java.util.ArrayList`, `java.util.List`, `java.util.Objects`,
    `java.util.TreeSet`, `java.util.regex.Matcher`, `java.util.regex.Pattern`
    only. No jspecify imports (`@NullMarked` package-info is T16, later slice).
11. `VariableRoundTripTest` was authored after both services existed and its
    targeted run was green on first submission (3/3) — no red state, per the
    composition-pin contract (design A5).
12. Imports verified per R2: `GlossaryTermMatch.java` imports
    `java.util.Objects` + `com.lucalzt.mctranslator.domain.model.GlossaryEntryClassification`
    only; `ScalingDecision.java` imports `java.util.List`, `java.util.Objects`
    + `com.lucalzt.mctranslator.domain.model.TranslationEngineType` only. Zero
    `org.springframework`/`jakarta`/jspecify imports in this slice
    (`@NullMarked` package-info is T16, later slice — consistent with
    deviations 4/8/10).
13. `ScalingDecision` relies on `List.copyOf` for the null-element NPE (it
    throws `NullPointerException` on a null element), mirroring the
    `UnmaskResult` pattern (deviation 4); only the list-null case gets an
    explicit `Objects.requireNonNull` with a clear message. Both paths are
    pinned by T12 tests as NPE.
14. The T12 "heuristic-produced consistency" test builds the rule 1–6 shapes
    directly on the record (the heuristic does not exist until Slice E) and
    asserts the R10 value contract as a documented pin — the constructor does
    NOT re-validate it (D7), so this is a documentation/test pin, exactly per
    the task description.
15. **Spec-internal contradiction found during T14/T15 (rule 6 example).** The
    R9 "Rule 6 — default fall-through" scenario (and the T14 task text) pins
    `"A plain sentence"` (3 words) on `some.other.key` with an empty glossary →
    FAST/6. This is **unsatisfiable under the binding "Resolved in Spec"
    literal reading**: with an empty glossary, every ≤ 8-word text fires rule 5
    (the "no LORE detected" clause is vacuously true), so 3 words MUST yield
    FAST/5, exactly as the R9 empty-glossary scenario ("Hi" → FAST/5) and the
    pinned instruction "empty-glossary short text yields matchedRule 5" state.
    The implementation follows the **binding pin** (rule 5 wins on short text);
    the `defaultFallThroughToRule6` test was corrected to a 10-word text
    (> 8 → rule 5 blocked, ≤ 30 → rule 3 not reached) so rule 6 is still pinned
    via the genuine default path, and the correction is documented in the test
    comment. The LORE-guard test additionally pins rule 6 on short text. This
    is a spec-example defect, not an implementation deviation — flagged as a
    risk in the apply envelope so the spec can be corrected before verify.
16. `ScalingHeuristic` computes the rule-5 LORE flag as a lazy stream scan at
    the rule-5 site (after rules 1–4 failed), and the rule-3 word count is only
    evaluated when reached — faithful to D14 (lazy per-rule). The glossary
    match pass runs once, only if rule 1 failed, and is reused by rules 2 and 5
    (D3/D14).
17. Imports verified per R2: `ScalingHeuristic.java` imports
    `java.util.ArrayList`, `java.util.List`, `java.util.Objects`,
    `java.util.regex.Pattern` + `com.lucalzt.mctranslator.domain.model`
    (`GlossaryEntry`, `GlossaryEntryClassification`, `JsonPath`,
    `TranslationEngineType`) only. No jspecify imports (`@NullMarked`
    package-info is T16, later slice — consistent with deviations 4/8/10/12).
18. `ScalingHeuristic` declares its default public constructor explicitly with
    a short Javadoc (D13 satisfied either way; the explicit declaration makes
    the public-API Javadoc requirement R4 unambiguous for the constructor).
19. **T17 (R3) — Javadoc wording vs the `@Nullable` grep.** The first draft of
    `package-info.java` contained the literal token `{@code @Nullable}` in its
    Javadoc prose ("there are no {@code @Nullable} elements"). The R3 grep
    (`Select-String -Pattern '@Nullable'`) matched that prose line, even though
    no annotation is present. The Javadoc was reworded to "and no element is
    nullable" so the grep is genuinely zero-match — the same approach
    `domain/model/package-info.java` uses (it references the annotation as
    `{@link org.jspecify.annotations.Nullable}`, never the bare token). No
    behavior change; committed separately (b6f62ad) so the T17 evidence is
    clean and the letter + spirit of R3 both hold.
20. **T17 (R14d/R14e) — .NET/PowerShell regex artifacts, not violations.** A
    naive bare-token check `__VAR_\d+(?!__)` false-positives on multi-digit
    tokens in Javadoc prose: the greedy `\d+` backtracks, so inside
    `{@code __VAR_10__}` it matches `__VAR_1` (next chars `0_` ≠ `__`). The
    corrected check requires the full digit run to be followed by a non-digit,
    non-underscore char — `__VAR_\d+(?![\d_])` — which yields zero matches
    (the only bare-looking forms are the replacement construction
    `"__VAR_" + nextIndex + "__"` in `VariableMasker`, properly closed).
    Similarly, the compiled-pattern literal is stored in the `.java` file with
    TWO backslashes (`"__VAR_(\\d+)__"`), so the match pattern must be
    `'"__VAR_\(\\\\d\+\)__'` (regex `\\\\` = two literal backslashes); the
    first attempt with a single `\\` matched zero. Corrected evidence: exactly
    3 occurrences (`MaskedText`, `VariableUnmasker` TOKEN_PATTERN +
    `VariableMasker` VARIABLE_PATTERN branch), confirming the pinned regex
    `__VAR_(\d+)__` is compiled exactly as specified (R7/R14).
21. **T18 — final gate.** `.\mvnw.cmd clean verify` → `BUILD SUCCESS`, exit
    code 0, **116/116 tests green** (0 failures, 0 errors, 0 skipped — same
    count as the Slice E gate, as expected: Slice F adds no tests). All 8 new
    domain test classes green (5+12+9+22+6+9+3+9 = 75 new) + 41 prior tests
    (38 `domain.model` + 2 `TranslateCommandTests` + 1
    `MctranslatorApplicationTests`). Pre-existing `NonInteractiveShellRunner`
    ERROR log and Mockito self-attach warning observed, not failures.

## Build Environment Notes

- On Windows PowerShell, invoke the wrapper as `.\mvnw.cmd` (bare `mvnw.cmd` is
  not resolved). `.\mvnw.cmd clean verify` passes on the green head.
- Existing suite prints expected Spring Shell `NonInteractiveShellRunner` ERROR
  log and a Mockito self-attach warning — pre-existing, not failures.

## Rollback Boundary

**Slice A**: delete the 4 files (`MaskedText.java`, `UnmaskResult.java`,
`MaskedTextTest.java`, `UnmaskResultTest.java`) — or revert commits
aaa53e3..101bdfa — nothing else exists yet (T5+ not implemented). Zero
dependents in the tree.

**Slice B**: delete `VariableMasker.java` + `VariableMaskerTest.java` — or
revert commits af6ec0e..f00f6d7. Slice-A types have no dependency on
`VariableMasker` (clean rollback, per tasks.md). Later slices (C–F) not yet
implemented.

**Slice C**: delete `VariableUnmasker.java`, `VariableUnmaskerTest.java`,
`VariableRoundTripTest.java` — or revert commits e729b35..2afadca. Slice-A/B
types have no dependency on `VariableUnmasker` (clean rollback, per tasks.md).
Later slices (D–F) not yet implemented.

**Slice D**: delete `GlossaryTermMatch.java`, `ScalingDecision.java`,
`GlossaryTermMatchTest.java`, `ScalingDecisionTest.java` — or revert commits
180b856..e41c53c. Slices A–C types have no dependency on these two records
(clean rollback, per tasks.md). Later slices (E–F) not yet implemented.

**Slice E**: delete `ScalingHeuristic.java` + `ScalingHeuristicTest.java` — or
revert commits 9ae85d4..7c487f9. Slices A–D types have no dependency on
`ScalingHeuristic` (clean rollback, per tasks.md). Slice F not yet
implemented.

**Slice F**: delete `package-info.java` — or revert commits
5fb1571..b6f62ad. Slices A–E types have no dependency on the package-info
(clean rollback, per tasks.md). **All 18 tasks green at the Slice F gate**;
chain revert = full deletion of `domain/service` (zero side effects, per
proposal rollback plan).

## Next

All 18 tasks of `domain-services` are implemented and the change is
**complete**: masking capability (R5/R6/R7/R8), heuristic capability
(R9/R10/R11/R12) and closing gates (R1/R2/R3/R4/R14/R15) are all pinned by
tests and structural greps, with the final `mvnw.cmd clean verify` gate green
(116/116, exit 0). Next phase: **verify** — the change is fully implemented.
Before archive, apply the spec correction flagged in deviation 15: the R9
"Rule 6 — default fall-through" scenario example in `spec.md` should be
corrected from `"A plain sentence"` to a > 8-word text (e.g. the 10-word
`"This sentence has exactly ten plain words written here today"` used by the
implementation) so the spec matches the pinned literal reading.

---

## Archive State

- **ARCHIVED**: 2026-08-09 by the `sdd-archive` phase (mode `project`).
- The spec correction above (deviation 15) was applied to `spec.md:324`,
  `tasks.md:344` and `design.md:379` before archive (see `archive-report.md`).
- `tasks.md` checkboxes were flipped to `[x]` (18/18) at archive time — the
  mechanical reconciliation recorded in `archive-report.md` (W1 resolution;
  `verify-report.md` proved every task complete).
- Approved spec synced upward to `openspec/specs/domain-service/` (`spec.md` +
  `spec.yaml`, byte-identical readback).
- Close record: `openspec/changes/domain-services/archive-report.md`; Engram
  mirror `sdd/domain-services/archive-report`. Change cycle closed
  (`next_recommended: none`). No pushes/PRs created.
