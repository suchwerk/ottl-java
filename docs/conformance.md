# OTTL Conformance

This project aims for behavioral compatibility with the reference OTTL where the implemented subset
overlaps. Conformance tests are kept **separate** from Java-profile tests:

- `ottl-conformance-tests/.../standard/` — OTTL STANDARD-profile behavior.
- `ottl-conformance-tests/.../java/` — Java-profile extensions (POJOs, records, Jackson, functions).

References:
- OTTL v1 announcement: https://opentelemetry.io/blog/2026/ottl-v1/
- OTTL package: https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/main/pkg/ottl

## Reference editor cases currently exercised

| OTTL construct | Reference semantics | Covered by |
|---|---|---|
| `set(target, value)` | assign value to target path | `StandardMapConformanceTest.setAssignsNestedAttribute` |
| `delete_key(target, k)` | remove key `k` from map `target` | `...deleteKeyRemovesEntry` |
| `keep_keys(target, keys)` | keep only `keys` in map `target` | `...keepKeysRetainsOnlyListed` |
| `where <condition>` | guard a statement | `...whereGuardsStatement` |
| sequential statements | run top-to-bottom | `...multipleStatementsRunSequentially` |
| comparisons `== != < <= > >=` | numeric/string/bool compare | `RecordCopyTest`, `PojoMutationTest` |
| boolean `and`/`or`/`not`, parens | precedence & grouping | `ParserTest.parsesBooleanExpressionWithPrecedenceAndParens` |
| literals: string/int/float/bool/nil | literal parsing | `ParserTest.parsesFloatsNegativesAndNil` |
| keyed/indexed paths `a["k"][0]` | nested access | `ParserTest.parsesKeyedPathAndIntKeys` |

## ottl-java extensions

These are not part of reference OTTL. They are available in both profiles.

| Construct | Semantics | Covered by |
|---|---|---|
| `where <condition> { ... }` | evaluate the condition once, then run every item in the block. Valid in `OttlDialect.OTTL` and `EXTENDED` | `WhereBlockTest` |
| `EnumName.CONSTANT` | allowlisted enum constant used as a value; the enum is not a context | `EnumConstantTest` |
| `{ ... }` and `if` / `else if` / `else` | extended dialect only. Not OpenTelemetry OTTL. See [architecture.md §10](architecture.md#10-ottl-extended-language) | `ControlFlowTest` |

## Known differences / gaps

See [architecture.md §8](architecture.md#8-unsupported-ottl-features). Notably: math expressions, the
full converter library, and OTTL pdata contexts are not yet implemented. These should be added behind
the STANDARD profile and verified against reference fixtures before any "v1 compliance" is claimed.

## Adding new conformance cases

1. Add the OTTL source and expected result to a test in the `standard/` package.
2. If it needs a new function, implement it in `ottl-functions-standard` and register it via
   `META-INF/services/io.ottl.function.OttlFunction`.
3. Keep Java-only behavior in the `java/` package so the standard surface stays honest.
