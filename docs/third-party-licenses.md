# Third-party licenses

`ottl-java` is licensed under the **Apache License 2.0** (see [`LICENSE`](../LICENSE)). It depends on
third-party software distributed under its own licenses, summarised below. All third-party licenses
used are permissive (Apache-2.0, MIT, BSD) or weak-copyleft file-level (EPL-2.0) and are compatible
with distributing this project under Apache-2.0.

> This is a curated summary. See [Regenerating a full report](#regenerating-a-full-report) to produce
> an exhaustive, machine-generated list.

## Java runtime dependencies

| Dependency | License | Notes |
| --- | --- | --- |
| Eclipse LSP4J (`org.eclipse.lsp4j`, `…jsonrpc`) | **EPL-2.0 OR EDL-1.0** (EDL = BSD-3-Clause) | Language Server Protocol bindings used by `ottl-language-server`. |
| Google Gson | Apache-2.0 | Pulled in transitively by LSP4J for JSON-RPC (de)serialization. |
| Quarkus (core, REST, Jackson, WebSockets Next, Arc) | Apache-2.0 | Only in `quarkus-ottl/*` and `examples/*`. |
| Jackson (`com.fasterxml.jackson.*`) | Apache-2.0 | JSON binding. |

The core engine (`ottl-language`, `ottl-engine`, `ottl-functions-*`, `ottl-context-*`) has **no
third-party runtime dependencies** beyond the JDK.

## Java test dependencies

| Dependency | License |
| --- | --- |
| JUnit 5 | EPL-2.0 |
| AssertJ | Apache-2.0 |
| REST Assured | Apache-2.0 |

## Front-end dependencies (`examples/ottl-editor`)

These are **build/test-time only**. The production artifact is a static bundle; **no Node.js is
required at runtime**.

| Dependency | License |
| --- | --- |
| Vue, Quasar, `@quasar/extras`, `@quasar/vite-plugin` | MIT |
| Monaco Editor, `monaco-languageclient`, `vscode-ws-jsonrpc` | MIT |
| `@codingame/monaco-vscode-*` (editor-api, services, vsix plugin) | MIT |
| Vite, `@vitejs/plugin-vue`, `vue-tsc`, `sass-embedded` | MIT |
| TypeScript, `@playwright/test` | Apache-2.0 |

## A note on Eclipse LSP4J (EPL-2.0)

LSP4J is offered under the Eclipse Public License 2.0 **or** the Eclipse Distribution License 1.0
(a BSD-3-Clause license). EPL-2.0 is a file-level weak copyleft: you may depend on and redistribute
LSP4J alongside Apache-2.0 code without relicensing this project. If you modify LSP4J's own source
files, those modifications remain under its license. This project does not modify LSP4J.

## Regenerating a full report

Java (aggregated `THIRD-PARTY.txt`):

```bash
./mvnw org.codehaus.mojo:license-maven-plugin:2.4.0:aggregate-add-third-party \
  -Dlicense.outputDirectory=target -Dlicense.thirdPartyFilename=THIRD-PARTY.txt
# -> target/THIRD-PARTY.txt
```

Front end:

```bash
cd examples/ottl-editor
npx license-checker --production --summary      # quick summary
npx license-checker --json > licenses.json      # full machine-readable report
```
