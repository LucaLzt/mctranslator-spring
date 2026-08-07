# Estrategia de Implementación — Orquestación Dual de Motores

Complemento de [tech-stack.md](tech-stack.md). Define cómo se orquestan los dos motores de traducción (`FastNllbAdapter` + `PreciseLlmAdapter`) y cómo se gestiona la RAM del motor pesado.

> **Decisiones de diseño (v1):**
> 1. **Proyecto nuevo, todo desde cero.** Ninguno de los componentes mencionados aquí existe todavía: puertos, adapters y orquestador se construyen en este proyecto. No hay código del prototipo para reutilizar.
> 2. **Validación empírica pendiente.** El dataset auditado de 30.000 entradas no existe aún; la sección de ajuste empírico queda marcada como pendiente.
> 3. **Precedencia explícita de reglas** (sección 3) para resolver conflictos de la heurística.
> 4. **Motor preciso opcional (flag).** En v1 el usuario lo activa explícitamente; el binario base funciona solo con el motor rápido.

---

## 1. Principio de diseño

No se trata de "dos motores para elegir manualmente". El sistema **decide solo** qué motor usar según la naturaleza de cada clave, y **gestiona el ciclo de vida del motor pesado** para no pagar su costo de RAM salvo cuando realmente hace falta. Esto es lo que diferencia el proyecto de una integración genérica de IA.

## 2. Arquitectura hexagonal — componentes

El proyecto se estructura en las tres capas clásicas. El dominio no conoce infraestructura: define puertos (interfaces) y los adapters los implementan.

```
com.mctranslator
├── domain                          (sin dependencias de infraestructura)
│   ├── model                       TranslationKey (path JSON + texto), GlossaryEntry, TranslationResult
│   ├── port                        TranslationEnginePort, GlossaryPort, TranslationCachePort
│   └── service                     ScalingHeuristic, VariableMasker / VariableUnmasker (regex __VAR_N__)
├── application                     (casos de uso / orquestación)
│   └── GlossaryAwareTranslator     pipeline por clave: caché → mask → heurística → motor → unmask → persistir
└── infrastructure                  (adapters)
    ├── adapter/in                  comandos CLI (Spring Shell 4)
    └── adapter/out
        ├── nllb                    FastNllbAdapter (ONNX, embebido)
        ├── llama                   PreciseLlmAdapter + LlamaServerProcessManager (ciclo de vida)
        ├── glossary                JsonGlossaryAdapter
        └── cache                   SqliteCacheAdapter (SQLite vía JDBC)
```

Reglas de la capa:
- **El dominio solo conoce puertos.** `TranslationEnginePort` es la interfaz que implementan ambos motores; `GlossaryPort` entrega los términos del glosario; `TranslationCachePort` abstrae la caché SQLite.
- **`GlossaryAwareTranslator` vive en aplicación**: orquesta el flujo, conoce la clave que está traduciendo (path JSON) y el texto asociado, y delega la decisión de motor a `ScalingHeuristic` (dominio, puro y testeable de forma aislada).
- **Los adapters son intercambiables.** Cambiar ONNX por otro runtime, el formato del glosario o el backend de caché no toca el dominio.

## 3. Heurística de auto-escalado

`ScalingHeuristic` recibe el `TranslationKey` (path + texto) y el glosario, y devuelve el motor sugerido. Las reglas se evalúan **en orden de precedencia (de mayor a menor)** — la primera que matchea decide. Esto resuelve los conflictos (ej. `quest.description.*` con texto corto → gana la regla 1 → preciso).

| Orden | Condición | Motor |
|---|---|---|
| 1 | Path matchea `quest.description.*`, `lore.*`, `advancement.*` | **Preciso (LLM)** |
| 2 | Texto contiene ≥1 término del glosario marcado como ambiguo | **Preciso (LLM)** |
| 3 | Texto > 30 palabras | **Preciso (LLM)** |
| 4 | Path matchea `item.*`, `block.*`, `entity.*`, `gui.*` | **Rápido (NLLB)** |
| 5 | Texto ≤ 8 palabras y sin términos de lore detectados vía glosario | **Rápido (NLLB)** |
| 6 | Default (cualquier otro caso) | **Rápido (NLLB)** |

Notas:
- **Flag de motor preciso apagado** (v1 por defecto): la heurística no se evalúa — todo va a rápido; las claves que hubieran ido a preciso se registran como `[WARN]` para revisión manual.
- **Modelo ausente en disco** (aún no descargado): se trata como motor no disponible → degrada a rápido con `[WARN]`.
- Esta tabla es punto de partida y se ajusta empíricamente — ver [Validación empírica (pendiente)](#9-validación-empírica-pendiente).

## 4. Ciclo de vida del subproceso LLM (gestión de RAM)

Este es el punto crítico de la estrategia — evita que el "peor caso" de RAM sea el caso normal.

### 4.1 Flag de activación (v1)
El motor preciso es **opcional**. Solo se considera cuando:
- El flag `mctranslator.precise-engine.enabled` está activo, y
- El modelo GGUF está presente en la ruta configurada.

Con eso, el binario base queda liviano y el usuario decide si activa el modo completo.

### 4.2 Carga perezosa (lazy start)
- El `llama-server` **no arranca junto con la app**.
- Se lanza vía `ProcessBuilder` recién en el primer request que la heurística deriva al motor preciso.
- Costo: unos segundos de arranque en frío la primera vez que se necesita. Aceptable en un flujo de traducción batch (no interactivo en tiempo real).

### 4.3 Apagado por inactividad (idle timeout)
- Se mantiene un timestamp del último request servido por el motor preciso.
- Un scheduler liviano (Virtual Thread con `Thread.sleep` en loop, o `ScheduledExecutorService`) revisa cada intervalo si superó el umbral de inactividad (punto de partida: **2 minutos**).
- Si se supera, se llama a `Process.destroy()` sobre el subproceso `llama-server`, liberando los ~3.5-4.5 GB.
- Si llega un nuevo request después del apagado, se vuelve a aplicar el lazy start (4.2).

### 4.4 Aislamiento de fallos
- Al estar en un proceso separado (no JNI embebido), si `llama-server` crashea o no responde, la app captura la excepción de HTTP, loguea, y **degrada al motor rápido** para esa clave en particular en vez de abortar toda la corrida.
- El fallback es explícito en el log de salida (ej. `[WARN] Motor preciso no disponible, key 'quest.desc.42' traducida con motor rápido`) para que el usuario sepa qué claves conviene revisar manualmente.

## 5. Flujo completo

```
[Comando CLI: modpack-translator translate -f modpack.json]
                    │
                    ▼
        1. Matcher de Caché Local (SQLite)
                    │
              NO ───┴─── SÍ → retorna string
              │
              ▼
        2. Regex Masker (variables __VAR_N__)
                    │
                    ▼
        3. Heurística de Escalado
        (flag preciso + path + longitud + glosario)
              │                    │
        RÁPIDO                 PRECISO
              │                    │
              ▼                    ▼
    4a. ONNX/NLLB-200      4b. ¿llama-server activo?
    (embebido, directo)         │           │
                                NO          SÍ
                                 │           │
                        Lazy start    Request HTTP directo
                        + glosario
                        inyectado en
                        prompt system
                                 │           │
                                 └─────┬─────┘
                                       ▼
                          5. Regex Unmasker
                                       │
                                       ▼
                          6. Guardar en SQLite Caché
                                       │
                                       ▼
                          [Escribir en JSON Final]

  (paralelo, fuera del flujo por clave)
  Scheduler de idle-timeout → Process.destroy() del
  llama-server tras 2 min sin requests
```

## 6. Orden sugerido de implementación

1. **Dominio y puertos:** `TranslationKey`, `TranslationEnginePort`, `GlossaryPort`, `TranslationCachePort` + `VariableMasker`/`Unmasker` y `ScalingHeuristic` (tabla de reglas con precedencia, testeable de forma aislada sin infraestructura).
2. **`FastNllbAdapter`** (ONNX embebido): motor rápido funcionando de punta a punta.
3. **`PreciseLlmAdapter` + `LlamaServerProcessManager`**: arranque del subproceso, cliente HTTP, inyección de glosario en el prompt del sistema, detrás del flag.
4. **Scheduler de idle-timeout** + manejo de `Process.destroy()`.
5. **Fallback ante fallo del motor preciso** + logging explícito (`[WARN]` por clave).
6. **Validación empírica** — pendiente hasta contar con el dataset auditado (ver sección 9).

## 7. Métricas a loguear (para portfolio y debugging)

- % de claves resueltas por caché vs. motor rápido vs. motor preciso.
- Tiempo de arranque en frío del `llama-server` cuando se activa.
- Tiempo total de la corrida y RAM pico observada (útil para documentar en el README con datos reales, no solo teóricos).
- Cantidad de fallbacks por fallo del motor preciso (para medir confiabilidad).

## 8. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| RAM del motor preciso como caso normal | Flag opcional (v1 apagado) + lazy start + idle timeout de 2 min |
| llama-server crashea / no responde | Proceso separado + degradación a motor rápido por clave + `[WARN]` explícito |
| Heurística deriva mal (calidad pobre) | Precedencia explícita + validación empírica pendiente + fallback manual vía logs |
| Arranque en frío lento del LLM | Aceptado: flujo batch, no interactivo; métrica logueada |
| ONNX embebido en GraalVM Native Image | Requiere configuración de recursos/reflection del runtime — tratar como spike técnico temprano |

## 9. Validación empírica (pendiente)

El dataset auditado de 30.000 entradas **no existe todavía**; esta sección se completa cuando esté disponible. Objetivo:

- Medir en qué % de claves la heurística deriva bien vs. mal (motor rápido que produce resultados pobres → debería haber ido a preciso, y viceversa).
- Ajustar umbrales y precedencia de la sección 3 con datos reales.
- Documentar los resultados (métricas de la sección 7) en el README.

## 10. Ideas futuras — Cascaded LLM routing (aún por pensar)

> **Estado: idea en evaluación. NO se implementa en v1.** Esto no es una decisión tomada; se deja anotado para no perderlo.

**Motivación:** la heurística estática (sección 3) puede dejar pasar casos que no contempla (ej. claves con lore narrativo en paths inesperados). Una evolución posible es un **router LLM ultra-liviano** (0.6B–1.7B) que reciba la clave + texto + glosario y decida por cada clave si va al motor rápido o al preciso — patrón de *cascaded LLMs* (un modelo barato filtra, y solo lo dudoso sube al caro).

**Criterio de activación (lo que decidimos pensar):**
- Primero se **valida empíricamente el comportamiento de la heurística estática** contra el dataset auditado (sección 9).
- Solo si el % de derivaciones incorrectas lo justifica, se evalúa agregar el cascade. No se decide a priori.

**Formas posibles de activación (por decidir — nada elegido):**
- **Flag manual:** el usuario lo levanta solo si tiene una computadora potente (ej. `mctranslator.cascade.enabled`).
- **Automático:** la app decide por sí sola cuándo conviene el cascade.
- **Detección de hardware con permiso:** con consentimiento explícito del usuario, la app inspecciona los recursos del equipo (RAM total/disponible, CPU, GPU) y decide si el cascade es viable o si sigue con la heurística estática.

**Costos a considerar antes de decidir:**
- RAM extra de un tercer modelo (aunque sea liviano, ~1.5–2.5 GB).
- Latencia por clave en flujo batch si se aplica a todas las claves — mitigación posible: aplicarlo solo a las claves "grises" que la heurística no resuelve con confianza.
- Pérdida de determinismo del pipeline (el cascade es probabilístico; la heurística es reproducible).
- Alternativa más barata a evaluar primero: **clasificador entrenado** (embeddings + regresión logística o fine-tune chico) con el dataset auditado; el cascade solo si el clasificador no alcanza.
- El router puede devolver **confianza** (leer logits en 1 token): con confianza baja → cae al preciso (sesgo conservador).
