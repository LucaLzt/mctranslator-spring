# Stack Tecnológico — mctranslator

Aplicación **CLI** para traducir modpacks de Minecraft de forma local. Corre como binario nativo sin servidor HTTP: recibe el modpack, traduce los textos contra motores locales y persiste el resultado en una caché SQLite junto al ejecutable. Este documento define el stack del desarrollo inicial (ago-2026).

## 1. Lenguaje y runtime base

| Componente   | Elección                                                            | Motivo                                                                                                                                                                                                                                                                                                                          |
|--------------|---------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Lenguaje     | **Java 25 LTS**                                                     | Baseline de GraalVM Native Image para Spring Boot 4 / Framework 7. Hay un check explícito que bloquea el arranque del binario nativo si se generó con Java <25. 25 es LTS, no se pierde estabilidad.                                                                                                                              |
| Framework    | **Spring Boot 4.x** (Spring Framework 7)                            | Versión más moderna y vigente en ago-2026. Base para la arquitectura hexagonal + DDD del proyecto.                                                                                                                                                                                                                              |
| Interfaz     | **Spring Shell 4.0** (no Spring Web)                                | La app corre como CLI, no como servidor HTTP. No levanta Tomcat/Netty → arranque instantáneo y footprint mínimo. Spring Shell 4.0 GA salió en diciembre 2025 y es compatible con Boot 4.                                                                                                                                        |
| Compilación  | **GraalVM Native Image** (`org.graalvm.buildtools.native`)          | Compila a binario nativo `.exe`/ELF. Arranque `<15ms`, RAM base `30-50 MB`.                                                                                                                                                                                                                                                     |
| Concurrencia | **Virtual Threads** (`Executors.newVirtualThreadPerTaskExecutor()`) | Paralelismo real al procesar múltiples claves/archivos JSON sin overhead de threads de SO. También usado para las llamadas HTTP al motor LLM sin bloquear el pool. Son una feature de la JVM (estabilizada desde JDK 21); Java 25 trae mejoras incrementales de Loom (menos pinning en código sincronizado, mejor diagnóstico). |
| Resiliencia  | **Retry + concurrency throttling integrados de Framework 7**        | Resiliencia de primera clase built-in. Se usa directo en las llamadas HTTP al `llama-server`, en lugar del retry manual sobre `HttpClient` (ver `PreciseLlmAdapter`).                                                                                                                                                          |
| Caché local  | **SQLite vía JDBC** (o H2 en modo archivo)                          | Persiste traducciones ya resueltas en un `.db` junto al ejecutable. Evita reprocesar el mismo término dos veces.                                                                                                                                                                                                                |

## 2. Motores de traducción — arquitectura dual

Se definen **dos implementaciones** de un mismo contrato (`TranslationEnginePort`), no una elección única de modelo.

### 2.1 Motor rápido (default) — NMT clásico

- **Modelo:** NLLB-200, variante **600M parámetros** (Meta).
- **Por qué NLLB y no MarianMT/Opus-MT:** soporta 200 idiomas con un solo modelo, mejor fidelidad gramatical, no requiere ingeniería de prompts — es entrada/salida de texto directa.
- **Formato:** exportado a **ONNX** (`model.onnx`).
- **Runtime Java:** `com.microsoft.onnxruntime:onnxruntime` — corre embebido en el mismo proceso nativo, sin subproceso externo.
- **Tamaño en disco:** ~1.2–2.5 GB.
- **RAM en ejecución:** ~1.5 GB.
- **Velocidad:** ~100 palabras/s.
- **Limitación clave:** es un modelo *seq2seq* puro, **no es promptable**. No entiende instrucciones tipo "no traduzcas este término". Solo cubre el problema de variables de código (`%s`, `{0}`) vía enmascarado por regex en el pipeline de traducción — **no cubre nombres propios de mods** (ítems, bosses, lugares).

### 2.2 Motor preciso (opcional, on-demand) — SLM instructivo

- **Modelo:** **Qwen3.5-4B-Instruct** (Alibaba, marzo 2026, licencia Apache 2.0).
- **Por qué este y no Llama 3.2 / Gemma 2:** serie más reciente al momento (ago-2026), fuerte específicamente en instruction-following multilingüe, licencia permisiva para uso comercial/portfolio.
- **Formato:** **GGUF cuantizado** (Q4_K_M como punto de partida), vía `llama.cpp`.
- **Por qué NO vía ONNX Runtime:** los SLM modernos se distribuyen en GGUF, no en ONNX. Convertir a ONNX es viable pero añade fricción de mantenimiento sin beneficio real.
- **Por qué NO vía JNI embebido en el binario nativo:** bindings JNI a una librería C++ (llama.cpp) dentro de GraalVM Native Image implica configuración de reflection/JNI compleja y acopla la compilación nativa a la arquitectura de CPU del que compila. Se descarta.
- **Integración elegida:** `llama-server` como **subproceso independiente** (`ProcessBuilder`), expuesto en `localhost` vía HTTP. La app nativa le habla con `java.net.http.HttpClient` sobre Virtual Threads.
- **Tamaño en disco:** ~2.0–2.8 GB (cuantizado Q4).
- **RAM en ejecución:** ~3.5–4.5 GB.
- **Velocidad:** ~30-50 tokens/s.
- **Ventaja clave:** es promptable. Recibe el glosario (`GlossaryPort`) inyectado directamente en el prompt del sistema — puede razonar sobre lore y contexto narrativo, no solo traducir literal.

## 3. Comparativa de referencia (RAM/VRAM por categoría de modelo)

| Categoría              | Ejemplos                     | Disco       | RAM/VRAM        | Velocidad       | Uso en mctranslator                                                                         |
|------------------------|------------------------------|-------------|-----------------|-----------------|---------------------------------------------------------------------------------------------|
| NMT especializado base | MarianMT/Opus-MT             | ~300 MB     | ~0.5 GB         | >200 palabras/s | Descartado — NLLB-200 lo supera en cobertura de idiomas                                     |
| **NMT avanzado**       | **NLLB-200 (600M)**          | ~1.2-2.5 GB | **~1.5 GB**     | ~100 palabras/s | **Motor rápido (elegido)**                                                                  |
| SLM ultraliviano       | Qwen3.5 1.7B, Gemma 4B chico | ~1.0-1.8 GB | ~1.8-2.5 GB     | ~50-80 tok/s    | Alternativa si 4-4.5GB de RAM del motor preciso resulta muy alto para el hardware target    |
| **SLM mediano**        | **Qwen3.5-4B-Instruct**      | ~2.0-2.8 GB | **~3.5-4.5 GB** | ~30-50 tok/s    | **Motor preciso (elegido)**                                                                 |
| LLM estándar (8B)      | Llama 3.1 8B, Qwen3-8B       | ~4.5-5.5 GB | ~6.5-8 GB       | ~15-30 tok/s    | Descartado para este proyecto — excede el target de "PC estándar de 16GB" con margen cómodo |

## 4. Techo de RAM combinado (peor caso real)

App nativa base (~50 MB) + NLLB residente (~1.5 GB) + LLM **solo si está activo** (~4 GB) ≈ **~5.5 GB máximo**, cómodo en el hardware target (16 GB RAM). El LLM nunca está cargado por defecto — ver nota de estrategia de implementación para el ciclo de vida del subproceso.

## 5. Tags de portfolio / GitHub

`Java 25` · `Spring Boot 4` · `Spring Shell 4` · `GraalVM Native Image` · `Virtual Threads` · `ONNX Runtime` · `llama.cpp` · `NLLB-200` · `Qwen3.5` · `SQLite` · `Arquitectura Hexagonal` · `Regex Engine`
