# Specification: Extended Variable Masking (`masker-printf-v2`)

## Goal

Extend the masking behavior of `VariableMasker` in `com.lucalzt.mctranslator.domain.service` — as archived in the `domain-service` spec (change `domain-services`) — to mask the `%%` printf escape (F5, 1.90% of masked variables) and the extended printf conversions `%n %x %o %e %g %b %c %h %i %ld` (F4, 1.21%) as positional `__VAR_N__` tokens with byte-identical restoration, while NEVER treating a percent-encoded URL sequence `%[0-9A-Fa-f]{2}` as a conversion.

---

## Delta Overview (change vs. the archived `domain-service` spec)

This change is a strict delta over `openspec/specs/domain-service/spec.md`. It touches ONLY the masking behavior of `VariableMasker` (plus the round-trip guarantee it feeds); everything outside that behavior stays as archived. Requirement numbering follows the base spec: base requirements are cited as R1–R15, the new requirement is R16.

### Superseded content in the base spec

| Base element | Status under this change |
|---|---|
| "Resolved in Spec" — M3 printf regex coverage row (`%(?:\d+\$)?[sdf]`; `%%` and `%x/%o/...` as non-goals) | **Superseded** by the MODIFIED Requirement 6 below. |
| "Resolved in Spec" — M3 `%%s` adjacency row (documented v1 limitation) | **Closed for the observed real pattern**: `%s%%` (conversion + escape) is now a single masked token. The reverse adjacency `%%s` and the triple `%%%` remain edge cases, resolved by the natural sequential fallback pinned in Requirement 6. |
| Base Non-Goals — "Not masked by `VariableMasker`" items `%%` and conversions `%x %o %e %g %b %c %h %n` | **Removed from the non-goal set** (now in scope per the confirmed product decisions). The other items in that bullet (case variants, F3 flags/width/precision forms, `§` codes) stay non-goals. |

### Resolved in Spec (open items from the proposal)

| Open item | Resolution (binding) | Rationale |
|---|---|---|
| URL-guard precedence mechanism ("design phase pins the exact alternation order and the guard's precedence") | **Semantics pinned in this spec (R16):** `%` + exactly two hex digits is NEVER a conversion, escape, or variable, and wins over every printf branch — including when the hex pair could be read as a conversion char plus a width digit (`%e2`, `%b2`, `%c2` → left literal). The exact regex alternation ORDER (mechanism) is a design decision; R16's scenarios make the semantics testable regardless of mechanism. | The guard is a hard product decision (decision 4) backed by discovery #76: 20 false-positive "variables" in FTB Quests SNBT, 100% clickEvent URLs. |
| Merge-rule scope (decision 2 pins the observed `%s%%`) | **Generalized:** a conversion — positional or not, any of the v1 + F4 set — immediately followed by `%%` is masked as ONE token whose variable is the full original substring (e.g. `%s%%`, `%1$s%%`, `%d%%`). The merge consumes exactly ONE conversion + ONE `%%`; a second consecutive `%%` is an independent escape token. | Decision 2's wording is "conversion immediately followed by `%%`... incl. positional `%1$s%%`" — the general form is the natural reading; `%s%%` is the observed corpus instance (vanilla `"Progress: %s%%"`, `"+%s%% %s"`; apotheosis `"You have a %s%% chance to shoot a second arrow."`). |
| Escape-adjacency edge cases (`%%s` reverse adjacency, triple `%%%`) | **Natural sequential fallback:** `%%` is masked as its own escape token wherever the merge does not apply; leftover literal text stays literal; every tokenization round-trips byte-identical. No error, no over-merge. Pinned by scenarios. | Reverse adjacency occurs only in `translation.test.escape` (deliberately broken test string); the two-token fallback is acceptable per the proposal. |
| MessageFormat branch | Kept **verbatim** from v1 (`\{(\d+)(?:,[^}]*)?\}`); documented as NOT expandable. | `{0}` family = 0% of the corpus (explore F6); keeping it preserves v1 compatibility (non-goal: expansion). |

### Requirements NOT modified (remain in force exactly as archived)

- **R1** (Package Placement) — no package change; all work stays in `com.lucalzt.mctranslator.domain.service`.
- **R2** (Zero Framework Imports) — pattern rework is JDK-only (`java.util.regex`), no new imports.
- **R3** (JSpecify @NullMarked) — unchanged; no new public types.
- **R4** (Javadoc on All Public API) — unchanged as a requirement; the `VariableMasker` Javadoc is updated to describe the new pattern set as a natural consequence. No public type or member is added, so no new Javadoc obligations arise.
- **R5** (MaskedText Value Object) — unchanged: the VO contract (non-null/non-blank components, token-set invariant, defensive copy) is untouched. New variable content (`%%`, `%s%%`, `%x`) is non-blank and satisfies the invariant; `mask()` remains the only producer.
- **R8** (VariableUnmasker) — unchanged: restoration by token index with verbatim variables already yields byte-identical restoration for `%%`, merged tokens and F4 conversions; NO `%%` → `%` normalization is added anywhere in the pipeline. `VariableUnmasker` is expected to need no code change.
- **R9–R12** (ScalingHeuristic / ScalingDecision / GlossaryTermMatch / Word Counting) — untouched domain; masked tokens remain single words for the word-count contract.
- **R13** (Strict TDD — Red-First Unit Tests) — unchanged as a requirement; this change extends test coverage per its own scenarios (F4 per-conversion, F5 standalone/merged/byte-identical, URL guard) with the same red-first discipline and `mvnw.cmd clean verify` gate.
- **R14** (Pinned domain.model Inventory Untouched) — unchanged; no port or model type is touched.
- **R15** (Clean Verify Passes) — unchanged gate.

> **Note on R4/R5/R6/R7:** the orchestrator flagged R4–R7 as the masking cluster. R4 (Javadoc) and R5 (MaskedText) carry no normative change and are therefore kept in force as-is, listed above. The genuinely modified requirements are **R6 (VariableMasker)** and **R7 (Round-Trip Fidelity)**; the byte-identical restoration guarantee belongs in R7 (round-trip fidelity), not R5 (the VO).

### Requirements MODIFIED

- **R6 — VariableMasker**: pattern set, tokenization (escape-aware, F4 conversions, `%s%%` single-token merge, `%%` standalone) and the NOT-masked set.
- **R7 — Round-Trip Fidelity**: byte-identical restoration (no `%%` normalization) made explicit for the newly masked families.

### Requirements ADDED

- **R16 — URL Percent-Encoding Hard Guard**: `%[0-9A-Fa-f]{2}` is never masked, with hard precedence over F4 detection.

---

## MODIFIED Requirements

### Requirement 6: VariableMasker (MODIFIED)

**Delta vs. base:** the printf branch `%(?:\d+\$)?[sdf]` is replaced by an escape-aware printf branch that (a) keeps `%s`/`%d`/`%f` and the positional form `%N$conv` for `conv ∈ {s,d,f,n,x,o,e,g,b,c,h,i,ld}`; (b) masks the printf escape literal `%%` as a variable; (c) masks a conversion immediately followed by `%%` as ONE token whose variable is the full original substring; and (d) NEVER matches `%` + two hex digits (URL guard, R16). The literal-token branch `__VAR_(\d+)__` and the MessageFormat branch `\{(\d+)(?:,[^}]*)?\}` are kept verbatim. The NOT-masked set shrinks: `%%` and the F4 conversions are removed from it; case variants, F3 flags/width/precision forms, `%l` (the non-set two-character form), JSON/unbalanced braces, named braces, `§` codes and URL percent-encodings remain literal. The base v1 scenarios "Masks each pinned MessageFormat family", "First-occurrence order and no dedup", "Literal token is protected", "JSON braces are not masked" and "Rejects null and blank input" remain valid unchanged; "Masks each pinned printf family" stays valid (v1 inputs still produce v1 output); "Non-variable patterns are left literal" is REPLACED (its input `%%`/`%x` now mask).

`VariableMasker` MUST be a stateless domain service exposing `MaskedText mask(String text)`. It MUST reject a `null` argument (throws `NullPointerException`) and a blank argument — empty or whitespace-only (throws `IllegalArgumentException`).

Masking behavior (single precompiled `Pattern`, JDK-only; semantics binding, exact alternation order is a design decision):
- The variable-detection pattern covers, in this order of precedence: literal token `__VAR_(\d+)__` | escape-aware printf | MessageFormat `\{(\d+)(?:,[^}]*)?\}`. The escape-aware printf branch recognizes:
  - the v1 conversions `%s`, `%d`, `%f` and the F4 conversions `%n`, `%x`, `%o`, `%e`, `%g`, `%b`, `%c`, `%h`, `%i`, `%ld`, each with an optional positional index `%<positive-digit-index>$<conv>` (e.g. `%1$s`, `%2$x`, `%1$ld`). `%ld` is the ONLY two-character conversion in scope: a lone `%l` or `%l` followed by any other char (`%ls`) is NOT masked;
  - the printf escape literal `%%`, masked as a variable on its own when not part of a merge;
  - the single-token merge: a conversion (positional or not) immediately followed by `%%` is masked as ONE token whose variable is the full original substring (e.g. `%s%%`, `%1$s%%`, `%d%%`, `%x%%`). The merge consumes exactly ONE conversion and ONE `%%`; a second consecutive `%%` is an independent escape token;
  - it NEVER matches `%` followed by exactly two hex digits `[0-9A-Fa-f]{2}` — the hard URL guard (Requirement 16), which takes precedence over every printf branch.
- Every occurrence of any matched pattern is replaced by a positional token `__VAR_<k>__` where k is the occurrence index in **first-occurrence order** (0-based); the `variables` list of the returned `MaskedText` records each matched original text verbatim, one entry per occurrence (no dedup: two occurrences of `%s` produce two entries).
- A literal `__VAR_N__` substring in the source text is protected as a regular variable (matched by the first alternation branch), so no token leakage occurs: the masked output never contains a `__VAR_N__` token that does not correspond to a protected variable, and original literals are restored exactly.
- NOT masked (left literal in the output, round-trip identity): case variants (`%S`, `%D`, `%F`), printf flags/width/precision forms (`%10.2f`, `%-10s`, `%2$10d`, `%+d`, `%02d`), the non-set two-character form `%l` (and `%l<other>`), JSON braces and unbalanced braces (`{0`), non-digit named braces (`{p}`, `{player}`, `{buttonText}`), Minecraft `\u00a7` section-sign formatting codes (`§a`, `\u00a7a`), and URL percent-encoded sequences `%[0-9A-Fa-f]{2}` (Requirement 16).
- The MessageFormat branch is unchanged from v1: bare `{0}`, format variants `{0,number}`, `{0,date}`, `{0,number,integer}` and ChoiceFormat ranges `{0,choice,0#zero|1#one}` are masked. The branch is NOT expanded (documented non-goal: `{0}` family = 0% of the corpus, explore F6).

#### Scenario: Masks each pinned v1 printf family (kept from base)
- **Given** the texts `"HP: %s"`, `"Damage: %d"`, `"Ratio: %f"`, `"Hello %1$s"`, `"Cost %2$d"`, `"Slot %10$s"`
- **When** `mask` is invoked on each
- **Then** each variable occurrence is replaced by the next positional token (`__VAR_0__`, …) and the `variables` list holds the exact original substrings (`"%s"`, `"%d"`, `"%f"`, `"%1$s"`, `"%2$d"`, `"%10$s"`) — identical to v1.

#### Scenario: Masks `%n` as a variable
- **Given** the text `"Line: %n"`
- **When** `mask` is invoked
- **Then** the masked text is `"Line: __VAR_0__"` and `variables` equals `List.of("%n")`.

#### Scenario: Masks `%x` as a variable
- **Given** the text `"Hex: %x"`
- **When** `mask` is invoked
- **Then** the masked text is `"Hex: __VAR_0__"` and `variables` equals `List.of("%x")`.

#### Scenario: Masks `%o` as a variable
- **Given** the text `"Oct: %o"`
- **When** `mask` is invoked
- **Then** the masked text is `"Oct: __VAR_0__"` and `variables` equals `List.of("%o")`.

#### Scenario: Masks `%e` as a variable
- **Given** the text `"Sci: %e"`
- **When** `mask` is invoked
- **Then** the masked text is `"Sci: __VAR_0__"` and `variables` equals `List.of("%e")`.

#### Scenario: Masks `%g` as a variable
- **Given** the text `"Gen: %g"`
- **When** `mask` is invoked
- **Then** the masked text is `"Gen: __VAR_0__"` and `variables` equals `List.of("%g")`.

#### Scenario: Masks `%b` as a variable
- **Given** the text `"Flag: %b"`
- **When** `mask` is invoked
- **Then** the masked text is `"Flag: __VAR_0__"` and `variables` equals `List.of("%b")`.

#### Scenario: Masks `%c` as a variable
- **Given** the text `"Char: %c"`
- **When** `mask` is invoked
- **Then** the masked text is `"Char: __VAR_0__"` and `variables` equals `List.of("%c")`.

#### Scenario: Masks `%h` as a variable
- **Given** the text `"Hash: %h"`
- **When** `mask` is invoked
- **Then** the masked text is `"Hash: __VAR_0__"` and `variables` equals `List.of("%h")`.

#### Scenario: Masks `%i` as a variable
- **Given** the text `"Int: %i"`
- **When** `mask` is invoked
- **Then** the masked text is `"Int: __VAR_0__"` and `variables` equals `List.of("%i")`.

#### Scenario: Masks `%ld` as a variable
- **Given** the text `"Long: %ld"`
- **When** `mask` is invoked
- **Then** the masked text is `"Long: __VAR_0__"` and `variables` equals `List.of("%ld")`.

#### Scenario: Masks positional F4 conversions
- **Given** the text `"Cost %2$x and %1$ld"`
- **When** `mask` is invoked
- **Then** the masked text is `"Cost __VAR_0__ and __VAR_1__"` and `variables` equals `List.of("%2$x", "%1$ld")`.

#### Scenario: Masks the standalone printf escape
- **Given** the text `"100%% complete"`
- **When** `mask` is invoked
- **Then** the masked text is `"100__VAR_0__ complete"` and `variables` equals `List.of("%%")` — the escape is a variable on its own.

#### Scenario: Masks conversion + escape as ONE token (decision 2)
- **Given** the texts `"Progress: %s%%"` and `"+%s%% %s"` (the observed corpus patterns)
- **When** `mask` is invoked on each
- **Then** `"Progress: %s%%"` masks to `"Progress: __VAR_0__"` with `variables` equal to `List.of("%s%%")` — the conversion and the escape are ONE token — and `"+%s%% %s"` masks to `"+__VAR_0__ __VAR_1__"` with `variables` equal to `List.of("%s%%", "%s")`.

#### Scenario: Masks positional conversion + escape as ONE token
- **Given** the text `"Progress: %1$s%%"`
- **When** `mask` is invoked
- **Then** the masked text is `"Progress: __VAR_0__"` and `variables` equals `List.of("%1$s%%")`.

#### Scenario: Merge consumes exactly one conversion and one escape
- **Given** the text `"A %s%%%% B"` (conversion + escape + a second escape)
- **When** `mask` is invoked
- **Then** the masked text is `"A __VAR_0____VAR_1__ B"` and `variables` equals `List.of("%s%%", "%%")` — the second `%%` is an independent escape token.

#### Scenario: Escape-adjacency fallback round-trips byte-identical
- **Given** the texts `"100%%s"` (reverse adjacency, only ever in `translation.test.escape`), `"%%%"` and `"%s%%%%"`
- **When** `mask` is invoked
- **Then** `"100%%s"` masks to `"100__VAR_0__s"` with `variables` `List.of("%%")`; `"%%%"` masks to `"__VAR_0__%"` with `variables` `List.of("%%")`; `"%s%%%%"` masks to `"__VAR_0____VAR_1__"` with `variables` `List.of("%s%%", "%%")` — the natural sequential fallback applies and every tokenization round-trips byte-identical (Requirement 7).

#### Scenario: Masks each pinned MessageFormat family (kept from base)
- **Given** the texts `"Value {0}"`, `"Amount {1,number}"`, `"Day {0,date,full}"`, `"Pick {0,choice,0#zero|1#one}"`, `"N {12,number,integer}"`
- **When** `mask` is invoked on each
- **Then** each `{...}` occurrence is replaced by a positional token and the `variables` list holds the exact original substrings (e.g. `"{0,choice,0#zero|1#one}"`) — identical to v1; the branch is NOT expanded.

#### Scenario: Reduced non-variable set is left literal
- **Given** the text `"%S var, %10.2f wide, %-10s, %2$10d, %+d, %l, %ls, {p}, §a colored"`
- **When** `mask` is invoked
- **Then** the masked text is identical to the input (no tokens produced, `variables` is empty) — case variants, F3 flags/width/precision forms, the non-set two-character form `%l`, named braces and `§a` are not variables. (The v1 `%%`/`%x` entries of the base scenario are now masked; this scenario pins the REMAINING literal set.)

#### Scenario: First-occurrence order and no dedup (kept from base)
- **Given** the text `"A %s and %s and {0}"`
- **When** `mask` is invoked
- **Then** the masked text is `"A __VAR_0__ and __VAR_1__ and __VAR_2__"` and `variables` equals `List.of("%s", "%s", "{0}")` (two entries for the two `%s` occurrences).

#### Scenario: Literal token is protected, no leakage (kept from base)
- **Given** the text `"Use __VAR_3__ now"`
- **When** `mask` is invoked
- **Then** the masked text is `"Use __VAR_0__ now"` and `variables` equals `List.of("__VAR_3__")` — the literal is a protected variable, and every `__VAR_N__` in the output corresponds to one protected variable.

#### Scenario: JSON braces are not masked (kept from base)
- **Given** the text `"JSON { \"key\": \"value\" } and text {0}"`
- **When** `mask` is invoked
- **Then** the JSON braces are left literal, the `{0}` is replaced by `__VAR_0__`, and `variables` equals `List.of("{0}")`.

#### Scenario: Mixed v2 scan in one pass
- **Given** the text `"A %s {0} %1$s%% %n {1,number} %2$x"`
- **When** `mask` is invoked
- **Then** the masked text is `"A __VAR_0__ __VAR_1__ __VAR_2__ __VAR_3__ __VAR_4__ __VAR_5__"` and `variables` equals `List.of("%s", "{0}", "%1$s%%", "%n", "{1,number}", "%2$x")` — all branches (literal, v1 printf, merged printf, F4 printf, MessageFormat, positional F4) are handled in the same first-occurrence scan.

#### Scenario: Rejects null and blank input (kept from base)
- **Given** the `VariableMasker`
- **When** `mask` is invoked with `null` or with `""` / `"   "`
- **Then** a `NullPointerException` (null) or `IllegalArgumentException` (blank) is thrown.

---

### Requirement 7: Round-Trip Fidelity (MODIFIED)

**Delta vs. base:** the lossless round-trip contract is retained and extended with an explicit **byte-identical restoration** clause for the newly masked families: `%%`, merged `%s%%`-style tokens and every F4 conversion MUST restore exactly as found in the original — the pipeline performs NO `%%` → `%` normalization, at any stage, for any variable (decision 3). `VariableUnmasker` needs no change: restoration by token index with verbatim variables already satisfies this. The identity, literal-token and full-index scenarios are kept unchanged.

The mask→unmask round trip MUST be lossless for the protected variable set: applying the unmasker to a `MaskedText` whose translated text is the masked text itself restores the EXACT original input string, byte for byte. In particular:
- a `%%` variable restores exactly as `%%` — NEVER normalized to `%` (no printf-escape unescaping anywhere in the pipeline; `VariableUnmasker` performs no transformation beyond replacing tokens with their verbatim variable strings);
- a merged token restores its full original substring (e.g. `%s%%` restores as `%s%%`, `%1$s%%` as `%1$s%%`);
- every F4 conversion restores exactly as masked (`%x` as `%x`, `%ld` as `%ld`, …).
Full-index token matching MUST be used so that `__VAR_1__` never matches a prefix of `__VAR_10__`: the token regex is `__VAR_(\d+)__` with the index captured greedily as one number, and a token is only recognized when the trailing `__` is present (in `__VAR_10__` the `0` belongs to the index, so no `__VAR_1__` substring exists to match).

#### Scenario: Identity round trip (kept from base)
- **Given** the original text `"HP: %s, Cost: %1$d, Value: {0,number}"` and its `MaskedText` produced by `mask`
- **When** `unmask` is invoked with that `MaskedText` and the masked text as the translated text
- **Then** the restored text equals the exact original string and the result carries no discrepancies.

#### Scenario: `%%` restores byte-identical (decision 3)
- **Given** the original text `"100%% complete"` and its `MaskedText` produced by `mask` (masked text `"100__VAR_0__ complete"`, `variables` `List.of("%%")`)
- **When** `unmask` is invoked with that `MaskedText` and the masked text as the translated text
- **Then** the restored text equals `"100%% complete"` EXACTLY — never `"100% complete"`; no `%%` → `%` normalization occurs.

#### Scenario: Merged token restores byte-identical
- **Given** the original text `"Progress: %s%%"` and its `MaskedText` produced by `mask` (masked text `"Progress: __VAR_0__"`, `variables` `List.of("%s%%")`)
- **When** `unmask` is invoked with that `MaskedText` and the masked text
- **Then** the restored text equals `"Progress: %s%%"` exactly — the engine never sees a dangling `%%` after a token, and the merged variable restores as a whole.

#### Scenario: F4 conversions restore byte-identical
- **Given** the original text `"Hex: %x, Long: %ld, Sci: %e"` and its `MaskedText` produced by `mask`
- **When** `unmask` is invoked with that `MaskedText` and the masked text
- **Then** the restored text equals `"Hex: %x, Long: %ld, Sci: %e"` exactly, with no discrepancies.

#### Scenario: Literal-token round trip has no leakage (kept from base)
- **Given** the original text `"Use __VAR_3__"` and its `MaskedText` produced by `mask`
- **When** `unmask` is invoked with that `MaskedText` and the masked text
- **Then** the restored text equals `"Use __VAR_3__"` exactly (the original literal, not a masked token).

#### Scenario: Full-index tokens never partially match (kept from base)
- **Given** a `MaskedText` whose variables list has 11 entries (indices 0..10) and the translated text `"A __VAR_10__ B __VAR_1__"`
- **When** `unmask` is invoked
- **Then** `__VAR_10__` is restored from `variables.get(10)` and `__VAR_1__` from `variables.get(1)` — no partial/prefix restoration of `__VAR_1` from `__VAR_10`.

---

## ADDED Requirements

### Requirement 16: URL Percent-Encoding Hard Guard (ADDED)

`VariableMasker` MUST NOT treat a `%` followed by exactly two hex digits (`[0-9A-Fa-f]{2}`) as a conversion, an escape, or any masked variable. Such sequences are URL percent-encoding (e.g. `%20`, `%E2`, `%2C`, `%0A`) and MUST be left literal in the output. The guard takes HARD precedence over every printf branch:
- it wins over F4 conversion detection even when the two hex digits could be read as a conversion char followed by a width digit (`%e2`, `%b2`, `%c2` — the whole sequence stays literal). The F3 width family is a non-goal, so this causes no v1 regression;
- it does NOT interfere with the escape `%%` (`%` is not a hex digit) nor with positional conversions such as `%1$s` (the `$` breaks the two-consecutive-hex-digit run, so the guard does not match and the positional conversion masks normally).

#### Scenario: URL encodings produce no variables
- **Given** the texts `"Path %20F"`, `"Enc %E2"`, `"List %2C"` and `"Newline %0A"`
- **When** `mask` is invoked on each
- **Then** each masked text is identical to its input and `variables` is empty — `%20F`, `%E2`, `%2C` and `%0A` are never treated as conversions.

#### Scenario: Quest-SNBT-like strings stay literal
- **Given** the text `"Click here: https://example.com/api?text=Portable%20Fluid%20Storage%2C%20Silo"` (FTB Quests clickEvent style, discovery #76)
- **When** `mask` is invoked
- **Then** the masked text is identical to the input and `variables` is empty — no false-positive "variables" are produced from URL percent-encodings.

#### Scenario: Guard precedence over hex-adjacent conversions
- **Given** the texts `"Rate %e2"` and `"Rate %e"` (and, analogously, `"Flag %b2"` vs `"Flag %b"`)
- **When** `mask` is invoked on each
- **Then** `"Rate %e2"` is left literal with empty `variables` (the guard wins: `%` + hex `e2`), while `"Rate %e"` masks to `"Rate __VAR_0__"` with `variables` `List.of("%e")` (the standalone F4 conversion is masked) — the guard's precedence over hex-adjacent conversion text is pinned.

#### Scenario: Guard does not affect the escape
- **Given** the text `"100%% done"`
- **When** `mask` is invoked
- **Then** the masked text is `"100__VAR_0__ done"` and `variables` equals `List.of("%%")` — the guard leaves the printf escape untouched (`%` is not a hex digit).

#### Scenario: Guard does not affect positional conversions
- **Given** the text `"Hello %1$s"`
- **When** `mask` is invoked
- **Then** the masked text is `"Hello __VAR_0__"` and `variables` equals `List.of("%1$s")` — the guard requires TWO consecutive hex digits after `%`; the `$` breaks the run, so the positional conversion masks normally (F2 regression safety).

---

## Non-Goals (delta update)

- **REMOVED from the base non-goals (now in scope):** `%%`; printf conversions `%x %o %e %g %b %c %h %n` (and the F4 additions `%i`, `%ld`).
- **Kept from the base non-goals:** case variants `%S`/`%D`/`%F`; printf flags/width/precision forms (`%.1f`, `%10.2f`, `%-10s`, `%+d`, `%2$10d`) — 0.05% (3 occurrences, all the deliberately-broken `translation.test.invalid2` string) + collision risk with URL-encoding; Minecraft `\u00a7` section-sign formatting codes (`§a`, `\u00a7a`) — 7,482 cases, confirmed non-variables.
- **Confirmed non-goals with explore evidence:**
  - **F7 named braces** `{p}` / `{player}` / `{buttonText}` — 0.62% (35 cases), only help meta-text and key references (`{key.ftbquests.*}`), never in quest/lore text.
  - **PlaceholderAPI `%name%`** — 0% (the 84 raw candidates were `%s%` from `%s%%` — false positives).
  - **`${...}`** — 0.18% (10 cases, Twilight Forest keybinds only).
  - **`<...>`** — 0.25% (14 cases, command syntax in help text).
  - **JSON text components** (`"translate"` / `"with"`) — 0 occurrences in quest JSON/SNBT.
  - **JEI uppercase macros** `%MODNAME`, `%Creative` — 2 occurrences; neither printf nor PlaceholderAPI; left literal (documented in the F4 evidence caveat; they are NOT part of the F4 conversion set).
  - **MessageFormat expansion** — the `{0}` branch is kept verbatim and NOT expanded (0% corpus frequency, explore F6).
  - **Reverse adjacency `%%s` / triple `%%%`** — no special handling beyond the natural sequential fallback (only `translation.test.escape` contains them).
- **Unchanged from the base non-goals:** application-layer orchestration, engine handoff/configuration, glossary/cache/ports integration, `domain.model` inventory, infrastructure (Spring Shell, SQLite, logging), variable deduplication, unmasker failure modes.

---

## Risks & Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| `%n` concentration (66/69 F4 corpus candidates in a single BetterQuesting help file) | Corpus skew — F4 mostly exercises one pattern | Set is small and cheap; per-conversion scenarios in R6; the caveat is documented as evidence (proposal decision 1) |
| URL guard eats hex-adjacent conversion text (`%e2`, `%b2`, `%c2`) | A legit conversion adjacent to hex digits is left literal | F3 width forms are non-goals, so no v1 regression; guard precedence pinned in R16 with dedicated scenarios and in the design |
| Escape-merge edge cases (`%%s` reverse adjacency, triple `%%%`, `%s%%%%`) | Ambiguous tokenization | Reverse adjacency does not occur in real content (only `translation.test.escape`); the natural sequential fallback is pinned in R6 with byte-identical round-trip scenarios |
| Behavioral change vs v1 (`%s%%` → 1 token; `%%`/`%x` now masked) | Existing v1 R6 scenarios pinned the old left-literal behavior | Those scenarios are updated in this change (R6 delta); ALL other v1 tests (token no-leakage, full-index matching, MessageFormat, round-trip fidelity, best-effort unmask discrepancies) remain untouched as the regression safety net; revert restores pattern + scenarios together (same commits, proposal rollback plan) |
| Merge-rule generalization beyond the observed `%s%%` instance | Broader tokenization than the corpus proves | Consistent with decision 2 wording ("conversion immediately followed by `%%`... incl. positional `%1$s%%`"); flagged in "Resolved in Spec" for design awareness; all merge forms round-trip byte-identical |
| Guard vs positional interaction (`%1$s`, `%2$x`) | Positional F2 family accidentally eaten by the guard | Guard requires TWO consecutive hex digits; `$` breaks the run — pinned by the R16 positional scenario |
| `%ld` vs `%l` ambiguity (two-character conversion) | `%l`/`%ls` wrongly masked | Only `%ld` is in scope; `%l` alone and `%l<other>` are pinned literal in R6 with a scenario |
| Corpus limits (en_us only, 2 modpacks, version mix 1.7.10–1.21.4) | Extrapolation risk to other locales/packs | Documented in the explore artifact (#75) and carried into this spec's non-goals/evidence |
| Review workload (≈ 4–6 files: 1–2 main + 2–3 test classes) | 400-line review budget | Tasks phase MUST forecast; likely a single PR or 2 slices (masking; tests/gates) per the proposal |

---

## Traceability to the Proposal

| Proposal element | Spec requirement(s) |
|---|---|
| Purpose — F5 `%%`, F4 extended conversions, URL guard | R6 (MODIFIED), R7 (MODIFIED), R16 (ADDED) |
| Approach 1 — rework the `VariableMasker` alternation in place (escape-aware printf, merge, guard) | R6, R16 |
| Approach 2 — `VariableUnmasker` unchanged, byte-identical restoration falls out of verbatim variables | R7 (MODIFIED), R8 (NOT modified) |
| Approach 3 — update the v1 R6 scenarios that pinned the old left-literal behavior | R6 delta note, R7 delta note |
| Approach 4 — red-first JUnit 6 + AssertJ tests, `mvnw.cmd clean verify` gate | R13 (NOT modified), R6/R7/R16 scenarios, R15 |
| Decision 1 — F4 full set (`%n %x %o %e %g %b %c %h %i %ld`) + `%MODNAME`/`%Creative` caveat | R6 (per-conversion scenarios), Risks, Non-Goals |
| Decision 2 — `%s%%` as ONE token, incl. positional `%1$s%%` | R6 (merge scenarios), R7 (merged-token byte-identical) |
| Decision 3 — byte-identical restoration, `%%` never normalized to `%` | R7 (MODIFIED) |
| Decision 4 — URL guard `%[0-9A-Fa-f]{2}` is a hard requirement | R16 (ADDED) |
| Decision 5 — MessageFormat `{0}` unchanged, documented as not expanded | R6 (MessageFormat scenario), Non-Goals |
| Acceptance criteria 1 (verify gate), 2 (v1 compat), 3 (F5), 4 (byte-identical), 5 (F4), 6 (URL guard), 7 (MessageFormat), 8 (domain purity) | R15, R6 scenarios, R7 scenarios, R16 scenarios, R2/R3/R14 (NOT modified) |
| Non-goals (F3, F7, PAPI, `${...}`, `<...>`, JSON components, `§`/`&`, JEI macros) | Non-Goals (delta update) |
| Risks & limitations table | Risks & Mitigations (this spec) |
| Rollback plan — pattern + v1-pinned scenarios restored together | R6 delta note, Risks & Mitigations |
