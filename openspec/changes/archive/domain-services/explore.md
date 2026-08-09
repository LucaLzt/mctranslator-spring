# Exploration: Domain Services (`domain-services`)

> Status: exploration analysis — READ-ONLY. No code, no proposal/spec/design yet.
> Change goal: pure domain services for the translation pipeline, deferred from `domain-models` —
> `VariableMasker` / `VariableUnmasker` (protect code variables with `__VAR_N__` tokens before
> translation, restore after) and `ScalingHeuristic` (6-level precedence table deciding FAST vs
> PRECISE per key).
> Source of truth: `docs/architecture/implementation-strategy.md` (§2, §3, §5, §6),
> `docs/architecture/development-standards.md` (§2, §3, §5, §6), `docs/architecture/tech-stack.md`
> (§2.1), `openspec/config.yaml`, the archived `domain-models` change (proposal/design/tasks/spec —
> deferred-artifact notes), the live `domain-output-ports` spec, the actual sources under
> `src/main/java/com/lucalzt/mctranslator/domain/`, skills `hexagonal-architecture` and
> `domain-driven-design`.

---

## 1. Existing contracts (verified against source, not memory)

### 1.1 Already exists — `domain.model` (implemented + tested, `domain-models` change)

| Type | Contract (as built) |
|---|---|
| `TranslationKey` | `record TranslationKey(JsonPath path, String originalText, LanguageCode targetLanguage, ModpackId modpack)`; rejects null components (NPE) and blank `originalText` (IAE); identity = all 4 components. |
| `JsonPath` | `record JsonPath(String value)`; rejects null/blank/leading-dot/trailing-dot/consecutive-dots; accessor `value()`; **`boolean startsWith(String... segments)`** — case-sensitive leading-segment prefix match; non-null/non-blank segment guards, empty varargs → `true`. Wildcard `*` in docs is shorthand for "and everything below"; wildcards are never part of a path value. |
| `GlossaryEntry` | `record GlossaryEntry(String term, String translation, GlossaryEntryClassification classification)`; rejects nulls and blank term/translation. |
| `GlossaryEntryClassification` | enum exactly `AMBIGUOUS`, `LORE`, `PLAIN` — Javadoc says it drives heuristic rules 2 and 5. |
| `TranslationEngineType` | enum exactly `FAST`, `PRECISE` — Javadoc: "the `ScalingHeuristic` decision output". |
| `TranslationStatus` | enum exactly `CACHE_HIT`, `TRANSLATED_FAST`, `TRANSLATED_PRECISE`, `DEGRADED_TO_FAST`, `FALLBACK_TO_ORIGINAL`. |
| `TranslationResult` | `record TranslationResult(TranslationKey key, String translatedText, TranslationStatus status, @Nullable TranslationEngineType engine, @Nullable String warning, Duration duration)`; rejects null key/translatedText/status/duration and negative duration; engine/warning are the package's only `@Nullable`. |
| `LanguageCode` | `record LanguageCode(String value)`; non-null, non-blank; no normalization. |
| `ModpackId` | `record ModpackId(String name, String version)`; non-null, non-blank components. |
| `package-info.java` | `@NullMarked` (JSpecify 1.0.1, compile scope in `pom.xml`). |

The `domain.model` spec pins an **exact 9-type public inventory** (R3) and greps guarantee
`MaskedText`, `GlossaryTermMatch`, `CacheKey` are absent there. Adding a VO to `domain.model`
would therefore require a MODIFIED requirement against the archived `domain-models` spec — a
real cross-change cost to weigh when deciding placement (see §2.1 / open questions M1, H3).

### 1.2 Already exists — `domain.port.out` (implemented + tested, `domain-output-ports` change)

| Port | Contract (as built) |
|---|---|
| `GlossaryPort` | `List<GlossaryEntry> getTerms(ModpackId modpackId, LanguageCode sourceLang, LanguageCode targetLang)`; `@NullMarked`; pure interface. |
| `TranslationEnginePort` | `TranslationResult translate(TranslationKey key, LanguageCode sourceLang, LanguageCode targetLang, TranslationEngineType engineType)`; `@NullMarked`. |
| `TranslationCachePort` | `Optional<TranslationResult> find(TranslationKey key, LanguageCode sourceLang, LanguageCode targetLang)`; `void save(TranslationKey key, LanguageCode sourceLang, LanguageCode targetLang, TranslationResult result)`; `@NullMarked`. |
| `package-info.java` | `@NullMarked`. |

### 1.3 Does NOT exist yet (must be created by this change)

- Package `com.lucalzt.mctranslator.domain.service` — **absent** (verified by glob; `domain/` has only `model/` and `port/out/`).
- `VariableMasker`, `VariableUnmasker`, `ScalingHeuristic` — no sources anywhere in `src/`.
- `MaskedText`, `GlossaryTermMatch` — absent; both are explicitly deferred **to this change** by the `domain-models` proposal ("Deferred VO Rationale"): `MaskedText` is "the artifact of `VariableMasker`/`Unmasker`"; `GlossaryTermMatch` is "the output of `ScalingHeuristic` rule 2/5 matching".
- `application/` layer (`GlossaryAwareTranslator` etc.) — absent; that is a later change (non-goal here).

### 1.4 What the docs pin for these services (verbatim basis)

- `implementation-strategy.md` §2: `domain/service` holds `ScalingHeuristic`, `VariableMasker / VariableUnmasker (regex __VAR_N__)`.
- §3: "`ScalingHeuristic` recibe el `TranslationKey` (path + texto) y el glosario, y devuelve el motor sugerido" — input: path + text + glossary; output: suggested engine. Rules evaluated **in precedence order, first match wins** ("la primera que matchea decide").
- §5 flow: `1. cache → 2. Regex Masker → 3. ScalingHeuristic (flag preciso + path + longitud + glosario) → 4a/4b engine → 5. Regex Unmasker → 6. persist`. **The heuristic runs AFTER masking** — the text it sees is the masked text.
- §3 notes: (a) precise-flag off (v1 default) → everything goes fast, keys that would have gone precise are logged `[WARN]`; (b) model absent on disk → treated as engine unavailable → degrade to fast with `[WARN]`; (c) the table is a starting point, adjusted empirically later.
- `tech-stack.md` §2.1: NLLB is a pure seq2seq model, **not promptable**; it "solo cubre el problema de variables de código (`%s`, `{0}`) vía enmascarado por regex en el pipeline" — i.e. the only documented variable families are `%s` and `{0}`.
- `development-standards.md` §2/§3/§5/§6: domain is pure Java, zero Spring/JPA; VOs as records with compact-constructor validation; typed VOs; `@NullMarked` packages with `package-info.java`; Javadoc on all public API; domain services pure and testable in isolation with JUnit 6 + AssertJ (unit, no Spring context).

---

## 2. `VariableMasker` / `VariableUnmasker` design space

### 2.1 Round-trip contract: String-in/String-out vs `MaskedText` VO

The mask→unmask round trip cannot be a bare String→String function: the unmasker needs the
**mapping token → original variable** that was created at mask time. Two families of options:

- **Option A — `MaskedText` VO (carries the round-trip state).** A domain VO holding the masked
  string plus the ordered list of original variables (`__VAR_N__` ↔ `variables.get(N)`). The
  deferred note in the `domain-models` proposal describes exactly this ("masked string + the
  original-variable mapping") and assigns `MaskedText` to this change. API sketch:
  `VariableMasker.mask(String text) → MaskedText`; `VariableUnmasker.unmask(MaskedText mask, String translatedText) → String`.
  The VO gives an immutable, validated, testable contract; the unmasker cannot be misused
  (it has no way to invent a mapping).
- **Option B — stateless functions + re-derived mapping.** `mask(String) → String` plus
  `unmask(String translated, String original)` re-scanning `original` with the same regex to
  rebuild the mapping. Simpler signatures but duplicates the regex logic in two places, and the
  mapping reconstruction can drift from what was actually masked (e.g. if masking ever applies
  dedup or escaping, or if the pattern set changes between versions). Fragile; not recommended.
- **Option C — mapping as a standalone parameter type** (e.g. `List<String>` passed to unmask
  alongside the masked string). Equivalent to A with a weaker contract (raw `List<String>`
  instead of a typed VO, no invariant protection). A is the DDD-shaped version of this.

**Assessment: Option A.** The round-trip state is a real domain artifact the pipeline must
carry between step 2 and step 5 (and the engine call), the deferred note names `MaskedText` as
belonging to this change, and `domain-driven-design` favors typed VOs over raw collections.

**Placement open point (M1):** `domain.model` is pinned to an exact 9-type inventory by the
archived `domain-models` spec (R3) — adding `MaskedText`/`GlossaryTermMatch` there would need a
MODIFIED requirement across changes. The deferred note says these VOs "belong to the
domain-services change", not which package. Keeping them in `domain.service` (beside the
services that produce them) avoids touching the pinned inventory and keeps `domain.model` as
"pipeline data types" vs `domain.service` as "pipeline behavior + its private artifacts".
Recommendation: `domain.service`, unless the user prefers `domain.model`.

### 2.2 Token scheme: positional `__VAR_N__` vs named tokens

The docs pin the format **`__VAR_N__`** (N numeric). Two readings:

- **Positional (recommended):** `__VAR_0__`, `__VAR_1__`, … assigned in first-occurrence order;
  token index = position in the ordered variable list. Deterministic, order-preserving,
  trivially reversible (`__VAR_(\d+)__` → `variables.get(index)`). Reordered translations are
  handled naturally because each token carries its own index.
- **Named:** token derived from the variable content (e.g. hash/encode). Dedupes identical
  variables but introduces collision/length concerns and non-obvious token text; nothing in the
  docs suggests it. Not recommended; the docs' `__VAR_N__` is positional by appearance.

**Gotcha to pin in spec:** the unmask regex must match the **full token** — `__VAR_(\d+)__`
with the index greedily captured as one number, so `__VAR_10__` never matches the prefix of
`__VAR_100__` or partially restores `__VAR_1` + `0__`. Anchor the pattern (e.g.
`__VAR_(\d+)__` with the regex engine's longest-match behavior on `\d+`, or explicit
word-boundary/lookahead) and cover it with tests.

### 2.3 Variable pattern families — documented vs candidate (open decision M3)

The docs mention only `%s` and `{0}` (`tech-stack.md` §2.1). A conservative,
**documented-first pattern set** to propose (must be validated by the user in the proposal
round — NOT decided here):

- **printf-style (`String.format` / Java ResourceBundle `%s` conventions):** `%s`, `%d`, `%f`
  (the families the docs literally cite plus the two most common siblings), and positional
  `%1$s`, `%2$d` (`%<arg>$<conv>` form — common in mod strings that localize argument order).
  Regex sketch: `%(?:\d+\$)?[a-zA-Z]` — i.e. optional positional index, one conversion
  character — but **which conversion characters** (`s,d,f,x,o,e,g,b,c,h,n,%`) to accept is
  open; a conservative v1 could restrict to `[sdf]` + `%1$s`-style indices.
- **MessageFormat-style:** `{0}`, `{1}`, and `{0,number}` / `{0,date}` style (optionally
  `{0,choice,...}`). Regex sketch: `\{(\d+)(?:,[^}]*)?\}`. The docs cite only the bare `{0}`;
  the `,type[,style]` suffix variant is a candidate extension.
- **Not pinned anywhere in the docs** and therefore open decisions, NOT to be decided here:
  `%%` (escaped percent), case variants (`%S`), width/precision (`%10.2f`), hex `%x`,
  `{0,choice}` ranges, `\u00a7`-style formatting codes (Minecraft chat color codes) — the last
  one is plausibly part of Minecraft text but is absent from the docs; flag for the proposal.

**"`%s` occurrences inside already-translated placeholders":** in the pipeline the masker runs
on the *original* text only; the engine output is only unmasked, never re-masked, so
double-masking inside translated placeholders is not a first-order risk. The real edge cases
are: (a) the translated text still contains literal `%s`/`{0}` (engine kept them) — unmask
must leave them alone (they are not `__VAR_N__` tokens); (b) the original text itself contains
a literal `__VAR_N__`-looking substring — token collision (open question M5).

### 2.4 Unmasker edge behavior (open questions M6–M8)

- **Missing variable in translated text** (engine dropped the token): restore nothing? append
  the original variable at the end? produce a warning (but `VariableMasker`/`Unmasker` are
  domain services with no logging — an outcome flag would have to travel back)? Open.
- **Extra/unmatched tokens** in translated text (engine hallucinated `__VAR_9__` with no
  mapping, or the translation legitimately contains a `__VAR_N__`-shaped string): leave
  literally? replace with empty string? throw? Open.
- **Reordered variables:** positional tokens make this natural (restore by token index, not by
  unmask-call order); duplicates (engine repeated a token twice) restore the same original both
  times. Confirm this contract in the proposal; cover with tests.
- **Token format partial matches** (§2.2 gotcha) and **masking idempotency** (masking an
  already-masked string must not re-mask tokens) — pin behavior.

---

## 3. `ScalingHeuristic` design space

### 3.1 Input shape (open questions H1, H2)

Docs §3 pin the *content* of the input — path + text + glossary — and the output (suggested
engine), but not the parameter shape:

- **`TranslationKey` vs (path, text):** the docs phrase "recibe el `TranslationKey` (path +
  texto)". But note the **mask interaction**: the flow is mask (step 2) → heuristic (step 3),
  so the text the heuristic must see is the **masked text**, not `TranslationKey.originalText()`.
  Passing the whole `TranslationKey` would either require the caller to build a key whose
  `originalText` is the masked string (semantic overload of `originalText`) or pass a key whose
  text differs from what rule 3/5 will count. A focused `suggest(JsonPath path, String text,
  List<GlossaryEntry> glossary)` (where `text` = masked text, possibly as `MaskedText.maskedText()`)
  is simpler, pure, and avoids the key-copy awkwardness. Open decision, leaning to
  `(JsonPath path, String maskedText, List<GlossaryEntry>)`; the proposal should also state
  whether it accepts `MaskedText` directly (ties the two services' contracts together).
- **Glossary:** `List<GlossaryEntry>` parameter (pure; the application layer fetches it via
  `GlossaryPort.getTerms(...)`) vs depending on `GlossaryPort` inside the heuristic. The docs
  say "recibe … el glosario" (receives the glossary — a value), and `hexagonal-architecture`
  keeps domain services framework-free and testable in isolation. **Recommendation:
  `List<GlossaryEntry>` parameter.** `GlossaryPort` stays an application-consumed port; no new
  port methods needed. Empty list → rules 2/5 vacuously don't match → behavior falls to rules
  3/4/6 (confirm in spec).

### 3.2 The 6 rules — exact precedence from `implementation-strategy.md` §3

Evaluated **in order; the first rule that matches decides** (this resolves conflicts, e.g.
short `quest.description.*` text → rule 1 wins → PRECISE). `JsonPath.startsWith(String...)`
is the ready-made matcher for rules 1/4.

| Order | Condition (docs §3, translated) | Engine | Mapping to existing API |
|---|---|---|---|
| 1 | Path matches `quest.description.*`, `lore.*`, `advancement.*` | PRECISE | `path.startsWith("quest","description") \|\| path.startsWith("lore") \|\| path.startsWith("advancement")` |
| 2 | Text contains ≥1 glossary term classified `AMBIGUOUS` | PRECISE | glossary match over the (masked) text |
| 3 | Text **> 30 words** (strictly greater) | PRECISE | word count on masked text |
| 4 | Path matches `item.*`, `block.*`, `entity.*`, `gui.*` | FAST | `path.startsWith("item") \|\| startsWith("block") \|\| startsWith("entity") \|\| startsWith("gui")` |
| 5 | Text **≤ 8 words** AND **no `LORE`-classified glossary term detected** in the text | FAST | word count + glossary match (LORE only) |
| 6 | Default — any other case | FAST | fall-through |

Boundary semantics to pin (open question H6): `> 30` is strict — a text of exactly 30 words
does **not** match rule 3 and falls through; `≤ 8` is inclusive — exactly 8 words qualifies for
rule 5. Word counting: whitespace split (`text.split("\\s+")` — or equivalent) on the **masked**
text; a `__VAR_N__` token counts as one word. (Punctuation-only tokens count as words; whether
that matters is minor — pin in spec.)

**Observation — rule 5's LORE check is behaviorally inert in the exact table (open question
H5).** If rule 5's condition fails *only* because a LORE term was detected (text still ≤ 8
words), control falls to rule 6 → FAST anyway. So with the table exactly as documented, the
"no LORE terms" clause never changes the outcome. This is either (a) intended (the clause is a
placeholder for future empirical tuning — docs §3 says the table "se ajusta empíricamente"), or
(b) the sign of a missing rule ("LORE detected → PRECISE"?) that the docs do not contain.
**Do not change the table unilaterally** — flag for the proposal/user as a deliberate decision.

### 3.3 Output: engine only vs WHY (open question H3)

- **Return `TranslationEngineType` only** — minimal; the caller learns FAST/PRECISE but not
  which rule fired or which terms matched.
- **Return a decision record** (e.g. `ScalingDecision(TranslationEngineType engine, int matchedRule, List<GlossaryTermMatch> matches)` where `GlossaryTermMatch` is the deferred artifact from the `domain-models` proposal: "output of `ScalingHeuristic` rule 2/5 matching"). This supports:
  - the **flag-off `[WARN]` diagnostics**: docs §3 note says keys that *would* have gone precise
    are logged `[WARN]` — the caller can only produce that log if the heuristic tells it the
    unconstrained suggestion (see H4);
  - metrics/debugging (docs §7: % keys per engine, fallback counts; §8: "heurística deriva mal"
    analysis).

**Recommendation:** expose a decision record carrying the matched rule and the glossary-term
matches; the bare `TranslationEngineType` is a trivial projection of it. The deferred
`GlossaryTermMatch` name strongly suggests this is the intended shape.

### 3.4 "Precise-flag off → everything FAST with [WARN]" (open question H4)

Docs §3 note: with the flag off (v1 default) the heuristic result is overridden — everything
goes FAST — and the keys that would have gone PRECISE are logged `[WARN]` for manual review.
Note the subtlety: producing the `[WARN]` *requires knowing* which keys would have gone
PRECISE, so the heuristic still has to be evaluated (or the decision record must expose the
unconstrained suggestion). Placement options:

- **(a) Heuristic concern:** `suggest(..., boolean preciseEnabled)` and short-circuit to FAST
  when off. Then the WARN info is lost inside the service unless it also returns the
  unconstrained engine — and the domain service now knows about an operational flag.
- **(b) Application-layer concern (recommended):** the heuristic always evaluates and returns
  the decision; `GlossaryAwareTranslator` (application, later change) applies the flag override
  (PRECISE decision + flag off → translate FAST + `[WARN]`) and the model-absent degrade (docs
  §3 note b) with the same WARN pattern. This keeps the domain service pure, matches
  `hexagonal-architecture` ("services orchestrate; domain decides"), and matches the change
  boundary — the flag is configuration, i.e. application/infrastructure territory.

Flag as an open decision; recommended (b), which makes the flag-off behavior and `[WARN]`
logging **non-goals of this change** (they land with `GlossaryAwareTranslator`). The decision
record (§3.3) is what makes (b) possible without losing the would-be-engine info.

### 3.5 Glossary-term matching semantics (open question H7)

Rule 2 needs "text contains ≥1 AMBIGUOUS term"; rule 5 needs "no LORE term detected". Matching
mode is not pinned: `String.contains` (substring) vs word-boundary match; case-sensitive vs
case-folded; multi-word terms; overlap with masked tokens (a masked `__VAR_N__` cannot match a
glossary term — good). This shapes `GlossaryTermMatch` (which fields: term, classification,
found-in-text?) and must be pinned before the spec ("cero ambigüedad" rule). Open.

### 3.6 Other heuristic decisions to pin (minor, open)

- Stateless service vs instance; thresholds (30 / 8) as constants now, configurable later (docs:
  "punto de partida", empirically adjusted) — open but low risk.
- Whether `ScalingHeuristic` is a class with methods, a record of rules, or static functions —
  naming/API style, flag for proposal.

---

## 4. Open questions (for the proposal phase / proposal question round)

Every decision point NOT fully pinned by the docs, phrased for resolution. **Do not hide these;
the proposal/spec cannot be written unambiguously without answers** (`openspec/config.yaml`:
"cero ambigüedad en contratos de puertos y definición de Value Objects inmutables").

### Masking
- **M1 — Placement of `MaskedText` / `GlossaryTermMatch`:** `domain.service` (keeps the pinned
  `domain.model` 9-type inventory untouched) vs `domain.model` (would require MODIFIED
  requirements against the archived `domain-models` spec). Recommended: `domain.service`.
- **M2 — Token format:** confirm positional `__VAR_0__`, `__VAR_1__`, … in first-occurrence
  order (docs pin the `__VAR_N__` shape; the index semantics are ours to fix). Reject named/hash
  tokens? Confirm exact-token matching (full-index) is a spec requirement.
- **M3 — Variable pattern families:** the docs pin only `%s` and `{0}`. Approve the
  conservative documented-first set (`%s`, `%d`, `%f`, positional `%1$s`-style; `{0}` and
  `{0,type}`-style)? Which printf conversions (just `[sdf]` vs more)? Escaped `%%`? Case
  variants `%S`? Width/precision (`%10.2f`)? Minecraft `\u00a7` color codes — in or out? (Docs
  silent on all of these; candidate set proposed in §2.3.)
- **M4 — Masking of already-token-like text:** original text containing a literal `__VAR_N__`
  substring — escape it, skip masking it, or declare unsupported (documented limitation)?
- **M5 — Unmasker, missing variable** (engine dropped a token): leave as-is / restore original
  anyway / flag? What does the domain service return when it cannot fully restore — a warning
  channel (result type change) or silent behavior?
- **M6 — Unmasker, extra/unmatched tokens** in translated text (hallucinated `__VAR_9__`, or
  translation legitimately contains a token-shaped string): leave literal / replace with empty /
  error?
- **M7 — Reordered/duplicate tokens:** confirm restore-by-token-index semantics (reordering and
  duplication handled naturally); is there any case where the unmasker must error?
- **M8 — API shape / naming:** one class `VariableMasker` with `mask`+`unmask`, or two classes
  `VariableMasker` and `VariableUnmasker` (docs name both)? Is `unmask(MaskedText, String) →
  String` the right signature, or should unmask take the variable list separately?

### Heuristic
- **H1 — Parameter shape:** whole `TranslationKey` (docs-literal, but conflicts with the
  masked-text interaction) vs `(JsonPath path, String maskedText, List<GlossaryEntry>)` vs
  `(JsonPath path, MaskedText masked, List<GlossaryEntry>)`. Recommended: explicit path + masked
  text (+ terms); the proposal must state what text rules 3/5 count (masked).
- **H2 — Glossary input:** `List<GlossaryEntry>` parameter (recommended) vs `GlossaryPort`
  dependency inside the heuristic. Empty-glossary behavior (rules 2/5 vacuous → fall through).
- **H3 — Output shape:** `TranslationEngineType` only vs decision record
  (`engine` + matched rule + `List<GlossaryTermMatch>`). Recommended: decision record (deferred
  `GlossaryTermMatch` naming + flag-off `[WARN]` diagnostics need it).
- **H4 — Flag-off behavior placement:** boolean `preciseEnabled` parameter on the heuristic vs
  application-layer override in the future `GlossaryAwareTranslator`. Recommended: application
  layer → flag-off behavior + `[WARN]` logging are **non-goals of this change**. Same question
  for the "model absent on disk → degrade to FAST + [WARN]" note.
- **H5 — Rule 5 LORE clause:** the "no LORE terms detected" precondition is behaviorally inert
  in the exact table (failure falls to rule 6 → FAST anyway). Confirm it is a placeholder for
  empirical tuning, or whether a "LORE detected → PRECISE" outcome is intended (which the docs
  do NOT contain). The table itself must not be changed unilaterally.
- **H6 — Word-count semantics:** whitespace split on masked text; `__VAR_N__` = 1 word; strict
  `> 30` (exactly 30 → not rule 3) and inclusive `≤ 8` (exactly 8 → rule 5 candidate). Confirm
  punctuation-only tokens count as words.
- **H7 — Glossary matching mode:** `contains` vs word-boundary; case-sensitive vs folded;
  multi-word terms; what `GlossaryTermMatch` carries (term, classification, index/occurrence?).
- **H8 — Threshold configuration:** constants in code now vs injectable/`@ConfigurationProperties`
  later (docs: empirical tuning pending a dataset). Also stateless vs instance shape.

### Scope / cross-change
- **S1 — Package name:** `domain.service` (docs and development-standards both write
  `service` singular) — confirm; new `package-info.java` with `@NullMarked` + Javadoc required.
- **S2 — Engine masked-text interface:** `TranslationEnginePort.translate` takes a
  `TranslationKey` whose `originalText` is currently *unmasked*. This change's services produce
  `MaskedText`; how the translator feeds masked text to the engine (key copy with masked text vs
  a future contract change to the port) is application-layer — confirm it is out of scope and
  recorded as a constraint for the translator change.
- **S3 — Review budget:** expected scope ≈ 2–3 services + 2 deferred VOs + tests
  (`MaskedText`, `GlossaryTermMatch`, `VariableMasker`, `VariableUnmasker`, `ScalingHeuristic`,
  decision record) — likely 10–13 files; the tasks phase must forecast chained PRs if the
  400-line budget applies.

---

## 5. Approach comparison

### 5.1 VariableMasker / Unmasker

| Option | Shape | Tradeoffs |
|---|---|---|
| **A — `MaskedText` VO round trip (recommended)** | `mask(String) → MaskedText(maskedText, variables)`; `unmask(MaskedText, String translated) → String` | Matches the deferred-note definition; typed, immutable, testable; unmask cannot misuse the mapping; slightly more code than B. |
| B — String-in/String-out + re-derived mapping | `mask(String) → String`; `unmask(String translated, String original)` | Fewest types, but duplicates regex logic, fragile to pattern-set drift, weaker contract. |
| C — Bare mapping parameter | `mask(String) → String` + `List<String>` passed to unmask | A without the VO; loses invariant protection (e.g. list length vs token count). |

### 5.2 ScalingHeuristic

| Option | Shape | Tradeoffs |
|---|---|---|
| **B1 — Pure function + decision record (recommended)** | `suggest(JsonPath path, String maskedText, List<GlossaryEntry>) → ScalingDecision(engine, matchedRule, matches)` | Pure and testable; supports flag-off `[WARN]` diagnostics and satisfies the deferred `GlossaryTermMatch`; small extra record type. |
| B2 — Pure function, engine only | same input → `TranslationEngineType` | Simplest output; loses WHY; flag-off WARN info lost unless the caller re-runs. |
| B3 — Instance with `GlossaryPort` | `suggest(TranslationKey key)` (fetching terms internally) | Least pure (domain service reaching into a port), harder unit tests, contradicts hexagonal "domain knows only its inputs"; not recommended. |

### 5.3 Overall recommendation

1. Package `com.lucalzt.mctranslator.domain.service` + `package-info.java` (`@NullMarked`, Javadoc).
2. `MaskedText` VO (masked text + ordered variable list) in `domain.service` (per M1);
   positional `__VAR_N__` tokens; conservative documented-first regex families (§2.3);
   explicit edge behavior via spec scenarios (M5–M7).
3. `VariableMasker` / `VariableUnmasker` implementing the round trip.
4. `ScalingHeuristic` + decision record with `GlossaryTermMatch` (pure `List<GlossaryEntry>`
   input; `(path, maskedText)` input; exact 6-rule precedence; boundaries `>30`/`≤8`).
5. Flag-off behavior, `[WARN]` logging, model-absent degrade → application layer (non-goals
   here, per H4).
6. Resolve all §4 open questions in the proposal round before writing the spec (config.yaml
   "cero ambigüedad" rule).

---

## 6. Risks / unknowns

| Risk | Impact | Mitigation |
|---|---|---|
| Heuristic input shape ambiguous (whole key vs path+text; masked vs raw text) | Wrong contract → churn when `GlossaryAwareTranslator` lands; word counts and glossary matches computed on the wrong text | Pin in spec from H1; docs' "(path + texto)" wording supports the focused input; keep masked-text interaction explicit |
| Mask pattern set not pinned (only `%s`, `{0}` documented) | Under-masking real modpack strings (`%1$s`, `{0,number}`) → variables corrupted in output JSON | Conservative documented-first set + open decision M3 resolved in proposal; `%s`-only text edge case stays deferred to extraction change |
| Rule 5 LORE clause behaviorally inert in the exact table | Heuristic outcome differs from user intent if they expect "LORE → PRECISE" | Explicit H5 decision before spec; do not alter the table unilaterally |
| Unmasker edge cases unspecified (missing/extra/unmatched tokens) | Silent corruption of final translated JSON | Spec scenarios per M5–M7 with explicit behavior; unit tests pin each |
| Token collision/partial-match (`__VAR_1` vs `__VAR_10`; literal `__VAR_N__` in source) | Wrong restorations, ambiguous tokens | Exact full-index token regex + tests; M4 escape/limitation decision |
| `MaskedText`/`GlossaryTermMatch` placement vs pinned `domain.model` inventory | Cross-change MODIFIED requirements; spec drift | Prefer `domain.service` (M1); if `domain.model` chosen, plan the spec amendment explicitly |
| Flag-off `[WARN]` needs would-be-engine info | If heuristic short-circuits on the flag, diagnostics are impossible | Decision record (H3) + application-layer override (H4) |
| Scope creep into application layer (flag-off, degrade, WARN, engine masked-text handoff) | This change grows beyond domain/services | H4 + S2 recorded as explicit non-goals for the translator change |
| Review workload (≈10–13 files, strict TDD red-first) | Exceeds 400-line budget → chained-PR planning needed | Forecast in tasks phase; slice by service capability |
| Java 25 / GraalVM constraints | Preview features (e.g. String Templates) break native build | Stick to stable features: records, enums, `Pattern` (precompiled regex), JSpecify |

---

## 7. Recommendation summary

Proceed to **proposal** (next_recommended = propose), resolving the §4 open questions first
(proposal question round / user confirmation). Recommended scope: `domain.service` package with
`MaskedText`, `GlossaryTermMatch`, `VariableMasker`, `VariableUnmasker`, `ScalingHeuristic`
(+ decision record), pure Java, `@NullMarked`, Javadoc, JUnit 6 + AssertJ red-first tests, per
the deferred-artifact notes of the `domain-models` change. Flag-off behavior, `[WARN]` logging
and engine masked-text handoff belong to the future application-layer translator change.
