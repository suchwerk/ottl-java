# Statements

A script is a sequence of editor statements. There is no semicolon. A statement may wrap across lines, and each one starts with a function call.

```
set(order.status, OrderStatus.PROCESSING) where IsHighValue(order.amount)
set(customer.tier, "STANDARD")
```

The editors shipped with the engine are:

| Call | Effect |
| --- | --- |
| `set(target, value)` | Write `value` to the path `target`. |
| `delete_key(target, key)` | Remove one key from the map at `target`. |
| `keep_keys(target, ["a", "b"])` | Keep only those keys. Extra keys can also be separate arguments. |

Other functions registered on the engine, including `@OttlFunction` methods, are called the same way. A statement name starts with a lowercase letter. A function used as a value starts with an uppercase letter. Unknown functions are rejected at compile time.

# Values

A value is a literal, a list, a map, a path, a converter call, a math expression, or a lambda.

```
"REVIEW"
1500
12.5
.5
1e2
true
false
nil
0x0001
["a", "b"]
{"env": "prod", "n": 1}
order.amount + 2
(n) => n + 1
```

A minus or plus sign applies to the following value. `*` and `/` bind tighter than `+` and `-`. Integers and floats are not mixed: `1 + 1.5` is an error, and `7 / 2` is `3`. Division by zero is an error. A `BigDecimal` amount can be combined with an integer (`order.amount + 2`).

Enum constants are values when the enum is accessible, for example `OrderStatus.PROCESSING`. A bare symbol such as `SPAN_KIND_SERVER` is the same kind of value when exactly one registered constant has that name. The enum itself is not a context.

# Paths and indexes

A path is names separated by dots. An index is a string, an integer, or any expression.

```
order.status
order.address.city
attrs["http.method"]
items[0]
attrs[attrs["name"]]
Words("a,b,c")[1]
```

The first segment may be the context name. `order.status` and `status` address the same property when the root is an `Order`.

A converter name starts with an uppercase letter. An editor name starts with a lowercase letter, so `set(...)` is a statement and `Len(...)` would be a value.

# Named arguments

Named arguments follow positional ones. `set` accepts `target` and `value`.

```
set(value="HIGH", target=order.priority)
```

# Conditions

`and`, `or`, and `not` combine comparisons. Parentheses group them. `and` binds tighter than `or`, and both short-circuit.

```
==  !=  <  <=  >  >=
```

```
set(order.priority, "HIGH") where (order.amount + 1) > 1000 and order.status == OrderStatus.PENDING
```

A trailing `where` runs that one statement only when the condition is true.

# Where blocks

A where block evaluates the condition once, then runs every statement in the braces on the same root. This form is valid in the OTTL dialect and in the extended dialect.

```
where order.amount > 1000 {
    set(order.priority, "HIGH")
    set(order.status, OrderStatus.PROCESSING)
}
```

# Comments

`//` comments out the rest of the line. `#` does the same. `# context: Order` at the top of the editor is editor metadata, not part of the rule.

# What this dialect does not include

Bare `{ ... }` blocks and `if` / `else` belong to the extended dialect. In the OTTL dialect, `if(...)` is an ordinary function call, not a keyword.

The standard converter library (`Len`, `Concat`, `IsMatch`, and the rest) is not bundled. A lambda is a value, and no editor shipped with the engine calls one.

`let`, `switch`, and loops are not part of the language.
