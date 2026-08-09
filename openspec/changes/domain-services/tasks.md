# Tasks: Domain Services (`domain-services`)

## Review Workload Forecast

Decision needed before apply: Yes
Chained PRs recommended: Yes
400-line budget risk: High

The change is **16 files** (8 main sources including `package-info.java` + 8 test classes), estimated at
**≈ +1,550–1,750 changed lines** (design §13 forecast 1,300–1,600 — the upper edge is confirmed and slightly
revised upward by the verbose `@DisplayName` + AssertJ test style used in the repo, e.g. `JsonPathTest`).
This exceeds both the **800-line session budget** (user-chosen) and the **400-line per-PR default**, so a
chained/stacked PR plan is **MANDATORY**. No `pom.xml` change is expected (jspecify 1.0.1 already present,
verified — design §10.2); the whole change is a brand-new package with clean per-slice rollback (delete the
slice's files).

**Chained-PR plan (6 slices)** keeps every PR at or near the 400-line default and every slice green on
`mvnw.cmd clean verify` with a clean rollback boundary (rollback = delete the slice's files; later slices
depend on earlier ones, so mid-chain rollback only affects later slices):

| Slice | Scope (tasks) | Files | Est. lines | Gate |
|---|---|---|---|---|
| **A — Masking VOs** | T1–T4 (`MaskedText`, `UnmaskResult` + 2 tests) | 4 | **≈ 395–460** | `mvnw.cmd clean verify` after T4 |
| **B — Masker** | T5–T6 (`VariableMasker` + test) | 2 | **≈ 225–260** | `mvnw.cmd clean verify` after T6 |
| **C — Unmasker + round trip** | T7–T9 (`VariableUnmasker`, `VariableUnmaskerTest`, `VariableRoundTripTest`) | 3 | **≈ 290–330** | `mvnw.cmd clean verify` after T9 |
| **D — Heuristic VOs** | T10–T13 (`GlossaryTermMatch`, `ScalingDecision` + 2 tests) | 4 | **≈ 265–295** | `mvnw.cmd clean verify` after T13 |
| **E — Heuristic** | T14–T15 (`ScalingHeuristic` + test) | 2 | **≈ 390–430** | `mvnw.cmd clean verify` after T15 |
| **F — Closing gates** | T16–T18 (`package-info.java`, structural greps, final verify) | 1 + checks | **≈ 15–25** | `mvnw.cmd clean verify` after T18 |

**Aggregation options (user decision before apply — delivery strategy `ask-on-risk`):**

- **Option 1 (recommended, strict granularity): 6 chained PRs** as the table above. Every PR ≤ ~460 lines;
  only Slice A and Slice E nudge past the 400 default. Fallback sub-splits if the strict 400 must hold:
  A → **A1 = T1–T2** (`MaskedText`, ≈ 235–265) + **A2 = T3–T4** (`UnmaskResult`, ≈ 160–195). E cannot
  sub-split (its red test T14 and green impl T15 are one atomic red→green unit); accept it at ~390–430 or
  re-bundle it with D (Option 2).
- **Option 2 (800-session-budget, 4 PRs): A+B** (T1–T6, 6 files, ≈ 620–720), **C** (T7–T9, 3 files,
  ≈ 290–330), **D+E** (T10–T15, 6 files, ≈ 655–725), **F** (T16–T18). Every PR fits the 800 budget.
- **Option 3 (3 PRs, not recommended):** full masking capability (T1–T9, ≈ 910–1,050 — **exceeds 800**) +
  heuristic (T10–T15) + gates. The first PR blows the session budget, so this is only viable with explicit
  budget override.

Because the total change (~1,550–1,750 lines) exceeds the 800-line session budget, **apply will span more
than one review session** regardless of option; chained PRs make each slice independently reviewable. This is
the decision the user must confirm before apply starts.

## Chained PR Plan

Feature-branch chain; PR #1 targets the feature/tracker branch, child PRs target the previous PR branch;
retarget/rebase until each child diff is clean. Red-first per capability (strict TDD, `config.yaml`
`rules.tasks`): the first task of every capability writes the test in red (compilation failure of the
not-yet-existing type counts as red) before any production code.

- **Slice A — Masking VOs: `MaskedText` + `UnmaskResult` (T1–T4, PR #1)**
  - Start: T1 (`MaskedTextTest` RED). Finish: T4 green + `clean verify`.
  - Scope: the two standalone records with compact-constructor validation, defensive `List.copyOf`, token-set
    invariant scan (`boolean[] seen`), Javadoc, red-first tests.
  - Verification: `mvnw.cmd clean verify` passes. Rollback: delete `MaskedText.java`, `UnmaskResult.java` and
    their 2 test classes (nothing else exists yet).
- **Slice B — Masker: `VariableMasker` (T5–T6, PR #2)**
  - Start: T5 (`VariableMaskerTest` RED). Finish: T6 green + `clean verify`.
  - Scope: stateless masker, pinned 3-branch alternation `__VAR_(\d+)__|%(?:\d+\$)?[sdf]|\{(\d+)(?:,[^}]*)?\}`,
    first-occurrence numbering, no-leakage literal protection, content-based branch detection (D1).
  - Verification: `mvnw.cmd clean verify` passes. Rollback: delete `VariableMasker.java` + test
    (Slice-A types have no dependency on it — clean).
- **Slice C — Unmasker + round trip (T7–T9, PR #3)**
  - Start: T7 (`VariableUnmaskerTest` RED). Finish: T9 green + `clean verify`.
  - Scope: best-effort unmasker (restore by index, `quoteReplacement` — D8; unmatched left verbatim;
    missing/unmatched/reordered discrepancy computation) + the composition-level round-trip pin (R7).
  - Verification: `mvnw.cmd clean verify` passes. Rollback: delete `VariableUnmasker.java`,
    `VariableUnmaskerTest.java`, `VariableRoundTripTest.java` (Slice-A/B types unaffected).
- **Slice D — Heuristic VOs: `GlossaryTermMatch` + `ScalingDecision` (T10–T13, PR #4)**
  - Start: T10 (`GlossaryTermMatchTest` RED). Finish: T13 green + `clean verify`.
  - Scope: the two heuristic output records with compact-constructor validation, Javadoc, red-first tests.
  - Verification: `mvnw.cmd clean verify` passes. Rollback: delete the 2 types + 2 test classes.
- **Slice E — Heuristic: `ScalingHeuristic` (T14–T15, PR #5)**
  - Start: T14 (`ScalingHeuristicTest` RED). Finish: T15 green + `clean verify`.
  - Scope: stateless 6-rule precedence evaluator (first match wins, lazy per-rule — D14), masked-text-only
    evaluation, whole-word case-sensitive glossary matching (`Pattern.quote` — D9), package-private
    `countWords` (D4), compile-time thresholds 30/8.
  - Verification: `mvnw.cmd clean verify` passes. Rollback: delete `ScalingHeuristic.java` + test.
- **Slice F — Closing gates (T16–T18, PR #6)**
  - Start: T16 (`package-info.java`). Finish: T18 final `clean verify`.
  - Scope: `@NullMarked` package-info (Javadoc **before** the annotation), structural greps (R1/R2/R3/R14),
    final full build gate.
  - Verification: `mvnw.cmd clean verify` passes with exit code 0. Rollback: delete `package-info.java`;
    chain revert = full deletion of `domain/service` (zero side effects, per proposal rollback plan).

## Task Breakdown

### Slice A — Masking VOs: `MaskedText` + `UnmaskResult` (PR #1)

- [x] T1: Write `MaskedTextTest` (RED)
  - **Description**: Create
    `src/test/java/com/lucalzt/mctranslator/domain/service/MaskedTextTest.java` (JUnit 6 + AssertJ,
    `@Test` + `@DisplayName`, tab indentation, no Spring context — `JsonPathTest` style) covering R5 (all 4
    scenarios) + value equality: `null` `maskedText` → `NullPointerException`; `""`/`"   "` → IAE;
    `null` `variables` list / `null` element → NPE; blank element → IAE; **token-set invariant**
    (`("foo __VAR_2__ bar", List.of("a"))` → IAE out-of-range; `("foo __VAR_0__ __VAR_0__", List.of("a"))` →
    IAE duplicate; missing index `("__VAR_1__", List.of("a","b"))` → IAE); valid
    `("Use __VAR_0__ and __VAR_1__", List.of("%s","{0,number}"))` accepted + accessors + the
    `__VAR_N__ ↔ variables.get(N)` correspondence; `("hello", List.of())` accepted (empty token set);
    **defensive copy** (caller mutates its list after construction → instance unaffected; accessor-returned
    list is immutable — `assertThatThrownBy(() -> accessorList.add(...))`); equality/hashCode.
  - **Criteria**: Test class exists referencing the missing `MaskedText` type only; targeted run **FAILS**
    (red state — compilation failure of the not-yet-existing type counts as red per strict TDD).
  - **Requirements**: R5, R13
  - **Dependencies**: None
  - **Verification**: `mvnw.cmd -Dtest=MaskedTextTest test` → expected FAILURE (red)

- [x] T2: Implement `MaskedText` (GREEN)
  - **Description**: Create
    `src/main/java/com/lucalzt/mctranslator/domain/service/MaskedText.java` as
    `public record MaskedText(String maskedText, List<String> variables)` (design §3.1). Compact constructor
    in order: `maskedText` null → NPE, blank → IAE; `variables` null or any element null → NPE, any element
    blank → IAE; **token-set invariant** with `private static final Pattern TOKEN_PATTERN =
    Pattern.compile("__VAR_(\\d+)__")` and a `boolean[] seen` + match counter (D10): the indices occurring in
    `maskedText` must be exactly `{0, 1, …, n-1}` (n = `variables.size()`), each exactly once, else IAE
    (textual token order NOT validated); reassign `variables = List.copyOf(variables)` (defensive copy).
    Javadoc on the record (token correspondence `__VAR_N__ ↔ variables.get(N)`), the compact constructor
    (`@throws` NPE/IAE), and both accessors (R4, `java-docs` skill). Imports: JDK + jspecify only (R2); no
    custom exceptions (D11); canonical constructors only (D12).
  - **Criteria**: `mvnw.cmd -Dtest=MaskedTextTest test` → all green; Javadoc present on the record and all
    public members; exactly 2 components.
  - **Requirements**: R2, R4, R5, D5, D10, D11, D12
  - **Dependencies**: T1
  - **Verification**: `mvnw.cmd -Dtest=MaskedTextTest test` (green)

- [x] T3: Write `UnmaskResultTest` (RED)
  - **Description**: Create
    `src/test/java/com/lucalzt/mctranslator/domain/service/UnmaskResultTest.java` (JUnit 6 + AssertJ, no
    Spring context) covering the R8 carrier record + D2: `null` `restoredText` → NPE; `null`
    `missingTokenIndices` list → NPE; `null` `unmatchedTokenIndices` list → NPE; **defensive copy** of both
    lists (caller mutation → instance unaffected; returned lists immutable); valid construction preserves
    components; equality/hashCode across the 4 components.
  - **Criteria**: Test class exists referencing the missing `UnmaskResult` type; targeted run **FAILS** (red).
  - **Requirements**: R8, R13
  - **Dependencies**: None
  - **Verification**: `mvnw.cmd -Dtest=UnmaskResultTest test` → expected FAILURE (red)

- [x] T4: Implement `UnmaskResult` (GREEN)
  - **Description**: Create
    `src/main/java/com/lucalzt/mctranslator/domain/service/UnmaskResult.java` as
    `public record UnmaskResult(String restoredText, List<Integer> missingTokenIndices, List<Integer> unmatchedTokenIndices, boolean reordered)`
    (design §3.2). Compact constructor: `restoredText` null → NPE; each list null → NPE; both lists
    defensively copied with `List.copyOf`. **No validation of sortedness/dedup/consistency** — those are the
    unmasker's producer contract, documented in Javadoc and pinned by tests (D2). Javadoc on the record
    documenting the discrepancy semantics: `missingTokenIndices` = sorted ascending indices `N` (`0 ≤ N < n`)
    present in the masked token set but absent from the translated text; `unmatchedTokenIndices` = sorted
    ascending, deduplicated indices `N ≥ n` occurring in the translated text; `reordered` = `true` iff the
    appearance-order sequence of token indices (ALL tokens) is not monotonically non-decreasing (duplicates
    alone never imply reordering); plus `@throws` on the constructor and Javadoc on all 4 accessors (R4).
    **Slice A gate**: `mvnw.cmd clean verify` must pass with all existing + new tests green.
  - **Criteria**: `mvnw.cmd -Dtest=UnmaskResultTest test` → green; Javadoc present on record + all members;
    exactly 4 components; `mvnw.cmd clean verify` → `BUILD SUCCESS`.
  - **Requirements**: R2, R4, R8, D2, D11, D12
  - **Dependencies**: T3
  - **Verification**: `mvnw.cmd -Dtest=UnmaskResultTest test` (green); then `mvnw.cmd clean verify`

### Slice B — Masker: `VariableMasker` (PR #2)

- [x] T5: Write `VariableMaskerTest` (RED)
  - **Description**: Create
    `src/test/java/com/lucalzt/mctranslator/domain/service/VariableMaskerTest.java` (JUnit 6 + AssertJ, no
    Spring context) covering R6 (all 8 scenarios): each printf family `"HP: %s"`, `"Damage: %d"`,
    `"Ratio: %f"`, `"Hello %1$s"`, `"Cost %2$d"`, `"Slot %10$s"` → positional tokens + exact `variables`
    entries; each MessageFormat family `"Value {0}"`, `"Amount {1,number}"`, `"Day {0,date,full}"`,
    `"Pick {0,choice,0#zero|1#one}"`, `"N {12,number,integer}"`; **first-occurrence order + no dedup**
    (`"A %s and %s and {0}"` → `"A __VAR_0__ and __VAR_1__ and __VAR_2__"`, variables
    `List.of("%s","%s","{0}")`); **literal token protected, no leakage** (`"Use __VAR_3__ now"` →
    `"Use __VAR_0__ now"`, variables `List.of("__VAR_3__")`); **non-variables left literal**
    (`"100%% complete, %S var, %x hex, %10.2f wide, §a colored"` → identical output, empty variables);
    **JSON braces not masked** (`"JSON { \"key\": \"value\" } and text {0}"` → only `{0}` masked);
    combined mixed scan (`"A %s {0} %1$s {1,number}"`); `null` → NPE; `""`/`"   "` → IAE.
  - **Criteria**: Test class exists referencing the missing `VariableMasker` type (and the existing
    `MaskedText`); targeted run **FAILS** (red).
  - **Requirements**: R6, R13
  - **Dependencies**: T2
  - **Verification**: `mvnw.cmd -Dtest=VariableMaskerTest test` → expected FAILURE (red)

- [x] T6: Implement `VariableMasker` (GREEN)
  - **Description**: Create
    `src/main/java/com/lucalzt/mctranslator/domain/service/VariableMasker.java` as a stateless `final class`
    with a default public constructor (D13) exposing `MaskedText mask(String text)` (design §5.1).
    `private static final Pattern VARIABLE_PATTERN = Pattern.compile("__VAR_(\\d+)__|%(?:\\d+\\$)?[sdf]|\\{(\\d+)(?:,[^}]*)?\\}")`
    (pinned alternation order: literal | printf | MessageFormat — D5). `mask`: `Objects.requireNonNull(text)`
    → NPE; `text.isBlank()` → IAE; scan with `find()` + `appendReplacement`/`appendTail`; per match assign
    the next token index k in first-occurrence order, replace with the literal `"__VAR_" + k + "__"` (no
    `$`/`\` in the replacement — no quoting needed, still documented), append `matcher.group()` verbatim to
    `variables` (no dedup); **branch detection by prefix** (D1): `__VAR_` → literal branch, `%` → printf,
    else → MessageFormat. Non-masked set left literal by construction (no pattern branch matches `%%`, `%S`,
    `%x`, `%10.2f`, `§a`, JSON braces). Return `new MaskedText(masked, variables)` (VO validates the
    invariant — trivially satisfied). Javadoc on the class and `mask` (R4): first-occurrence numbering, the
    pinned pattern set, the no-leakage property, the documented `%%s` adjacency limitation, and the
    re-entrancy note (masking an already-masked string re-numbers literals — pinned, not a bug). **Slice B
    gate**: `mvnw.cmd clean verify` passes.
  - **Criteria**: `mvnw.cmd -Dtest=VariableMaskerTest test` → green; Javadoc present; no instance fields;
    only JDK + jspecify + `domain.model` imports (R2); `mvnw.cmd clean verify` → `BUILD SUCCESS`.
  - **Requirements**: R2, R4, R6, D1, D5, D11, D12, D13
  - **Dependencies**: T5
  - **Verification**: `mvnw.cmd -Dtest=VariableMaskerTest test` (green); then `mvnw.cmd clean verify`

### Slice C — Unmasker + round trip (PR #3)

- [x] T7: Write `VariableUnmaskerTest` (RED)
  - **Description**: Create
    `src/test/java/com/lucalzt/mctranslator/domain/service/VariableUnmaskerTest.java` (JUnit 6 + AssertJ, no
    Spring context) covering R8 (all 6 scenarios) + the D8 quoting pin: **missing token never throws**
    (`MaskedText` with `List.of("%s","%d")`, translated `"Hello __VAR_0__"` → `"Hello %s"`, missing `[1]`,
    unmatched empty); **unmatched left verbatim + reported** (`List.of("%s")`, translated
    `"Hi __VAR_0__ and __VAR_9__ and __VAR_9__"` → `"Hi %s and __VAR_9__ and __VAR_9__"`, unmatched `[9]`
    deduplicated/sorted, missing empty); **reordered restored by index** (`List.of("%s","%d")`, translated
    `"Second __VAR_1__ first __VAR_0__"` → `"Second %d first %s"`, `reordered` true, both lists empty);
    **duplicate not reordered** (`List.of("{0}")`, translated `"A __VAR_0__ B __VAR_0__"` → `"A {0} B {0}"`,
    `reordered` false, both lists empty); **perfect translation** → no discrepancies; interleaved
    matched/unmatched (`[0,9,1]` order → `reordered` true, unmatched `[9]`); **`$`-variable restored exactly**
    (variable `"%1$s"` round-trips verbatim — pins D8 quoting); `null` masked → NPE; `null` translatedText →
    NPE.
  - **Criteria**: Test class exists referencing the missing `VariableUnmasker` type (and existing
    `MaskedText` / `UnmaskResult`); targeted run **FAILS** (red).
  - **Requirements**: R8, R13
  - **Dependencies**: T2, T4
  - **Verification**: `mvnw.cmd -Dtest=VariableUnmaskerTest test` → expected FAILURE (red)

- [x] T8: Implement `VariableUnmasker` (GREEN)
  - **Description**: Create
    `src/main/java/com/lucalzt/mctranslator/domain/service/VariableUnmasker.java` as a stateless `final
    class` with a default public constructor (D13) exposing
    `UnmaskResult unmask(MaskedText masked, String translatedText)` (design §5.2).
    `private static final Pattern TOKEN_PATTERN = Pattern.compile("__VAR_(\\d+)__")` (full-index, greedy
    `\d+`, mandatory trailing `__` — R7 partial matches structurally impossible; D5). `unmask`:
    `Objects.requireNonNull` on both args → NPE; scan `translatedText`; `n = masked.variables().size()`;
    track `seen` (indices `0..n-1` present), `unmatched` (`TreeSet<Integer>` of indices `N ≥ n`), `maxSeen`
    (running maximum → `reordered = true` as soon as a token index is strictly smaller — duplicates alone
    never trigger it); per token `__VAR_N__`: `N < n` → replace with `masked.variables().get(N)` wrapped in
    `Matcher.quoteReplacement(...)` (**mandatory**, D8 — variables contain `$`/`\` such as `%1$s`); `N ≥ n` →
    left verbatim (replacement = the quoted token itself, M6 resolution — never `""`, never throws);
    `missingTokenIndices` = ascending `0..n-1` not in `seen`; `unmatchedTokenIndices` = TreeSet contents;
    return `new UnmaskResult(...)`. Javadoc on the class and `unmask` (R4): best-effort contract (never
    throws for lacking/extra/reordered/duplicated tokens), restore-by-index semantics, discrepancy semantics.
  - **Criteria**: `mvnw.cmd -Dtest=VariableUnmaskerTest test` → green; Javadoc present; no instance fields;
    JDK + jspecify + `domain.model` imports only (R2).
  - **Requirements**: R2, R4, R7, R8, D5, D8, D11, D12, D13
  - **Dependencies**: T7
  - **Verification**: `mvnw.cmd -Dtest=VariableUnmaskerTest test` (green)

- [x] T9: Write `VariableRoundTripTest` (R7 composition pin — green gate)
  - **Description**: Create
    `src/test/java/com/lucalzt/mctranslator/domain/service/VariableRoundTripTest.java` (JUnit 6 + AssertJ,
    no Spring context) covering R7 (all 3 scenarios): **identity round trip** (`"HP: %s, Cost: %1$d, Value:
    {0,number}"` → `unmask(mask(original), masked)` → exact original, no discrepancies); **literal-token
    round trip has no leakage** (`"Use __VAR_3__"` → unmask → `"Use __VAR_3__"` exactly, not a masked
    token); **full-index tokens never partially match** (11-entry `MaskedText`, translated
    `"A __VAR_10__ B __VAR_1__"` → `variables.get(10)` and `variables.get(1)` restored — no prefix
    restoration of `__VAR_1` from `__VAR_10`). **Red-first note**: authored after both services exist (design
    A5 depends on A3+A4); the R7 behaviors were already pinned red-first by the component red phases
    (T1/T5/T7) — this class is the cross-service composition pin and its targeted run must be GREEN; any red
    here is a defect to fix via the TDD loop in the responsible service task (T6/T8) before proceeding.
    **Slice C gate**: `mvnw.cmd clean verify` passes.
  - **Criteria**: `mvnw.cmd -Dtest=VariableRoundTripTest test` → all green; `mvnw.cmd clean verify` →
    `BUILD SUCCESS` (masking capability complete).
  - **Requirements**: R7, R13
  - **Dependencies**: T6, T8
  - **Verification**: `mvnw.cmd -Dtest=VariableRoundTripTest test` (green); then `mvnw.cmd clean verify`

### Slice D — Heuristic VOs: `GlossaryTermMatch` + `ScalingDecision` (PR #4)

- [x] T10: Write `GlossaryTermMatchTest` (RED)
  - **Description**: Create
    `src/test/java/com/lucalzt/mctranslator/domain/service/GlossaryTermMatchTest.java` (JUnit 6 + AssertJ,
    no Spring context) covering R11 (both scenarios): `null` `term` → NPE; blank `term` → IAE; `null`
    `classification` → NPE; happy path preserves both components; **value equality** (equal term +
    classification → equal + equal hash codes; differing classification → not equal).
  - **Criteria**: Test class exists referencing the missing `GlossaryTermMatch` type (and the existing
    `GlossaryEntryClassification` enum from `domain.model`); targeted run **FAILS** (red).
  - **Requirements**: R11, R13
  - **Dependencies**: None
  - **Verification**: `mvnw.cmd -Dtest=GlossaryTermMatchTest test` → expected FAILURE (red)

- [x] T11: Implement `GlossaryTermMatch` (GREEN)
  - **Description**: Create
    `src/main/java/com/lucalzt/mctranslator/domain/service/GlossaryTermMatch.java` as
    `public record GlossaryTermMatch(String term, GlossaryEntryClassification classification)` (design §3.3).
    Compact constructor: `term` null → NPE; `term` blank → IAE; `classification` null → NPE. Equality by
    both components. Javadoc on the record (term = verbatim source-language term of the matched entry) and
    both accessors (R4). Imports: JDK + jspecify + `com.lucalzt.mctranslator.domain.model.GlossaryEntryClassification`
    (R2). No factories (D12).
  - **Criteria**: `mvnw.cmd -Dtest=GlossaryTermMatchTest test` → green; Javadoc present; exactly 2
    components.
  - **Requirements**: R2, R4, R11, D11, D12
  - **Dependencies**: T10
  - **Verification**: `mvnw.cmd -Dtest=GlossaryTermMatchTest test` (green)

- [x] T12: Write `ScalingDecisionTest` (RED)
  - **Description**: Create
    `src/test/java/com/lucalzt/mctranslator/domain/service/ScalingDecisionTest.java` (JUnit 6 + AssertJ, no
    Spring context) covering R10 (both scenarios) + defensive copy: `matchedRule` 0 and 7 → IAE; `null`
    `engine` → NPE; `null` `glossaryMatches` list → NPE; list containing a `null` element → NPE; defensive
    copy (caller mutation → instance unaffected; returned list immutable); valid construction with rule-2 and
    rule-5 shapes preserves components; **heuristic-produced consistency** (one decision per rule 1–6 from the
    `ScalingHeuristic` scenarios: `matchedRule` in 1..6, engine consistent with the rule mapping
    (1–3 → PRECISE, 4–6 → FAST), `glossaryMatches` non-empty only for rules 2/5, rule-2 has ≥ 1 `AMBIGUOUS`,
    rule-5 has no `LORE`).
  - **Criteria**: Test class exists referencing the missing `ScalingDecision` type (and existing
    `GlossaryTermMatch` / `TranslationEngineType`); targeted run **FAILS** (red).
  - **Requirements**: R10, R13
  - **Dependencies**: T11
  - **Verification**: `mvnw.cmd -Dtest=ScalingDecisionTest test` → expected FAILURE (red)

- [x] T13: Implement `ScalingDecision` (GREEN)
  - **Description**: Create
    `src/main/java/com/lucalzt/mctranslator/domain/service/ScalingDecision.java` as
    `public record ScalingDecision(TranslationEngineType engine, int matchedRule, List<GlossaryTermMatch> glossaryMatches)`
    (design §3.4). Compact constructor: `engine` null → NPE; `matchedRule` outside 1..6 → IAE;
    `glossaryMatches` null or any element null → NPE; `glossaryMatches = List.copyOf(glossaryMatches)`
    (defensive copy). **Value contract documented in Javadoc, NOT re-validated** (D7, mirrors `domain-models`
    D4): `glossaryMatches` non-empty exactly when `matchedRule` is 2 or 5; rule 2 ⇒ ≥ 1 `AMBIGUOUS` match;
    rule 5 ⇒ no `LORE` match; rules 1–3 ⇒ PRECISE, 4–6 ⇒ FAST. Javadoc on the record (rule-to-engine mapping,
    D3 note that rule-2 decisions may carry non-AMBIGUOUS matches — the full match list) and all 3 accessors
    (R4). **Slice D gate**: `mvnw.cmd clean verify` passes.
  - **Criteria**: `mvnw.cmd -Dtest=ScalingDecisionTest test` → green; Javadoc present; exactly 3 components;
    `mvnw.cmd clean verify` → `BUILD SUCCESS`.
  - **Requirements**: R2, R4, R10, D3, D7, D11, D12
  - **Dependencies**: T12
  - **Verification**: `mvnw.cmd -Dtest=ScalingDecisionTest test` (green); then `mvnw.cmd clean verify`

### Slice E — Heuristic: `ScalingHeuristic` (PR #5)

- [x] T14: Write `ScalingHeuristicTest` (RED)
  - **Description**: Create
    `src/test/java/com/lucalzt/mctranslator/domain/service/ScalingHeuristicTest.java` (JUnit 6 + AssertJ, no
    Spring context) covering R9 (all scenarios), the R10 contract and R12 (all 3 scenarios): **rule 1** —
    `quest.description.task1` short text → PRECISE/1 (wins over rule 5); `lore.story`,
    `advancement.husbandry.root` → PRECISE/1; **rule 2** — AMBIGUOUS `"ancient temple"` on `item.artifact` →
    PRECISE/2 with the match (wins over rule 4); whole-word case-sensitive (`"Iron is common; irony is not"`
    with AMBIGUOUS entries `iron` and `Iron` → PRECISE/2 with exactly the `Iron` match, `iron` inside `irony`
    NOT detected); **rule 3 strict** — exactly 30 words on `gui.menu` → FAST/4 (rule 3 does NOT match at 30);
    31 words on `item.sword` → PRECISE/3 (wins over rule 4); **rule 4** — `item.sword`, `block.stone`,
    `entity.zombie`, `gui.container` short → FAST/4; **rule 5** — `"Hello there"` (2 words) on `random.key`
    with only-PLAIN glossary → FAST/5; exactly 8 words → FAST/5 (inclusive); **LORE guard pinned** —
    `"The dark lord rises"` (≤ 8 words) with LORE `"dark lord"` → FAST/6 (outcome-equivalence pinned, MUST
    NOT become LORE→PRECISE); same text with PLAIN `"dark lord"` → FAST/5; **empty glossary** — `"Hi"` →
    FAST/5 (rule 2 vacuous false, rule 5 vacuously true); 40 words → PRECISE/3; **rule 6** —
    `"This sentence has exactly ten plain words written here today"` (10 words, > 8 and ≤ 30) on
    `some.other.key` empty glossary → FAST/6 (rule 5 blocked by word count, rule 3 not reached); **evaluation on masked text** — masked
    `"A __VAR_0__ with __VAR_1__ detail"` whose raw form has 40 words → counted on the MASKED text only
    (tokens = 2 words); **R12 direct**: `countWords("Hello, world!")` = 2, `countWords("...")` = 1,
    `countWords("A __VAR_0__ with __VAR_1__")` = 4, `countWords("   ")` = 0; **masked-token glossary
    safety** — a `VAR`/`0` letter term never matches inside `__VAR_0__`; `null` path / `null` maskedText /
    `null` glossary → NPE.
  - **Criteria**: Test class exists referencing the missing `ScalingHeuristic` type (and existing
    `ScalingDecision` / `GlossaryTermMatch` / `domain.model` types); targeted run **FAILS** (red).
  - **Requirements**: R9, R10, R12, R13
  - **Dependencies**: T11, T13
  - **Verification**: `mvnw.cmd -Dtest=ScalingHeuristicTest test` → expected FAILURE (red)

- [x] T15: Implement `ScalingHeuristic` (GREEN)
  - **Description**: Create
    `src/main/java/com/lucalzt/mctranslator/domain/service/ScalingHeuristic.java` as a stateless `final class`
    with a default public constructor (D13) exposing
    `ScalingDecision suggest(JsonPath path, String maskedText, List<GlossaryEntry> glossary)` (design §5.3,
    H1 pin — accepts a bare `String`, NOT `MaskedText`). Compile-time named constants
    `private static final int THRESHOLD_PRECISE_WORDS = 30` (rule 3: strictly greater) and
    `THRESHOLD_FAST_WORDS = 8` (rule 5: inclusive) — H8, no configuration (D5). `suggest`:
    `Objects.requireNonNull` on `path`, `maskedText`, `glossary` → NPE; **evaluation ALWAYS on
    `maskedText`** (R9); word count via package-private `static int countWords(String)` (D4): `strip()`, empty
    → 0, else `split("\\s+").length` (the `isEmpty()` guard is required — `"".split("\\s+")` returns `[""]`);
    glossary matching: per entry `Pattern.compile("\\b" + Pattern.quote(term) + "\\b")` and `find()` on
    `maskedText` (whole-word, case-sensitive, H7/D9; `Pattern.quote` mandatory — terms may contain regex
    metacharacters; one compile per entry per call — D6, stateless); **rule evaluation in strict order, first
    match wins, lazy per-rule** (D14): 1) `path.startsWith("quest","description") || startsWith("lore") ||
    startsWith("advancement")` → PRECISE/1; 2) any match classified `AMBIGUOUS` (computed lazily only if rule
    1 failed) → PRECISE/2 with the FULL match list (D3); 3) `countWords(maskedText) > 30` → PRECISE/3;
    4) `path.startsWith("item") || startsWith("block") || startsWith("entity") || startsWith("gui")` → FAST/4;
    5) `countWords(maskedText) ≤ 8` AND no match classified `LORE` → FAST/5 (empty glossary: vacuously true —
    pinned literal reading); 6) default → FAST/6. **Pinned behaviors** (R9/T9): the LORE guard falls to rule 6
    with the SAME engine outcome (FAST) — MUST NOT be "fixed" into LORE→PRECISE; empty-glossary short text
    yields `matchedRule` 5. Javadoc on the class (the 6-rule precedence table, first-match-wins, thresholds,
    masked-text-only evaluation) and on `suggest` / `countWords` (R4). **Slice E gate**: `mvnw.cmd clean
    verify` passes.
  - **Criteria**: `mvnw.cmd -Dtest=ScalingHeuristicTest test` → green; Javadoc present; no instance fields;
    JDK + jspecify + `domain.model` imports only (R2); `mvnw.cmd clean verify` → `BUILD SUCCESS`.
  - **Requirements**: R2, R4, R9, R12, D3, D4, D5, D6, D9, D13, D14
  - **Dependencies**: T14
  - **Verification**: `mvnw.cmd -Dtest=ScalingHeuristicTest test` (green); then `mvnw.cmd clean verify`

### Slice F — Closing gates (PR #6)

- [x] T16: Create `package-info.java` with `@NullMarked` + package Javadoc
  - **Description**: Create
    `src/main/java/com/lucalzt/mctranslator/domain/service/package-info.java` (design §3.5): **Javadoc block
    FIRST** (package role: pure domain services of the translation pipeline — variable masking/unmasking
    `VariableMasker` / `VariableUnmasker` and the scaling heuristic `ScalingHeuristic`; zero framework
    dependencies; every type and member is non-null by default — no `@Nullable` elements), **then**
    `@org.jspecify.annotations.NullMarked`, **then** `package com.lucalzt.mctranslator.domain.service;`
    (correct package-info form, per the `domain-models` apply-progress correction).
  - **Criteria**: File exists; `@NullMarked` present; Javadoc describes the package role; `mvnw.cmd -q
    compile` succeeds with the annotation resolving.
  - **Requirements**: R1, R3, R4
  - **Dependencies**: T15
  - **Verification**: `mvnw.cmd -q compile`; inspect the file for Javadoc-then-annotation order

- [x] T17: Run structural verification greps (R1 / R2 / R3 / R14)
  - **Description**: Run the verification-time checks (not JUnit): (a) **package placement (R1)** — the 8
    in-scope files (7 types + `package-info.java`) live under
    `src/main/java/com/lucalzt/mctranslator/domain/service/` and no type exists under any `com.mctranslator`
    package; public type inventory is exactly 7 (`MaskedText`, `VariableMasker`, `VariableUnmasker`,
    `UnmaskResult`, `GlossaryTermMatch`, `ScalingDecision`, `ScalingHeuristic`); (b) **zero framework imports
    (R2)** — no `import org.springframework`, `import jakarta`, or any import outside
    `java.*` / `org.jspecify.*` / `com.lucalzt.mctranslator.*`; (c) **no `@Nullable` (R3)** — zero matches in
    the package; (d) **deferred VOs absent from `domain.model` (R14)** — `MaskedText` / `GlossaryTermMatch` /
    `CacheKey` absent from `domain/model` and the model package still enumerates exactly its 9 pinned public
    types; (e) **ports untouched (R14)** — `git diff` shows zero changes under `domain/port/out`.
  - **Criteria**: All greps pass with zero offending matches; the public-type inventory is exact (7 in
    `domain/service`, 9 in `domain/model`); port sources unchanged.
  - **Requirements**: R1, R2, R3, R14
  - **Dependencies**: T2, T4, T6, T8, T11, T13, T15, T16
  - **Verification**:
    `rg "import (org\.springframework|jakarta)" src/main/java/com/lucalzt/mctranslator/domain/service` → no
    matches (plus the exhaustive check `rg --pcre2 "^import (?!java\.|org\.jspecify\.|com\.lucalzt\.mctranslator\.)" src/main/java/com/lucalzt/mctranslator/domain/service` → no matches);
    `rg "@Nullable" src/main/java/com/lucalzt/mctranslator/domain/service` → no matches;
    `rg "public (record|final class|class|interface|enum)" src/main/java/com/lucalzt/mctranslator/domain/service` → exactly 7;
    `rg "MaskedText|GlossaryTermMatch|CacheKey" src/main/java/com/lucalzt/mctranslator/domain/model` → no matches;
    enumerate public types under `domain/model` (exactly 9);
    `git diff -- src/main/java/com/lucalzt/mctranslator/domain/port/out` → empty

- [x] T18: Full build gate — `mvnw.cmd clean verify`
  - **Description**: Run the complete build on Windows (`mvnw.cmd clean verify`; `./mvnw clean verify` on
    Linux/macOS). All existing and new tests (JUnit 6 + AssertJ, no Spring context for the domain tests) must
    pass: zero test failures, zero compilation errors, exit code 0. **Final gate / Slice F gate.**
  - **Criteria**: `BUILD SUCCESS`; exit code 0; all 8 new test classes (`MaskedTextTest`, `UnmaskResultTest`,
    `VariableMaskerTest`, `VariableUnmaskerTest`, `VariableRoundTripTest`, `GlossaryTermMatchTest`,
    `ScalingDecisionTest`, `ScalingHeuristicTest`) + existing project tests green.
  - **Requirements**: R15
  - **Dependencies**: T17
  - **Verification**: `mvnw.cmd clean verify` → `BUILD SUCCESS` (exit 0)

---

## Traceability summary

| Capability | Tasks | Spec requirements |
|---|---|---|
| Masking (Slice A/B/C) | T1–T9 | R2, R4, R5, R6, R7, R8, R13 |
| Heuristic (Slice D/E) | T10–T15 | R2, R4, R9, R10, R11, R12, R13 |
| Gates (Slice F) | T16–T18 | R1, R2, R3, R4, R14, R15 |

Design decisions covered: D1 (T6), D2 (T4), D3 (T13/T15), D4 (T15), D5 (T2/T6/T8/T15), D6 (T15), D7 (T13),
D8 (T8), D9 (T15), D10 (T2), D11 (all GREEN tasks), D12 (all GREEN tasks), D13 (T6/T8/T15), D14 (T15).
Every spec requirement R1–R15 and every design decision D1–D14 is mapped to at least one task; there are no
orphan tasks.
