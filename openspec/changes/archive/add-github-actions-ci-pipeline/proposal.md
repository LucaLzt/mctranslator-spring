# Status: done

# Proposal: Add GitHub Actions CI Pipeline

## Purpose
Add an automated GitHub Actions Continuous Integration (CI) pipeline (`.github/workflows/ci.yml`) to `mctranslator-spring`.
- **What**: Create a workflow that runs `./mvnw clean verify` on every pull request and push to `main` and `master`.
- **Why**: Ensure automated build validation, code quality checks, and test execution across all contributions without manual intervention, maintaining high reliability for our Spring Boot 4 / Java 25 CLI application.

## Approach
1. Create `.github/workflows/ci.yml` adhering to GitHub Actions standards.
2. Configure workflow triggers:
   - `push` to `main` and `master` branches.
   - `pull_request` targeting `main` and `master` branches.
3. Configure the build job:
   - Runs on `ubuntu-latest`.
   - Checks out the repository code.
   - Sets up Java 25 (Eclipse Temurin distribution).
   - Caches Maven dependencies (`~/.m2/repository`) to speed up subsequent builds.
   - Executes `./mvnw clean verify` to compile, run unit/slice/integration tests, and check build integrity.

## Scope
### In-Scope
- `.github/workflows/ci.yml` workflow definition file.
- Java 25 setup and Maven wrapper execution (`./mvnw clean verify`).
- Caching configuration for faster CI runs.

### Non-Goals (Out of Scope)
- Continuous Deployment (CD) or artifact publishing (e.g., GitHub Releases, Docker registry publishing).
- Integration with third-party quality scanners (SonarCloud, Codecov) unless subsequently requested.
- Advanced matrix testing across multiple OSes (Linux is sufficient for CLI tool validation).

## Rollback Plan
If the pipeline fails or requires modification, revert the commit introducing `.github/workflows/ci.yml` or delete the file. No database schema or production runtime changes are involved.

