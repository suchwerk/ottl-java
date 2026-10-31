# ottl-java

A native **Java implementation of the [OpenTelemetry Transformation Language (OTTL)](https://opentelemetry.io/blog/2026/ottl-v1/)**,
extended to transform arbitrary Java objects — `Map`s, POJOs, Java records and Jackson `JsonNode`
trees — with **rules that can be compiled and replaced at runtime** and a first-class **Quarkus**
extension.

> **Status:** working MVP. `./mvnw verify` is green (38 tests). This README distinguishes
> ✅ *verified* functionality from 🟡 *partial* and ⛔ *planned* work; see
> [docs/architecture.md](docs/architecture.md).

## Why

OTTL is a concise language for conditional data transformation. This project brings it to the JVM as a
small embeddable engine, adds a **JAVA profile** for domain objects, and makes rules **hot-swappable**
so you can change transformation logic without redeploying.

## Highlights

- ✅ OTTL subset: literals, paths & nested/keyed access, comparisons, boolean logic, `where`, sequential
  statements, and the `set` / `delete_key` / `keep_keys` editors.
- ✅ Two profiles: **STANDARD** (OTTL-compatible) and **JAVA** (POJOs/records/beans/JSON).
- ✅ **MUTATE** vs **COPY** execution — records use copy-on-write, including nested records.
- ✅ Pluggable property access (`PropertyResolver` SPI), discovered via `ServiceLoader`.
- ✅ Runtime-updatable rules: versioned, validated-before-activation, atomic swap, previous version
  retained on failure, thread-safe concurrent reads.
- ✅ Genuine Quarkus extension (runtime + deployment), CDI injection, `@OttlFunction`, `quarkus.ottl.*`
  config, and a working REST example.
- 🟡 JMH benchmarks skeleton.
- ⛔ OpenTelemetry pdata contexts, native-image verification (architecture is native-ready — see below).

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

Inject the engine / rule manager, and expose Java functions:
```java
@ApplicationScoped
public class OrderService {
    @Inject RuleManager<Order> rules;           // provided via an explicit @Produces (see example)
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
quarkus.ottl.max-statements=100
quarkus.ottl.max-source-length=10000
```

> **Note on generic injection:** CDI cannot synthesize `RuleManager<Order>` automatically. The
> application declares an explicit `@Produces RuleManager<Order>` (see `OrderRulesProducer` in the
> example). This is intentional.

### Run the example app
```bash
./mvnw -pl examples/quarkus-example -am quarkus:dev
```
Endpoints:
- `POST /orders/transform` — apply the active rules to an order (JSON).
- `PUT /rules/{id}` — validate, compile and activate a rule (body = OTTL source, `text/plain`).
- `GET /rules/{id}` — return the active rule and version.

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

> ⚠️ **The `/rules/*` management API is development-only.** It compiles and activates arbitrary rule
> source. Secure it (authn/authz, network policy) before any production use.

## Language

See [docs/architecture.md §3–§4](docs/architecture.md) for the grammar and semantics, and
[docs/conformance.md](docs/conformance.md) for reference cases and known gaps. Unsupported OTTL
features are listed explicitly in [architecture.md §8](docs/architecture.md#8-unsupported-ottl-features).

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

The architecture avoids runtime scanning and dynamic classes and registers reflection/services at
build time, so it is **native-ready**. A native binary was **not** built/verified here (no
GraalVM/Mandrel available), so native support is not claimed. To try it:
```bash
./mvnw -pl examples/quarkus-example -am -Dnative verify
```

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

## License

Apache License 2.0 — see [LICENSE](LICENSE).
