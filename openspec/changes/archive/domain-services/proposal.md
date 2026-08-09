# Proposal: Domain Services (`domain-services`)

## Purpose

Create the pure-Java domain services of the translation pipeline in `com.lucalzt.mctranslator.domain.service` — the package the docs pin as `service` (`docs/architecture/implementation-strategy.md` §2) and which is currently **absent** (verified: `domain/` has only `model/` and `port/out/`).

- **What**: `VariableMasker` / `VariableUnmasker` (protect code variables with `__VAR_N__` tokens before translation, restore after) and `ScalingHeuristic` (6-level precedence table deciding FAST vs PRECISE per key), plus the deferred VOs `MaskedText` and `GlossaryTermMatch` that the archived `domain-models` change explicitly owns to this change.
- **Why**: Every downstream step of the documented flow (`mask → heuristic → engine → unmask`, `implementation-strategy.md` §5) consumes these services. They are pure domain behavior (regex/predicates), testable in isolation with zero infrastructure, and are the first concrete consumers of the `domain.model` contracts (`TranslationKey`, `JsonPath.startsWith`, `GlossaryEntry`, `TranslationEngineType`, `GlossaryEntryClassification`). The future application layer (`GlossaryAwareTranslator`) will orchestrate them; nothing in the application layer exists yet.

## Approach

1. Create package `com.lucalzt.mctranslator.domain.service` with a `package-info.java` (`@NullMarked` + Javadoc, mirroring `domain.model` conventions).
2. `MaskedText` VO (in `domain.service`): immutable record holding the masked string + the ordered list of original variables (`__VAR_N__` ↔ `variables.get(N)`). It carries the round-trip state the unmasker needs — the `domain-models` deferred-artifact note defines exactly this shape.
3. `VariableMasker.mask(String) → MaskedText`: protects the resolved variable patterns with positional `__VAR_N__` tokens in first-occurrence order. Pure regex, precompiled `Pattern`, JDK-only.
4. `VariableUnmasker.unmask(MaskedText, String translated) → UnmaskResult`: best-effort restoration by token index (reordering and duplication handled naturally); the result exposes discrepancies (missing tokens, unmatched tokens, reordered variables) for the application layer to turn into `[WARN]` diagnostics. Never throws merely because a translation lacks, adds, or reorders variables.
5. `ScalingHeuristic.suggest(...) → ScalingDecision`: evaluates the exact 6-rule precedence table in order — first match wins — using `JsonPath.startsWith(...)` for rules 1/4 and word count + glossary matching for rules 3/5. Returns a decision record (engine + matched rule + optional `GlossaryTermMatch`), never a bare `TranslationEngineType`.
6. `GlossaryTermMatch` VO (in `domain.service`): the output of rule 2/5 glossary matching, per the `domain-models` deferred note.
7. Strict TDD: JUnit 6 + AssertJ unit tests in `src/test/java/com/lucalzt/mctranslator/domain/service/`, red-first (compilation failure of the not-yet-existing type counts as red), no Spring context, per `openspec/config.yaml` `rules.tasks` and the `spring-boot-testing` skill. Verify with `mvnw.cmd clean verify`.

## Resolved Product Decisions (user-confirmed, reflected verbatim)

1. **Variable patterns**: protect the full printf family `%s`, `%d`, `%f`, `%1$s` (incl. positional) AND the MessageFormat family `{0}`, `{0,number}` (incl. indexed/format variants).
2. **Heuristic rule 5**: literal table — rule 5 matches only when text is <=8 words AND no LORE glossary terms are detected; if LORE terms are present the rule does not match and evaluation falls to rule 6 → FAST. The LORE guard stays as a testable clause; its outcome-equivalence with rule 6 is accepted and MUST be documented in the proposal. **Documented equivalence**: in the literal table, when rule 5's condition fails *only* because a LORE term was detected (text still ≤8 words), control falls to rule 6 → FAST — the same outcome rule 5 would have produced. The LORE guard is therefore behaviorally inert today; it is retained as a testable, pinned clause and as a hook for the empirical tuning the docs anticipate (`implementation-strategy.md` §3: "esta tabla es punto de partida y se ajusta empíricamente"). No "LORE → PRECISE" rule is invented.
3. **Unmasker edge cases**: best-effort restoration, and the API must expose discrepancies (e.g. missing tokens, unmatched tokens, reordered variables) so the application layer can emit warnings. Never throws merely because a translation lacks/extra/reorders variables.
4. **ScalingHeuristic output**: a decision record (engine + matched rule + optional `GlossaryTermMatch`) — not just `TranslationEngineType`.
5. **Precise-engine flag-off**: OUT OF SCOPE for this change — the heuristic stays pure with NO flag; the application layer (`GlossaryAwareTranslator`) decides whether to evaluate and emits `[WARN]`.

## Defaults Carried Into Design (flagged, not to be silently changed)

- **VO placement**: `MaskedText` and `GlossaryTermMatch` live in `domain.service` (beside the services that produce them). The pinned 9-type inventory of `domain.model` (spec R3) is NOT touched — no MODIFIED requirements against the archived `domain-models` spec.
- **Token collision**: strict `__VAR_N__` pattern; a literal `__VAR_N__` in the source text is protected as a regular variable (no token leakage — round-trip output never contains a token that was not an original literal, and original literals are restored exactly).
- **Word counting**: whitespace split; a `__VAR_N__` token counts as one word.

## Open Question Resolution

| Explore question | Resolution status |
|---|---|
| M1 — VO placement | **RESOLVED** — `domain.service` (default above). |
| M2 — Token format | **RESOLVED** — positional `__VAR_0__`, `__VAR_1__`, … in first-occurrence order; exact full-index token matching (`__VAR_(\d+)__`) is a spec requirement so `__VAR_10__` never partially matches `__VAR_1__`. |
| M3 — Pattern families | **PARTIALLY RESOLVED** — decision 1 covers `%s`, `%d`, `%f`, `%1$s` and `{0}`, `{0,number}`. Remainder (escaped `%%`, `%S`, width/precision `%10.2f`, `%x`, `{0,choice,...}`, Minecraft `\u00a7`) stays OPEN (see Open Items). |
| M4 — Literal `__VAR_N__` in source | **RESOLVED** — protected as a regular variable; no token leakage (default above). |
| M5 — Missing variable in translation | **RESOLVED** — best-effort + discrepancy exposed; never throws (decision 3). |
| M6 — Extra/unmatched tokens | **RESOLVED (policy)** — never throws; discrepancy exposed (decision 3). Exact rendering in the restored text stays OPEN (see Open Items). |
| M7 — Reordered/duplicate tokens | **RESOLVED** — restore by token index; reordering handled naturally; duplicates restore the same original both times; no error on reorder (decision 3). |
| M8 — API shape / naming | **RESOLVED (naming)** — two classes `VariableMasker` + `VariableUnmasker` (docs name both); round trip via `MaskedText`. Exact shape of the discrepancy exposure (result record fields) stays OPEN (see Open Items). |
| H1 — Parameter shape | **OPEN** — recommendation carried: `(JsonPath path, String maskedText, List<GlossaryEntry>)`; the text rules 3/5 count is the masked text. Whether to accept `MaskedText` directly instead of bare `String` is a design call (see Open Items). |
| H2 — Glossary input | **RESOLVED** — `List<GlossaryEntry>` parameter; empty list → rules 2/5 vacuously don't match → falls to rules 3/4/6. Glossary loading via `GlossaryPort` is an explicit Non-Goal (below), so the heuristic never reaches a port. |
| H3 — Output shape | **RESOLVED** — decision record (engine + matched rule + optional `GlossaryTermMatch`) per decision 4. |
| H4 — Flag-off placement | **RESOLVED** — application layer; flag-off behavior + `[WARN]` are Non-Goals (decision 5). The heuristic has no flag and always evaluates. |
| H5 — Rule 5 LORE clause | **RESOLVED** — literal table; testable clause; outcome-equivalence with rule 6 accepted and documented above (decision 2). |
| H6 — Word-count semantics | **RESOLVED** — whitespace split on masked text; token = 1 word (default). Boundaries: `> 30` strict (exactly 30 → rule 3 does NOT match), `≤ 8` inclusive (exactly 8 → rule 5 candidate). Punctuation-only tokens counting as words is a minor pin (see Open Items). |
| H7 — Glossary matching mode | **OPEN** (see Open Items). |
| H8 — Threshold configuration | **OPEN**, low risk (see Open Items). |
| S1 — Package name | **RESOLVED** — `domain.service` (docs + `development-standards.md` §2 write `service` singular); new `package-info.java` with `@NullMarked` + Javadoc. |
| S2 — Engine masked-text handoff | **RESOLVED** — out of scope; recorded as a constraint for the translator change (`TranslationEnginePort.translate` still receives a `TranslationKey` with unmasked `originalText`; how the pipeline feeds masked text to the engine is application-layer work). |
| S3 — Review budget | **NOTE** — expected ≈ 10–13 files (2 VOs + 2 masker classes + heuristic + decision record + package-info + ~6 test classes); the tasks phase MUST forecast chained PRs if the 400-line budget applies. |

## Open Items for Spec/Design (do NOT resolve here)

1. **M3 remainder — regex coverage set**: escaped `%%`; case variants `%S`; width/precision (`%10.2f`); `%x`/other conversions beyond `[sdf]`; MessageFormat `{0,choice,...}` ranges; Minecraft `\u00a7` color codes — in or out of the masker pattern set.
2. **H1 — input shape detail**: `suggest(JsonPath, String maskedText, List<GlossaryEntry>)` vs `suggest(JsonPath, MaskedText, List<GlossaryEntry>)` (accepting the VO directly ties the two services' contracts together).
3. **H7 — glossary matching mode**: substring vs word-boundary; case-sensitive vs case-folded; multi-word terms; exact fields of `GlossaryTermMatch` (term, classification, occurrence/position?).
4. **H8 — thresholds & shape**: constants in code now vs configurable later (docs: empirical tuning pending a dataset); stateless class vs instance.
5. **M8 remainder — discrepancy contract**: exact type/shape of the unmask result and its discrepancy report (e.g. missing-token, unmatched-token, reordered-variable kinds).
6. **M6 remainder — extra-token rendering**: leave the unmatched token literal in the restored text vs replace with empty string.
7. **H6 remainder — tokenization detail**: confirm punctuation-only whitespace-separated tokens count as words.
8. **S3 carry-forward**: tasks-phase review-workload forecast with chained-PR slicing (natural slices: masking capability; heuristic capability; gates).

## Scope

### In-Scope

Package `com.lucalzt.mctranslator.domain.service` (pure Java, zero framework):

| Type | Kind | Justification |
|---|---|---|
| `package-info.java` | `@NullMarked` + Javadoc | Package-level nullness contract (mirrors `domain.model`). |
| `MaskedText` | VO (deferred artifact) | Round-trip state of mask→unmask (masked string + ordered variable list). |
| `VariableMasker` | domain service | `mask(String) → MaskedText`; protects the resolved variable families. |
| `VariableUnmasker` | domain service | `unmask(MaskedText, String) → UnmaskResult`; best-effort restore by token index + discrepancy report. |
| `GlossaryTermMatch` | VO (deferred artifact) | Output of `ScalingHeuristic` rule 2/5 matching. |
| `ScalingHeuristic` | domain service | `suggest(...) → ScalingDecision`; exact 6-rule precedence table, first match wins. |
| `ScalingDecision` (or equivalent decision record) | record | Engine + matched rule + optional glossary matches (decision 4). |
| Unmask result / discrepancy type(s) | records | Best-effort restoration output + discrepancy report (decision 3); exact shape per Open Item 5. |

Supporting scope: JUnit 6 + AssertJ unit tests (red-first, no Spring context), Javadoc on all public API, `mvnw.cmd clean verify` gate. No `pom.xml` change expected (jspecify 1.0.1 already present).

### Non-Goals (Out of Scope)

- **Precise-engine flag-off behavior and `[WARN]` emission** — the heuristic stays pure with no flag; the application layer (`GlossaryAwareTranslator`) applies the flag override and logs `[WARN]` (decision 5, H4).
- **Application-layer orchestration** — `GlossaryAwareTranslator` and any use-case wiring (cache → mask → heuristic → engine → unmask → persist) belong to a later application-layer change.
- **Glossary loading via `GlossaryPort`** — the heuristic receives `List<GlossaryEntry>` as a value; no port is consumed inside `domain.service`.
- **Cache/ports integration** — `TranslationCachePort`, `TranslationEnginePort` usage, and the masked-text handoff to the engine (S2) are out of scope; the port contracts are untouched.
- **The engines themselves** — NLLB/ONNX and llama adapters, their lifecycle, and their configuration.
- **Any change to the `domain.model` inventory** — the pinned 9-type set (spec R3) stays exactly as-is; `MaskedText`/`GlossaryTermMatch` are created in `domain.service`, never in `domain.model`.
- **Infrastructure** — Spring Shell commands, adapters, SQLite, config plumbing, logging infrastructure.
- **Extraction edge cases** — empty/whitespace-only leaf policy and `%s`-only text handling remain with the extraction change (as in `domain-models` Non-Goals).

## Acceptance Criteria

Measurable, behavior-oriented (each maps to tests):

1. `mvnw.cmd clean verify` passes with zero test failures, zero compilation errors, exit code 0.
2. `domain.service` compiles with **zero** imports from `org.springframework`, `jakarta`, or any other framework package (grep-verifiable), and `package-info.java` carries `@NullMarked` + Javadoc. Javadoc present on every public type/member (review check).
3. **Masker round trip**: for each pattern family in decision 1 (`%s`, `%d`, `%f`, `%1$s`, `{0}`, `{0,number}`), `mask` replaces every occurrence with a positional `__VAR_N__` token in first-occurrence order, and `unmask` of the tokenized text restores the exact original string.
4. **No token leakage**: a source text containing a literal `__VAR_N__` substring round-trips back to the identical literal; the masked output never contains a `__VAR_N__` token that does not correspond to a protected variable.
5. **Full-index token matching**: `__VAR_10__` is never restored from a partial match of `__VAR_1__` (unmask regex captures the full index; covered by tests).
6. **Best-effort unmask** (decision 3): a translation that drops a token, contains an extra/unmatched token, or reorders tokens → no exception thrown; each case yields a restored text plus a discrepancy exposed by the API; reordered tokens are restored by index; a duplicated token restores the same original twice.
7. **Heuristic precedence**: rules 1–6 evaluated in order, first match wins — one test per rule; rule 1 wins over rule 4/5 on conflicting paths (e.g. short `quest.description.*` text → PRECISE); rule 3 is strict `> 30` words (exactly 30 → falls through); rule 5 is inclusive `≤ 8` words; rule 5 with LORE terms present → no match → falls to rule 6 → FAST (outcome-equivalence documented in the test); empty glossary → rules 2/5 don't match.
8. **Word counting**: whitespace split on the masked text; a `__VAR_N__` token counts as exactly one word (so a masked variable never inflates the `> 30` / `≤ 8` counts).
9. **Decision record** (decision 4): `suggest` returns engine + matched rule number + glossary matches; `GlossaryTermMatch` present when rule 2/5 matched, empty/absent otherwise; bare `TranslationEngineType` is a trivial projection.
10. **`domain.model` untouched**: grep for `MaskedText` / `GlossaryTermMatch` / `CacheKey` over `src/main/java/com/lucalzt/mctranslator/domain/model/` → 0 matches; public types there still enumerate to exactly the 9.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Pattern-set coverage beyond decision 1 (Open Item 1: `%%`, `%S`, `%10.2f`, `%x`, `{0,choice}`, `\u00a7`) unresolved | Under-masking real modpack strings → variables corrupted in translated output | Resolve the regex set in the spec with explicit scenarios; conservative documented-first set; tests per accepted pattern |
| Rule 5 LORE guard outcome-equivalence (decision 2) | Future readers misread the guard as "LORE → PRECISE" and "fix" the table unilaterally | Documented equivalence in proposal + spec scenario pinning the literal behavior |
| Best-effort unmask (decision 3) | Silent quality degradation if the application layer ignores discrepancies | Discrepancy report is a first-class API output; spec requirement mandates the application layer consume it for `[WARN]` |
| Token collision / partial-match (`__VAR_1` vs `__VAR_10`; literal `__VAR_N__` in source) | Wrong restorations in final JSON | Strict full-index token regex + no-leakage default + dedicated tests (criteria 4, 5) |
| Heuristic input text ambiguity (masked vs raw; H1 open) | Word counts and glossary matches computed on the wrong text → wrong engine | Spec pins masked text as the input; H1 resolved in design; criterion 8 test |
| Scope creep into application layer (flag-off, WARN, engine handoff) | Change grows beyond domain/services | Explicit Non-Goals above (decision 5, S2); heuristic has no flag by construction |
| Review workload ≈ 10–13 files, strict TDD red-first | Exceeds 400-line review budget | Tasks phase forecasts chained PRs (slices: masking; heuristic; gates) per S3 |
| Java 25 / GraalVM constraints | Preview features (String Templates) break the native build | Only stable features: records, enums, precompiled `Pattern` (as in `domain-models` design §7.3) |

## Rollback Plan

Service-only change in a brand-new package: revert the commit(s) introducing `domain/service`. No existing code references the new package (verified: absent today), no `pom.xml` change is expected (jspecify already present), no database schema or migrations exist, and the CLI/runtime behavior is untouched — the revert is a clean deletion with zero side effects, identical in shape to the `domain-models` rollback.
