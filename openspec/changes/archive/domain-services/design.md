# Design: Domain Services (`domain-services`)

## Executive Summary

This design specifies the pure-Java domain services of the translation pipeline in
`com.lucalzt.mctranslator.domain.service` — `VariableMasker` / `VariableUnmasker` (protecting printf and
MessageFormat variables with positional `__VAR_N__` tokens and restoring them best-effort), `ScalingHeuristic`
(the exact 6-rule precedence table deciding FAST vs PRECISE per key), and the deferred value objects
`MaskedText`, `GlossaryTermMatch`, plus the decision and unmask-result records (`ScalingDecision`,
`UnmaskResult`). Exactly 8 public types + a `@NullMarked` `package-info.java`, 100% framework-free (JDK +
jspecify + `com.lucalzt.mctranslator.domain.model` imports only), Javadoc-covered, pinned by red-first
JUnit 6 + AssertJ unit tests with no Spring context. No `pom.xml` change is needed (jspecify 1.0.1 already
present from the archived `domain-models` change). All validation lives in record compact constructors using
only JDK exceptions (`NullPointerException` for nulls, `IllegalArgumentException` for invalid values), mirroring
the `domain-models` decisions D1–D9. Every design element traces to a spec requirement (R1–R15); anything not
pinned by the spec was resolved to the simplest option and flagged in §7.

---

## 1. Scope Anchoring

- **In scope**: the 8 public types of R1 (`MaskedText`, `VariableMasker`, `VariableUnmasker`, `UnmaskResult`,
  `GlossaryTermMatch`, `ScalingHeuristic`, `ScalingDecision`, `package-info.java`) under
  `com.lucalzt.mctranslator.domain.service`; Javadoc on all public API (R4); `@NullMarked` package with **no**
  `@Nullable` elements (R3); red-first unit tests (R13); `mvnw.cmd clean verify` gate (R15).
- **Explicitly out of scope** (Non-Goals): precise-engine flag-off behavior and `[WARN]` emission (application
  layer, decision 5); application-layer orchestration (`GlossaryAwareTranslator`); glossary loading via
  `GlossaryPort` (the heuristic receives `List<GlossaryEntry>` as a value — no port is consumed inside
  `domain.service`); cache/ports integration and the masked-text handoff to `TranslationEnginePort` (S2); the
  engines themselves; any change to the `domain.model` 9-type inventory (R14) or to any port contract (R14);
  infrastructure; extraction edge cases; non-masked printf/MessageFormat/Minecraft patterns (R6 non-goal list);
  glossary matching extensions (case-folding, occurrence reporting); threshold configuration; variable
  deduplication; unmasker failure modes (discrepancies, never exceptions).

---

## 2. Architecture & Component Design

### 2.1 Package layout

```
src/main/java/com/lucalzt/mctranslator/
└── domain/
    └── service/                                ← pure Java, zero framework (R2)
        ├── package-info.java                   ← @NullMarked + package role Javadoc (R3)
        ├── MaskedText.java                     ← record VO, round-trip state (R5)
        ├── VariableMasker.java                 ← stateless service, mask (R6)
        ├── VariableUnmasker.java               ← stateless service, unmask (R8)
        ├── UnmaskResult.java                   ← record, unmask output + discrepancy report (R8)
        ├── GlossaryTermMatch.java              ← record VO, rule 2/5 glossary output (R11)
        ├── ScalingDecision.java                ← record, heuristic decision output (R10)
        └── ScalingHeuristic.java               ← stateless service, suggest (R9, R12)
```

Test mirror (R13):

```
src/test/java/com/lucalzt/mctranslator/domain/service/
├── MaskedTextTest.java
├── VariableMaskerTest.java
├── VariableUnmaskerTest.java
├── VariableRoundTripTest.java
├── UnmaskResultTest.java
├── GlossaryTermMatchTest.java
├── ScalingDecisionTest.java
└── ScalingHeuristicTest.java
```

### 2.2 Class/record/enum inventory and public API signatures

| Type | Kind | Public API | Spec |
|---|---|---|---|
| `MaskedText` | record | `MaskedText(String maskedText, List<String> variables)`; accessors `maskedText()`, `variables()` | R5 |
| `VariableMasker` | final class (stateless) | `MaskedText mask(String text)` | R6 |
| `VariableUnmasker` | final class (stateless) | `UnmaskResult unmask(MaskedText masked, String translatedText)` | R8 |
| `UnmaskResult` | record | `UnmaskResult(String restoredText, List<Integer> missingTokenIndices, List<Integer> unmatchedTokenIndices, boolean reordered)` | R8 |
| `GlossaryTermMatch` | record | `GlossaryTermMatch(String term, GlossaryEntryClassification classification)` | R11 |
| `ScalingDecision` | record | `ScalingDecision(TranslationEngineType engine, int matchedRule, List<GlossaryTermMatch> glossaryMatches)` | R10 |
| `ScalingHeuristic` | final class (stateless) | `ScalingDecision suggest(JsonPath path, String maskedText, List<GlossaryEntry> glossary)` | R9, R12 |
| `package-info.java` | annotation + Javadoc | `@org.jspecify.annotations.NullMarked` | R3 |

No enum is created by this change (the heuristic consumes the existing `GlossaryEntryClassification` and
`TranslationEngineType` enums from `domain.model`). No custom exceptions (D11), no factories (D12), no new
public types beyond the R1 inventory.

### 2.3 Import policy (R2)

Allowed imports in `domain/service` sources: the JDK (`java.util.*`, `java.util.regex.*`, `java.lang.*`), the
sibling package `com.lucalzt.mctranslator.domain.model.*`, and `org.jspecify.annotations.*` (only the
`@NullMarked` annotation in `package-info.java`). **Zero** `org.springframework.*`, `jakarta.*`, or any other
library import. The services reference `domain.model` types (`TranslationEngineType`, `GlossaryEntry`,
`GlossaryEntryClassification`, `JsonPath`) — a `domain.*` sibling, allowed by R2.

### 2.4 Nullability policy (R3)

`package-info.java` carries `@NullMarked` and a Javadoc description of the package role (pure domain services
of the translation pipeline: masking, unmasking and scaling heuristic; zero framework dependencies). The
package has **no** `@Nullable` elements: every public API is non-null by default and returns non-null values
(`UnmaskResult` never carries a null `restoredText`; empty lists are returned, never null). Javadoc comes
**before** the annotation in `package-info.java` (correct form, per the `domain-models` apply-progress
correction).

### 2.5 Statelessness, thread-safety and concurrency

All three services are stateless final classes with no instance fields and no declared constructor (default
public constructor, so the future application layer can instantiate/inject them). `Pattern` constants are
`private static final`; `Pattern` is thread-safe for concurrent `find()`/`matcher()` use, and a fresh
`Matcher` is created per call — no shared mutable state. The services are therefore safe under virtual
threads without synchronization. No transactions, locking, or ordering guarantees beyond token scan order
(§5); the mask→heuristic→engine→unmask ordering is pipeline flow, owned by the application layer (S2).

---

## 3. Data Model

All records are immutable with value-based `equals`/`hashCode` derived from all components (record semantics).
Validation lives in compact constructors and runs at construction time (fail fast). The two lists in
`UnmaskResult`/`ScalingDecision`/`MaskedText` are defensively copied with `List.copyOf` so the VO is unaffected
by later caller mutation.

### 3.1 `MaskedText` — record VO (R5)

- **Declaration**: `public record MaskedText(String maskedText, List<String> variables)`.
- **Compact constructor** (order matters — null checks first, then blank, then the token-set invariant):
  - `maskedText` null → `NullPointerException`; blank (`isBlank()`) → `IllegalArgumentException`.
  - `variables` null or any element null → `NullPointerException`; any element blank → `IllegalArgumentException`.
  - **Token-set invariant** (single regex scan of `maskedText` with `__VAR_(\d+)__`): the set of token indices
    occurring in `maskedText` must equal exactly `{0, 1, …, n-1}` where n = `variables.size()`, each occurring
    exactly once; any violation → `IllegalArgumentException`. Mechanism (D10): a `boolean[] seen` of size n plus
    a match counter; each match parses its index and checks `0 ≤ i < n` and `!seen[i]`; afterwards the counter
    must equal n. Textual order of tokens is **not** validated (restoration is by index regardless — spec note).
  - Reassign `variables = List.copyOf(variables)` (defensive copy; `List.copyOf` also NPEs on null elements).
- **Accessors**: `maskedText()`, `variables()` (both Javadoc'd; `variables()` documents the immutable,
  defensive copy returned and the `__VAR_N__ ↔ variables.get(N)` correspondence — R4).
- **Edge cases accepted as valid**: `("hello", List.of())` (empty token set with n=0); `("A __VAR_0__", List.of("%s"))`.

### 3.2 `UnmaskResult` — record (R8)

- **Declaration**:
  `public record UnmaskResult(String restoredText, List<Integer> missingTokenIndices, List<Integer> unmatchedTokenIndices, boolean reordered)`.
- **Compact constructor** (D2): `restoredText` non-null → `NullPointerException`; each list non-null →
  `NullPointerException`; both lists defensively copied (`List.copyOf`). **No validation of sortedness,
  dedup or consistency** — those are the unmasker's producer contract, documented in Javadoc (R4) and pinned
  by tests, NOT re-validated here (mirrors `domain-models` D4 "document + test" and the `ScalingDecision`
  NOT-re-validated note in R10).
- **Contract semantics** (Javadoc, R4): `missingTokenIndices` = sorted ascending indices `N` (`0 ≤ N < n`)
  present in the masked text's token set but absent from the translated text; `unmatchedTokenIndices` = sorted
  ascending, deduplicated indices `N ≥ n` occurring in the translated text; `reordered` = `true` iff the
  sequence of token indices in the translated text (in order of appearance, ALL tokens — matched and unmatched)
  is not monotonically non-decreasing (contains an inversion); duplicates do not by themselves imply reordering.

### 3.3 `GlossaryTermMatch` — record VO (R11)

- **Declaration**: `public record GlossaryTermMatch(String term, GlossaryEntryClassification classification)`.
- **Compact constructor**: `term` null → `NullPointerException`; blank → `IllegalArgumentException`;
  `classification` null → `NullPointerException`. Equality by both components.

### 3.4 `ScalingDecision` — record (R10)

- **Declaration**:
  `public record ScalingDecision(TranslationEngineType engine, int matchedRule, List<GlossaryTermMatch> glossaryMatches)`.
- **Compact constructor**: `engine` null → `NullPointerException`; `matchedRule` outside 1..6 →
  `IllegalArgumentException`; `glossaryMatches` null or any element null → `NullPointerException`;
  `glossaryMatches = List.copyOf(glossaryMatches)` (defensive copy).
- **Value contract** (documented in Javadoc — R4 — guaranteed by the heuristic, NOT re-validated here):
  `glossaryMatches` non-empty exactly when `matchedRule` is 2 or 5; rule 2 ⇒ at least one `AMBIGUOUS` match;
  rule 5 ⇒ no `LORE` match; rules 1–3 ⇒ `PRECISE`, rules 4–6 ⇒ `FAST` (R10).

### 3.5 `package-info.java` (R3)

```java
/**
 * Pure domain services of the translation pipeline: variable masking/unmasking
 * (VariableMasker / VariableUnmasker) and the scaling heuristic (ScalingHeuristic).
 * ...
 */
@org.jspecify.annotations.NullMarked
package com.lucalzt.mctranslator.domain.service;
```

Javadoc block first, then the annotation (correct package-info form, per the `domain-models` apply-progress
correction), then the package declaration.

---

## 4. Internal Helpers (compiled `Pattern` constants, all `private static final`)

Per-class private constants (D5) — no shared helper class, because R1 pins the public type inventory and a
shared package-private holder adds a file for a pinned literal:

| Class | Constant | Java source |
|---|---|---|
| `MaskedText` | `TOKEN_PATTERN` (token-set invariant scan) | `"__VAR_(\\d+)__"` |
| `VariableMasker` | `VARIABLE_PATTERN` (3-branch alternation, pinned order: literal \| printf \| MessageFormat) | `"__VAR_(\\d+)__|%(?:\\d+\\$)?[sdf]|\\{(\\d+)(?:,[^}]*)?\\}"` |
| `VariableUnmasker` | `TOKEN_PATTERN` | `"__VAR_(\\d+)__"` |
| `ScalingHeuristic` | `THRESHOLD_PRECISE_WORDS = 30` (rule 3: strictly greater), `THRESHOLD_FAST_WORDS = 8` (rule 5: ≤), `WORD_BOUNDARY` glue | constants per H8 (compile-time named constants, no config) |

**Group numbering in the masker alternation**: group 1 = `(\d+)` inside the literal-token branch; group 2 =
`(\d+)` inside the MessageFormat branch; the printf branch has no capturing groups. The design does **not**
rely on group numbers for branch detection (D1): it inspects the whole match (`Matcher.group()`), whose
prefixes are disjoint (`_` / `%` / `{`), making content-based branch detection unambiguous and robust to
future group renumbering.

---

## 5. Behavioral Design

### 5.1 Masking algorithm (`VariableMasker.mask`)

1. **Validation**: `Objects.requireNonNull(text)` → NPE; `text.isBlank()` → IAE (R6).
2. **Scan**: `VARIABLE_PATTERN.matcher(text)`, loop `find()` with `appendReplacement`/`appendTail`.
3. **Per match**, in first-occurrence order (single left-to-right scan, so numbering is positional):
   - Assign the next token index `k` (0-based counter).
   - Replace the match with the literal token `"__VAR_" + k + "__"` (no `$`/`\` in the replacement, so
     `appendReplacement` needs no quoting; still documented).
   - Append `matcher.group()` (the exact matched substring, verbatim) to the `variables` list — **no dedup**:
     two occurrences of `%s` produce two entries (R6).
   - Branch detection by prefix (D1): starts with `__VAR_` → literal-token branch (the original literal, e.g.
     `__VAR_3__`, is protected as a regular variable — no token leakage, R6); starts with `%` → printf branch;
     else → MessageFormat branch.
4. **Return** `new MaskedText(maskedString, variables)` — the VO's compact constructor validates the invariant
   (mask() trivially satisfies it; see §3.1).
5. **Pinned non-masked set** (left literal, round-trip identity — R6): `%%`, `%S`/`%D`/`%F`, `%x`/`%o`/`%e`/
   `%g`/`%b`/`%c`/`%h`/`%n`, width/precision forms (`%10.2f`, `%-10s`, `%2$10d`), Minecraft `\u00a7` section
   signs (`§a`). JSON braces and unbalanced braces produce no match (`{` must be followed by a digit, and the
   closing `}` must be present). The `%%s` adjacency edge (`100%%s` masks the trailing `%s`) is an accepted
   v1 limitation (spec "Resolved in Spec"); the alternation has no escape-awareness by design.
6. **Re-entrancy / idempotency note**: masking an already-masked string treats `__VAR_N__` as a literal
   (branch 1) and re-numbers it — pinned behavior, not a bug; the pipeline masks the original text exactly
   once (S2).

### 5.2 Unmasking algorithm (`VariableUnmasker.unmask`)

1. **Validation**: `Objects.requireNonNull(masked)` / `requireNonNull(translatedText)` → NPE (R8). Best-effort:
   never throws because the translation lacks, adds, reorders or duplicates tokens.
2. **Scan** `translatedText` with `TOKEN_PATTERN`; `n = masked.variables().size()`. Track:
   - `seen` (set of indices `N` in `0..n-1` appearing in the translated text);
   - `unmatched` (`TreeSet<Integer>` of indices `N ≥ n`);
   - `maxSeen` (running maximum of token indices in appearance order) → `reordered = true` as soon as a token
     index is strictly smaller than `maxSeen` (R8: sequence not monotonically non-decreasing; duplicates
     alone never trigger it — `[0,0]` is non-decreasing).
3. **Per token** `__VAR_N__` (full-index match, greedy `\d+` — `__VAR_10__` never partial-matches `__VAR_1__`,
   R7):
   - `N < n` → replace with `masked.variables().get(N)` wrapped in `Matcher.quoteReplacement(...)` —
     **mandatory** (D8): variables contain `$` and `\` (e.g. `%1$s`), which `appendReplacement` interprets as
     group references/escapes; without quoting, valid round trips would corrupt or throw.
   - `N ≥ n` → leave **verbatim**: replacement = the matched token itself (quoted); record in `unmatched`
     (M6 resolution).
4. **Discrepancy computation**: `missingTokenIndices` = ascending `0..n-1` not in `seen` (natural order);
   `unmatchedTokenIndices` = `TreeSet` contents (ascending, deduplicated).
5. **Return** `new UnmaskResult(restoredText, missing, unmatched, reordered)`.

### 5.3 Heuristic evaluation (`ScalingHeuristic.suggest`) — R9/R12

1. **Validation**: `Objects.requireNonNull` on `path`, `maskedText`, `glossary` → NPE (R9).
2. **Evaluation is on the MASKED text always** — word counting and glossary matching never touch raw text (R9).
3. **Word count** (R12, package-private static `countWords` for direct tests — D4): `stripped = text.strip()`;
   if `stripped.isEmpty()` → `0`; else `stripped.split("\\s+").length`. Every whitespace-separated token counts
   as one word, including `__VAR_N__` tokens and punctuation-only tokens (e.g. `...` → 1). The `isEmpty()` guard
   is required: `"".split("\\s+")` in Java returns `[""]` (length 1) — the spec's "0 when blank after
   stripping" mandates the special case.
4. **Glossary matching** (R9/H7 — whole-word, case-sensitive): for each glossary entry, compile
   `Pattern.compile("\\b" + Pattern.quote(term) + "\\b")` and `find()` on `maskedText`; a match produces one
   `GlossaryTermMatch(term, classification)` per **entry** (no dedup by term — two entries with the same term
   yield two matches). `Pattern.quote(term)` is mandatory (D9): glossary terms may contain regex
   metacharacters. Java `\b` word boundaries make masked tokens safe: `_` is a word character, so letter terms
   cannot match inside `__VAR_0__`. One compiled pattern per entry per `suggest` call (D6) — stateless (H8),
   no caching; acceptable for a once-per-key heuristic.
5. **Rule evaluation — strict order, first match wins** (lazy computation to avoid wasted work on the
   highest-frequency rules):

   | Order | Predicate | Engine / `matchedRule` | `glossaryMatches` |
   |---|---|---|---|
   | 1 | `path.startsWith("quest","description") \|\| path.startsWith("lore") \|\| path.startsWith("advancement")` | PRECISE / 1 | empty (no glossary work) |
   | 2 | any match classified `AMBIGUOUS` (matches computed lazily only if rule 1 failed) | PRECISE / 2 | the **full** match list (D3 — see §7) |
   | 3 | `countWords(maskedText) > 30` (strict — exactly 30 does NOT match) | PRECISE / 3 | empty |
   | 4 | `path.startsWith("item") \|\| startsWith("block") \|\| startsWith("entity") \|\| startsWith("gui")` | FAST / 4 | empty |
   | 5 | `countWords(maskedText) ≤ 8` (inclusive — exactly 8 qualifies) AND no match classified `LORE` | FAST / 5 | the full match list (contains no `LORE` by construction) |
   | 6 | default — any other case | FAST / 6 | empty |

   - **Empty glossary** (pinned literal reading): rule 2 never fires (no `AMBIGUOUS` match possible — vacuous
     false); rule 5 DOES fire on ≤ 8 words (the "no LORE detected" clause is vacuously true) → `matchedRule` 5.
   - **Rule-5 LORE guard** (pinned equivalence): with a LORE term detected and ≤ 8 words, rule 5 does not
     match and control falls to rule 6 → FAST — the same engine outcome rule 5 would have produced. The guard
     is a testable, documented clause; it MUST NOT be "fixed" into a LORE → PRECISE rule (R9 scenario).
   - `matchedRule` + `engine` are always consistent (R10): rules 1–3 → PRECISE, 4–6 → FAST.

---

## 6. Integration Points

- **No port dependency**: `ScalingHeuristic` receives `List<GlossaryEntry>` as a value; `GlossaryPort` is
  never consumed inside `domain.service` (H2 resolution; `hexagonal-architecture` — domain knows only its
  inputs). No port interface and no port method changes (R14).
- **Contract decoupling between services**: `suggest` accepts a bare `String maskedText`, NOT a `MaskedText`
  (H1 pin) — the heuristic's contract does not tie to the masking VO. The application layer will pass
  `masked.maskedText()`.
- **What the future application layer (`GlossaryAwareTranslator`) consumes** (later change, non-goal here):
  1. `MaskedText mt = new VariableMasker().mask(key.originalText());` — before the engine call (flow step 2);
  2. `ScalingDecision d = new ScalingHeuristic().suggest(key.path(), mt.maskedText(), glossaryTerms);` — flow
     step 3, on the masked text; it applies the precise-flag override and `[WARN]` from `d` (decision 5/H4);
  3. `UnmaskResult r = new VariableUnmasker().unmask(mt, engineOutput);` — flow step 5; it emits `[WARN]`
     diagnostics from `r.missingTokenIndices()` / `r.unmatchedTokenIndices()` / `r.reordered()` (decision 3);
  4. `d.engine()` is the trivial projection of `ScalingDecision` (R10).
- **Engine masked-text handoff (S2, constraint for the translator change)**: `TranslationEnginePort.translate`
  still receives a `TranslationKey` with the unmasked `originalText`. How the pipeline feeds masked text to the
  engine (key copy with masked text vs a future port change) is application-layer work, recorded here so the
  translator change picks it up.
- **Observability/resilience channel**: domain services emit no logs (zero-framework purity, R2). Diagnostics
  travel as return values — `UnmaskResult` discrepancies and `ScalingDecision.matchedRule`/`glossaryMatches` —
  and the application layer turns them into `[WARN]` (decision 3/5). The unmasker never throws for translation
  anomalies (R8); the heuristic has no flag and always evaluates (decision 5).

---

## 7. Design Decisions & Alternatives

| ID | Decision point | Alternatives considered | Chosen | Rationale / trace |
|---|---|---|---|---|
| D1 | Masker branch detection | (a) group-number detection (`group(1)` literal / `group(2)` MessageFormat); (b) content-based on the whole match | **(b) content-based** | The pinned alternation's matched prefixes are disjoint (`_` / `%` / `{`), making prefix inspection unambiguous and robust to future group renumbering; the single precompiled `Pattern` requirement (R6) is preserved. |
| D2 | `UnmaskResult` constructor validation | (a) full invariant validation (sortedness, dedup, index ranges); (b) null + defensive copy only; (c) no validation | **(b) null + defensive copy** | R8 pins the record shape but no validation list; sortedness/dedup are the unmasker's producer contract, documented (R4) and tested — same "document + test" pattern as `domain-models` D4. Prevents null-list constructions (runtime null-safety, threat T7-style). |
| D3 | `glossaryMatches` content for `matchedRule` 2 | (a) only the `AMBIGUOUS` matches; (b) the full list of all matched entries | **(b) full list** | One matching pass reused by rules 2 and 5 (simplest); R10's "at least one AMBIGUOUS" is a minimum the full list satisfies; more diagnostic value for the future `[WARN]` layer. Flagged: rule-2 decisions may carry non-AMBIGUOUS matches too. |
| D4 | Word-count helper visibility | (a) `private static` + behavior-only tests; (b) package-private `static` + direct tests | **(b) package-private `static countWords(String)`** | R12's three scenarios ("...", masked-token, blank) are pinned precisely and directly; a package-private member is invisible outside the package (R1 pins *public* inventory only) and keeps "cero ambigüedad". Javadoc'd per the `java-docs` skill. |
| D5 | `Pattern` constant placement | (a) per-class `private static final`; (b) shared package-private holder class | **(a) per-class constants** | R1 pins the public type inventory; a holder class adds a file for a spec-pinned literal. The token regex is duplicated between `MaskedText` and `VariableUnmasker` — accepted; the literal is pinned by R5/R7. |
| D6 | Glossary pattern compilation strategy | (a) compile per entry per call; (b) static pattern cache (instance state) | **(a) compile per call** | H8 pins a stateless service; a cache is instance state and premature for a once-per-key heuristic. Linear cost in glossary size, acceptable. |
| D7 | `ScalingDecision` value consistency | (a) re-validate engine↔rule↔matches in the constructor; (b) document + test only | **(b) document + test** | R10 explicitly says the contract is "documented in Javadoc, guaranteed by Requirement 9, NOT re-validated in the constructor"; the constructor validates exactly the R10 list (nulls, rule range, copy). |
| D8 | Unmasker replacement quoting | (a) raw `appendReplacement`; (b) `Matcher.quoteReplacement` on every replacement | **(b) quoteReplacement always** | Variables contain `$`/`\` (`%1$s`, `{0,number}`); unquoted, `appendReplacement` interprets `$s` as group syntax → corrupted restorations or `IllegalArgumentException` on valid round trips, violating R7/R8. Correctness-critical (threat T3). |
| D9 | Glossary term regex safety | (a) interpolate term raw; (b) `Pattern.quote(term)` inside `\b...\b` | **(b) `Pattern.quote`** | Glossary terms may contain regex metacharacters (`a+b`, `(x)`); quoting keeps whole-word matching literal (R9/H7). |
| D10 | Token-set invariant mechanism | (a) `Set<Integer>` + size compare; (b) `boolean[] seen` + counter | **(b) `boolean[] seen` + counter** | n is small (masked variables per leaf); O(1) membership, deterministic rejection of duplicates and out-of-range indices in one scan (R5). |
| D11 | Exception types | JDK `NullPointerException` / `IllegalArgumentException` vs custom domain exceptions | **JDK exceptions** | R5/R6/R8/R9/R10/R11 scenarios pin NPE/IAE; custom exceptions would be new *public types* → violates R1's exact 8-type inventory (mirrors `domain-models` D7). |
| D12 | Factories | canonical constructors only vs semantic factories | **canonical constructors only** | Spec scenarios construct via canonical constructors; `mask()`/`suggest()`/`unmask()` are the only producers and trivially satisfy the invariants. Simplest option (mirrors `domain-models` D2). |
| D13 | Service shape | (a) stateless classes with instance methods; (b) static-only utility classes | **(a) instance methods, default constructor** | The future application layer injects these services; static methods are awkward for DI. Statelessness (H8) still holds — no fields. |
| D14 | Rule evaluation order of work | (a) compute everything up front; (b) lazy per-rule | **(b) lazy per-rule** | Rule 1 (path-only) needs no glossary scan and no word count; rule 3's count is computed only if rules 1–2 failed; matches are computed once and reused by rules 2 and 5. Minimal wasted work on the highest-frequency path. |

---

## 8. Sequencing (strict TDD — informs `tasks`)

Per `openspec/config.yaml` (`rules.tasks`) and R13, capabilities are ordered by dependency; each capability's
test class is written **red-first** (compilation failure of the not-yet-existing type counts as red), then the
production code is implemented to green. No `pom.xml` work is needed (jspecify 1.0.1 already present, verified
in `pom.xml` lines 25–26).

1. **Cap A — Masking capability** (R5, R6, R7, R8):
   - A1 `MaskedText` (red: `MaskedTextTest`) — standalone VO.
   - A2 `UnmaskResult` (red: `UnmaskResultTest`) — standalone record.
   - A3 `VariableMasker` (red: `VariableMaskerTest`) — depends on A1 (returns `MaskedText`).
   - A4 `VariableUnmasker` (red: `VariableUnmaskerTest`) — depends on A1, A2.
   - A5 Round-trip (red: `VariableRoundTripTest`) — depends on A3, A4 (R7).
2. **Cap B — Heuristic capability** (R9, R10, R11, R12):
   - B1 `GlossaryTermMatch` (red: `GlossaryTermMatchTest`) — standalone VO.
   - B2 `ScalingDecision` (red: `ScalingDecisionTest`) — depends on B1.
   - B3 `ScalingHeuristic` (red: `ScalingHeuristicTest`) — depends on B1, B2 + `domain.model` types (already
     built and green).
3. **Cap C — Closing gates** (R1, R2, R3, R14, R15):
   - C1 `package-info.java` (`@NullMarked` + role Javadoc).
   - C2 Structural greps (R1/R2/R14: package placement, zero framework imports, `domain.model` untouched, no
     `@Nullable`).
   - C3 `mvnw.cmd clean verify` (R15).

---

## 9. Testing Strategy (JUnit 6 + AssertJ, red-first, no Spring context)

Pure unit tests per the `spring-boot-testing` skill ("business logic in service? → plain JUnit, no Spring
context"). One test class per type; `@Test` + `@DisplayName` + AssertJ (`assertThat`,
`assertThatThrownBy`), matching repo conventions (`JsonPathTest` style: tab indentation, `@DisplayName`
descriptions, happy path → edge cases → validation rejection).

| Test class | Spec coverage | Cases |
|---|---|---|
| `MaskedTextTest` | R5 (all 4 scenarios), R16-style equality | `null` maskedText → NPE; `""`/`"   "` → IAE; `null` variables / null element → NPE; blank element → IAE; **invariant**: `("foo __VAR_2__ bar", List.of("a"))` → IAE (index out of range), `("foo __VAR_0__ __VAR_0__", List.of("a"))` → IAE (duplicate), missing index `("__VAR_1__", List.of("a","b"))` → IAE; valid `("Use __VAR_0__ and __VAR_1__", List.of("%s","{0,number}"))` accepted + accessors + `__VAR_N__ ↔ variables.get(N)`; `("hello", List.of())` accepted; **defensive copy** (caller mutates list after construction → instance unaffected; accessor list immutable — `assertThatThrownBy(() -> accessorList.add(...))`); equality/hashCode |
| `VariableMaskerTest` | R6 (all 8 scenarios) | Each printf family: `"HP: %s"`, `"Damage: %d"`, `"Ratio: %f"`, `"Hello %1$s"`, `"Cost %2$d"`, `"Slot %10$s"` → positional tokens + exact `variables`; each MessageFormat family: `"Value {0}"`, `"Amount {1,number}"`, `"Day {0,date,full}"`, `"Pick {0,choice,0#zero\|1#one}"`, `"N {12,number,integer}"`; **first-occurrence order + no dedup** (`"A %s and %s and {0}"` → `__VAR_0__`,`__VAR_1__`,`__VAR_2__`, variables `["%s","%s","{0}"]`); **literal protected** (`"Use __VAR_3__ now"` → `"Use __VAR_0__ now"`, variables `["__VAR_3__"]`, no leakage); **non-variables left literal** (`"100%% complete, %S var, %x hex, %10.2f wide, §a colored"` → unchanged, empty variables); **JSON braces not masked** (`"JSON { \"key\": \"value\" } and text {0}"` → only `{0}` masked); `null`/blank → NPE/IAE; mixed `"A %s {0} %1$s {1,number}"` combined scan |
| `VariableRoundTripTest` | R7 (all 3 scenarios) | **Identity round trip** (`"HP: %s, Cost: %1$d, Value: {0,number}"` → unmask(mask) → exact original, no discrepancies); **literal-token round trip no leakage** (`"Use __VAR_3__"` → unmask → `"Use __VAR_3__"` exact); **full-index never partial** (11-entry MaskedText, translated `"A __VAR_10__ B __VAR_1__"` → `variables.get(10)` and `get(1)` restored, no prefix restoration) |
| `VariableUnmaskerTest` | R8 (all 6 scenarios) | **Missing** (`List.of("%s","%d")`, translated `"Hello __VAR_0__"` → `"Hello %s"`, missing `[1]`, no throw); **unmatched verbatim** (`List.of("%s")`, translated `"Hi __VAR_0__ and __VAR_9__ and __VAR_9__"` → `"Hi %s and __VAR_9__ and __VAR_9__"`, unmatched `[9]` deduped/sorted); **reordered** (`List.of("%s","%d")`, `"Second __VAR_1__ first __VAR_0__"` → `"Second %d first %s"`, reordered `true`, empty lists); **duplicate not reordered** (`List.of("{0}")`, `"A __VAR_0__ B __VAR_0__"` → `"A {0} B {0}"`, reordered `false`); **perfect translation** (no discrepancies); `null` masked / null translated → NPE; `$`-variable round trip (`"%1$s"` restored exactly — pins the D8 quoting); interleaved matched/unmatched ordering (`[0,9,1]` → reordered `true`, unmatched `[9]`) |
| `UnmaskResultTest` | R8 carrier, D2 | `null` restoredText → NPE; `null` missing list / null unmatched list → NPE; defensive copy of both lists; valid construction preserves components; equality/hashCode |
| `GlossaryTermMatchTest` | R11 (both scenarios) | `null` term → NPE; blank term → IAE; `null` classification → NPE; happy path; **value equality** (equal term+classification equal + equal hashes; differing classification not equal) |
| `ScalingDecisionTest` | R10 (both scenarios) | `matchedRule` 0 and 7 → IAE; `null` engine / null list / null element → NPE; defensive copy; valid construction with rule 2/5 shapes; **heuristic-produced consistency** (one decision per rule 1–6 from `ScalingHeuristic` scenarios: `matchedRule` in 1..6, engine consistent with rule mapping, `glossaryMatches` non-empty only for rules 2/5, rule-2 has ≥1 `AMBIGUOUS`, rule-5 has no `LORE`) |
| `ScalingHeuristicTest` | R9 (all scenarios), R10 contract, R12 (all 3 scenarios) | **Rule 1**: `quest.description.task1` short text → PRECISE/1 (wins over rule 5); `lore.story`, `advancement.husbandry.root` → PRECISE/1; **rule 2**: AMBIGUOUS `"ancient temple"` on `item.artifact` → PRECISE/2 with match; whole-word case-sensitive (`"Iron is common; irony is not"`, entries `iron`/`Iron` AMBIGUOUS → PRECISE/2 with exactly the `Iron` match, `iron` inside `irony` NOT detected); **rule 3**: exactly 30 words on `gui.menu` → FAST/4 (rule 3 does NOT match at 30); 31 words on `item.sword` → PRECISE/3 (wins over rule 4); **rule 4**: `item.sword`, `block.stone`, `entity.zombie`, `gui.container` short → FAST/4; **rule 5**: `"Hello there"` 2 words on `random.key` with only-PLAIN glossary → FAST/5; exactly 8 words → FAST/5 (inclusive); **LORE guard pinned**: `"The dark lord rises"` with LORE `"dark lord"` → FAST/6 (outcome-equivalence pinned, must not become LORE→PRECISE); same text with PLAIN `"dark lord"` → FAST/5; **empty glossary**: `"Hi"` → FAST/5 (rule 2 vacuous false, rule 5 vacuously true); 40 words → PRECISE/3; **rule 6**: `"This sentence has exactly ten plain words written here today"` (10 words, > 8 and ≤ 30) on `some.other.key` empty glossary → FAST/6 (rule 5 blocked by word count, rule 3 not reached; corrected example — see apply-progress deviation 15); **evaluation on masked text**: masked `"A __VAR_0__ with __VAR_1__ detail"` whose raw form has 40 words → counted on masked text only (token = 1 word); **R12 direct**: `countWords("Hello, world!")` = 2, `countWords("...")` = 1, `countWords("A __VAR_0__ with __VAR_1__")` = 4, `countWords("   ")` = 0; **precedence conflicts**: short `quest.description.*` beats rule 4/5; 31-word `item.*` beats rule 4; LORE-term ≤8 words falls to 6; `null` path / null maskedText / null glossary → NPE; masked-token glossary safety (`__VAR_0__` never matches a `VAR`/`0` term) |

**Structural checks** (verification-time greps, not JUnit): R1 (all types under
`src/main/java/com/lucalzt/mctranslator/domain/service/`, none under `com.mctranslator`); R2 (`import
org.springframework` / `import jakarta` / any non-JDK, non-jspecify, non-`com.lucalzt.mctranslator` import →
zero matches); R3 (no `@Nullable` in the package); R14 (`MaskedText` / `GlossaryTermMatch` / `CacheKey` absent
from `domain/model`; `domain/model` still enumerates exactly 9 public types; port sources byte-identical in
signature).

---

## 10. Build & Verification Integration

### 10.1 Build gate (R15)

`mvnw.cmd clean verify` (Windows) / `./mvnw clean verify` (Linux/macOS) must pass with zero test failures,
zero compilation errors, exit code 0. CI (existing GitHub Actions workflow) runs the same command.

### 10.2 No `pom.xml` change

Verified: `org.jspecify:jspecify` 1.0.1 (compile scope) already declared (`pom.xml` lines 25–26, from the
archived `domain-models` change) and `spring-boot-starter-test` present. Nothing is added.

### 10.3 Java 25 / GraalVM constraints

Only stable language features: records (with compact constructors), enums, `Objects.requireNonNull`,
`String.isBlank`/`strip`, `List.copyOf`, `Matcher.quoteReplacement`, precompiled `Pattern`. **No String
Templates** (preview/rework risk in a GraalVM native build — proposal risk; `tech-stack.md` targets GraalVM
Native Image).

---

## 11. Threat Matrix

Security by default (every High impact has a mitigation or an explicit accepted-risk note).

| ID | Asset | Threat | Impact | Likelihood | Mitigation | Trace |
|---|---|---|---|---|---|---|
| T1 | Round-trip integrity | **Token collisions/spoofing**: literal `__VAR_N__` in source text, or `__VAR_1` vs `__VAR_10` partial matches, corrupt final JSON | High | Medium | Alternation branch 1 protects source literals as regular variables (no token leakage — output never contains a token that was not a protected variable, R6); full-index regex `__VAR_(\d+)__` with greedy `\d+` and mandatory trailing `__` (R7) makes partial matches structurally impossible; unmask treats `N ≥ n` as unmatched → left verbatim + reported (R8); dedicated tests pin all three defenses | R6, R7, R8 |
| T2 | Availability | **Regex ReDoS on pathological input**: adversarial modpack strings cause catastrophic backtracking | Low | Low | Audited patterns contain no nested/overlapping quantifiers: `\d+\$` backtracks linearly; `[^}]*` scans linearly; `\b…\b` with `Pattern.quote` is linear. Worst case is one linear scan per pattern per call. Documented, no mitigation code needed | R6, R9 |
| T3 | Round-trip integrity | **Replacement syntax injection**: variables containing `$`/`\` (`%1$s`) misinterpreted by `appendReplacement` as group references → corrupted restorations or thrown exceptions on valid round trips | High | Medium | `Matcher.quoteReplacement` on every replacement (D8) — mandatory, covered by a dedicated `$`-variable test | R7, R8 |
| T4 | Final output quality | **Discrepancy-driven corruption if the application layer ignores `UnmaskResult`**: missing/unmatched/reordered variables silently written into translated JSON | High | Medium | Discrepancies are a first-class API output (R8); the proposal (decision 3) mandates the application layer consume them for `[WARN]`. **Accepted-risk note**: enforcement transfers to the future `GlossaryAwareTranslator`; this change pins the contract and the tests | R8 |
| T5 | Availability | **Unbounded text sizes**: very long leaves → linear regex scans and large `variables` lists | Medium | Low | **Accepted risk with follow-up**: length bounding is deliberately deferred to the extraction change (same note as `domain-models` T1); services stay linear-time so impact is bounded CPU/memory per leaf, never exponential | R6, R9 |
| T6 | Architecture purity | **Framework leakage**: a `org.springframework`/`jakarta` import slips into `domain/service`, breaking hex purity and the native build | High | Low | R2 import policy + structural grep gate in Cap C; zero framework deps make the package trivially auditable | R2 |
| T7 | Null-safety | **Null-list constructions** in records with list components (`MaskedText`, `UnmaskResult`, `ScalingDecision`) defeat `@NullMarked` documentation | Medium | Medium | `Objects.requireNonNull` + `List.copyOf` (NPE on null elements) in every compact constructor; unit tests cover every rejection path (R5/R8/R10) | R5, R8, R10 |
| T8 | Glossary matching | **Regex metacharacters in glossary terms** (e.g. `a+b`) interpreted as patterns → false matches or PatternSyntaxException | Medium | Medium | `Pattern.quote(term)` inside `\b…\b` (D9); matching is literal whole-word | R9 |
| T9 | Behavior stability | **Rule-5 LORE guard misread as "LORE → PRECISE"** and "fixed" unilaterally, changing pipeline behavior | Medium | Low | Pinned equivalence documented in proposal/spec/design + explicit test (`"The dark lord rises"` LORE → FAST/6) that MUST NOT be altered | R9 |
| T10 | VO invariants | **Hand-built `MaskedText` rejection** (strong token-set invariant) surprises callers who construct VOs directly | Low | Low | `mask()` is the only producer and trivially satisfies the invariant; rejection behavior is pinned by R5 scenarios; documented | R5 |

No High-impact row lacks a mitigation or an explicit accepted-risk note (T4 transfers enforcement to the
application layer by design; T5 follows the established extraction-change follow-up).

---

## 12. Requirement Traceability

| Req | Mechanism (section) |
|---|---|
| R1 — real base package, exact 8 public types | §2.1 layout + §2.2 inventory; structural grep §9; D11/D12 (no extra public types) |
| R2 — zero framework imports | §2.3 import policy; grep gate §9 |
| R3 — `@NullMarked` package-info, no `@Nullable` | §2.4, §3.5; grep §9 |
| R4 — Javadoc on all public API | §3 (per type/member contract Javadoc) + §4 constants |
| R5 — `MaskedText` VO | §3.1 (validation + invariant + defensive copy) + D10 |
| R6 — `VariableMasker` | §4 (pinned alternation) + §5.1 + D1 |
| R7 — Round-trip fidelity / full-index tokens | §5.2 + `VariableRoundTripTest` §9 |
| R8 — `VariableUnmasker` + `UnmaskResult` | §3.2 + §5.2 + D2/D8 |
| R9 — `ScalingHeuristic` 6-rule precedence | §5.3 + D3/D4/D6/D14 |
| R10 — `ScalingDecision` record | §3.4 + D7 |
| R11 — `GlossaryTermMatch` VO | §3.3 |
| R12 — word counting contract | §5.3 step 3 + D4 |
| R13 — strict TDD red-first | §8 + §9 |
| R14 — `domain.model` untouched / ports unchanged | §1, §2.3, §6, grep gate §9 |
| R15 — clean verify | §10.1 |

---

## 13. Scope Guards & Flags for Later Phases

- **Flag for `tasks` phase — review workload forecast** (proposal S3, mandatory): this change is **16 files**
  (8 main sources incl. `package-info.java` + 8 test classes), estimated ≈ **+1,300–1,600 added lines** —
  well above the **800-line session budget** and the **400-line default**; chained PRs are REQUIRED. Natural
  capability slices (each ends green on `mvnw.cmd clean verify`, clean rollback = delete the slice's files):
  - **Slice A — Masking VOs** (MaskedText, UnmaskResult + 2 tests): 4 files ≈ 400–450 lines.
  - **Slice B — Masker** (VariableMasker + test): 2 files ≈ 300–350 lines.
  - **Slice C — Unmasker + round trip** (VariableUnmasker, VariableRoundTripTest + VariableUnmaskerTest):
    3 files ≈ 450–550 lines → may sub-split if the strict 400-line default applies.
  - **Slice D — Heuristic VOs** (GlossaryTermMatch, ScalingDecision + 2 tests): 4 files ≈ 280–330 lines.
  - **Slice E — Heuristic** (ScalingHeuristic + test): 2 files ≈ 450–520 lines → may sub-split.
  - **Slice F — Closing gates** (package-info.java + structural greps + final verify): 1 file + checks ≈ 40–60
    lines.
  Aggregation options: 6 chained PRs under the strict 400-line default, or 4 merged PRs (A+B, C, D+E, F) under
  the 800-line session budget. Decision belongs to the user before apply (`ask-on-risk`).
- **Red-first per capability** (§8): compilation failure of the not-yet-existing type is the red state for
  each Cap A/B test task (strict TDD, config.yaml).
- **No `pom.xml` change** (§10.2): jspecify 1.0.1 already present — tasks must NOT add dependencies.
- **Non-goals to guard against scope creep**: precise-flag/`[WARN]` (application layer), `GlossaryPort`
  consumption, engine masked-text handoff (S2), `domain.model` additions (R14), threshold configuration.
- **Pinned behaviors that must not be "fixed"**: the rule-5 LORE guard outcome-equivalence (T9); the
  empty-glossary rule-5 literal reading; the `%%s` adjacency accepted limitation; the non-masked printf/
  MessageFormat/Minecraft set.
