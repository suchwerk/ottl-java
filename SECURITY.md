# Security policy

## Reporting a vulnerability

Please report security vulnerabilities **privately**. Do not open a public issue or pull request for
a security problem.

- Preferred: use GitHub's **"Report a vulnerability"** (Security → Advisories) on this repository.
- Alternatively, contact the maintainers privately (see the repository owner's profile).

Please include:
- a description of the issue and its impact,
- steps to reproduce or a proof of concept,
- affected versions/commit, and any suggested remediation.

We aim to acknowledge reports within a few business days and will keep you informed of progress.
Coordinated disclosure is appreciated — please give us reasonable time to release a fix before any
public disclosure.

## Scope and threat model

`ottl-java` treats **rule authors as less trusted** than application developers. The engine enforces:
- an allowlist of functions (`FunctionRegistry`) and declared properties (`PropertyAccessor` /
  `PropertyResolver`) — no arbitrary method/class access,
- no file or network I/O from expressions,
- compile-time complexity limits (`OttlLimits`),
- validation before activation (an invalid rule never replaces a working one).

### Development tools are not production-hardened

The rule editor and language server shipped in `examples/*` are **development tools**:
- the `/editor/*` REST endpoints and the `/ottl/lsp` WebSocket are **unauthenticated by default**,
- they compile and execute rule source and expose type metadata for approved context types.

**Do not expose them to untrusted networks without adding** authentication/authorization (per tenant
and per rule set), request/size/rate limits, and by keeping the type/function allowlist strict. See
the "Security" section of [`docs/language-server.md`](docs/language-server.md). Reports about the
example apps' default-open posture are acknowledged but are by-design for local development.
