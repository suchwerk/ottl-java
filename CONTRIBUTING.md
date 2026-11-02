# Contributing to ottl-java

Thanks for your interest in contributing! This document explains how to build, test, and submit
changes.

## Code of conduct

This project follows the [Contributor Covenant](CODE_OF_CONDUCT.md). By participating, you are
expected to uphold it.

## Ground rules (architecture constraints)

Please keep the project's invariants intact (see [`docs/architecture.md`](docs/architecture.md)):

- **Java 21**, Maven multi-module. The core engine **must not depend on Quarkus** — Quarkus lives
  only in `quarkus-ottl/*` and `examples/*`. The language server core (`ottl-language-server`) must
  not depend on Quarkus or the front end.
- Parsing is a **hand-written recursive-descent parser** (no ANTLR); keep the AST pure.
- Property access goes through the `PropertyResolver` / `PropertyAccessor` SPI; functions go through
  the `FunctionRegistry` allowlist. **No unrestricted reflection or arbitrary method invocation.**
- Compiled programs are immutable and thread-safe; never recompile inside `apply(...)`.
- **Security first:** rule authors are less trusted. Enforce allowlists and complexity limits; no I/O
  from expressions; validate before activation.
- **Honesty:** don't claim functionality that isn't implemented and tested. Leave explicit `TODO`s for
  skeletons, and don't claim native-image support without a passing native build.

## Building and testing

Prerequisites: a **Java 21** JDK. Use the Maven wrapper.

```bash
# Full JVM build + tests
./mvnw verify

# A single module
./mvnw -pl ottl-language-server test

# Native build + native integration tests (needs Docker/Podman)
./mvnw -pl examples/quarkus-example verify -Dnative
```

Front end (`examples/ottl-editor`):

```bash
cd examples/ottl-editor
npm install
npm run build          # type-check (vue-tsc) + production build
npm run test:e2e       # Playwright e2e (starts Quarkus dev + Vite automatically)
```

See [`docs/language-server.md`](docs/language-server.md) for the editor/LSP development workflow.

## Submitting changes

1. Fork the repo and create a topic branch.
2. Keep changes focused; add tests for new behaviour (unit, `@QuarkusTest`, and/or e2e as
   appropriate).
3. Ensure `./mvnw verify` is green locally. If you touched the front end, also run `npm run build`.
4. Use clear commit messages. Add an SPDX header to new source files:
   ```
   /*
    * Copyright The OTTL Java Authors
    * SPDX-License-Identifier: Apache-2.0
    */
   ```
5. Open a pull request describing the change and how you verified it. CI runs `./mvnw verify`.

## Reporting bugs and requesting features

Use the GitHub issue templates. For **security** issues, do **not** open a public issue — follow
[`SECURITY.md`](SECURITY.md).

## License

By contributing, you agree that your contributions are licensed under the project's
[Apache License 2.0](LICENSE).
