# Proposal: Extended Variable Masking (`masker-printf-v2`)

## Purpose

Extend the archived `domain-services` masking capability — `VariableMasker` / `VariableUnmasker` in `com.lucalzt.mctranslator.domain.service` — to cover the two remaining evidence-backed variable families of the corpus, measured in the explore phase (47,978 values scanned): the `%%` printf escape (F5, 1.90% of masked variables, distributed across vanilla + mods) and the extended printf conversions `%n %x %o %e %g %b %c %h %i %ld` (F4, 1.21%, with a documented `%n` concentration caveat) — plus a hard guard against percent-encoded URLs (`%20F`-style) that currently produce false-positive printf matches in quest SNBT. The v1 pattern set (`%s`/`%d`/`%f`, positional `%1$s`, MessageFormat `{0,...}`) stays fully covered; the MessageFormat branch is kept unchanged (0% corpus frequency — documented, not expanded).

- **What**: modify the detection pattern and tokenization of `VariableMasker` in place (public API shape unchanged: `mask(String) → MaskedText`) so that: standalone `%%` becomes a masked variable; `%s%%` (conversion + escape) becomes ONE token; every masked variable — including `%%` — restores byte-identical to the original; and `%[0-9A-Fa-f]{2}` (URL percent-encoding) is never treated as a conversion. `VariableUnmasker` is expected to need no change (restoration already works by token index with verbatim variables).
- **Why**: v1 documented `%%`, `%x` and the other conversions as explicit non-goals pending empirical tuning (`domain-service` spec R6 and Non-Goals). The explore phase shows both families pass the ≥1% frequency threshold with stable, distributed evidence; masking them closes the documented v1 limitation (the `%%s` adjacency note) and the URL guard eliminates the false-positive class found in FTB Quests SNBT (`%20F`, `%E2`, `%2C` → 20 bogus "variables", all clickEvent URLs — discovery #76).

## Approach

1. Rework the `VariableMasker` alternation in place — single precompiled `Pattern`, JDK-only, stateless service (no constructor/API change):
   - keep branch 1 (literal `__VAR_N__` token protection) and the MessageFormat branch `\{(\d+)(?:,[^}]*)?\}` verbatim;
   - replace the printf branch `%(?:\d+\$)?[sdf]` with an escape-aware printf branch covering `[sdf]` (v1) plus the F4 conversions `x o e g b c h i n` and `ld`, with `%%` recognized as the printf escape literal;
   - merge rule: a conversion immediately followed by `%%` (the observed real pattern `%s%%`, incl. positional forms such as `%1$s%%`) is emitted as a SINGLE `__VAR_N__` token whose variable is the full original substring (`"%s%%"`);
   - URL guard: `%` followed by exactly two hex digits `[0-9A-Fa-f]{2}` is NOT a conversion and is never masked — it takes precedence over F4 detection (the design phase pins the exact alternation order and the guard's precedence over hex-adjacent conversion text such as `%e2`).
2. `VariableUnmasker`: expected unchanged — restore by token index, `Matcher.quoteReplacement`, full-index `__VAR_(\d+)__` matching. Byte-identical restoration falls out of recording each original substring verbatim; no `%%`→`%` normalization anywhere in the pipeline.
3. Update the v1 test scenarios that pinned the old left-literal behavior for `%%` and `%x` (Requirement 6 scenarios) to the new behavior. ALL other v1 tests (token no-leakage, full-index matching, MessageFormat, round-trip fidelity, best-effort unmask discrepancies) stay untouched as the regression safety net.
4. Add red-first JUnit 6 + AssertJ tests (no Spring context, per `openspec/config.yaml` `rules.tasks`): one scenario per F4 conversion, F5 standalone/merged/byte-identical, URL guard (incl. quest-SNBT-like strings), and a v1-compat sweep. Gate: `mvnw.cmd clean verify`.

## Confirmed Product Decisions (user-confirmed, binding for spec/design)

1. **F4 full set**: the extended conversions `%n`, `%x`, `%o`, `%e`, `%g`, `%b`, `%c`, `%h`, `%i`, `%ld` are masked, in addition to v1 `s/d/f`. Caveat documented as evidence: 66/69 corpus F4 candidates are `%n` in a single BetterQuesting help file; `%MODNAME`/`%Creative` (2/69, JEI uppercase macros) are NOT printf conversions and stay left-literal.
2. **`%s%%` as ONE token**: conversion + literal escape are masked as a single `__VAR_N__` (both round-trip either way; the merge is a translator-UX choice — the engine never sees a dangling `%%` after a token).
3. **Byte-identical restoration**: `%%` restores exactly as found in the original — never normalized to `%`.
4. **URL guard is a hard requirement**: `%[0-9A-Fa-f]{2}` (percent-encoding) is NEVER masked — protection against false positives in quest SNBT.
5. **MessageFormat `{0}` unchanged**: the v1 branch stays intact and is documented as NOT expanded (0% frequency in the corpus).

## Defaults Carried Into Design

- Public API shape unchanged: `VariableMasker.mask(String) → MaskedText` and `VariableUnmasker.unmask(MaskedText, String) → UnmaskResult`; no new public types (merge/guard logic is internal to the masker).
- Token contract unchanged: positional `__VAR_N__`, full-index matching `__VAR_(\d+)__`, no-leakage protection for literal tokens.
- Package stays 100% pure Java (`domain.service`), `@NullMarked`, Javadoc on all public API; the `domain.model` inventory and port contracts stay untouched (v1 spec Requirement 14).
- Strict TDD red-first; test sources in `src/test/java/com/lucalzt/mctranslator/domain/service/`.

## Scope

### In-Scope

- F5 `%%` escape-aware masking (standalone + `%s%%` single-token merge) — 1.90% frequency, closes the v1 documented limitation.
- F4 extended conversions `%n %x %o %e %g %b %c %h %i %ld` — 1.21% (concentration caveat above).
- `%s%%` single-token rule, including positional forms (`%1$s%%`).
- Byte-identical restoration of `%%` (no normalization).
- URL percent-encoding guard `%[0-9A-Fa-f]{2}`.
- MessageFormat `{0,...}` branch unchanged (kept, not expanded).
- Updated + new JUnit 6 / AssertJ tests (red-first), Javadoc, `mvnw.cmd clean verify` gate.

### Non-Goals (Out of Scope) — evidence from explore

- **F3 flags/width/precision** (`%.1f`, `%10.2f`, `%-10s`, `%+d`, `%2$10d`) — 0.05% (3 occurrences, all the deliberately-broken `translation.test.invalid2` string) + collision risk with URL-encoding; stays literal.
- **F7 named braces** `{p}` / `{player}` / `{buttonText}` — 0.62%, only help meta-text and key references, never in quest/lore text.
- **PlaceholderAPI `%name%`** — 0% (the raw candidates were `%s%` from `%s%%` — false positives).
- **`${...}`** — 0.18% (Twilight Forest keybinds only).
- **`<...>`** — 0.25% (command syntax in help text).
- **JSON text components** (`"translate"` / `"with"`) — 0 occurrences in quest JSON/SNBT.
- **`§`/`&` format codes** — confirmed non-variables (7,482 cases; the pipeline already leaves them literal).
- **JEI uppercase macros** (`%MODNAME`, `%Creative`) — 2 occurrences; neither printf nor PlaceholderAPI; left literal (documented in the F4 evidence note).
- **Application layer / engine handoff / configuration** — unchanged from the v1 non-goals.

## Acceptance Criteria

Measurable, behavior-oriented (each maps to tests):

1. `mvnw.cmd clean verify` passes — zero test failures, zero compilation errors, exit code 0.
2. **v1 compatibility**: every v1 masking behavior that is not deliberately modified still holds — `%s`/`%d`/`%f`, `%1$s`, `{0}`, `{0,number}`, `{0,choice,...}`, JSON braces left literal, token no-leakage, full-index matching, best-effort unmask discrepancy contract.
3. **F5**: standalone `%%` is masked as a variable; `%s%%` and `%1$s%%` are masked as exactly ONE token whose variable is the full original substring.
4. **Byte-identical**: unmasking a translated text containing a `%%` variable restores exactly the original `%%` (never `%`).
5. **F4**: each of `%n %x %o %e %g %b %c %h %i %ld` is masked and restores byte-identical.
6. **URL guard**: `%20F`, `%E2`, `%2C` (and any `%` + two hex digits) produce NO variables and are left literal, including inside quest-SNBT-like strings.
7. **MessageFormat unchanged**: `{0}`-family masking is identical to v1.
8. **Domain purity**: zero framework imports, `@NullMarked`, Javadoc on all public API, `domain.model` inventory and ports untouched (grep-verifiable).

## Risks & Limitations

| Risk | Impact | Mitigation |
|---|---|---|
| `%n` concentration (66/69 F4 candidates in a single BQ help file) | Corpus skew — F4 mostly exercises one pattern | Set is small and cheap; per-conversion scenarios; caveat documented as evidence |
| URL guard eats hex-adjacent conversion text (`%e2`) | A legit conversion adjacent to hex digits is left literal | F3 width forms are non-goals, so no v1 regression; guard precedence pinned in the design |
| Escape-merge edge cases (`%%s` reverse adjacency, triple `%%%`) | Ambiguous tokenization | Reverse adjacency does not occur in real content (only `translation.test.escape`, a deliberately-broken test string); natural two-token fallback is acceptable |
| Behavioral change vs v1 (`%s%%` → 1 token; `%%`/`%x` now masked) | Existing R6 scenarios pinned the old left-literal behavior | Those scenarios are updated in this change; all other v1 tests remain as the regression safety net |
| Corpus limits (en_us only, 2 modpacks, version mix 1.7.10–1.21.4) | Extrapolation risk to other locales/packs | Documented in the explore artifact and carried into spec risks |
| Review workload (≈ 4–6 files: 1–2 main + 2–3 test classes) | 400-line review budget | Tasks phase MUST forecast; likely a single PR or 2 slices (masking; tests/gates) |

## Rollback Plan

Pattern-only change confined to `domain.service` (primarily `VariableMasker`; `VariableUnmasker` expected untouched). Revert the change's commit set — the v1 detection pattern and the v1-pinned test scenarios are restored together because they live in the same commits. All v1 tests that were NOT modified remain green during the change and act as the regression safety net, making the revert a low-risk operation. No new public API and no external callers (the application layer does not exist yet), no `pom.xml` change expected, no schema/migrations, no CLI/runtime behavior change beyond masking output. Verification of a revert: `mvnw.cmd clean verify` with the v1 baseline patterns.
