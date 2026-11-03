# What it adds

`OttlDialect.EXTENDED` is ottl-java syntax. It is not OpenTelemetry OTTL. It accepts every statement from the OTTL dialect, and adds blocks and `if` / `else if` / `else`.

The library and the Quarkus extension default to `OttlDialect.OTTL`. This example sets `quarkus.ottl.dialect=EXTENDED`, so the editor accepts both.

Opt in from Java:

```
OttlEngine.builder()
    .dialect(OttlDialect.EXTENDED)
    .build();
```

Or in configuration:

```
quarkus.ottl.dialect=EXTENDED
```

Choosing a Java context does not turn the extended dialect on. The dialect and the data profile are separate.

# Blocks

A block is zero or more statements in braces. They run in order on the same root. An empty block is valid. A nested block does not create a new root.

```
{
    set(order.status, "REVIEW")
    set(order.priority, "HIGH")
}
```

# if / else if / else

```
if order.amount > 10000 {
    set(order.status, "REVIEW")
    set(order.priority, "HIGH")
} else if order.amount > 5000 {
    set(order.priority, "MEDIUM")
} else {
    set(order.priority, "LOW")
}
```

- Every branch needs braces. There is no single-statement form.
- Only the first matching branch runs. Its condition is evaluated once.
- Skipped branches are not evaluated. `and` and `or` still short-circuit.
- Every branch is checked at compile time, including branches that will not run.
- `if` and `else` are keywords. `order.if` is still a path.

A where block stays valid here. It is not the same construct as a bare block or an `if`.

# Not implemented

These are not available in either dialect:

- `let` bindings
- `switch` / `case`
- named rule blocks
- `while`, `for`, and `foreach`
