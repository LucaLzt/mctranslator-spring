# Design: Extended Variable Masking (`masker-printf-v2`)

## Executive Summary

This design is a **strict delta over the archived `domain-services` design** (`openspec/changes/archive/domain-services/design.md`), scoped to the masking behavior of `VariableMasker` only. The v1 3-branch alternation `__VAR_(\d+)__ | %(?:\d+\$)?[sdf] | \{(\d+)(?:,[^}]*)?\}` (base design §4/§5.1) is replaced by an **escape-aware, URL-guarded printf branch** with four sub-behaviors: (a) the F4 conversions `%n %x %o %e %g %b %c %h %i %ld` join the v1 set `%s %d %f`, all with the optional positional index `%N$conv`; (b) the printf escape `%%` is masked as its own variable; (c) a conversion immediately followed by `%%` is masked as **ONE token** whose variable is the full original substring (e.g. `%s%%`, `%1$s%%`, `%d%%`); and (d) a hard URL guard keeps `%` + two hex digits (`%[0-9A-Fa-f]{2}`) permanently literal, winning over every printf branch — implemented as a **negative lookahead inside the printf branches**, so the pattern structurally can never match a URL sequence and the masker's loop invariant ("every match becomes a token") is preserved unchanged.

`VariableUnmasker` and `MaskedText` need **no code change** (spec R5/R8 delta): restoration by token index with verbatim variables already yields byte-identical restoration for `%%`, merged tokens and F4 conversions, and the `MaskedText` token-set invariant is content-agnostic (it scans `__VAR_(\d+)__` in the masked text, never the variables). The only production change is `VariableMasker`: the `VARIABLE_PATTERN` constant (with inline comments) and the class Javadoc (R4). Tests: one v1 masker scenario is REPLACED (the one pinning `%%`/`%x` literal), ~18 new masker scenarios and 3 new round-trip scenarios are added; the rest of the v1 suite stays untouched as the regression safety net. Estimated workload ≈ 320–390 changed lines across 4 files → likely a single PR; the tasks phase must confirm with the forecast lines. No `pom.xml` change, no new public types, no new imports (`java.util.regex` only — R2).

---

## 1. Scope Anchoring (delta over the archived `domain-services` design)

- **In scope (changed by this delta)**: `VariableMasker.VARIABLE_PATTERN` (base design §4 row "VariableMasker") and the masker behavior in §5.1 of the base design — plus the class Javadoc (R4 consequence). Exactly one public class is touched; the public API shape `MaskedText mask(String text)` is unchanged.
- **In scope (added test surface)**: `VariableMaskerTest` (replace 1 v1 scenario + add v2 scenarios) and `VariableRoundTripTest` (add byte-identical restoration scenarios, R7). Both live in `src/test/java/com/lucalzt/mctranslator/domain/service/` (R13).
- **Explicitly out of scope (unchanged from the archived design)**: `VariableUnmasker` (R8 — no change, §3.8), `MaskedText` (R5 — no change, §3.8), `UnmaskResult`, `GlossaryTermMatch`, `ScalingDecision`, `ScalingHeuristic`, `package-info.java`, the `domain.model` inventory and all port contracts (R14); application-layer orchestration, engine handoff, infrastructure, thresholds, variable deduplication, unmasker failure modes.
- **Behavioral non-goals kept from v1, now re-confirmed**: F3 flags/width/precision forms (`%10.2f`, `%-10s`, `%+d`, `%2$10d`, `%02d`) — 0.05% corpus + URL collision risk; case variants `%S`/`%D`/`%F`; the non-set two-character form `%l` and `%l<other>`; JSON and unbalanced braces; named braces `{p}`; Minecraft `\u00a7` section-sign codes; MessageFormat branch expansion (kept verbatim, `{0}` family = 0% corpus).

---

## 2. Architecture & Component Design (delta)

### 2.1 Package layout, API surface, nullability, statelessness — **unchanged**

The `domain.service` package layout, the 8-type public inventory, the `@NullMarked` package-info, and the statelessness/thread-safety notes of the base design §2 are untouched. No type is added, removed or renamed; no public member signature changes. `VariableMasker` remains a stateless final class with the default public constructor and a single precompiled `Pattern` constant.

### 2.2 What actually changes

| Element | v1 (archived) | v2 (this change) | Section |
|---|---|---|---|
| `VariableMasker.VARIABLE_PATTERN` | `"__VAR_(\\d+)__\|%(?:\\d+\\$)?[sdf]\|\\{(\\d+)(?:,[^}]*)?\\}"` | escape-aware printf + URL guard (pinned pattern below) | §3.1 |
| `VariableMasker` class Javadoc | documents `%s/%d/%f` + MessageFormat; "Documented limitation" = `%%s` adjacency; `%%` and other conversions listed as NOT masked | documents F4 set, `%%` escape, `%s%%` merge, URL guard; the limitation paragraph is replaced by the escape-aware description | §3.7 |
| `VariableMasker.mask` algorithm | single left-to-right `find()` scan, `appendReplacement`/`appendTail`, content-based branch classification (D1) | **identical algorithm** — no loop change, no post-processing pass, no guard-match special case | §3.5 |
| `VariableUnmasker`, `MaskedText`, all other types | — | **zero code change** | §3.8 |

### 2.3 Import policy (R2)

The new pattern uses only `java.util.regex` primitives already imported by `VariableMasker` (`Pattern`, `Matcher`). **No new imports, no new dependencies**; the package stays 100% pure Java.

---

## 3. Behavioral Design — the masker rework (core of this change)

### 3.1 The pinned alternation (exact order and precedence)

The v1 alternation is replaced by this single precompiled pattern (Java source literal):

```java
private static final Pattern VARIABLE_PATTERN = Pattern.compile(
        "__VAR_(\\d+)__"                                               // (1) literal-token protection (v1, verbatim)
        + "|%(?![0-9A-Fa-f]{2}(?!\\$))(?:\\d+\\$)?(?:[sdfnxoegbchi]|ld)%%"   // (2a) MERGE: conversion + escape -> ONE token
        + "|%(?![0-9A-Fa-f]{2}(?!\\$))(?:\\d+\\$)?(?:[sdfnxoegbchi]|ld)"      // (2b) conversion: v1 + F4, optional positional index
        + "|%%"                                                        // (2c) standalone printf escape literal
        + "|\\{(\\d+)(?:,[^}]*)?\\}");                                 // (3) MessageFormat (v1, verbatim)
```

Equivalent single-line form (the pinned literal):

```
__VAR_(\d+)__|%(?![0-9A-Fa-f]{2}(?!\$))(?:\d+\$)?(?:[sdfnxoegbchi]|ld)%%|%(?![0-9A-Fa-f]{2}(?!\$))(?:\d+\$)?(?:[sdfnxoegbchi]|ld)|%%|\{(\d+)(?:,[^}]*)?\}
```

**Precedence semantics — why this exact order (this is the mechanism the spec left open):**

1. **Branch (1) `__VAR_(\d+)__` first (unchanged).** Literal tokens in the source must be protected before any `%`-logic runs; this preserves the no-leakage guarantee (base design T1/R6).
2. **Within the printf family, the URL guard is INSIDE branches (2a) and (2b), not a separate alternation branch.** A separate guard branch would *match* URL sequences, forcing the loop to detect and skip them (breaking the "every match becomes a token" invariant of D1). As a **negative lookahead**, the guard makes the printf branches *structurally incapable* of matching a URL sequence — the pattern literally "never matches" `%` + two hex digits (R16 wording), the loop needs no new case, and the guard's precedence over the conversion and merge branches cannot be mis-ordered by a future edit.
3. **(2a) merge before (2b) conversion — mandatory.** At a `%` position the engine tries the longest form first: if the trailing `%%` is present, (2a) matches the full substring as ONE token; if absent, (2a) fails and (2b) matches the bare conversion. Reversing this order would split `%s%%` into two tokens (`%s` + `%%`), violating decision 2.
4. **(2b) conversion before (2c) escape.** At a `%` position the second character is either a conversion char (`s d f n x o e g b c h i l`) or another `%`. These two second-chars are disjoint, so (2b) and (2c) can never both match at one position; the order is defensive clarity. (2c) is what makes standalone `%%` a variable and is the natural fallback for reverse adjacency (`%%s`) and triples (`%%%`).
5. **Branch (3) MessageFormat last (unchanged).** Disjoint prefix `{`; kept verbatim and NOT expanded (decision 5).

**Group numbering is unchanged** (base design §4 note): group 1 = `(\d+)` in branch (1); group 2 = `(\d+)` in branch (3); the printf branches contribute **no capturing groups** (the guard is a lookahead, `(?:...)` groups are non-capturing). Content-based branch detection (D1) still works unchanged: matched prefixes are disjoint (`__VAR_` / `%` / `{`), and every `%`-prefixed match is now guaranteed to be a printf match — there is no guard "match" to classify.

### 3.2 URL percent-encoding hard guard (R16 — mechanism)

**Guard lookahead:** `(?![0-9A-Fa-f]{2}(?!\$))` placed immediately after `%` in branches (2a) and (2b).

Reading it inside-out:

- `[0-9A-Fa-f]{2}` — two consecutive hex digits after the `%`;
- `(?!\$)` — the character after that hex pair is **not** `$`;
- outer `(?! ... )` — **block the printf branch** when both hold.

Consequences (all pinned by R16 scenarios):

| Input | Guard result | Behavior |
|---|---|---|
| `%20F`, `%E2`, `%2C`, `%0A`, `%d2`, `%e2`, `%b2`, `%c2`, `%f2` | hex pair + next char ≠ `$` → blocked | literal, no token (R16: "URL encodings produce no variables"; "Guard precedence over hex-adjacent conversions") |
| `%e`, `%b`, `%c`, `%d`, `%f`, `%x`, `%h`, `%n`, `%g`, `%o`, `%s`, `%i` (standalone) | fewer than 2 hex chars follow → guard passes | conversion masked (R6 per-conversion scenarios) |
| `%%`, `%%%` | second char `%` is not a hex digit → guard cannot apply | escape masked / fallback (R16: "Guard does not affect the escape") |
| `%1$s`, `%2$x`, `%10$s`, `%12$ld` | digit run terminated by `$` → `(?!\$)` fails → guard passes | positional conversion masked (R16: "Guard does not affect positional conversions"; v1 "Slot %10$s" scenario) |

**The `(?!\$)` refinement is the critical design pin:** the spec's R16 scenario pins `%1$s` (one digit + `$`), but the v1 scenario `"Slot %10$s"` (TWO digits, both hex) must keep masking. Without the `(?!\$)` refinement, the naive guard `(?![0-9A-Fa-f]{2})` would see `1`+`0` as a hex pair and wrongly block `%10$s`. The refinement encodes the spec's own rationale — "the `$` breaks the two-consecutive-hex-digit run" — generalized to any digit-run length. A literal `$` never follows a percent-encoded pair in real URLs (`$` is itself encoded as `%24`), so the refinement sacrifices nothing.

The guard is applied to BOTH (2a) and (2b) — **including the merge branch**. Without it on (2a), `%e2%%` would merge into one token (conv `e` + `%%`), which would mask the URL sequence `%e2` and violate the guard's hard precedence. With it, `%e2%%` → `%e2` literal + `%%` standalone escape token (the natural composition of R16 + R6).

**Accepted sacrifice (documented, not a bug):** conversion chars that are themselves hex digits (`d f e b c`) followed by a hex digit (`%d2`, `%f2`, `%e2`, `%b2`, `%c2`) are left literal — the guard wins. The F3 width family is a non-goal (0.05% corpus), so no v1 regression (spec Risks row 2).

### 3.3 Merge mechanism: conversion + `%%` → ONE token (decision 2 — mechanism)

**Mechanism: a dedicated alternation branch (2a), no post-processing pass.** The merge is realized purely by the branch matching the *full* substring:

```
%(?![0-9A-Fa-f]{2}(?!\$))(?:\d+\$)?(?:[sdfnxoegbchi]|ld)%%
```

- **What it matches:** exactly ONE conversion — positional (`%1$s`, `%2$x`, `%10$s`) or not (`%s`, `%d`, `%x`, `%ld`), v1 or F4 — immediately followed by exactly ONE literal `%%`. The whole match becomes ONE token; `variables` records the full original substring verbatim (e.g. `"%s%%"`, `"%1$s%%"`, `"%d%%"`, `"%x%%"`, `"%ld%%"`).
- **Why one token, not two:** both tokenizations round-trip byte-identically; the single token is the translator-UX choice pinned by decision 2 — the engine never sees a dangling `%%` after a token (proposal decision 2; R7 "Merged token restores byte-identical").
- **Second consecutive `%%` → independent escape token:** the branch consumes exactly ONE `%%`; `Matcher.find()` resumes immediately after it, and the next `%%` matches branch (2c). `"A %s%%%% B"` → `%s%%` (2a) + `%%` (2c) → `"A __VAR_0____VAR_1__ B"`, `variables = ["%s%%", "%%"]` (R6 scenario "Merge consumes exactly one conversion and one escape"). No extra logic: scan order does the work.
- **Generalization beyond the observed `%s%%`:** the merge covers any conversion in the set (spec "Resolved in Spec" generalizes decision 2's wording to "a conversion — positional or not, any of the v1 + F4 set — immediately followed by `%%`"). All merge forms round-trip byte-identical, so the broader surface is safe (spec Risks row "Merge-rule generalization").
- **Fallback sequential (`%%s`, `%%%`) — no over-merge:** branches (2a)/(2b) cannot match `%%` (second char `%` is not a conversion char), so the leading `%%` falls to (2c) as its own escape token and the remainder stays literal or scans normally. `"100%%s"` → `"100__VAR_0__s"` (`["%%"]`); `"%%%"` → `"__VAR_0__%"` (`["%%"]`); `"%s%%%%"` → `"__VAR_0____VAR_1__"` (`["%s%%","%%"]`). Reverse adjacency occurs only in `translation.test.escape` (a deliberately broken test string); the two-token fallback is pinned by the R6 "Escape-adjacency fallback round-trips byte-identical" scenario.

### 3.4 The conversion set and `%ld`

- Conversion characters: `(?:[sdfnxoegbchi]|ld)` — v1 `s d f` + F4 `n x o e g b c h i`, plus the only two-character conversion `ld`.
- **`l` is deliberately excluded from the character class** so that `%l` (bare) and `%ls` (followed by any other char) cannot match: `%l` fails the class and `ld` requires a trailing `d`; `%ls` fails `ld`. Only `%ld` matches (spec: "`%ld` is the ONLY two-character conversion in scope"). Pinned by the R6 scenario "Reduced non-variable set is left literal" (`%l`, `%ls` → literal).
- The optional positional group `(?:\d+\$)?` is unchanged from v1: any digit run (including multi-digit and, as in v1, `%0$s` — the spec's "positive-digit-index" is descriptive; the v1 regex accepted `\d+` and v1 compat requires keeping that behavior). Hex-looking digit runs like `%10$s` are protected by the guard's `(?!\$)` refinement (§3.2).

### 3.5 Branch classification — unchanged (D1 preserved)

The loop body of `VariableMasker.mask` (current lines 76–87) needs **no change**: every `Matcher.find()` result is a genuine variable and becomes the next positional token; branch classification stays content-based on the whole match (prefix `__VAR_` / `%` / `{`). Because the guard is a lookahead, there is no "guard match" to skip, so the token counter and the `variables` list advance for every match — the loop invariant is untouched. `appendReplacement(masked, "__VAR_" + nextIndex + "__")` is unchanged (the replacement still contains no `$`/`\`).

### 3.6 Updated NOT-masked set (R6 delta)

| Family | v1 status | v2 status |
|---|---|---|
| `%%` (standalone), `%s%%` merged | NOT masked (non-goal) | **MASKED** (escape / merged token) |
| `%n %x %o %e %g %b %c %h %i %ld` | NOT masked (non-goal) | **MASKED** (F4 set) |
| `%S %D %F` case variants | literal | literal (unchanged) |
| F3 forms `%10.2f %-10s %+d %2$10d %02d` | literal | literal (unchanged) |
| `%l`, `%l<other>` | literal | literal (unchanged) |
| JSON braces, unbalanced braces `{0`, named braces `{p}` | literal | literal (unchanged) |
| Minecraft `\u00a7` codes (`§a`) | literal | literal (unchanged) |
| URL percent-encodings `%[0-9A-Fa-f]{2}` | literal (by non-matching) | literal (HARD guard, R16) |
| `%d2 %f2 %e2 %b2 %c2` (hex-adjacent conversions) | literal (F3 non-goal) | literal (guard wins — accepted, §3.2) |

### 3.7 Javadoc update (R4 — delta)

`VariableMasker`'s class Javadoc (current lines 9–47) is rewritten to describe the new pattern set: the escape-aware printf branch (v1 + F4 conversions, optional positional index, `%ld` as the only two-char conversion), the `%%` escape masked standalone, the single-token merge rule (`conversion + %%`), and the URL guard `%[0-9A-Fa-f]{2}` with its hard precedence. The "Documented limitation" paragraph (lines 34–37, the v1 `%%s` adjacency limitation) is **replaced** by the v2 description of the merge rule and the sequential fallback for reverse adjacency (`%%s`/`%%%` — now resolved, not a limitation). The `mask` method Javadoc (lines 53–66) is unchanged (the token-replacement and non-masking statements still hold). No new public type or member is added, so no new Javadoc obligations arise beyond this rewrite.

### 3.8 Tokenization & restoration — `VariableUnmasker` and `MaskedText` confirmed unchanged (R5/R7/R8)

- **`VariableUnmasker` (NO code change):** restoration is by token index with `Matcher.quoteReplacement` (current lines 68–88). The new variable contents — `%%`, `%s%%`, `%1$s%%`, `%x`, `%ld` — restore verbatim: `%` is not special to `quoteReplacement`; `$` (in `%1$s%%`) is already handled by the existing quoting (base design D8/T3). **No `%%` → `%` normalization exists anywhere in the pipeline** — the unmasker performs no transformation beyond token→verbatim-variable replacement, so byte-identical restoration (R7) falls out by construction. The R8 delta says "expected to need no code change"; this design confirms it.
- **`MaskedText` (NO code change):** the compact constructor validates (a) null/blank components and (b) the token-set invariant via a scan of `__VAR_(\d+)__` in `maskedText` (current lines 41–74). The invariant is **content-agnostic** — it never inspects the `variables` strings. New variables `%%`, `%s%%`, `%x` are non-null and non-blank (all start with `%`), so every validation branch accepts them; `mask()` remains the only producer and trivially satisfies the invariant. The masked text produced by v2 (e.g. `"100__VAR_0__ complete"`, `"Progress: __VAR_0__"`, `"Hex: __VAR_0__"`) is structurally identical to v1 masked text — the token-set invariant is unaffected.
- **Round-trip invariants preserved:** full-index matching `__VAR_(\d+)__` (R7) is untouched; literal-token no-leakage (branch 1) is untouched; a merged token restores as a whole because it is a single `variables` entry.

---

## 4. Data Model — unchanged

No entity, record or value object changes. `MaskedText`'s validation/invariant behavior and `UnmaskResult`'s shape are exactly as archived (base design §3). The new variable contents are accepted by the existing invariants (§3.8). The MessageFormat branch's matched forms are unchanged (decision 5 — not expanded).

---

## 5. Integration Points — unchanged

The base design §6 stands: no port consumed, `suggest` contract decoupled from `MaskedText`, the future application-layer flow consumes `mask()` / `unmask()` unchanged, diagnostics travel as return values. The only observable difference for downstream phases is the content of `MaskedText.variables()` (richer set: `%%`, merged tokens, F4 conversions) — which the pipeline already treats as opaque verbatim strings.

---

## 6. Design Decisions & Alternatives (delta — continues D1–D14 of the archived design)

| ID | Decision point | Alternatives considered | Chosen | Rationale / trace |
|---|---|---|---|---|
| D15 | URL guard mechanism | (a) separate alternation branch `%[0-9A-Fa-f]{2}` first among printf, with a loop special-case that detects guard matches and leaves them literal (appendReplacement with the matched text, no token); (b) negative lookahead `(?![0-9A-Fa-f]{2}(?!\$))` inside the printf branches | **(b) negative lookahead** | Keeps the loop invariant "every match is a token" and content-based classification (D1) untouched — no new branch case, no skip logic; the pattern structurally *never matches* a URL sequence (R16's "NEVER matches" wording); the guard's precedence over merge and conversion cannot be re-ordered by a future edit because it is part of those branches. (a) would make "never matched" false (the pattern matches and the code must remember to skip) — a maintenance trap. |
| D16 | Guard vs. positional digit runs (`%1$s`…`%10$s`) | (a) naive guard `(?![0-9A-Fa-f]{2})`; (b) refined guard with inner `(?!\$)` | **(b) `(?![0-9A-Fa-f]{2}(?!\$))`** | (a) blocks `%10$s` (digits `1`,`0` are both hex) breaking the v1 "Slot %10$s" scenario; (b) encodes the spec's rationale — "the `$` breaks the two-consecutive-hex-digit run" — generalized to any digit-run length. URLs never have a literal `$` after a hex pair (`$` → `%24`), so (b) sacrifices nothing. Pinned by the v1 positional scenario + R16 "Guard does not affect positional conversions". |
| D17 | Merge mechanism | (a) dedicated alternation branch (2a) matching the full `conv%%` substring; (b) post-processing pass that re-joins adjacent conversion+escape matches after the scan | **(a) dedicated branch** | One precompiled `Pattern`, single left-to-right scan, zero extra state (spec: "single precompiled Pattern, JDK-only"); the branch emits the merged substring as one match, so numbering, `variables` and the MaskedText invariant are produced naturally. (b) would need look-behind state between matches and renumbering logic — more surface for the same result. |
| D18 | Merge consumption scope | (a) merge only the observed `%s%%`; (b) merge any conversion (positional or not, v1 + F4) + one `%%` | **(b) general rule** | Spec "Resolved in Spec" generalizes decision 2's wording; all merge forms round-trip byte-identical so the broader surface is safe; the corpus instance (`%s%%`, vanilla + apotheosis) is a special case of (b). The branch consumes exactly one `%%`; a second consecutive `%%` is an independent escape token by scan resumption — no over-merge. |
| D19 | `%ld` vs `%l` disambiguation | (a) add `l` to the char class and special-case `%l` exclusion; (b) exclude `l` from the class and add an explicit `ld` member | **(b) explicit `(?:[sdfnxoegbchi]|ld)`** | The class is exactly the single-char conversions; `ld` is the only two-char form and must match as a unit. `%l` fails both the class and `ld`; `%ls` fails `ld` — literal, pinned by the R6 reduced-literal scenario. Ordering inside the group is immaterial (`l` is not in the class) — written `[sdfnxoegbchi]|ld` for readability. |
| D20 | Escape branch placement & fallback | (a) escape `%%` before conversion; (b) conversion before escape, merge first | **(b) merge (2a) → conversion (2b) → escape (2c)** | Merge must precede conversion or `%s%%` splits into two tokens (decision 2 violation). Conversion-vs-escape order is behavior-neutral (disjoint second chars `%` vs conversion chars) — (2b) before (2c) is defensive clarity. (2c) provides the sequential fallback for `%%s`/`%%%` (R6 fallback scenario). |
| D21 | Javadoc/limitation text | (a) keep the v1 "documented limitation" paragraph, append v2 notes; (b) rewrite the class Javadoc, replacing the limitation paragraph with the v2 behavior description | **(b) rewrite + replace** | The v1 limitation (`%%s` adjacency masks the trailing `%s`) is *resolved* by v2 (the leading `%%` now masks first via 2c) — keeping the old paragraph would be factually wrong. R4 requires Javadoc to describe the actual pinned pattern set. |

---

## 7. Sequencing (strict TDD — informs `tasks`)

Per `openspec/config.yaml` (`rules.tasks`) and R13, the test changes come red-first; the pattern change is the only production step. No `pom.xml` work (R2/R15 gate unchanged).

1. **M1 — Masker scenarios (red):** rewrite `VariableMaskerTest` — REPLACE `leavesNonVariablesLiteral` (pins the reduced literal set) and ADD the v2 scenarios of §8. With the v1 pattern still in place, the new tests are RED (behavioral red, no compilation red — the API is unchanged).
2. **M2 — Round-trip scenarios (red):** add the byte-identical restoration scenarios to `VariableRoundTripTest` (§8). RED against the v1 pattern (e.g. `%%` is not masked in v1).
3. **M3 — Production (green):** implement the new `VARIABLE_PATTERN` + inline comments and rewrite the `VariableMasker` class Javadoc (§3.1, §3.7). All masker + round-trip tests turn green; every untouched v1 test must stay green (regression net).
4. **M4 — Closing gates (R15/R2/R14):** `mvnw.cmd clean verify`; structural greps — no new imports in `VariableMasker` (R2), `domain.model` inventory and port sources untouched (R14), no `@Nullable` added (R3).

Red-first discipline note: unlike `domain-services`, no new type is created, so "red" is behavioral (tests asserting v2 behavior fail against the v1 pattern), not a compilation failure. The tasks phase MUST record this so apply does not wait for a non-existent compile red.

---

## 8. Testing Strategy (JUnit 6 + AssertJ, red-first, no Spring context)

### 8.1 Modified v1 tests

| Test | Change | Reason (spec) |
|---|---|---|
| `VariableMaskerTest.leavesNonVariablesLiteral` (current lines 64–69, input `"100%% complete, %S var, %x hex, %10.2f wide, §a colored"`) | **REPLACED** by "Reduced non-variable set is left literal" with input `"%S var, %10.2f wide, %-10s, %2$10d, %+d, %l, %ls, {p}, §a colored"` → unchanged, empty `variables` | R6 delta: `%%` and `%x` are no longer literal; the scenario pins the REMAINING literal set |

All other v1 `VariableMaskerTest` tests stay valid unchanged: v1 inputs still produce v1 output (`%s/%d/%f/%1$s/%2$d/%10$s`, MessageFormat family, first-occurrence/no-dedup, literal-token protection, JSON braces, null/blank rejection).

### 8.2 New masker tests (`VariableMaskerTest` — one per spec scenario)

| Spec scenario | Test input(s) → expected `variables` |
|---|---|
| F4 per-conversion (R6, 10 scenarios) | `"Line: %n"` → `["%n"]`; `"Hex: %x"` → `["%x"]`; `"Oct: %o"` → `["%o"]`; `"Sci: %e"` → `["%e"]`; `"Gen: %g"` → `["%g"]`; `"Flag: %b"` → `["%b"]`; `"Char: %c"` → `["%c"]`; `"Hash: %h"` → `["%h"]`; `"Int: %i"` → `["%i"]`; `"Long: %ld"` → `["%ld"]` |
| Positional F4 | `"Cost %2$x and %1$ld"` → `["%2$x", "%1$ld"]` |
| Standalone escape | `"100%% complete"` → `["%%"]` |
| Merge (decision 2) | `"Progress: %s%%"` → `["%s%%"]`; `"+%s%% %s"` → `["%s%%", "%s"]` |
| Positional merge | `"Progress: %1$s%%"` → `["%1$s%%"]` |
| Merge consumes exactly one | `"A %s%%%% B"` → `["%s%%", "%%"]` |
| Escape-adjacency fallback | `"100%%s"` → `["%%"]`; `"%%%"` → `["%%"]`; `"%s%%%%"` → `["%s%%", "%%"]` |
| Reduced literal set (REPLACED scenario, §8.1) | `"%S var, %10.2f wide, %-10s, %2$10d, %+d, %l, %ls, {p}, §a colored"` → unchanged, `[]` |
| Mixed v2 scan | `"A %s {0} %1$s%% %n {1,number} %2$x"` → `["%s", "{0}", "%1$s%%", "%n", "{1,number}", "%2$x"]` |
| URL guard: encodings | `"Path %20F"`, `"Enc %E2"`, `"List %2C"`, `"Newline %0A"` → unchanged, `[]` |
| URL guard: quest-SNBT-like | `"Click here: https://example.com/api?text=Portable%20Fluid%20Storage%2C%20Silo"` → unchanged, `[]` |
| URL guard: precedence over hex-adjacent | `"Rate %e2"` → unchanged, `[]`; `"Rate %e"` → `["%e"]`; `"Flag %b2"` → unchanged, `[]`; `"Flag %b"` → `["%b"]`; also `%c2` vs `%c`, `%d2` vs `%d` (same rule, guard wins) |
| URL guard: does not affect escape | `"100%% done"` → `["%%"]` |
| URL guard: does not affect positional | `"Hello %1$s"` → `["%1$s"]` (plus a `%10$s` guard-regression guard inside the v1 positional scenario, kept) |

### 8.3 New round-trip tests (`VariableRoundTripTest` — R7 byte-identical)

| Scenario | Round trip |
|---|---|
| `%%` restores byte-identical (decision 3) | `mask("100%% complete")` → `unmask(masked, masked.maskedText())` → restored == `"100%% complete"` EXACTLY (never `"100% complete"`), no discrepancies |
| Merged token restores byte-identical | `mask("Progress: %s%%")` → unmask → restored == `"Progress: %s%%"`, no discrepancies (merged variable restores as a whole; also covers `%1$s%%` quoting path) |
| F4 conversions restore byte-identical | `mask("Hex: %x, Long: %ld, Sci: %e")` → unmask → restored == the exact original, no discrepancies |

### 8.4 Untouched test classes

`MaskedTextTest`, `VariableUnmaskerTest`, `UnmaskResultTest`, `GlossaryTermMatchTest`, `ScalingDecisionTest`, `ScalingHeuristicTest`, `VariableRoundTripTest`'s existing 3 scenarios: **zero changes** — they form the regression safety net (spec Risks row "Behavioral change vs v1").

**Structural checks** (verification-time greps, unchanged from base §9): R1 package placement; R2 zero framework imports (VariableMasker gains no imports); R3 no `@Nullable`; R14 `domain.model` inventory and port sources untouched.

---

## 9. Build & Verification Integration — unchanged gate

`mvnw.cmd clean verify` (R15) with zero failures and exit code 0; the existing CI workflow runs the same command. No `pom.xml` change (R2/R15). Java 25/GraalVM constraint of the base design §10.3 stands: the new pattern uses only stable regex features (lookaheads, POSIX-free char class `[0-9A-Fa-f]`, non-capturing groups) — no preview features.

---

## 10. Threat Matrix (delta rows over base design §11)

Security by default: every High impact has a mitigation or an explicit accepted-risk note.

| ID | Asset | Threat | Impact | Likelihood | Mitigation | Trace |
|---|---|---|---|---|---|---|
| TD1 | Round-trip integrity / v1 compat | **URL guard eats positional F2 conversions**: a hex-looking positional index (`%10$s`; both digits hex) wrongly blocked as a URL | High | Medium | Guard lookahead includes the `(?!\$)` refinement (D16) — a `$`-terminated digit run is a positional index, never a URL; the v1 scenario `"Slot %10$s"` is kept verbatim and the R16 positional scenario pins `%1$s`. A regression here would fail the untouched v1 suite immediately | R6 (v1 scenario), R16 |
| TD2 | Final output quality | **Guard sacrifices hex-adjacent conversion text** (`%e2`, `%b2`, `%c2`, `%d2`, `%f2` stay literal) — a legit conversion next to hex digits is not masked | Medium | Medium | Accepted by design (R16 hard precedence; F3 width family is a non-goal — 0.05% corpus, all the deliberately-broken `translation.test.invalid2`); pinned by the R16 precedence scenarios and the spec Risks row 2. Left-literal text round-trips with identity — under-masking is recoverable, over-masking is not (base spec M3 rationale) | R16, spec Risks |
| TD3 | Round-trip integrity | **Merge over-application**: `%s%%` merged in content where the translator would prefer two tokens | Low | Medium | Both tokenizations round-trip byte-identical (decision 2 — the merge is a translator-UX choice); the merge consumes exactly ONE conversion + ONE `%%`; a second consecutive `%%` is an independent escape token (pinned scenario). No data-corruption path exists | R6 merge scenarios, R7 |
| TD4 | Final output quality | **`%ld` over-masking**: `%l` or `%ls` wrongly treated as a conversion | Medium | Low | `l` is excluded from the conversion class; only the explicit `ld` member matches (D19); pinned literal in the reduced-literal scenario | R6 |
| TD5 | Availability | **Regex ReDoS on pathological input**: the new branches introduce backtracking surface | Low | Low | The lookahead `(?![0-9A-Fa-f]{2}(?!\$))` is bounded (≤ 3 chars); `(?:\d+\$)?` backtracks linearly over a digit run; `[^}]*` scans linearly (unchanged); no nested/overlapping quantifiers anywhere (extends base T2 audit) — worst case is one linear scan per call | R6, R16 |
| TD6 | Behavior stability | **Regression vs v1**: `%%`/`%x`/`%s%%` now mask; the v1 scenario that pinned them literal is replaced | Medium | Medium | Only ONE v1 scenario + ONE test are modified (R6 delta); every other v1 test stays as the regression net; pattern + scenarios change in the same commits → rollback restores both together (proposal rollback plan) | R6 delta, proposal rollback |

Base rows T1–T10 remain in force (token no-leakage, replacement quoting, discrepancy contract, framework purity, null-safety, etc.) — none of their mechanisms changed.

---

## 11. Requirement Traceability

| Req | Mechanism (section) |
|---|---|
| R2 — zero framework imports | §2.3 — pattern uses only `java.util.regex`; no new imports |
| R4 — Javadoc on all public API | §3.7 — `VariableMasker` class Javadoc rewritten (no new public members) |
| R5 — `MaskedText` VO | §3.8 — unchanged; new variable contents satisfy the existing invariants |
| R6 — `VariableMasker` (MODIFIED) | §3.1–§3.6 (pinned pattern, precedence, merge, guard, updated NOT-masked set) + D15–D20 |
| R7 — Round-trip fidelity (MODIFIED) | §3.8 (byte-identical falls out of verbatim variables, no normalization) + §8.3 tests |
| R8 — `VariableUnmasker` (unchanged) | §3.8 — confirmed no code change |
| R13 — strict TDD red-first | §7 (behavioral red — no new types) + §8 |
| R14 — `domain.model` / ports untouched | §1, §2.2, §5 — zero touch |
| R15 — clean verify | §9 |
| R16 — URL hard guard (ADDED) | §3.2 (guard lookahead, precedence table) + D15/D16 + §8.2 guard tests |
| R1/R3/R9–R12 (unchanged) | base design §2/§3/§5.3 — no mechanism touched |

---

## 12. Scope Guards & Flags for Later Phases

- **Flag for `tasks` — review workload forecast (proposal S3, mandatory):** estimated **4 files** — `VariableMasker.java` (pattern + Javadoc, ≈ ±40 lines), `VariableMaskerTest.java` (1 replaced + ~18 new tests, ≈ +240–300 lines), `VariableRoundTripTest.java` (+3 tests, ≈ +55–70 lines) — total ≈ **320–390 changed lines**, under the 400-line default → **likely a single PR**; the tasks phase MUST produce the exact forecast lines (`Decision needed before apply`, `Chained PRs recommended`, `400-line budget risk`) and may compact per-conversion F4 tests (parameterized `@ParameterizedTest` or a helper) to stay safely under budget.
- **Red-first nuance (record for apply):** no new type is created, so the red state is **behavioral** (v2 tests fail against the v1 pattern), NOT a compilation failure. `apply` must not wait for a compile red.
- **Pinned behaviors that must not be "fixed"**: the URL guard's hard precedence over hex-adjacent conversions (`%e2` literal — TD2); the merge-rule generalization to any conversion (D18); the `%10$s` guard refinement (D16 — the naive guard is a known trap); `%l`/`%ls` literal (D19); the MessageFormat branch kept verbatim; the two-token fallback for `%%s`/`%%%`.
- **Non-goals to guard against scope creep**: F3 flags/width/precision; MessageFormat expansion; `%MODNAME`/`%Creative` JEI macros (left literal, not printf); changes to `VariableUnmasker`/`MaskedText`/`ScalingHeuristic`; new public types; `pom.xml` changes.
- **No rollback hazard**: reverting the change's commits restores the v1 pattern + the v1-pinned scenario together; all unmodified v1 tests remain green during the change and after revert (proposal rollback plan).
