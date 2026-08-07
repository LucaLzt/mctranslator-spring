# AGENTS.md — mctranslator

Guía de trabajo para agentes de IA en este repositorio. Complementa el AGENTS.md global de OpenCode.

## Arquitectura y skills obligatorias

Este proyecto adopta las siguientes skills de OpenCode. **Cárgalas y aplícalas antes de escribir código** en el área correspondiente:

| Área | Skill | Cuándo aplicarla |
|---|---|---|
| Estructura de paquetes | `hexagonal-architecture` | Siempre que se agregue/refactorice código: capas `domain` / `application` / `infrastructure`, puertos (`port/in`, `port/out`) y adaptadores. El dominio es Java puro, sin anotaciones de Spring. |
| Modelado de dominio | `domain-driven-design` | Modelos ricos, value objects, agregados, eventos de dominio. Prohibido el CRUD anémico. |
| Setup del proyecto | `java-springboot` | Dependencias, configuración, estructura Maven. Stack: Java 25, Boot 4.x, Spring Shell 4.0. |
| Documentación | `java-docs` | Javadoc obligatorio en clases y métodos públicos. |
| Testing | `spring-boot-testing` | JUnit 6, AssertJ, Testcontainers, `@MockitoBean` (no `@MockBean`), slices. |

## Workflow SDD (OpenSpec)

- Las especificaciones y propuestas de cambio viven en `openspec/` y se gestionan con el CLI `openspec`.
- Instalación: `pnpm add -g @fission-ai/openspec@latest` (usar siempre pnpm).
- Ciclo: `/opsx-propose` → revisión → `/opsx-apply` → `/opsx-archive`. Comandos en `.opencode/commands/`.
- No editar código durante la fase de proposición; solo planificar.

## Convenciones del código

- Paquete base: `com.lucalzt.mctranslator`.
- Inyección de dependencias por constructor con campos `private final`.
- Sin servidor web: la app es CLI pura (Spring Shell), sin Tomcat/Netty.
- Caché local en SQLite junto al ejecutable.
- Commits convencionales: `type(scope): subject` (ej. `feat(cli): add translate command`).
