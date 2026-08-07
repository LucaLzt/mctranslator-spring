# Tasks: Add GitHub Actions CI Pipeline

## Review Workload Forecast
Decision needed before apply: No
Chained PRs recommended: No
400-line budget risk: Low

## Task Breakdown

- [x] T1: Create Workflow Directory & Configuration File
  - **Description**: Create `.github/workflows/` directory if absent and initialize `.github/workflows/ci.yml` with basic workflow name, permissions (`contents: read`), and triggers for `push` and `pull_request` on `main` and `master`.
  - **Criteria**: `.github/workflows/ci.yml` exists and defines valid trigger events and least-privilege permissions.
  - **Requirements**: Req 1
  - **Dependencies**: None

- [x] T2: Configure Job Environment, Checkout, and Java 25 Setup with SHA Pinning
  - **Description**: Define the `build` job running on `ubuntu-latest`. Add pinned steps for repository checkout (`actions/checkout@11bd71901bbe5b1630ceea73d27597364c9af683` # v4.2.2) and Java 25 setup (`actions/setup-java@3a3fa871871216b3f7f8d6896265005d95d105fd` # v4.4.0 with `distribution: 'temurin'` and `java-version: '25'`).
  - **Criteria**: Steps use exact immutable commit SHAs with version comment tags as required by design, successfully provisioning Java 25.
  - **Requirements**: Req 2
  - **Dependencies**: T1

- [x] T3: Configure Maven Dependency Caching
  - **Description**: Add caching step using `actions/cache@1bd1e3253bdc8e1684399d10c3f7107419388280` (# v4.1.2) targeting `~/.m2/repository` with a cache key based on `pom.xml`.
  - **Criteria**: Caching step is properly configured to cache and restore Maven dependencies across workflow runs.
  - **Requirements**: Req 3
  - **Dependencies**: T2

- [x] T4: Add Build and Test Verification Step
  - **Description**: Add workflow step to execute `./mvnw clean verify` to compile the Spring Boot 4 application and run all unit, slice, and integration tests.
  - **Criteria**: Workflow step executes Maven wrapper verification successfully and fails the job upon any test or compilation failure.
  - **Requirements**: Req 4
  - **Dependencies**: T3

- [x] T5: Local Verification & Syntax Review
  - **Description**: Run `./mvnw clean verify` locally to ensure current project tests and build pass without regressions, and review workflow YAML syntax against GitHub Actions specifications.
  - **Criteria**: Local build completes successfully (`BUILD SUCCESS`) and workflow file is syntactically valid.
  - **Requirements**: Req 4
  - **Dependencies**: T4
