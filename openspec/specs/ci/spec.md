# Specification: CI Pipeline (GitHub Actions)

## Goal
Establish an automated GitHub Actions Continuous Integration (CI) pipeline (`.github/workflows/ci.yml`) for `mctranslator-spring` that builds, tests, and verifies code changes on every pull request and push to main/master branches using Java 25.

---

## ADDED Requirements

### Requirement 1: Workflow Trigger Configuration
The CI pipeline (`.github/workflows/ci.yml`) MUST trigger automatically on `push` events to `main` and `master` branches, and on `pull_request` events targeting `main` and `master`.

#### Scenario: Trigger on push and pull request
- **Given** the repository contains the workflow file at `.github/workflows/ci.yml`
- **When** a developer pushes commits to `main` or `master`, or opens/updates a pull request targeting `main` or `master`
- **Then** GitHub Actions initiates a CI workflow run for that commit or pull request.

---

### Requirement 2: Environment and Tooling Setup
The CI workflow MUST run on `ubuntu-latest`, check out the repository source code using official checkout actions, and set up Java 25 (Eclipse Temurin distribution).

#### Scenario: Setup Java 25 runner environment
- **Given** a triggered GitHub Actions workflow job
- **When** the workflow runner initializes and executes checkout and setup-java steps
- **Then** Java 25 (Temurin) is successfully installed and available on `PATH`, and `java -version` reports Java 25.

---

### Requirement 3: Maven Dependency Caching
The CI workflow MUST cache Maven dependencies (`~/.m2/repository`) using `actions/cache` keyed on `pom.xml` hashes to speed up subsequent pipeline runs.

#### Scenario: Cache and restore Maven dependencies
- **Given** a cached Maven dependency repository from previous workflow runs
- **When** the pipeline executes the dependency caching step with an unchanged `pom.xml`
- **Then** cached dependencies are restored instantly into `~/.m2/repository`, reducing build times.

---

### Requirement 4: Build and Test Verification
The CI workflow MUST execute `./mvnw clean verify` to compile the Spring Boot application, run all unit, slice, and integration tests, and fail the build if any test fails or compilation errors occur.

#### Scenario: Successful build and test verification
- **Given** the Java 25 environment is provisioned and dependencies are available
- **When** the workflow runs `./mvnw clean verify`
- **Then** the application compiles cleanly, all test suites execute and pass successfully, and the workflow job exits with status code 0.

#### Scenario: Failing build on test regression
- **Given** a code contribution containing a failing unit test or compilation error
- **When** the workflow runs `./mvnw clean verify`
- **Then** the build halts and fails, reporting the test or compilation failure, and the workflow job exits with a non-zero error code.

---

## Non-Goals
- Continuous Deployment (CD) or automatic publishing of artifacts to external registries (Docker Hub, Maven Central, GitHub Releases).
- Integration with external static analysis or code coverage services (SonarCloud, Codecov) in the initial pipeline scope.
- Multi-OS matrix testing (Linux Ubuntu-latest is fully sufficient for validating the Spring Boot 4 / Java 25 CLI application).

---

## Risks & Mitigations
- **Risk 1**: Java 25 setup action incompatibility or missing Temurin package version.
  - *Mitigation*: Use official `actions/setup-java@v4` with `distribution: 'temurin'` and `java-version: '25'`.
- **Risk 2**: Maven build timeout or network flakiness when downloading dependencies on cache miss.
  - *Mitigation*: Reliable caching via `actions/cache` and resilient Maven wrapper invocation (`./mvnw`).
