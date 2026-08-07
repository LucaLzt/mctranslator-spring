# Estándares de Desarrollo — Skills de Arquitectura Adoptadas

Documento de referencia para la adopción formal de skills de OpenCode en el proyecto mctranslator. Las skills son cargadas por los agentes antes de escribir código; este documento las resume y fija el contrato de aplicación por área.

## 1. Skills adoptadas

| Skill | Ubicación | Rol en el proyecto |
|---|---|---|
| `hexagonal-architecture` | `~/.config/opencode/skills/hexagonal-architecture/` | Estructura de capas, puertos y adaptadores |
| `domain-driven-design` | `~/.config/opencode/skills/domain-driven-design/` | Modelado de dominio rico (no CRUD anémico) |
| `java-springboot` | `~/.config/opencode/skills/java-springboot/` | Setup del proyecto (Java 25, Boot 4, Spring Shell 4) |
| `java-docs` | `~/.config/opencode/skills/java-docs/` | Javadoc obligatorio en APIs públicas |
| `spring-boot-testing` | `~/.config/opencode/skills/spring-boot-testing/` | JUnit 6, AssertJ, Testcontainers, slices |

> La lista completa de skills instaladas globalmente se mantiene en el AGENTS.md global de OpenCode. La aplicación obligatoria por área se define en el `AGENTS.md` del repo.

## 2. Arquitectura hexagonal — contrato por capa

```
com.lucalzt.mctranslator
├── domain/                        Java puro. Cero anotaciones de Spring.
│   ├── model/                     Entidades, value objects, agregados
│   ├── port/in/                   Puertos conductores (interfaces de caso de uso)
│   ├── port/out/                  Puertos conducidos (repositorios, motores, caché)
│   └── service/                   Servicios de dominio puros (ej. ScalingHeuristic, VariableMasker)
├── application/                   Spring permitido aquí.
│   └── usecase/                   @Service que implementan puertos `in`
└── infrastructure/                Detalles de framework/IO
    ├── adapter/in/                Comandos Spring Shell 4 (driving adapters)
    └── adapter/out/               Implementaciones de puertos `out`
        ├── nllb/                  FastNllbAdapter (ONNX, embebido)
        ├── llama/                 PreciseLlmAdapter + LlamaServerProcessManager
        ├── glossary/              JsonGlossaryAdapter
        └── cache/                 SqliteCacheAdapter (SQLite vía JDBC)
```

Reglas invariantes:
- **El dominio no conoce infraestructura.** Los puertos son interfaces; los adapters las implementan.
- **Nada de `jakarta.persistence` ni Spring en `domain/`.** Los `@Transactional` viven en `application/`, no en servicios de dominio.
- **Los adapters son intercambiables** sin tocar dominio (cambiar runtime ONNX, formato de glosario o backend de caché no modifica el núcleo).
- **Un puerto por contrato:** `port/in` = lo que la app ofrece, `port/out` = lo que la app necesita.

## 3. Domain-Driven Design — modelo de dominio

- **Value objects inmutables** como records con validación en el constructor compacto (ej. `TranslationKey`, `GlossaryEntry`, `TranslationResult`).
- **IDs tipados** (value objects), no `Long`/`String` sueltos.
- **Agregados pequeños**: acceso a hijos solo vía la raíz del agregado; referencias entre agregados por ID.
- **Comportamiento en el objeto de dominio**, no en servicios con getters/setters.
- **Eventos de dominio** inmutables, publicados después del commit (Spring Data `@DomainEvents` o `@TransactionalEventListener(AFTER_COMMIT)`).
- **Nullabilidad explícita**: paquetes de dominio anotados `@NullMarked` (JSpecify, estándar Framework 7); solo valores excepcionales marcan `@Nullable`.
- Modelos ya previstos en la estrategia: `TranslationKey`, `GlossaryEntry`, `TranslationResult` (ver `docs/architecture/implementation-strategy.md`).

## 4. Java / Spring Boot / Spring Shell

- **Java 25 LTS**, baseline de GraalVM Native Image (check de arranque bloquea Java <25).
- **Spring Boot 4.x** (parent en `pom.xml`) con **Spring Shell 4.0** via `spring-shell-starter` (no `spring-boot-starter-web*`: la app es CLI pura, sin Tomcat/Netty).
- **Inyección por constructor** con campos `private final`; sin `@Autowired` en campos.
- **Configuración externa** en `application.yaml`; propiedades tipadas con `@ConfigurationProperties` cuando haga falta.
- **Logging SLF4J** con mensajes parametrizados (`logger.info("... {}", arg)`).
- El bootstrap del CLI vive en `MctranslatorApplication` + comandos `@ShellComponent` en `infrastructure/adapter/in/`.

## 5. Javadoc

- Javadoc obligatorio en clases y métodos públicos/protegidos.
- Primera frase = resumen conciso terminado en punto.
- `@param` (minúscula inicial, sin punto final), `@return`, `@throws` donde corresponda.
- `{@code}` para snippets inline; `<pre>{@code ... }</pre>` para bloques.
- Paquetes nuevos con `package-info.java` documentando su rol en la capa.

## 6. Testing

- **JUnit 6 + AssertJ** (vía `spring-boot-starter-test`).
- **`@MockitoBean`** en slices (nunca `@MockBean`, deprecado en Boot 4).
- **Pirámide**: unit (dominio puro, sin Spring) > slice > integración con Testcontainers.
- Dominio testeable de forma aislada: `ScalingHeuristic` y `VariableMasker`/`Unmasker` son puros (regex/predicados), sin infraestructura.
- Slices Boot 4 requieren los starters modulares (ej. `spring-boot-starter-webmvc-test` si algún día se expone API).
- Contexto: para esta app CLI el test base es `@SpringBootTest` cargando el contexto de Spring Shell.

## 7. Verificación del entorno

- OpenSpec CLI: `openspec --version` (instalado con `pnpm add -g @fission-ai/openspec@latest`).
- Estado del repo: `openspec doctor`.
- Build: `./mvnw clean verify` (Linux/macOS) o `mvnw.cmd clean verify` (Windows).
