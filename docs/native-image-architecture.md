# Native Image Architecture

How OTTL-Java compiles and runs OTTL rules inside a GraalVM/Mandrel **native executable**, while
keeping rules fully editable and recompilable **at application runtime**.

The guiding principle:

> **Java types and their property accessors are known and prepared at Quarkus _build time_;
> OTTL rules remain fully editable and compilable at application _runtime_.**

---

## 1. Build time vs. runtime

| Phase | What happens | Where |
|-------|--------------|-------|
| **Build time** (Quarkus augmentation) | Discover `@OttlAccessible` types; generate a reflection-free `PropertyAccessor` per type; build the immutable `PropertyAccessorRegistry`; discover `@OttlFunction` methods; register the minimal reflection/service metadata the image needs. | `quarkus-ottl-deployment` |
| **Native image generation** | GraalVM/Mandrel compiles the closed-world image, including the OTTL parser, evaluator, generated accessors, and registry. | Mandrel builder container |
| **Runtime** (inside the native executable) | Parse OTTL source → AST → **semantic validation against the registry** → immutable execution plan; activate plans atomically; execute transformations; hot-swap rules on demand. | `ottl-engine` + `quarkus-ottl` runtime |

Nothing about rule *content* is frozen into the image. Only the *set of accessible Java types and
functions* is fixed at build time — exactly the closed-world information GraalVM requires.

```
                         BUILD TIME                      │                 RUNTIME (native)
                                                         │
  @OttlAccessible types ──► Gizmo accessor generation    │   OTTL source ─► Parser ─► AST
            │                        │                    │                             │
            ▼                        ▼                    │                             ▼
   reflection registration   GeneratedAccessorRegistry    │                  SemanticValidator
   (Jackson (de)serialization)   (@Singleton bean)         │             (checks AST vs. registry)
            │                        │                    │                             │
            └────────► native image ◄┘                    │                             ▼
                                                         │                 Immutable ExecutionPlan
  @OttlFunction methods ──► FunctionMetadata (recorded)   │                             │
                                                         │                             ▼
                                                         │        AtomicReference swap ► POJO / Record / Map / JSON
```

---

## 2. Reflection / MethodHandle / ServiceLoader / bytecode audit

The original prototype relied on mechanisms that are awkward under native image. The native path
removes or constrains each of them:

| Mechanism | Prototype (JVM) | Native path |
|-----------|-----------------|-------------|
| **Property access reflection** | `BeanResolver` / `RecordResolver` use reflective getters/setters & canonical constructors. | **Not used.** Replaced by build-time **generated accessors** (`AccessorGenerator` + Gizmo). The reflective resolvers remain available for plain JVM/library use but are **not wired** into the Quarkus engine. |
| **`ServiceLoader`** | Engine discovers resolvers & functions via SPI. | **Disabled** in the Quarkus engine (`loadDefaults(false)`). Resolvers and functions are wired in `OttlRecorder`. SPI providers are **not** registered into the native image, so reflective bean/record resolvers cannot bypass the generated accessors. |
| **`@OttlFunction` dispatch** | Reflective `Method.invoke`. | **Direct call** from a Gizmo-generated `@Singleton` invoker. Arc injects the host bean (a client proxy when the host is normal-scoped). |
| **Runtime bytecode generation** | none | **none** — explicitly avoided. Accessors are generated at *build* time only. |
| **Runtime class loading / Java compiler** | none | **none.** The parser is a hand-written recursive-descent parser (no ANTLR), so no runtime codegen. |
| **`MethodHandle`** | none | none |
| **Jackson** | reflective (de)serialization | `@OttlAccessible` types are registered via `ReflectiveClassBuildItem` (methods + fields + constructors); response DTOs use `@RegisterForReflection`. |

---

## 3. The accessor registry

### `PropertyAccessor` (SPI, `ottl-engine`)

```java
public interface PropertyAccessor {
    Object get(Object instance, String property);
    Object set(Object instance, String property, Object value); // returns same (POJO) or new (record)
    String descriptor();                                        // "name:typeFqn:flags;..." (flags: r|w|rw)
}
```

`set(...)` returns the (possibly new) instance. POJOs mutate in place and return the same identity;
records return a **new** instance built from the canonical constructor. The engine uses this identity
signal to *bubble* rebuilt children up through their parents, implementing COPY semantics generically
for nested immutable records (see `ExecutionContext.setRec`).

### `PropertyAccessorRegistry`

```java
public interface PropertyAccessorRegistry {
    boolean contains(Class<?> type);
    PropertyAccessor accessor(Class<?> type);
    TypeMetadata metadata(Class<?> type);   // parsed from the accessor descriptor
    Class<?> typeByName(String fqn);         // for nested-type navigation during validation
}
```

`DefaultPropertyAccessorRegistry` is a plain map-backed implementation. At build time the extension
generates a `@Singleton` subclass (`GeneratedAccessorRegistry`) whose constructor instantiates each
generated accessor with `new` and registers it — **zero runtime reflection**.

### How accessors are generated (`AccessorGenerator`)

For each `@OttlAccessible` type the deployment step loads the class (build-time reflection is fine)
and emits a `PropertyAccessor` implementation with [Gizmo](https://github.com/quarkusio/gizmo):

- **Records**: `get` calls the component accessors; `set` builds a new instance via the canonical
  constructor, reading unchanged components and coercing the changed one (`ValueCoercion`).
- **POJOs/JavaBeans**: `get` calls `getX()` or, for booleans, `isX()` when both exist and agree;
  `set` calls the single `setX(...)` and returns the same instance (MUTATE).
- Public concrete classes and records get accessors. A public `@OttlAccessible` enum does not: its
  constants are loaded with `getstatic` into an enum registry so rules can name `EnumName.CONSTANT`.
  The enum is not a context and has no rule manager. Interfaces, abstract classes, local classes and
  types that are not public (including a public nested type inside a non-public outer type) fail
  augmentation. Static methods are ignored. Bridge and synthetic methods are ignored so generic
  overrides keep the specialized signature.
- Overloaded setters, and a getter/setter pair whose types differ, fail augmentation. The message
  names the class, the property and the conflicting signatures. There is no implicit coercion between
  a getter type and a different setter type. Array properties are rejected because the descriptor
  format cannot represent them.
- Accessor class names keep distinct binary names distinct (`a.b.Foo_Bar` and `a.b.Foo$Bar` do not
  collide).
- Primitives are boxed/unboxed explicitly; `ValueCoercion` handles `Number`, `String`, `BigDecimal`,
  `Boolean`, enums, `char`, etc.
- The `descriptor()` string encodes each property's name, declared type, and read/write flags; the
  registry parses it into `TypeMetadata` for semantic validation.

---

## 4. Runtime compilation pipeline

```
OTTL source
   │ (Parser — hand-written recursive descent, runs at runtime)
   ▼
AST (OttlScript)
   │ (Validator — arity/limits)  +  (SemanticValidator — strict, registry-backed)
   ▼
Immutable CompiledProgram  ──►  held in an AtomicReference by the RuleManager
   │
   ▼
execute: ExecutionContext per apply() → resolvers → PropertyAccessor → POJO/Record/Map/JSON
```

- The **parser executes at runtime** — new rules can be submitted after the native image has started.
- **Semantic validation** (`SemanticValidator`, enabled via `strictTypes(true)`) walks every path in
  the AST and checks it against the registry: unknown properties, non-writable write targets, and
  navigation into non-accessible types are rejected **before activation**. Maps/`JsonNode`/`Object`
  are treated as dynamic and allowed.
- `CompiledProgram` is **immutable and thread-safe**; a fresh `ExecutionContext` is created per call.

---

## 5. Quarkus extension responsibilities

**Build time** (`OttlProcessor`):
- `feature()` — registers the `ottl` feature.
- `accessibleTypes()` — discovers `@OttlAccessible` via Jandex, rejects types and properties the
  generator cannot support, generates accessors (`GeneratedClassBuildItem`), generates the registry
  bean (`GeneratedBeanBuildItem`), marks it unremovable, and registers the domain types for Jackson
  reflection (constructors, methods and fields).
- `engine()` (`@Record(RUNTIME_INIT)`) — validates `@OttlFunction` methods, generates a CDI invoker
  per method, registers host beans, and records the synthetic `OttlEngine` bean.
- `ruleManagers()` — records a `@DefaultBean` `@Singleton` `RuleManager<T>` for each approved type.
  An application producer of the same parameterized type is preferred.
- `defaultBeans()` — the in-memory rule repository, overridable by an application `RuleRepository` bean.

**Runtime** (`OttlRecorder`, `RUNTIME_INIT`):
- Reads `OttlConfig` via `SmallRyeConfig.getConfigMapping` (**runtime** config, never frozen).
  Non-positive or unbounded limit values fail startup. Strict type checking is not configurable.
- Fetches the generated `PropertyAccessorRegistry` from Arc.
- Builds the engine with `loadDefaults(false)`, the accessor registry, `strictTypes(true)`, the
  `MapResolver` and `JsonNodeResolver`, the standard editors, and every generated `@OttlFunction`
  invoker.

Custom property resolvers are not a Quarkus extension point. Add functions with `@OttlFunction`.
The plain Java library still discovers resolvers and functions through `ServiceLoader`.

Mutable rule state (the active `CompiledProgram` per rule id) lives in the `RuleRepository`/
`RuleManager`, created at **runtime init** — it is never captured into the image heap.

---

## 6. Runtime rule hot swapping

`RuleManager.activate(id, source)`:
1. Compiles and validates the new source (parser + validators) **before** touching active state.
2. On success, atomically replaces the active `CompiledProgram` (versioned, `AtomicReference`).
3. On failure, throws `OttlCompilationException`; the **previous version stays active**.
4. Concurrent `apply(...)` calls always observe one complete program version.

This is verified by the native integration tests (`OttlNativeIT`), including a concurrency test that
hammers transforms while a swapper thread flips the active rule.

---

## 7. Security model

- **Allowlisted types** — only `@OttlAccessible` types are resolvable in strict mode; anything else is
  rejected at compile time (`"… is not registered as @OttlAccessible"`).
- **Allowlisted properties** — only properties present in the generated descriptor are accessible;
  writes require a writable flag.
- **Allowlisted functions** — only registered standard editors and discovered `@OttlFunction` methods
  can be called; unknown functions fail validation.
- **No arbitrary method invocation** — resolvers expose declared properties / map entries / record
  components only. There is no path syntax that calls an arbitrary Java method.
- **No file/network/process access** through expressions.
- **Complexity limits** — `OttlLimits` bounds source length, statement count, and path/argument depth.
- **No silent reflection fallback** — the reflective bean/record resolvers are *not* wired into the
  native engine, so an unregistered type cannot "accidentally" resolve.

> The rule-management REST endpoints and the editor API live in the example application, not in the
> extension. They compile arbitrary rule source and are **development-only**. The extension exposes
> no HTTP routes. Secure any management API you add (authn/authz, network policy) before production
> use. Compilation limits come from `quarkus.ottl.max-source-length` and `quarkus.ottl.max-statements`.

---

## 8. Building the native executable

Requires a GraalVM/Mandrel toolchain, **or** a container runtime (Docker/Podman) for a container build.

```bash
# Local toolchain (GRAALVM_HOME / native-image on PATH):
./mvnw verify -Dnative -pl examples/quarkus-example -am

# Container build (no local toolchain needed; requires Docker/Podman running):
./mvnw verify -Dnative -Dquarkus.native.container-build=true -pl examples/quarkus-example -am
```

The example module's `native` profile sets `quarkus.native.enabled=true`,
`quarkus.native.container-build=true`, and `skipITs=false`, and uses the Mandrel builder image that
matches the project's Quarkus version.

---

## 9. Native integration tests (acceptance criterion)

`examples/quarkus-example/src/test/java/io/ottl/example/OttlNativeIT.java` is a
`@QuarkusIntegrationTest` that runs against the **actual compiled native executable** (not the JVM):

1. **Initial rule** — submit a rule via REST, transform an `Order`.
2. **Runtime modification** — submit a new rule after startup, verify the new rule is used.
3. **Invalid rule** — unknown property is rejected; previous version stays active.
4. **POJO transformation** — JavaBean mutated through setters.
5. **Record transformation** — COPY semantics; untouched components preserved.
6. **Nested object access** — `shipment.address.city` update reconstructs both records.
7. **Concurrent execution** — transforms while repeatedly swapping rules; every response observes a
   complete version.
8. **Native restrictions** — unknown properties, nested unknown paths, and unregistered functions are
   rejected.

---

## 10. Native compatibility checklist

| Concern | Status |
|---------|--------|
| Parser (no ANTLR) | Hand-written recursive descent; pure runtime, no codegen. |
| Jackson serialization | `@OttlAccessible` types + DTOs registered for reflection. |
| Java record constructors | Generated accessors use canonical constructors (registered via Gizmo, not reflection). |
| `MethodHandle` | Not used. |
| `ServiceLoader` | Disabled in the engine; explicit wiring in the recorder. |
| CDI bean init | Registry = generated `@Singleton`; engine = synthetic `RUNTIME_INIT` bean. |
| Static vs runtime init | Mutable rule state created at `RUNTIME_INIT`, never frozen. |
| Quarkus-generated bytecode | Accessors + registry generated at build time via Gizmo. |

---

## 11. Performance

- Rules are parsed/compiled **once** per activation; execution reuses the immutable plan.
- Accessors are cached in the registry and invoke getters/setters/constructors directly.
- Active programs are swapped via `AtomicReference`; readers never block.
- No per-call reflection and no runtime bytecode generation.

---

## 12. Troubleshooting native builds

- **`No serializer found for class X` / `InvalidDefinitionException`** — a REST request/response DTO
  isn't registered for reflection. Annotate it with `@RegisterForReflection` (domain types used in
  rules should instead be `@OttlAccessible`).
- **`… is not registered as @OttlAccessible`** — the engine is in strict mode and the type has no
  generated accessor. Add `@OttlAccessible` to the type.
- **`Unknown property '…'`** — the property isn't in the generated descriptor (typo, or not a
  bean property / record component).
- **`Port already bound`** during tests — a stale process is holding the test port; stop it and re-run.
- **Container build can't find Docker** — start Docker/Podman, or install a local Mandrel toolchain
  and drop `-Dquarkus.native.container-build=true`.
