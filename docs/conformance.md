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
| literals: string/int/float/bool/nil/bytes | literal parsing | `ParserTest`, `OttlSyntaxConformanceTest` |
| lists and maps | literal collections | `OttlSyntaxConformanceTest` |
| math `+ - * /` | int/float not mixed; integer division truncates | `OttlSyntaxConformanceTest.mathFollowsOttlNumericRules` |
| named arguments | `set(value=..., target=...)` | `OttlSyntaxConformanceTest.mapsBytesIndexesAndNamedArguments` |
| converter and path indexes | `Words("a,b")[1]`, `attributes[name]` | `OttlSyntaxConformanceTest` |
| lambdas | value, read-only parameters; no standard caller | `OttlSyntaxConformanceTest.lambdaAppliesWithOuterCapture` |
| keyed/indexed paths `a["k"][0]` | nested access | `ParserTest.parsesKeyedPathAndIntKeys` |
| `Len`, `Concat`, `Split`, `Substring` | byte length, join, `strings.Split`, byte window | `ConverterConformanceTest` |
| `IsMatch`, `Trim` | unanchored match, `strings.Trim` cutset | `ConverterConformanceTest` |
| `Int`, `Double`, `String` | collector conversions, including nil | `ConverterConformanceTest` |
| `replace_match`, `replace_all_matches` | gobwas glob, `*` matches `/` | `StringFunctionConformanceTest` |
| `replace_pattern`, `replace_all_patterns` | regex replace, `$1` / `$$` | `StringFunctionConformanceTest` |
| `TrimPrefix`, `TrimSuffix`, `ConvertCase` | one affix, strcase lower/upper/snake/camel | `StringFunctionConformanceTest` |
| `IsString`, `IsBool`, `IsDouble`, `IsList`, `IsMap` | type tests, nil is false | `StringFunctionConformanceTest` |
| `append`, `clear`, `delete_index` | list append, zero values, half-open slice delete | `CollectionEditorConformanceTest` |
| `delete_matching_keys`, `keep_matching_keys` | unanchored key regex | `CollectionEditorConformanceTest` |
| `flatten` | nested maps and lists, UTF-8 key order, conflict suffixes | `CollectionEditorConformanceTest` |
| `limit` | priority keys, then UTF-8 order up to the limit | `CollectionEditorConformanceTest` |
| `merge_maps` | `insert`, `update`, `upsert` | `CollectionEditorConformanceTest` |
| `stringify_all`, `truncate_all` | string form of values, UTF-8 byte limit | `CollectionEditorConformanceTest` |

## ottl-java extensions

These are not part of reference OTTL. They are available in both profiles.

| Construct | Semantics | Covered by |
|---|---|---|
| `where <condition> { ... }` | evaluate the condition once, then run every item in the block. Valid in `OttlDialect.OTTL` and `EXTENDED` | `WhereBlockTest` |
| `EnumName.CONSTANT` | allowlisted enum constant used as a value; the enum is not a context | `EnumConstantTest` |
| `{ ... }` and `if` / `else if` / `else` | `OttlDialect.EXTENDED` only, on either profile. Not OpenTelemetry OTTL. See [architecture.md §10](architecture.md#10-ottl-extended-language) and [AGENTS.md](../AGENTS.md) | `ControlFlowTest` |

## Known differences / gaps

See [architecture.md §8](architecture.md#8-unsupported-ottl-features). The rest of `ottlfuncs` and the
OTTL pdata contexts are not implemented. These should be added behind the STANDARD profile and
verified against reference fixtures before any "v1 compliance" is claimed.

`ConverterConformanceTest`, `StringFunctionConformanceTest`, and `CollectionEditorConformanceTest`
follow the collector implementations of the shipped functions, with these differences:

- `IsMatch` uses Java regular expressions, not RE2. Named groups are `(?<name>...)`. A pattern that
  fails to compile is an evaluation error, and a hostile pattern can backtrack for a long time.
- `Len` and `Substring` count UTF-8 bytes. A `Substring` window that splits a character is decoded
  with Java's UTF-8 decoder, which replaces an incomplete sequence with U+FFFD. Go keeps the raw bytes.
- `String` encodes lists and maps as JSON with sorted keys and no extra spaces. A byte slice is
  lowercase hex. Number text uses Java's `Double.toString`, adjusted to `encoding/json`'s
  plain-versus-scientific cutoff, so some values differ in the last digit from Go's `strconv`.
- `Int("2.0")` is an error. The collector README lists that call, and `strconv.ParseInt` rejects it.
- `Int` and `Double` accept a `BigDecimal`, which standard OTTL does not have. `Int` truncates it
  toward zero. `IsDouble` does not treat a `BigDecimal` as a float.
- `replace_match` and `replace_all_matches` compile globs the way the collector does, with
  gobwas/glob and no separators, so `*` matches `/`. The collector README still describes
  `filepath.Match`. The optional converter argument and `replacementFormat` are not supported.
- `replace_pattern` and `replace_all_patterns` use Java regular expressions. Replacement text
  follows Go's `$$`, `$1`, and `${name}` rules. `mode` must be the literal `key` or `value`.
- `delete_matching_keys` and `keep_matching_keys` use Java regular expressions. A match may start
  anywhere in the key, as with `IsMatch`.
- `flatten` visits keys in UTF-8 order. `depth` must be greater than 0; a depth of 0 is a compile
  error. `prefix`, `depth`, and `resolveConflicts` are literals. With `resolveConflicts`, the first
  value keeps the key and later values are written at `key.0`, `key.1`, and so on.
- `limit` keeps every listed priority key, then keeps other keys in UTF-8 order until the limit.
  The collector README describes the drop as random; a pdata map walks keys in sorted order, and
  this engine does the same. `limit` is an int literal and must be at least the number of priority
  keys. The parameter name is `priorityKeys`.
- `truncate_all` counts UTF-8 bytes. `utf8Safe` defaults to true. With `utf8Safe` false, a cut that
  splits a character is decoded with Java's UTF-8 decoder, which replaces the incomplete sequence
  with U+FFFD. Go keeps that raw byte. `truncationMarker` counts against the limit. The parameter
  names are `utf8Safe` and `truncationMarker`.
- `clear` turns a map into an empty map and a list into an empty list. A byte slice becomes nil.
  A string, int, float, or bool becomes its zero value. Any other object becomes nil.
- `append` rejects a map target. `values` appends each element of the list. At least one of
  `value` or `values` is required. An optional parameter may be omitted when a later one is named.

## Adding new conformance cases

1. Add the OTTL source and expected result to a test in the `standard/` package.
2. If it needs a new function, implement it in `ottl-functions-standard` and register it via
   `META-INF/services/io.ottl.function.OttlFunction`.
3. Keep Java-only behavior in the `java/` package so the standard surface stays honest.
