# Proposal: Domain Output Ports (TranslationEnginePort, GlossaryPort, TranslationCachePort)

## Purpose
Define the outbound domain port interfaces (`TranslationEnginePort`, `GlossaryPort`, and `TranslationCachePort`) in the domain layer (`com.lucalzt.mctranslator.domain.port.out`) to enable external translation, glossary retrieval, and caching capabilities without coupling the core domain to any framework or infrastructure technology (Hexagonal Architecture).

## Approach
1. Create `TranslationEnginePort` interface with `translate` contract method.
2. Create `GlossaryPort` interface with `getTerms` contract method.
3. Create `TranslationCachePort` interface with `find` and `save` contract methods.
4. Ensure all port interfaces reside strictly in `com.lucalzt.mctranslator.domain.port.out` with zero framework dependencies (pure Java / JSpecify).
5. Add unit tests verifying port method signatures and types.

## Scope
### In-Scope
- `TranslationEnginePort` interface and supporting domain request/response types if needed.
- `GlossaryPort` interface and glossary domain types if needed.
- `TranslationCachePort` interface and cache domain types if needed.
- Unit tests for domain ports.

### Non-Goals
- Infrastructure adapters implementing these ports (e.g., DeepL adapter, SQLite cache adapter, etc.).
- Application use cases orchestrating these ports.

## Rollback Plan
Remove the created port interface files and unit tests from the domain package.
