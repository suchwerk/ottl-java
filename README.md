# ottl-java

A native **Java implementation of the [OpenTelemetry Transformation Language (OTTL)](https://opentelemetry.io/blog/2026/ottl-v1/)**,
extended to transform arbitrary Java objects — `Map`s, POJOs, Java records and Jackson `JsonNode`
trees — with **rules that can be compiled and replaced at runtime** and a first-class **Quarkus**
extension.

> **Status:** working MVP. `./mvnw verify` is green. This README distinguishes
> ✅ *verified* functionality from 🟡 *partial* and ⛔ *planned* work; see
> [docs/architecture.md](docs/architecture.md).

## Why

OTTL is a concise language for conditional data transformation. This project brings it to the JVM as a
small embeddable engine, adds a **JAVA profile** for domain objects, and makes rules **hot-swappable**
so you can change transformation logic without redeploying.

## Highlights

- ✅ OTTL syntax: literals (string, int, float, bool, `nil`, `0x` bytes), lists and maps, paths with
  constant or computed indexes, comparisons, boolean logic, arithmetic (`+ - * /`), named arguments,
  converter indexing, lambdas, `where`, sequential statements, `where { }` blocks, enum constants
  (`OrderStatus.PENDING` and a bare `SPAN_KIND_SERVER` when it is unambiguous), the `set` /
  `delete_key` / `keep_keys` / `append` / `clear` / `delete_index` / `delete_matching_keys` /
  `keep_matching_keys` / `flatten` / `limit` / `merge_maps` / `stringify_all` / `truncate_all` /
  `replace_match` / `replace_all_matches` / `replace_pattern` / `replace_all_patterns` editors, and
  the converters `Concat`, `ConvertCase`, `Double`, `Int`,
  `IsBool`, `IsDouble`, `IsList`, `IsMap`, `IsMatch`, `IsString`, `Len`, `Split`, `String`,
  `Substring`, `Trim`, `TrimPrefix`, and `TrimSuffix`. The rest of the collector function library
  is not bundled. No built-in function takes a lambda.
- ✅ Optional **extended dialect** (`OttlDialect.EXTENDED`): statement blocks and `if` / `else if` /
  `else`. This is not official OTTL. The default dialect stays `OTTL`.
- ✅ Two profiles: **STANDARD** (OTTL-compatible) and **JAVA** (POJOs/records/beans/JSON). Profiles
  choose data shapes; the dialect chooses syntax.
- ✅ **MUTATE** vs **COPY** execution — records use copy-on-write, including nested records.
- ✅ Pluggable property access (`PropertyResolver` SPI), discovered via `ServiceLoader`.
- ✅ Runtime-updatable rules: versioned, validated-before-activation, atomic swap, previous version
  retained on failure, thread-safe concurrent reads.
- ✅ Genuine Quarkus extension (runtime + deployment), CDI injection, `@OttlFunction`, `quarkus.ottl.*`
  config, and a working REST example.
- ✅ **Language Server (LSP4J)** with real diagnostics, completion, hover and signature help, served
  over a **Quarkus WebSocket** transport, plus a **Vue 3 / Quasar / Monaco** rule editor — all reusing
  the real parser, compiler and type registry (no duplicated schema). See
  [docs/language-server.md](docs/language-server.md).
- 🟡 JMH benchmarks skeleton.
- ⛔ OpenTelemetry pdata contexts.

## Quick start

### Requirements
- Java 21 (the build is pinned to `--release 21`)
- Maven (or the bundled wrapper `./mvnw`)

### Build & test everything
```bash
./mvnw verify
```

### Use the engine (library)

```java
OttlEngine engine = OttlEngine.builder()
        .profile(OttlProfile.JAVA)
        .build();

OttlProgram<Order> program = engine.compile("""
        set(order.status, "REVIEW")
          where order.amount > 10000
        """, Order.class);

Order updated = program.apply(order); // records: returns a NEW Order; original untouched
```

Maps (STANDARD profile):
```java
OttlEngine engine = OttlEngine.builder().profile(OttlProfile.STANDARD).build();
OttlProgram<Map<String,Object>> p =
        engine.compile("set(attributes[\"env\"], \"prod\")", (Class<Map<String,Object>>)(Class<?>)Map.class);
p.apply(myMap); // mutates in place
```

POJOs mutate in place; records copy; Jackson `JsonNode` trees mutate — all through the same API.

### Runtime rule replacement

```java
RuleManager<Order> rules = engine.ruleManager(Order.class, new InMemoryRuleRepository());
rules.activate("order-rules", "set(order.status, \"REVIEW\") where order.amount > 10000");
Order a = rules.apply("order-rules", order);           // version 1

rules.activate("order-rules", "set(order.priority, \"URGENT\")"); // atomic swap, version 2
Order b = rules.apply("order-rules", order);           // uses the new rule immediately
```
If the new source fails to compile, the previous version is retained.

## Quarkus

Add the extension:
```xml
<dependency>
  <groupId>io.ottl</groupId>
  <artifactId>quarkus-ottl</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Inject the engine and a rule manager. The extension supplies {@code RuleManager<T>} for every
{@code @OttlAccessible} type, so no producer is required for the common case. A hand-written
{@code @Produces RuleManager<T>} still wins when an application needs a custom repository or lifecycle.

```java
@ApplicationScoped
public class OrderService {
    @Inject RuleManager<Order> rules;
    public Order process(Order order) { return rules.apply("order-rules", order); }
}

@ApplicationScoped
public class OrderFunctions {
    @OttlFunction("IsHighValue")                 // callable from rule source
    public boolean isHighValue(BigDecimal amount) { return amount.compareTo(new BigDecimal("10000")) > 0; }
}
```

Configuration (`application.properties`):
```properties
quarkus.ottl.profile=JAVA
quarkus.ottl.dialect=OTTL
quarkus.ottl.max-statements=100
quarkus.ottl.max-source-length=10000
```

`quarkus.ottl.dialect=EXTENDED` enables blocks and `if` / `else` for every rule that engine compiles.
The example application sets `EXTENDED`. The extension default is `OTTL`.

Strict checking of types, properties and functions stays on. It is not a configuration switch.
`max-source-length` must be between 1 and 1000000, and `max-statements` between 1 and 10000.
Path depth and call nesting stay at the engine default of 16.

> **Note on generic injection:** the extension registers a `@DefaultBean` `RuleManager<T>` for each
> `@OttlAccessible` type. Inject `RuleManager<Order>` directly. Declare your own `@Produces` method
> only when you need to replace that default.

### Run the example app
```bash
./mvnw -pl examples/quarkus-example -am quarkus:dev
```
Endpoints:
- `POST /orders/transform` — apply the active rules to an order (JSON record).
- `PUT /rules/{id}` / `GET /rules/{id}` — activate / read the order rule (body = OTTL source, `text/plain`).
- `POST /customers/transform` + `PUT|GET /customers/rules` — mutable JavaBean (POJO) example.
- `POST /shipments/transform` + `PUT|GET /shipments/rules` — nested record (`shipment.address.city`) example.

Demonstrate a runtime rule change (no restart):
```bash
# default rule sets status=REVIEW for high-value orders
curl -s -XPOST localhost:8080/orders/transform -H 'Content-Type: application/json' \
  -d '{"id":"o1","amount":15000,"status":"NEW","priority":"LOW"}'
# -> {"id":"o1","amount":15000,"status":"REVIEW","priority":"LOW"}

# replace the rule at runtime
curl -s -XPUT localhost:8080/rules/order-rules -H 'Content-Type: text/plain' \
  -d 'set(order.priority, "URGENT")'

curl -s -XPOST localhost:8080/orders/transform -H 'Content-Type: application/json' \
  -d '{"id":"o2","amount":500,"status":"NEW","priority":"LOW"}'
# -> priority is now "URGENT"
```

> The `/rules/*`, `/customers/rules`, `/shipments/rules` and `/editor/*` endpoints belong to the
> example application. The extension does not publish them. They compile and activate arbitrary rule
> source and are for local development. Do not expose them without authentication and authorization.

## Language

See [docs/architecture.md §3–§4](docs/architecture.md) for the grammar and semantics, and
[docs/conformance.md](docs/conformance.md) for reference cases and known gaps. Unsupported OTTL
features are listed explicitly in [architecture.md §8](docs/architecture.md#8-unsupported-ottl-features).

## OTTL Extended Language

Blocks and `if` / `else if` / `else` are an ottl-java extension. They are **not** OpenTelemetry OTTL.
Enable them with `OttlDialect.EXTENDED` (import `io.ottl.lang.OttlDialect`). The default dialect is
`OTTL`, which keeps today's statements and `where` blocks and rejects the new syntax with
`Language feature 'if/else' requires OttlDialect.EXTENDED`.

```java
OttlEngine engine = OttlEngine.builder()
        .profile(OttlProfile.JAVA)
        .dialect(OttlDialect.EXTENDED)
        .build();

OttlProgram<Order> program = engine.compile("""
        if order.amount > 10000 {
            set(order.status, "REVIEW")
            set(order.priority, "HIGH")
        } else {
            set(order.priority, "LOW")
        }
        """, Order.class);
```

A block runs its statements in order against the same root. Records still copy, JavaBeans and JSON
still mutate, and a false branch is not evaluated. Invalid statements inside a branch that will not
run are still rejected at compile time. There is no `while`, `for`, or `let`. Nesting is bounded by
`OttlLimits`. Full semantics, Quarkus activation, and the compatibility rules are in
[architecture.md §10](docs/architecture.md#10-ottl-extended-language).

## Extending

- **New function:** implement `io.ottl.function.OttlFunction` and (for auto-discovery) list it in
  `META-INF/services/io.ottl.function.OttlFunction`, or add it via `OttlEngine.builder().function(...)`,
  or annotate a Quarkus bean method with `@OttlFunction`.
- **New data type:** implement `io.ottl.spi.PropertyResolver` and register it via
  `META-INF/services/io.ottl.spi.PropertyResolver` (or the builder).
- **New rule storage:** implement `io.ottl.rules.RuleRepository` (e.g. PostgreSQL) and provide it as a
  CDI bean to override the default in-memory repository.

## Security model

Rule authors are treated as **less trusted** than application developers:
- Only allowlisted functions and declared properties are reachable — **no arbitrary method calls**.
- No file/network access from expressions.
- Compile-time complexity limits (`OttlLimits`).
- Rules are validated before activation; invalid updates never replace a working rule.

## Native image

The extension is **GraalVM/Mandrel native-image compatible**, and the example app has been built and
verified as a native executable. Rules stay fully editable and recompilable **at runtime** inside the
native binary — only the *accessible Java types* are fixed at build time.

Register a domain type with `@OttlAccessible`; the extension generates a reflection-free property
accessor for it at build time (records → COPY via the canonical constructor; POJOs → MUTATE via
setters) and wires an immutable accessor registry — **no runtime reflection** for property access:

```java
@OttlAccessible
public record Order(String id, BigDecimal amount, String status, String priority) {}
```

In strict (native) mode the compiler validates every rule path against the registry, so rules
referencing unknown types/properties/functions are rejected before activation.

Build the native executable (container build needs Docker/Podman; drop the flag if you have a local
Mandrel/GraalVM toolchain):
```bash
./mvnw verify -Dnative -Dquarkus.native.container-build=true -pl examples/quarkus-example -am
```

This runs the `@QuarkusIntegrationTest` suite (`OttlNativeIT`) against the **actual native binary**,
covering runtime rule compilation, hot swapping, POJO/record/nested transforms, concurrency, and
security rejections. See [docs/native-image-architecture.md](docs/native-image-architecture.md) for
the full build-time vs. runtime design.

## Language server & rule editor

An Eclipse **LSP4J** language server provides real diagnostics, completion (including nested Java
property navigation), hover and signature help by reusing the actual OTTL parser, compiler and type
registry — the language is **not** reimplemented in JavaScript. It is served over a **Quarkus
WebSocket** (`ws://localhost:8080/ottl/lsp`, one isolated session per client, no stdio framing), and a
small **Vue 3 / Quasar / Monaco** app consumes it with `monaco-languageclient`.

Run the two processes in separate terminals. The editor on port 5173 proxies the language server
and the `/editor` API to the Quarkus app, so start the backend first and wait until it is listening
on port 8080.

```bash
# terminal 1 — backend (REST + LSP WebSocket), from the repo root
./mvnw -pl examples/quarkus-example -am quarkus:dev
```

```bash
# terminal 2 — Vue/Quasar/Monaco editor. Requires Node 22.12, 24, or 26+.
cd examples/ottl-editor
npm install
npm run dev
```

Open http://localhost:5173.

The editor lists runtime rules, offers a context/sample-input panel, and can **validate**, **test**
(run against sample JSON) and **activate** rules through dev-only `/editor/*` endpoints that reuse the
existing `RuleManager`s. A Playwright e2e test covers the full flow. For architecture, native-image
notes and production security hardening, see [docs/language-server.md](docs/language-server.md).

## Benchmarks

```bash
./mvnw -q -pl ottl-benchmarks -am package
# then run org.openjdk.jmh.Main io.ottl.benchmarks.ProgramBenchmark on the built classpath
```

## Modules & MCP

Module responsibilities are summarized in [docs/architecture.md §2](docs/architecture.md#2-module-layout).
The repository is configured for Cursor Agent with the official Quarkus Agent MCP server
(`.cursor/mcp.json`); see [.cursor/rules](.cursor/rules) for project conventions and how to verify MCP
connectivity.

## Contributing

Contributions are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md) for build/test instructions and the
architecture constraints to respect, and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md). Please report
security issues privately per [SECURITY.md](SECURITY.md).

## License

Apache License 2.0 — see [LICENSE](LICENSE) and the [NOTICE](NOTICE) file. Third-party dependency
licenses are summarized in [docs/third-party-licenses.md](docs/third-party-licenses.md).
