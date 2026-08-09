# Specification: Domain Output Ports

## Goal
Define clean domain outbound port interfaces (`TranslationEnginePort`, `GlossaryPort`, `TranslationCachePort`) adhering to Hexagonal Architecture without any framework dependencies.

## Requirements
1. **REQ-1**: `TranslationEnginePort` must define a `translate` method taking input parameters (source text, source language, target language, engine type, etc. or command/request record) and returning translation results.
2. **REQ-2**: `GlossaryPort` must define a `getTerms` method (or equivalent glossary retrieval method) to fetch translation glossaries or terms for a modpack or language pair.
3. **REQ-3**: `TranslationCachePort` must define `find` and `save` methods for caching and retrieving cached translation entries.
4. **REQ-4**: All three port interfaces must reside in package `com.lucalzt.mctranslator.domain.port.out`.
5. **REQ-5**: Domain port interfaces and any associated domain request/response/value types must be pure Java with zero framework annotations (e.g., no Spring `@Service`, no JPA `@Entity`, etc.).

## Non-Goals
- Concrete implementations or infrastructure adapters (JPA repositories, REST clients, Redis/SQLite cache).
- Use case orchestration services.
