# AGENTS.md

Instructions for coding agents and contributors working in **ottl-java**.

Long-form design lives in [`docs/architecture.md`](docs/architecture.md). Build and review
expectations live in [`CONTRIBUTING.md`](CONTRIBUTING.md). Always-applied Cursor rules live in
[`.cursor/rules/`](.cursor/rules). This file is the mandatory rule for language evolution. Do not
contradict it with a local shortcut.

## Repository constraints

These existing constraints stay in force:

- **Java 21**, Maven multi-module. Build and test with `./mvnw verify` on a Java 21 JDK.
- **The core engine must not depend on Quarkus.** Only `quarkus-ottl/*` and `examples/*` may use
  Quarkus. `ottl-language-server` must not depend on Quarkus or the front end.
  Dependency direction: `ottl-language` ← `ottl-engine` ← (`ottl-functions-*`, `ottl-context-*`) ← `quarkus-ottl`.
- **Parsing is a hand-written recursive-descent parser** in `ottl-language` (no ANTLR). Keep the AST
  pure (no evaluation logic). See `docs/architecture.md` §3.
- **Property access goes through the `PropertyResolver` SPI** (`io.ottl.spi`). Add a data type as a
  new resolver plus a `META-INF/services/io.ottl.spi.PropertyResolver` entry. Never add unrestricted
  reflection or arbitrary method invocation.
- **Functions go through `FunctionRegistry`** (the allowlist). Add standard functions to
  `ottl-functions-standard` with a `META-INF/services/io.ottl.function.OttlFunction` entry.
- **Compiled programs are immutable and thread-safe.** Do not store per-execution state on them; use
  `ExecutionContext`. Never recompile expressions inside `apply(...)`.
- **Security first:** rule authors are less trusted. Enforce allowlists, complexity limits
  (`OttlLimits`), no I/O, and validation-before-activation in `RuleManager`.
- **Native-image awareness:** no runtime classpath scanning or dynamic class generation. Do build-time
  discovery (Jandex) in the deployment module and register reflection and services there. Do not claim
  native support unless a native build and smoke test actually pass.
- **Honesty:** if something is beyond current scope, leave an explicit `TODO` or skeleton. Do not
  claim OTTL v1 compliance before conformance passes, and do not describe planned syntax as supported.
- For Quarkus API or config questions, use the Quarkus Agent MCP server configured in
  `.cursor/mcp.json`. Do not build a custom MCP server as part of this project.

## Language Dialect vs. Data Profile — Mandatory Separation

`OttlProfile` defines **what data can be accessed**.
`OttlDialect` defines **which language constructs can be used**.
Neither dimension implicitly determines the other.

This separation is mandatory. It keeps ottl-java compatible with standard OpenTelemetry OTTL while
allowing Java-specific language extensions (blocks, `if` / `else`, and later `let`, `switch`, and
reusable rule blocks) to evolve on their own.

`OttlProfile` lives in `io.ottl`. `OttlDialect` lives in `io.ottl.lang` because the parser enforces
it. Do not merge them into one enum, one builder flag, or one Quarkus config key.

### OttlProfile defines the data model

`OttlProfile` determines which data contexts, property resolvers, and access semantics are available.

| Profile | Role |
|---|---|
| `STANDARD` | OTTL-compatible data behavior: maps and attributes. |
| `JAVA` | `STANDARD` plus Java object access (POJOs, records, beans) and Java-only functions. Maps and Jackson `JsonNode` trees are resolver concerns, not a syntax mode. |

A profile must not determine which grammar features are available.

**Current code.** The enum exists and is stored on `OttlEngine`. The library default is
`OttlProfile.STANDARD`. The Quarkus extension default is `quarkus.ottl.profile=JAVA`. The parser
never reads the profile. Resolvers are installed explicitly (ServiceLoader in the library; generated
accessors, `MapResolver`, and `JsonNodeResolver` in `OttlRecorder`), not by a grammar switch. If a
future change makes the profile select resolvers, that selection must stay independent of
`OttlDialect`.

### OttlDialect defines the language

`OttlDialect` determines which syntax and language constructs the parser and compiler accept.

| Dialect | Role |
|---|---|
| `OTTL` | Standard-compatible OTTL syntax, plus ottl-java syntax that already shipped in this dialect. Default. |
| `EXTENDED` | The `OTTL` dialect plus explicitly supported language extensions. These extensions are ottl-java. They are not OpenTelemetry OTTL unless the feature has actually been standardized. |

**Implemented in `EXTENDED` today** (verified in `Parser`, `OttlDialect`, and `ControlFlowTest`):

- Statement blocks `{ ... }`
- `if` / `else if` / `else`

**Already valid in `OTTL` and in `EXTENDED`.** A `where` block (`where <condition> { ... }`) shipped
before the dialect split. It stays valid in `OttlDialect.OTTL`. Moving it behind `EXTENDED` would
reject programs that parse today. It remains a `WhereBlock`, distinct from a bare block and from
`IfStatement`.

**Not implemented.** Do not document these as supported syntax:

- Local variables (`let`)
- `switch` / `case`
- Reusable named rule blocks
- Loops (`while`, `for`, `foreach`)

`OttlDialect.OTTL` rejects bare blocks and `if` / `else` with
`Language feature '<feature>' requires OttlDialect.EXTENDED`. In that dialect, `if(...)` remains an
editor call. `order.if` remains a path in both dialects.

The library and the Quarkus extension both default the dialect to `OTTL`.

## Orthogonality is mandatory

Every valid profile/dialect combination must be conceptually supported. The matrix is the
architectural target. A cell is "implemented" only for constructs that the code and tests actually
cover today.

| Profile | Dialect | Expected behavior | Status today |
|---|---|---|---|
| `STANDARD` | `OTTL` | Standard OTTL syntax with standard data contexts | Default library engine. Standard conformance tests use this dialect by default. |
| `STANDARD` | `EXTENDED` | Extended syntax with standard data contexts | `ControlFlowTest` builds this combination for blocks and `if` / `else`. |
| `JAVA` | `OTTL` | Standard OTTL syntax with Java data contexts | Default Quarkus dialect. Java conformance tests and the OTTL-dialect rejection checks use it. |
| `JAVA` | `EXTENDED` | Extended syntax with Java data contexts | `ControlFlowTest` and the example application's `quarkus.ottl.dialect=EXTENDED`. |

`let`, `switch`, and named rule blocks are absent from every cell.

The following assumptions are forbidden:

- `OttlProfile.JAVA` automatically enables extended syntax.
- `OttlProfile.STANDARD` forbids extended syntax.
- `OttlDialect.EXTENDED` automatically enables Java property access.
- Language syntax is determined by property-resolver selection.
- The parser changes grammar based on the data profile.
- Selecting a Java context in the editor or language server switches the dialect.
- A bare block and a `where` block are the same language feature.

The parser depends on the dialect, not on the profile. `ottl-language` must not take a dependency on
`OttlProfile`.

The semantic analyzer may depend on both, because it validates language constructs against the
available data model. It must not use that dual dependency to choose a grammar.

## Component responsibilities

### Lexer and parser

- Parse according to `OttlDialect`.
- Never infer the dialect from `OttlProfile`.
- Reject unsupported extended syntax in `OTTL` mode, including bare blocks and `if` / `else`.
- Keep `where <condition> { ... }` valid in `OTTL`.
- Preserve standard-compatible parsing behavior. Adding a construct must not change how existing
  `OTTL` programs parse.
- Keep language syntax independent of Java domain types. The lexer and parser do not know about
  POJOs, records, or resolvers.

### AST

- Represent language constructs independently of the data profile.
- Avoid profile-specific AST nodes. A block or `if` is the same node under `STANDARD` and `JAVA`.
- Preserve source locations.
- Remain suitable for recursive semantic validation.
- Support future language evolution without requiring unrelated changes to the data model.
- Keep evaluation logic out of the AST.

### Semantic validation

- Validate language constructs according to the selected dialect. Today the dialect gate is the
  parser; validators walk the AST the parser accepted.
- Validate paths and property access according to the selected profile and the installed resolvers
  or accessor registry.
- Reject unknown or inaccessible properties.
- Validate every branch, including branches that will not execute at runtime.
- Enforce resource and complexity limits (`OttlLimits`: source length, statement count, path depth,
  and nesting of calls, `where` blocks, bare blocks, and `if` / `else if`).
- Maintain native-image safety: no classpath scanning and no arbitrary method invocation while
  validating.

### Execution engine

- Execute compiled programs through the same plan regardless of which profile produced the data
  access. Data access goes through `PropertyResolver` and accessor abstractions.
- Preserve mutable POJO and immutable record semantics (`MUTATE` vs `COPY`, including copy-on-write
  bubbling).
- Preserve per-execution `ExecutionContext`. Do not store per-execution state on a compiled program.
- Preserve atomic runtime rule replacement: compile and validate before swap; keep the previous
  version when compilation fails.
- Do not recompile inside `apply(...)`.
- Do not consult `OttlProfile` to decide whether a statement is legal. That decision belongs to the
  dialect, and it happens before execution.

### Quarkus extension

- Expose dialect and profile as independent configuration concepts.
- Do not derive dialect from profile.
- Preserve runtime rule compilation inside a native image. Accessible Java types are fixed at build
  time; rule source is not.
- Keep syntax configuration separate from data-access configuration.

Both keys exist today. Defaults are `profile=JAVA` and `dialect=OTTL`:

```properties
quarkus.ottl.profile=JAVA
quarkus.ottl.dialect=OTTL
```

`quarkus.ottl.dialect=EXTENDED` is implemented. It enables the constructs `EXTENDED` actually
supports (blocks and `if` / `else if` / `else`) for every rule that engine compiles. It does not
enable `let`, `switch`, or named rule blocks. `OttlRecorder` passes `config.profile()` and
`config.dialect()` to the builder as separate calls.

### Language Server

- Use the selected dialect for syntax diagnostics and completion. `OttlMetadata` carries
  `OttlDialect`; `Analyzer` parses with that dialect.
- Use the selected profile and type registry for property completion. Completion reads the accessor
  registry. It does not read `OttlProfile` to decide which keywords exist.
- Offer `if` and `else` as keywords only in `EXTENDED`.
- Avoid duplicating language grammar in the editor. Diagnostics, completion, hover, and signature
  help reuse the Java parser and compiler.
- Distinguish syntax errors from invalid property access. A missing `EXTENDED` opt-in and an unknown
  property are different diagnostics.
- There is no `OttlProfile` field on `OttlMetadata` today. Do not add one that selects grammar.

### Monaco editor

- Treat dialect and profile as independent configuration values.
- Do not assume a Java context requires extended syntax.
- Preserve compatibility with standard OTTL editing.
- Obtain semantic information from the Language Server.

**Current editor.** Monaco's Monarch tokenizer highlights `if` and `else` for display only. The
editor selects a data context with `# context: Type` and through the context panel. It does not
have its own dialect or profile controls; the Quarkus engine's dialect is what the language server
enforces. Do not add UI that turns `EXTENDED` on because the context is a Java type, and do not
treat the tokenizer keyword list as the grammar.

## Backward compatibility

1. Existing OTTL programs must continue working.
2. Adding new syntax must not silently change existing OTTL semantics.
3. The default dialect remains `OTTL` unless an explicitly documented compatibility decision changes
   it. Library and Quarkus both default to `OTTL` today.
4. Extended syntax requires an explicit opt-in (`OttlEngine.builder().dialect(OttlDialect.EXTENDED)`
   or `quarkus.ottl.dialect=EXTENDED`).
5. Standard-mode conformance tests stay independent of extended-language tests. Reference OTTL cases
   live under `ottl-conformance-tests/.../standard/`. Java data-model cases live under `.../java/`.
   Extended syntax is not part of the standard conformance surface.
6. Extended syntax must never be described as official OpenTelemetry OTTL unless it has actually been
   standardized.
7. Changing the data profile must not implicitly change the language dialect.
8. Changing the dialect must not implicitly change the accessible data model.
9. `where <condition> { ... }` remains valid in `OttlDialect.OTTL`.

Language extensions are additive. They preserve the existing OTTL compatibility guarantees.

## Future Language Evolution

New language constructs belong to the `EXTENDED` dialect unless they are standardized in
OpenTelemetry OTTL. A standardized construct may then be accepted in `OTTL`, with an explicit
compatibility note. Do not put a new construct in `OTTL` only because `OttlProfile.JAVA` needs it.

Blocks and `if` / `else` are already in `EXTENDED`. The later snippets (`let`, `switch`, named rule
blocks) are possible future syntax. They are **not** implementation requirements and they are
**not** supported today. Each new construct needs defined semantics, validation rules, complexity
limits, and tests before anyone implements it.

### Blocks

Bare blocks **are** implemented in `EXTENDED`. This snippet is the supported shape, included so
later constructs can build on it:

```ottl
{
    set(order.status, "REVIEW")
    set(order.priority, "HIGH")
}
```

### Conditional execution

`if` / `else if` / `else` **is** implemented in `EXTENDED`. Conditional bodies require braces.
Only the first matching branch runs. Every branch is still validated at compile time.

```ottl
if order.amount > 10000 {
    set(order.priority, "HIGH")
} else {
    set(order.priority, "LOW")
}
```

### Local variables

**Not implemented.**

```ottl
let normalized = Trim(order.customerName)
set(order.customerName, normalized)
```

### Switch expressions or statements

**Not implemented.**

```ottl
switch order.status {
    case "NEW" {
        set(order.priority, "NORMAL")
    }
    case "URGENT" {
        set(order.priority, "HIGH")
    }
}
```

### Reusable rule blocks

**Not implemented.**

```ottl
rule highValue {
    set(order.priority, "HIGH")
}

if order.amount > 10000 {
    apply highValue
}
```

## Testing requirements for future agents

Every new language feature must be tested across the profile/dialect matrix, or the gap must be
called out as a limitation rather than implied support:

- `STANDARD` + `OTTL`
- `STANDARD` + `EXTENDED`
- `JAVA` + `OTTL`
- `JAVA` + `EXTENDED`

Tests should verify, as far as the feature reaches:

- Syntax acceptance and rejection. `OTTL` must reject the new construct.
- Semantic validation, including branches that do not run.
- Correct execution on the profiles where the feature is expected to run.
- Backward compatibility of existing `OTTL` programs.
- Rule hot swapping: a failed compile must not replace the active rule.
- Concurrency safety of immutable programs.
- Native-image compatibility where the feature is wired through Quarkus.
- Language Server diagnostics and completion for both dialects.

`ControlFlowTest` is the current model for blocks and `if` / `else`: it covers `JAVA`+`EXTENDED`,
`STANDARD`+`EXTENDED`, and rejection under `JAVA`+`OTTL`. New constructs need the same kind of
coverage. Standard conformance tests must keep passing without opting into `EXTENDED`.

If a combination is not yet supported, tests and documentation must say so. Do not leave a green
test suite that only exercises `JAVA`+`EXTENDED` and a comment that calls the matrix done.

## Instructions for AI coding agents

Before implementing any parser, AST, semantic validation, compiler, runtime, Quarkus configuration,
or Language Server change, verify that it preserves the independence of `OttlDialect` and
`OttlProfile`.

Also:

- Do not introduce profile checks into grammar-selection logic.
- Do not enable extended syntax implicitly for Java contexts.
- Do not couple Java property access to extended syntax.
- Prefer additive language evolution.
- Preserve standard compatibility, including `where` blocks in the `OTTL` dialect.
- Keep compiled programs immutable.
- Preserve native-image compatibility.
- Validate unsupported syntax before execution.
- Update relevant tests and documentation when language behavior changes.
- Do not describe `let`, `switch`, or named rule blocks as available.
- Do not move `OttlDialect` into `io.ottl` just to sit beside `OttlProfile`.

If a proposed implementation violates these rules, reconsider the design. Do not introduce a
shortcut that derives one dimension from the other.
