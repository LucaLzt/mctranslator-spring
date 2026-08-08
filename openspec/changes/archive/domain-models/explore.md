# Exploration: Domain Models & Value Objects (`domain-models`)

> Status: exploration analysis — READ-ONLY. No code, no proposal/spec/design yet.
> Source of truth: `docs/architecture/tech-stack.md`, `docs/architecture/implementation-strategy.md`,
> `docs/architecture/development-standards.md`, `openspec/config.yaml`, archived change
> `openspec/changes/archive/add-github-actions-ci-pipeline/`, skills `hexagonal-architecture`,
> `domain-driven-design`, `java-springboot`, `java-docs`, `spring-boot-testing`.

## 1. What the pipeline does end-to-end (from the docs)

`implementation-strategy.md` §5 defines the per-key flow (current state of the project: **nothing of this exists yet** — the strategy doc states "Ninguno de los componentes mencionados aquí existe todavía"; today the repo only has a Spring Shell bootstrap `TranslateCommand` returning a "not implemented" message):

```
[CLI: modpack-translator translate -f modpack.json]
    → 1. Local cache match (SQLite)            — hit → return cached string
    → 2. Regex masker (variables → __VAR_N__)
    → 3. ScalingHeuristic (flag + path + length + glossary) → FAST | PRECISE
    → 4a. FastNllbAdapter (ONNX/NLLB-200, embedded, direct)
    → 4b. PreciseLlmAdapter (lazy-start llama-server, glossary injected into system prompt, HTTP)
    → 5. Regex unmasker
    → 6. Persist to SQLite cache
    → 7. Write into final JSON
(parallel, out-of-band: idle-timeout scheduler → Process.destroy() llama-server after 2 min)
```

- Input: a modpack JSON file (Minecraft lang file). Output: the translated JSON.
- Two interchangeable engines behind one port (`TranslationEnginePort`); decision per key via `ScalingHeuristic`.
- The precise engine is optional (flag `mctranslator.precise-engine.enabled`, v1 default off); fallbacks degrade to fast with explicit `[WARN]` per key.
- Metrics to log: % keys resolved by cache vs fast vs precise; cold-start time; total runtime; fallback count.

## 2. Domain model the docs prescribe (component map)

`implementation-strategy.md` §2 and `development-standards.md` §2 (base package in actual code: **`com.lucalzt.mctranslator`**, not the `com.mctranslator` shown in the strategy doc — documented mismatch, see Risks):

```
com.lucalzt.mctranslator
├── domain/                        pure Java, zero Spring
│   ├── model/                     TranslationKey (JSON path + text), GlossaryEntry, TranslationResult
│   ├── port/                      TranslationEnginePort, GlossaryPort, TranslationCachePort
│   └── service/                   ScalingHeuristic, VariableMasker / VariableUnmasker (regex __VAR_N__)
├── application/
│   └── GlossaryAwareTranslator    pipeline per key: cache → mask → heuristic → engine → unmask → persist
└── infrastructure/
    ├── adapter/in/                Spring Shell 4 CLI commands
    └── adapter/out/
        ├── nllb/                  FastNllbAdapter (ONNX embedded)
        ├── llama/                 PreciseLlmAdapter + LlamaServerProcessManager
        ├── glossary/              JsonGlossaryAdapter
        └── cache/                 SqliteCacheAdapter (SQLite via JDBC)
```

Ports are **driven ports** (`port/out`) consumed by the application use case `GlossaryAwareTranslator`; there is no `port/in` use-case interface documented yet, but per hexagonal skill the use case would normally be exposed through a driving port (e.g., `TranslateModpackUseCase`) that `TranslateCommand` will eventually call. In scope question: whether ports belong to this change.

## 3. Where the three named classes fit

| Class | Role in pipeline | Created by | Consumed by |
|---|---|---|---|
| `TranslationKey` (JSON path + original text) | Identity + input of every per-key pipeline step; cache lookup identity | extraction of the modpack JSON (leaf key-value → key) | `TranslationCachePort` (lookup), `VariableMasker` (input), `ScalingHeuristic` (path+text+glossary), engines (text to translate) |
| `TranslationResult` | Outcome of one translated key, incl. engine used / fallback / warning | `GlossaryAwareTranslator` (application) after engine + unmask + persist | final JSON writer; metrics/logging (cache vs fast vs precise %, fallback count) |
| `GlossaryEntry` | Glossary term used by the heuristic (rule 2: "término del glosario marcado como ambiguo"; rule 5: "sin términos de lore detectados vía glosario") and injected into the precise engine's system prompt via `GlossaryPort` | `JsonGlossaryAdapter` (infra, via `GlossaryPort`) | `ScalingHeuristic`, `PreciseLlmAdapter` |

## 4. Value objects beyond the named three (candidates)

Derived from the pipeline semantics, the docs' language, and the DDD/hexagonal skills:

1. **`JsonPath`** (JSON path segments) — paths like `quest.description.*`, `lore.*`, `advancement.*`, `item.*`, `block.*`, `entity.*`, `gui.*` drive heuristic rules 1, 4, 5. A typed VO with segment list and behavior (`startsWith(segment...)`, `matches(prefix)`) keeps the heuristic pure and testable and gives `TranslationKey` a real `JsonPath` (not a raw String).
2. **`LanguageCode`** — NLLB supports 200 languages; a CLI translate command logically needs a target language. **Not mentioned anywhere in the docs** — open question (see §7 Q1).
3. **`TranslationStatus`** (enum) — pipeline has cache hit, translated by fast, translated by precise, fallback-to-fast (`[WARN]`), failure. Needed for metrics and result semantics. Consider `CACHE_HIT | TRANSLATED_FAST | TRANSLATED_PRECISE | FALLBACK_FAST | FAILED | SKIPPED`.
4. **`TranslationEngineType`** (enum FAST | PRECISE) — the heuristic's decision output and `TranslationResult` metadata; used by metrics and fallback logic.
5. **`MaskedText`** — `VariableMasker`/`VariableUnmasker` are domain services (regex `__VAR_N__`); the mask→unmask round-trip requires carrying the masked string + the original-variable mapping. A VO in `domain/model` keeps this contract explicit (docs put the maskers in domain, so the artifact belongs in domain).
6. **`GlossaryTermMatch`** — heuristic rule 2 needs "text contains ≥1 glossary term flagged ambiguous"; a match result VO (term, found, ambiguous/lore flags) makes rule 2/5 testable as pure logic.
7. **`CacheKey`** — derived identity for the SQLite cache (hash of path + original text [+ target language?]). The `TranslationCachePort` contract and later `SqliteCacheAdapter` schema depend on its exact definition.
8. **`ModpackId` / modpack identity** — needed only if the cache must avoid collisions across modpacks/files (same path+text in two modpacks). Open question (§7 Q6).
9. **`Variable`** (a single `{0}`/`%s`/`%1$s` placeholder value) — only if masking needs fine-grained representation; possibly overkill if `MaskedText` holds the map.

Not domain candidates (infrastructure concerns): `PromptTemplate`/glossary-prompt assembly (llama adapter), JSON file reader/writer, SQLite row mapping.

## 5. Design style the project expects

- **Records with compact-constructor validation** — `development-standards.md` §3: "Value objects inmutables como records con validación en el constructor compacto (ej. `TranslationKey`, `GlossaryEntry`, `TranslationResult`)". Matches `domain-driven-design` skill (immutable records, invariant checks, factory methods like `of(...)`).
- **Typed IDs / no raw Strings for domain concepts** — §3: "IDs tipados (value objects), no `Long`/`String` sueltos".
- **Rich domain, not anemic** — behavior on domain objects (path matching, heuristic decisions, mask/unmask); services orchestrate, domain decides (DDD skill).
- **Pure domain, zero framework** — hexagonal skill: no Spring annotations, no `jakarta.persistence`, no JPA in `domain/`; ports are interfaces; `@Transactional` lives in `application/` only. Nothing in the current code depends on `domain/` yet (no package exists), so no migration risk.
- **`@NullMarked` (JSpecify)** on domain packages, `@Nullable` only for exceptional values — §3 and DDD skill; Framework 7 standard.
- **Javadoc mandatory** for public/protected members + `package-info.java` per new package (§5, `java-docs` skill).
- **Testing**: pure domain unit tests with JUnit 6 + AssertJ, no Spring context (§6; `spring-boot-testing` skill — plain JUnit + Mockito for business logic).
- **Java 25 features**: records, sealed interfaces, pattern matching for switch are stable and idiomatic; do NOT rely on String Templates (still preview/reworked in recent JDKs — not a safe assumption for a GraalVM native build).
- **OpenSpec rule** (`openspec/config.yaml`, `rules.specs`): "Cero ambigüedad en contratos de puertos y definición de Value Objects inmutables" — every VO must have its contract fully pinned before spec, reinforcing the need to resolve §7 open questions first.
- **Artifact format conventions** from the archived CI change: proposal = Purpose/Approach/Scope(Non-Goals)/Rollback; spec = delta ADDED/MODIFIED/REMOVED with mandatory `#### Scenario:` blocks; design = Executive Summary, Architecture & Component Design, Data Model, Integration Points, Sequencing, Security, Threat Matrix.

## 6. Approach comparison

- **A — Docs-literal (minimal):** implement exactly the 3 named records + 3 ports + 2 services as listed, no extra VOs. *Tradeoffs*: faithful, small; but leaves cache semantics, languages, statuses and mask artifacts unspecified — would violate the config.yaml "cero ambigüedad" rule and cause churn when the pipeline lands.
- **B — Pipeline-derived (recommended):** follow the docs' structure, enrich the model with the §4 VOs that the documented flow logically requires (`JsonPath`, `LanguageCode`, `MaskedText`, `TranslationStatus`, `EngineType`, `GlossaryTermMatch`, `CacheKey`). *Tradeoffs*: slightly larger first change, but each VO is directly justified by a documented step; makes `ScalingHeuristic` and maskers testable pure units now (they are listed first in the docs' implementation order).
- **C — Names-only placeholder:** stub the 3 classes with minimal fields and refine later. *Tradeoffs*: fastest, but guarantees rework and contradicts the spec rule on unambiguous VO contracts.

**Recommendation**: B, scoped to `domain/model` (+ the domain services and the three documented `port/out` interfaces if the user confirms ports in scope — they are `domain/` artifacts per the docs, and the pipeline steps cannot be expressed without them). If the user prefers a strictly model-only change, ports move to an immediately following change.

## 7. Open questions / gaps (must be answered before the proposal/spec phase)

1. **Target language** — where does it come from (CLI option `-l es`? config? default en→es?)? Does `TranslationKey`/`TranslationResult` carry it, and does it participate in the cache key? (Docs never mention it — CRITICAL, shapes `LanguageCode` and cache contract.)
2. **Cache semantics** — cache hit keyed by (path + original text)? + target language? Does a hit return only the string, or also engine metadata? Is `TranslationCachePort` in scope for this change?
3. **GlossaryEntry structure** — fields: term + translation (+ source/target lang)? Is "ambiguous" and "lore" a boolean flag or a classification enum (rule 2 vs rule 5 imply at least two categories)? One translation per term or many? Per-language glossaries? Is a glossary entry allowed to be empty/absent when no glossary file exists?
4. **TranslationResult content** — must carry: translated text, original key reference, engine type, status, warning/fallback message, elapsed time? Should it expose a factory like `TranslationResult.fromKey(TranslationKey, String text, TranslationEngineType, TranslationStatus)`? One result per key?
5. **Failure semantics** — engine failure: result marked FAILED, or fallback to the ORIGINAL text so the modpack stays valid? (Docs say degrade to fast engine + `[WARN]`; total failure behavior unspecified.)
6. **Modpack identity** — is a `ModpackId` (name/version/file) needed for cache uniqueness, or is (path, text, lang) globally unique enough?
7. **Input extraction** — only string leaves of the JSON become `TranslationKey`s? Non-string leaves (numbers, nested objects) skipped/preserved verbatim? Empty or whitespace-only text and all-variable text (`%s` only) — translate, skip, or pass through?
8. **Output shape** — final JSON = input modpack JSON with translated values substituted (same structure, order preserved)? Are untranslated/failed keys written as original? Pretty-printed? Does the domain need an output document model or just a `List<TranslationResult>`?
9. **Scope of the change** — model classes only, or also `domain/port/out` (the 3 documented interfaces) and the two domain services (`ScalingHeuristic`, `VariableMasker`/`Unmasker`) + their `MaskedText` artifact? The card title says "Domain Models & Value Objects"; the docs group ports/services in `domain/`.

## 8. Risks

- **CRITICAL — Package mismatch in docs**: `implementation-strategy.md` uses `com.mctranslator`; actual code, `development-standards.md` and `openspec/config.yaml` use `com.lucalzt.mctranslator`. The real base package wins; do not copy the strategy doc's package.
- **CRITICAL — Language/cache semantics unspecified**: `TranslationKey` identity and the cache contract depend on answers to Q1/Q2/Q6; guessing now propagates into `TranslationCachePort` and the future `SqliteCacheAdapter` schema.
- Docs are aspirational (explicitly "todo desde cero"); the domain layer is greenfield, so no existing code constraints — but also no tests to anchor behavior yet; strict TDD (per config.yaml tasks rule) must write failing unit tests first.
- Java 25: stick to stable language features (records, sealed, pattern-matching switch); avoid String Templates (preview/rework risk in a GraalVM native build).
- Over-engineering risk in Option B: keep each extra VO justified by a documented pipeline step or drop it; the proposal should list exactly which VOs are in and why.
- Glossary classification (Q3) affects the heuristic rules 2/5 — unresolved, the spec cannot be written unambiguously (config.yaml rule).

## 9. Recommendation summary

Proceed to proposal with approach B, resolving §7 first via the orchestrator/user. Suggested change scope (pending Q9): `domain/model` records/VOs (`TranslationKey`, `JsonPath`, `TranslationResult`, `TranslationStatus`, `TranslationEngineType`, `GlossaryEntry`, `GlossaryTermMatch`, `MaskedText`, `LanguageCode`, `CacheKey`) with compact-constructor validation, `@NullMarked` packages, `package-info.java`, and JUnit 6 + AssertJ unit tests in red-first order per the project's strict-TDD rule.
