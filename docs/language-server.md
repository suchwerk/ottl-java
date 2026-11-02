# OTTL Language Server & Rule Editor

This document describes the Language Server Protocol (LSP) implementation for OTTL, its Quarkus
WebSocket transport, and the Vue 3 / Quasar / Monaco rule editor that consumes it.

The design goal is **real language intelligence with no duplication**: the editor reuses the actual
OTTL parser, compiler and type registry that run in production. The language is never reimplemented in
JavaScript — the browser only renders syntax highlighting (a Monarch tokenizer) and delegates every
semantic feature (diagnostics, completion, hover, signatures) to the Java server.

```
┌────────────────────┐   JSON-RPC over    ┌─────────────────────┐   reuses   ┌──────────────────┐
│ Vue 3 / Quasar     │   WebSocket        │ ottl-lsp-quarkus    │──────────▶ │ ottl-engine      │
│ Monaco +           │◀──────────────────▶│ (WebSocket transport│            │ parser, compiler │
│ monaco-languageclt │  ws://…/ottl/lsp   │  + per-session LSP) │            │ PropertyAccessor │
└────────────────────┘                    └─────────┬───────────┘            │ FunctionRegistry │
         │ REST /editor/*                            │ embeds                 └──────────────────┘
         ▼                                           ▼
┌────────────────────┐                     ┌─────────────────────┐
│ dev-only editor API│                     │ ottl-language-server│  (pure LSP4J core, no Quarkus,
│ validate/test/     │                     │  Analyzer + OttlMeta│   no frontend dependency)
│ activate/metadata  │                     └─────────────────────┘
└────────────────────┘
```

## Modules

| Module | Responsibility | Depends on |
| --- | --- | --- |
| `ottl-language-server` | Pure LSP core (Eclipse LSP4J): diagnostics, completion, hover, signature help, document lifecycle. Reuses the real lexer/parser and a shared metadata model. | `ottl-language`, `ottl-engine`, `org.eclipse.lsp4j` |
| `ottl-lsp-quarkus` | Quarkus WebSockets-Next transport. One LSP server instance per client session; JSON-RPC framing without stdio `Content-Length`. | `ottl-language-server`, `quarkus-ottl` |
| `examples/quarkus-example` | Dev-only editor REST API (`/editor/*`) reusing the existing `RuleManager`s; hosts the `/ottl/lsp` WebSocket. | both of the above |
| `examples/ottl-editor` | Vue 3 + Quasar + Vite + Monaco front end using `monaco-languageclient` + `vscode-ws-jsonrpc`. | the Quarkus app (REST + WS) |

> **Architecture constraint:** `ottl-language-server` must **not** depend on Quarkus or any frontend
> code. It can be embedded in any transport (stdio, socket, WebSocket).

## Shared type metadata (no duplicate schemas)

`io.ottl.lsp.metadata.OttlMetadata` is built from the **same** `PropertyAccessorRegistry` and
`FunctionRegistry` the engine uses at runtime. In the Quarkus app it is produced from the live
`OttlEngine`:

```java
@Produces @Singleton
OttlMetadata ottlMetadata(OttlEngine engine) {
    return new OttlMetadata(engine.accessorRegistry(), engine.functions());
}
```

Because the metadata comes from the registry, the editor automatically respects the same security
restrictions as execution: only `@OttlAccessible` types are visible, only readable properties are
offered for reads, only writable properties are valid assignment targets, and only allowlisted
functions complete. There is no hand-maintained schema to drift out of sync, and unrelated
application classes are never discoverable.

## LSP capabilities

All features reuse the production parser/compiler, so ranges and errors match what the engine would
actually reject.

- **Diagnostics** — real syntax errors from the hand-written parser (with 1-based line/column mapped
  to LSP ranges), plus semantic checks: unknown function, unknown property, write to a read-only
  property, navigation into a non-accessible type, and arity errors.
- **Completion** — OTTL/custom functions (with signature + docs), keywords/operators, and context
  properties including **nested** navigation (e.g. `set(order.` → `Order` properties; `where
  order.customer.` → `Customer` properties). Items carry the property type and read/write flags.
- **Hover** — property type, readable/writable flags and declaring type; function signature and
  documentation.
- **Signature help** — the active function signature with the active parameter highlighted.

### Choosing the context type

A document selects its root type with a comment directive the lexer already ignores:

```ottl
# context: Order
set(order.priority, "HIGH") where order.amount > 1000
```

If omitted, the first registered type is used. The editor manages this directive automatically when
you pick a context in the right-hand panel.

## WebSocket transport

The transport (`ottl-lsp-quarkus`) uses **Quarkus WebSockets Next**. Each text frame is exactly one
JSON-RPC message — the stdio `Content-Length` framing is deliberately **not** applied, per the LSP
WebSocket convention.

- `@OnOpen` creates a dedicated `OttlLanguageServer` + LSP4J `RemoteEndpoint` bound to that
  connection (`LspSession`), so document state is isolated per client.
- `@OnTextMessage` (`@Blocking`) parses the frame and dispatches it to that session's endpoint.
- `@OnClose` shuts the server down and removes the session.

Multiple concurrent sessions are supported; closing a connection cleans up its state.

## Development-only editor REST API

`EditorResource` (`/editor/*`) backs the UI and **reuses the existing engine and rule managers** — it
does not reimplement execution:

| Endpoint | Purpose |
| --- | --- |
| `GET /editor/contexts` | Approved context types (+ properties) and functions, from the registry. |
| `POST /editor/validate` | Compile a rule for a context and report success/error. |
| `POST /editor/test` | Compile + run against a sample JSON input, returning the transformed object. |
| `POST /editor/activate` | Activate a rule via the matching `RuleManager` (hot swap). |
| `GET /editor/rules` | The currently active rules. |

Context names are an explicit allowlist (`EditorContexts`) mapping to a fixed set of Java types and
their rule managers. **Arbitrary Java class names from user input are never accepted** — an unknown
context returns `400`.

## Running it (development)

Two processes: the Quarkus backend and the Vite dev server.

```bash
# 1. Backend: REST + LSP WebSocket on :8080
./mvnw -pl examples/quarkus-example quarkus:dev

# 2. Front end: Vue/Quasar/Monaco on :5173 (proxies REST + WS to :8080)
cd examples/ottl-editor
npm install
npm run dev
# open http://localhost:5173
```

The Vite dev server proxies `/editor/*` and the `/ottl/lsp` WebSocket to the backend, so the browser
talks same-origin. Override the backend with `OTTL_BACKEND=http://host:port`.

### Production static hosting (no Node.js at runtime)

Build the front end once and let Quarkus serve the static bundle — Node.js is only needed at build
time:

```bash
examples/ottl-editor/scripts/build-static.sh   # builds and copies dist/ into the Quarkus app
./mvnw -pl examples/quarkus-example quarkus:dev # editor served at http://localhost:8080/
```

## Native image

`ottl-lsp-quarkus` ships a `reflect-config.json` registering the LSP4J protocol DTOs and Gson type
adapters (`org.eclipse.lsp4j.*`, `org.eclipse.lsp4j.jsonrpc.messages.*`) so JSON-RPC
(de)serialization works in a native image. The WebSocket transport and REST API are standard Quarkus
extensions and are native-compatible. `OttlLspNativeIT` exercises the LSP `initialize` handshake and
`/editor/test` **against the compiled native binary**:

```bash
./mvnw -pl examples/quarkus-example verify -Dnative
```

> Rules remain fully editable and recompilable at runtime inside the native binary; only the set of
> accessible Java types is fixed at build time. No runtime class generation is used by the LSP.

## Tests

- **LSP core** (`ottl-language-server`): diagnostics (syntax, unknown property, read-only write,
  unknown function, nested paths), completion (functions, root + nested properties), hover, signature
  help, server lifecycle, document version handling, concurrent sessions.
- **Transport + editor API** (`examples/quarkus-example`): `LspWebSocketTest` (initialize
  capabilities, `didOpen` → diagnostics), `EditorResourceTest` (metadata, validate, test-run,
  activate), and the native `OttlLspNativeIT`.
- **End-to-end** (`examples/ottl-editor/e2e`): a Playwright test driving the full flow — type invalid
  OTTL and see diagnostics, fix and see them clear, trigger completion, run a valid rule, activate a
  modified rule and confirm the next run uses it.

```bash
cd examples/ottl-editor
npm run test:e2e:install   # one-time: download the browser
npm run test:e2e           # starts Quarkus dev + Vite automatically
```

## Security (production hardening)

The editor API and LSP WebSocket live in the example application (`examples/quarkus-example` and
`ottl-lsp-quarkus`). The OTTL extension itself does not open HTTP or WebSocket endpoints, so a
production application that only depends on `quarkus-ottl` does not expose rule management.

Before exposing the example endpoints in production:

- **Authenticate and authorize** the rule-management endpoints and the WebSocket (e.g. per tenant,
  per rule set). Separate *validate/test* permissions from *activate* permissions.
- **Keep the strict allowlist**: only registered `@OttlAccessible` types and allowlisted functions are
  reachable — never accept arbitrary class or method names.
- **Do not leak internal metadata**: expose only approved context properties to authorized users.
- **Limit** source size and request/validation rate to bound compilation cost.
- Consider disabling the `/editor/*` endpoints entirely in production profiles if the editor is not
  needed there.
