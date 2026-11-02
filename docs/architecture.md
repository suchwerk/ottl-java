# OTTL Java — Architecture

This document records the architectural decisions behind **ottl-java**, a native Java
implementation of the [OpenTelemetry Transformation Language (OTTL)](https://opentelemetry.io/blog/2026/ottl-v1/),
extended for arbitrary Java objects and with support for runtime-updatable rules and Quarkus.

> Status legend: ✅ implemented & tested · 🟡 partial/MVP · ⛔ skeleton/planned

## 1. Goals and non-goals

**Goals**
- A small, correct, well-tested OTTL subset that runs on plain Java (no framework required).
- First-class support for Java data shapes: `Map`, nested maps, POJOs/JavaBeans, records, Jackson `JsonNode`.
- Compile transformation rules once into immutable, thread-safe programs; **replace rules at runtime** without restart.
- A genuine Quarkus extension (runtime + deployment) with CDI wiring and native-image awareness.
- Security posture that assumes rule authors are **less trusted** than application developers.

**Non-goals (MVP)**
- Full OTTL v1 language coverage (see [Unsupported features](#8-unsupported-ottl-features)).
- OpenTelemetry pdata contexts (spans/metrics/logs) — module skeleton only (⛔ `ottl-context-otel`).
- A verified native binary — the architecture is native-ready but no GraalVM/Mandrel was available to
  prove it, so native support is **not claimed** (see [§7](#7-native-image-strategy)).

## 2. Module layout

```
ottl-language            ✅ lexer, recursive-descent parser, AST (pure Java, zero deps)
ottl-engine             ✅ evaluator, compiled programs, PropertyResolver SPI, FunctionRegistry,
                            rule manager/repository  (depends only on ottl-language)
ottl-functions-standard ✅ set / delete_key / keep_keys  (ServiceLoader-registered)
ottl-context-map        ✅ PropertyResolver for Map
ottl-context-bean       ✅ PropertyResolver for POJOs (mutate) and records (copy)
ottl-context-jackson    ✅ PropertyResolver for Jackson JsonNode
ottl-context-otel       ⛔ skeleton for OpenTelemetry pdata
quarkus-ottl/runtime    ✅ @OttlFunction, config, recorder, CDI producers
quarkus-ottl/deployment ✅ build steps: feature, function discovery, synthetic engine bean, native reg.
ottl-language-server    ✅ LSP4J core: diagnostics/completion/hover/signatures, shared type metadata
                            (reuses lexer/parser + registries; no Quarkus, no frontend deps)
ottl-lsp-quarkus        ✅ Quarkus WebSocket (WebSockets-Next) JSON-RPC transport, per-session LSP
examples/quarkus-example✅ REST app: CDI + runtime rule replacement + dev-only editor API + /ottl/lsp
examples/ottl-editor    ✅ Vue 3 / Quasar / Vite / Monaco editor (monaco-languageclient over WS)
ottl-conformance-tests  ✅ OTTL-standard vs Java-profile tests (kept in separate packages)
ottl-benchmarks         🟡 JMH micro-benchmarks (compile vs apply)
```

See [language-server.md](language-server.md) for the LSP / editor architecture.

The **core engine never depends on Quarkus**. Quarkus lives entirely in `quarkus-ottl/*` and the example.

## 3. Language: parser choice

The task suggested ANTLR4 "unless the reference implementation suggests a better approach." The
reference Go implementation does **not** use ANTLR — it uses a PEG/parser-combinator approach
(`participle`). We therefore use a **hand-written lexer + recursive-descent parser**
(`ottl-language`). Rationale:

- Mirrors the reference's combinator style more closely than a generated LL(*) grammar.
- No code-generation step, no generated-sources, no ANTLR runtime on the classpath — simpler builds
  and a smaller, obviously native-image-friendly footprint.
- Full control over error messages with precise `line:column` positions (see `OttlParseException`).

**Statements are self-delimiting**: every editor statement begins with `identifier(`, and a
`where` condition is a complete boolean expression. No statement terminator is needed, and a
statement may wrap across lines. A `where` block (`where <condition> { ... }`) is an ottl-java
extension: the condition is evaluated once, then every item in the braces runs. A trailing
`where` on a single editor statement is unchanged. Blocks and `if` / `else` are a further
extension, accepted only when the engine's [dialect](#10-ottl-extended-language) is `EXTENDED`.

### Grammar (implemented subset)

```
script      := item*
item        := block | ifStatement | whereBlock | statement
block       := "{" item* "}"                              # OttlDialect.EXTENDED
ifStatement := "if" condition block
               ("else" "if" condition block)*
               ("else" block)?                            # OttlDialect.EXTENDED
whereBlock  := "where" condition "{" item* "}"
statement   := functionCall ("where" condition)?
condition   := orExpr
orExpr      := andExpr ("or" andExpr)*
andExpr     := unaryCond ("and" unaryCond)*
unaryCond   := "not" unaryCond | "(" condition ")" | comparison
comparison  := value (("=="|"!="|"<"|"<="|">"|">=") value)?
value       := STRING | INT | FLOAT | "true" | "false" | "nil"
             | "-" (INT|FLOAT) | list | functionCall | path
list        := "[" (value ("," value)*)? "]"
path        := IDENT ("." IDENT | "[" (STRING|INT) "]")*
functionCall:= IDENT "(" (value ("," value)*)? ")"
```

## 4. Engine design

### Profiles and dialects
A **profile** chooses data shapes. A **dialect** chooses syntax. They are independent.

- `OttlProfile.STANDARD` — OTTL-compatible behavior (maps/attributes).
- `OttlProfile.JAVA` — STANDARD plus Java object access (POJOs, records, beans) and Java-only functions.
- `OttlDialect.OTTL` (default) — supported OTTL statements, a trailing `where`, and the existing
  `where condition { ... }` block. Bare `{ ... }` blocks and `if` / `else` are rejected.
- `OttlDialect.EXTENDED` — the OTTL dialect plus blocks and `if` / `else if` / `else`. This is not
  OpenTelemetry OTTL. See [§10](#10-ottl-extended-language).

### Property access SPI
`io.ottl.spi.PropertyResolver` is the extension point. Resolvers are discovered via `ServiceLoader`
(so the documented zero-wiring API works) and ordered by `priority()`:

| Resolver | Handles | Priority | Write semantics |
|---|---|---|---|
| `JsonNodeResolver` | `JsonNode` | 10 | mutate tree |
| `MapResolver` | `Map` | 20 | mutate in place |
| `RecordResolver` | records | 30 | **copy** via canonical constructor |
| `BeanResolver` | other objects | 100 | mutate via setters |

### Context name and paths
`program.apply(root)` binds `root` to a **context name** derived from the compiled type's simple name
(`Order` → `order`), or set explicitly. A path whose first segment equals the context name is
stripped, so both `order.status` and `status` address the same property. This makes the task's
`set(order.status, ...)` example work while keeping bare-attribute OTTL style available.

### Execution modes & copy-on-write bubbling
`MUTATE` (default, maps/POJOs) vs `COPY` (records, auto-selected). The engine implements a single
elegant mechanism for both: each `setField/setKey` returns the (possibly new) target. If the returned
child is a **new identity** (records), the engine rebuilds the parent chain up to the root
(copy-on-write "bubbling"); if it is the **same identity** (mutation), nothing bubbles. This yields
nested immutable-record updates (e.g. `set(shipment.address.city, "Berlin")`) for free.

### Functions
`io.ottl.function.OttlFunction` covers both **editors** (`set`, `delete_key`, `keep_keys`) and
**converters** (value functions). The `FunctionRegistry` is the **allowlist**: unknown functions fail
at compile time. Arguments are lazy (`Argument`), so `set`'s first argument can be used as an l-value
path while others evaluate as r-values.

### Compilation & caching
`OttlEngine.compile(...)` parses once, validates (arity, unknown functions, complexity limits), and
produces an immutable `CompiledProgram`. `apply` only walks the pre-parsed plan — **expressions are
never recompiled per execution**. Programs are immutable and safe to share across threads.

## 5. Runtime rule updates

- `RuleDefinition(id, source, version, updatedAt)` — immutable, versioned.
- `RuleRepository` — `find`/`save`; `InMemoryRuleRepository` for the MVP; interface kept minimal so a
  PostgreSQL/config-server implementation can be added later.
- `RuleManager<T>` — `activate(id, source)`, `apply(id, input)`, `active(id)`.
- `DefaultRuleManager` stores active programs in a `ConcurrentHashMap` and uses `compute(...)` so that
  **the new program is compiled before the swap**: if compilation fails, the map is untouched and the
  previous version is retained (validated by tests). Reads are lock-free; programs are immutable, so
  concurrent reads during an update are safe (covered by `ConcurrencyTest`).

## 6. Quarkus extension

- **runtime** (`quarkus-ottl`): `@OttlFunction` annotation, `OttlConfig` (`quarkus.ottl.*`),
  `OttlRecorder` (builds the shared `OttlEngine` at `RUNTIME_INIT`), generated function invokers, and a
  `@DefaultBean RuleRepository` producer.
- **deployment** (`quarkus-ottl-deployment`): `FeatureBuildItem("ottl")`, Jandex discovery of
  `@OttlAccessible` types and `@OttlFunction` methods, build-time validation, Gizmo accessors and
  function invokers, a synthetic `OttlEngine` bean, and a `@DefaultBean RuleManager<T>` for each
  approved type.
- **Generic injection**: applications inject `RuleManager<Order>` directly. The extension registers
  that parameterized bean as a `@DefaultBean`. An explicit `@Produces RuleManager<Order>` replaces it
  without an ambiguous-resolution error.
- **Functions**: `@OttlFunction` methods are validated at build time (visibility, duplicates, collisions
  with `set` / `delete_key` / `keep_keys`). Each method gets a generated invoker that calls it
  directly. Host classes with no scope become `@Singleton`.
- **No ServiceLoader in the extension**: the Quarkus engine uses `loadDefaults(false)` and does not
  register SPI providers into the native image. Maps, JSON, the standard editors and discovered
  functions are wired explicitly. Reflective bean/record resolvers stay available to the plain Java
  library and are not installed by the extension.

## 7. Native-image strategy

The design avoids runtime classpath scanning (discovery happens at build time via Jandex), avoids
dynamic class generation, and registers the needed reflection/service metadata in the deployment
module. Property accessors are cached and can be pre-populated via `PropertyResolver.prepare(type)`.

**However**, no GraalVM/Mandrel was available in this environment, so a native build and smoke test
were **not** run. Per the task, native support is therefore **not claimed**. To validate later:
`mvn -pl examples/quarkus-example -Dnative verify` on a machine with Mandrel, and register app domain
types with `@RegisterForReflection` (already done for `Order`).

## 8. Unsupported OTTL features

Explicitly **not** implemented in the MVP (documented honestly rather than faked):

- Math expressions (`+ - * /`), indexing math, and `Len`/`Concat`/most standard converters.
- OTTL pdata contexts (span/metric/log/resource/scope) — `ottl-context-otel` is a skeleton.
- Time/duration literals, byte-slice literals, `nil`-coalescing specifics. Enum constants are supported
  as expression values (`OrderStatus.PENDING`) when the enum is `@OttlAccessible`; the enum is not a
  context type.
- `where` combined with OTTL's full converter library; only the allowlisted functions are available.
- Statement-level `ottl`-config features (conditions list, error modes, telemetry settings).
- Full operator-precedence parity audits against the Go PEG grammar (we cover the common subset).
- Loops (`while`, `for`, `foreach`), recursive functions, arbitrary Java method calls, `let` bindings,
  and named rule blocks. Blocks are shaped so a future `let` can be lexical and immutable; they are
  not implemented.

See [conformance.md](conformance.md) for the reference cases currently exercised.

## 9. Security & performance

- **Allowlists**: only registered functions are callable; only declared properties / map entries /
  record components are accessible. There is **no path to arbitrary Java method invocation**.
- **No I/O**: expressions can only call curated functions — no file or network access.
- **Complexity bounds**: `OttlLimits` caps source length, editor-statement count (including statements
  nested in blocks and branches), path depth, and nesting of calls, `where` blocks, bare blocks, and
  `if` / `else if`. The parser enforces the same nesting limit, and refuses to recurse past 64 levels,
  before a program is executed.
- **Validation before activation**: `RuleManager.activate` compiles+validates before swapping.
- **Clear diagnostics**: parse errors carry `line:column`; evaluation errors name the offending path.
- **Performance**: immutable compiled plans, cached accessors, lock-free reads. JMH benchmarks live in
  `ottl-benchmarks`.

## 10. OTTL Extended Language

`if` / `else` and statement blocks are an **ottl-java extension**. They are not OpenTelemetry OTTL.
The default dialect is `OttlDialect.OTTL`, so existing programs keep parsing as they do today.
Opt in explicitly:

```java
OttlEngine engine = OttlEngine.builder()
        .profile(OttlProfile.JAVA)
        .dialect(OttlDialect.EXTENDED)
        .build();
```

`OttlDialect` lives in `io.ottl.lang` because the parser enforces it. The engine module already
depends on the language module; putting the enum in `io.ottl` would split that package across jars.

In the OTTL dialect the parser rejects the new syntax:

```text
Language feature 'if/else' requires OttlDialect.EXTENDED.
```

`where condition { ... }` stays valid in **both** dialects. It shipped before the dialect split, and
moving it would reject programs that already parse. It has the same execution shape as an `if`
without `else` (the condition runs once, then the body runs on the same root) but remains a
`WhereBlock` so existing AST consumers stay compatible. `if` / `else if` / `else` is an `IfStatement`.
`else if` is a nested `IfStatement`; `else` is a `Block`.

### Syntax

```text
{
    set(order.status, "REVIEW")
    set(order.priority, "HIGH")
}

if order.amount > 10000 {
    set(order.status, "REVIEW")
    set(order.priority, "HIGH")
} else if order.amount > 5000 {
    set(order.priority, "MEDIUM")
} else {
    set(order.priority, "LOW")
}
```

Rules:

- A block is zero or more statements, executed in order on the **same** root. Empty blocks are valid.
  Nested blocks do not create a new root or a new `ExecutionContext`.
- Conditional bodies require braces. There is no single-statement form.
- Only the first matching branch runs. Its condition is evaluated once. Skipped branches are not
  evaluated. `and` / `or` still short-circuit.
- Every branch is validated at compile time, including branches that will not run. An unknown or
  read-only property inside a nested block fails compilation.
- `if` and `else` are statement keywords in the extended dialect. `order.if` is still a path.
  In the OTTL dialect, `if(...)` remains an editor call.
- A lone `else`, a missing `{`, and an extra `}` are parse errors with a line and column.

### Examples

Java record (COPY). The original is left unchanged:

```java
OttlProgram<Order> program = engine.compile("""
        if order.amount > 10000 {
            set(order.status, "REVIEW")
        } else {
            set(order.status, "APPROVED")
        }
        """, Order.class);
```

JavaBean (MUTATE). `customer` is updated in place:

```java
engine.compile("""
        if customer.premium == true {
            set(customer.tier, "GOLD")
        } else {
            set(customer.tier, "BASIC")
        }
        """, Customer.class);
```

JSON (`JsonNode`, mutate the tree):

```java
engine.compile("""
        if order.amount > 10000 {
            set(order.status, "REVIEW")
        } else {
            set(order.status, "APPROVED")
        }
        """, JsonNode.class, "order");
```

Quarkus. The dialect is runtime configuration and is applied to every rule the engine compiles.
The extension default is `OTTL`. The example application sets `EXTENDED`:

```properties
quarkus.ottl.profile=JAVA
quarkus.ottl.dialect=EXTENDED
```

```java
rules.activate("order-rules", """
        if order.amount > 10000 {
            set(order.status, "REVIEW")
        } else {
            set(order.priority, "LOW")
        }
        """);
```

Activation still compiles before swapping. A program that fails to parse or that names an unknown
property does not replace the active rule.

### Limits and what is intentionally absent

`OttlLimits.maxStatements` counts editor calls in every branch. `maxCallDepth` counts each `where`
block, bare block, and `if` / `else if` as one level. The braces that belong to an `if` or `else` do
not add a second level. `maxCallDepth` must be between 1 and 64; the parser will not recurse past
that, so a deeply nested script cannot overflow the stack. There is no `while`, `for`, `foreach`,
recursive call, or arbitrary method invocation.

`let` and named `rule` blocks are not implemented. A `Block` is the place a future immutable
lexical `let` would be scoped. Nothing stores per-execution bindings today.
```
