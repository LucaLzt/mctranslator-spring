# Design: Add GitHub Actions CI Pipeline (`add-github-actions-ci-pipeline`)

## Executive Summary
This design defines the technical specification for implementing the GitHub Actions Continuous Integration (CI) pipeline in `mctranslator-spring`. The pipeline automates code validation, compilation, and test execution (`./mvnw clean verify`) for every pull request and push to `main` and `master` branches using Java 25 (Eclipse Temurin) on Ubuntu Linux runners, incorporating dependency caching for optimized execution speed.

---

## Architecture & Component Design

### Workflow Structure (`.github/workflows/ci.yml`)
The CI pipeline is defined as a declarative GitHub Actions workflow YAML file located at `.github/workflows/ci.yml`.

- **Triggers**:
  - `push`: branches `main`, `master`
  - `pull_request`: branches `main`, `master`
- **Permissions**:
  - `contents: read` (Principle of least privilege for repository access).
- **Jobs**:
  - `build`:
    - **Runs-on**: `ubuntu-latest`
    - **Steps**:
      1. **Checkout Repository**: Uses `actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683` (# v4.2.2) to clone the repository immutably.
      2. **Set up Java**: Uses `actions/setup-java@3a3fa871871216b3f7f8d6896265005d95d105fd` (# v4.4.0) with `distribution: 'temurin'` and `java-version: '25'`.
      3. **Cache Maven Dependencies**: Uses `actions/cache@1bd1e3253bdc8e1684399d10c3f7107419388280` (# v4.1.2) to cache and restore `~/.m2/repository` keyed by `pom.xml` hashes.
      4. **Run Verification**: Executes `./mvnw clean verify` to compile and run the full test suite (unit, slice, integration tests).

---

## Data Model & File Structure Changes

### File Changes
- **New File**: `.github/workflows/ci.yml`
  - Purpose: Defines the CI workflow configuration.
  - Ownership: Repository CI/CD configuration.

---

## Integration Points & External Systems

1. **GitHub Actions Runner Environment**:
   - Execution environment provided by GitHub (`ubuntu-latest`).
2. **Maven Central / Package Repositories**:
   - External dependency repositories accessed via Maven during `./mvnw clean verify` (mitigated by local caching via `actions/cache`).

---

## Sequencing & Execution Workflow

```
[Trigger: push/PR]
       │
       ▼
[Runner: ubuntu-latest]
       │
       ├─► 1. actions/checkout@v4 (Clone repo)
       │
       ├─► 2. actions/setup-java@v4 (Install Temurin Java 25)
       │
       ├─► 3. actions/cache@v4 (Restore/Save ~/.m2/repository)
       │
       └─► 4. Run `./mvnw clean verify` (Compile & Test)
```

---

## Security & Supply Chain Considerations

- **Least Privilege**: Workflow defines explicit `permissions: contents: read` to limit runner token capabilities.
- **Pinned Immutable Actions (SHA Pinning)**: All GitHub Actions are pinned to exact immutable commit SHAs with version tag comments (e.g., `@11bd71901bbe5b1630ceea73d27597364c9af683 # v4.2.2`) to prevent supply-chain injection attacks if mutable tags are compromised.
- **Secret Management**: No production secrets or API keys are required or stored in workflow files for public PR validation.

---

## Observability & Diagnostics

- **GitHub Actions Console UI**: Real-time step-by-step logs, test execution summaries, and failure annotations.
- **Commit Status Checks**: Integration with GitHub pull requests to block merges on test or compilation failure.

---

## Threat Matrix

| ID | Asset | Threat | Impact | Likelihood | Mitigation |
|---|---|---|---|---|---|
| T1 | Repository Source / CI Runner | Malicious code execution via untrusted PR workflow modifications | High | Medium | Require status checks and branch protection; GitHub Actions restricts write permissions on forks. |
| T2 | Supply Chain / Dependencies | Compromised third-party dependencies downloaded during Maven build | High | Low | Maven dependency version pinning in `pom.xml`, checksum verification, and local dependency caching. |
| T3 | Workflow Actions | Action tampering / supply chain injection via mutable action tags | Medium | Low | Pin all GitHub Actions to exact immutable commit SHAs with version comments (e.g., `@11bd71901bbe5b1630ceea73d27597364c9af683 # v4.2.2`). |
