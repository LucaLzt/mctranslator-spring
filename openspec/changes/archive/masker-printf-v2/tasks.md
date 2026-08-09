# Tasks: Extended Variable Masking (`masker-printf-v2`)

## Review Workload Forecast

- Decision needed before apply: No
- Chained PRs recommended: No
- 400-line budget risk: Low

The change affects **4 files** (1 production source + 2 test classes + 1 reference/documentation file if any, or strictly `VariableMasker.java`, `VariableMaskerTest.java`, `VariableRoundTripTest.java`), estimated at **≈ 320–390 changed lines** (design §7/§12 forecast):
- `VariableMasker.java`: pattern constant + inline comments + class Javadoc (≈ ±40 lines)
- `VariableMaskerTest.java`: replace 1 v1 scenario + add ~18 new v2 test scenarios (≈ 240–300 lines)
- `VariableRoundTripTest.java`: add 3 byte-identical round-trip scenarios (≈ 55–70 lines)

Because the total change is well under the 400-line default budget and fits comfortably within a single session/PR, **chained PRs are NOT required**. A single PR is recommended. No `pom.xml` changes, no new public types, no new imports (R2). Rollback boundary is atomic (revert the change commits).

## Task Breakdown

### T1: Update `VariableMaskerTest` with v2 scenarios (RED) [x]

- **Description**: Rewrite and expand `src/test/java/com/lucalzt/mctranslator/domain/service/VariableMaskerTest.java`:
  - **REPLACE** `leavesNonVariablesLiteral` (current lines 64–69) with the "Reduced non-variable set is left literal" scenario: input `"%S var, %10.2f wide, %-10s, %2$10d, %+d, %l, %ls, {p}, §a colored"` must return unchanged text with an empty `variables` list (R6).
  - **ADD** v2 test methods covering all new spec scenarios (§8.2):
    - F4 per-conversion scenarios for `%n`, `%x`, `%o`, `%e`, `%g`, `%b`, `%c`, `%h`, `%i`, `%ld` (each returning `List.of("<conv>")`).
    - Positional F4 scenario: `"Cost %2$x and %1$ld"` → `List.of("%2$x", "%1$ld")`.
    - Standalone escape scenario: `"100%% complete"` → `List.of("%%")`.
    - Merge scenarios (decision 2): `"Progress: %s%%"` → `List.of("%s%%")`; `"+%s%% %s"` → `List.of("%s%%", "%s")`; positional merge `"Progress: %1$s%%"` → `List.of("%1$s%%")`.
    - Merge consumption & fallback scenarios: `"A %s%%%% B"` → `List.of("%s%%", "%%")`; `"100%%s"` → `List.of("%%")`; `"%%%"` → `List.of("%%")`; `"%s%%%%"` → `List.of("%s%%", "%%")`.
    - Mixed v2 scan scenario: `"A %s {0} %1$s%% %n {1,number} %2$x"` → `List.of("%s", "{0}", "%1$s%%", "%n", "{1,number}", "%2$x")`.
    - URL guard scenarios (R16): encodings (`"%20F"`, `"%E2"`, `"%2C"`, `"%0A"` → empty `variables`); quest-SNBT-like (`"Click here: https://example.com/api?text=Portable%20Fluid%20Storage%2C%20Silo"` → empty `variables`); guard precedence over hex-adjacent (`"Rate %e2"` → empty `variables`, `"Rate %e"` → `List.of("%e")`, same for `%b2`/`%b`, `%c2`/`%c`, `%d2`/`%d`); guard does not affect escape (`"100%% done"` → `List.of("%%")`); guard does not affect positional (`"Hello %1$s"` → `List.of("%1$s")`, keeping `%10$s` safety).
- **Criteria**: `VariableMaskerTest` compiles and runs against the v1 `VariableMasker` pattern, resulting in **behavioral RED** (the new assertions fail because the v1 pattern does not mask F4/escape/merge and does not guard URLs).
- **Requirements**: R6, R13, R16
- **Dependencies**: None
- **Verification**: `mvnw.cmd -Dtest=VariableMaskerTest test` → expected FAILURE (behavioral red).

### T2: Add byte-identical round-trip scenarios to `VariableRoundTripTest` (RED) [x]

- **Description**: Expand `src/test/java/com/lucalzt/mctranslator/domain/service/VariableRoundTripTest.java` to add three new R7 byte-identical restoration scenarios (§8.3):
  - `testStandaloneEscapeRoundTrip`: `original = "100%% complete"` → `unmask(mask(original), masked.maskedText())` → restored equals `"100%% complete"` exactly (never `"100% complete"`).
  - `testMergedTokenRoundTrip`: `original = "Progress: %s%%"` → unmask → restored equals `"Progress: %s%%"` exactly.
  - `testF4ConversionsRoundTrip`: `original = "Hex: %x, Long: %ld, Sci: %e"` → unmask → restored equals the exact original string.
- **Criteria**: `VariableRoundTripTest` compiles and runs against the v1 `VariableMasker`, resulting in **behavioral RED** (e.g. `%%` is not masked in v1, so round-trip comparison fails).
- **Requirements**: R7, R13
- **Dependencies**: None
- **Verification**: `mvnw.cmd -Dtest=VariableRoundTripTest test` → expected FAILURE (behavioral red).

### T3: Implement `VariableMasker` pattern rework and Javadoc update (GREEN) [x]

- **Description**: Update `src/main/java/com/lucalzt/mctranslator/domain/service/VariableMasker.java` (design §3.1, §3.7):
  - Replace `VARIABLE_PATTERN` constant with the pinned 5-part alternation pattern including inline comments and the URL lookahead guard:
    ```java
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
            "__VAR_(\\d+)__"                                               // (1) literal-token protection (v1, verbatim)
            + "|%(?![0-9A-Fa-f]{2}(?!\\$))(?:\\d+\\$)?(?:[sdfnxoegbchi]|ld)%%"   // (2a) MERGE: conversion + escape -> ONE token
            + "|%(?![0-9A-Fa-f]{2}(?!\\$))(?:\\d+\\$)?(?:[sdfnxoegbchi]|ld)"      // (2b) conversion: v1 + F4, optional positional index
            + "|%%"                                                        // (2c) standalone printf escape literal
            + "|\\{(\\d+)(?:,[^}]*)?\\}");                                 // (3) MessageFormat (v1, verbatim)
    ```
  - Rewrite `VariableMasker` class Javadoc to document the escape-aware printf branch, the F4 set, `%ld`, `%%` standalone escape, `%s%%` merge rule, and the URL percent-encoding guard with its hard precedence (R4). Replace the old "Documented limitation" paragraph with the new behavior descriptions.
  - Leave `mask(String)` algorithm and imports untouched (R2 — `java.util.regex` only).
- **Criteria**: `mvnw.cmd -Dtest=VariableMaskerTest test` and `mvnw.cmd -Dtest=VariableRoundTripTest test` turn **GREEN**. Every existing v1 test in `VariableMaskerTest` and other test classes stays **GREEN** (regression net).
- **Requirements**: R2, R4, R6, R7, R16
- **Dependencies**: T1, T2
- **Verification**: `mvnw.cmd test` → all tests pass successfully.

### T4: Closing verification gates (R2, R4, R14, R15) [x]

- **Description**: Run full verification checks and build gate:
  - Verify zero new imports in `VariableMasker.java` (R2 — only `java.util.regex` and existing imports).
  - Verify `domain.model` inventory and output ports are untouched (`git status` / `git diff`) (R14).
  - Verify Javadoc is present on `VariableMasker` (R4).
  - Run the full Maven build and test suite.
- **Criteria**: `mvnw.cmd clean verify` executes with `BUILD SUCCESS` and exit code 0.
- **Requirements**: R2, R4, R14, R15
- **Dependencies**: T3
- **Verification**: `mvnw.cmd clean verify` → `BUILD SUCCESS` (exit 0).

---

## Traceability Summary

| Capability / Feature | Tasks | Spec Requirements |
|---|---|---|
| Masking Rework & URL Guard | T1, T3 | R6, R16 |
| Round-Trip Byte-Identical Fidelity | T2, T3 | R7 |
| Testing & Verification Gates | T1, T2, T3, T4 | R2, R4, R13, R14, R15 |
