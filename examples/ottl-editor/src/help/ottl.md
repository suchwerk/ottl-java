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
| `append(target, value, values)` | Append to the list at `target`. A scalar becomes the first element. At least one of `value` or `values` is required. |
| `clear(target)` | Write the zero value. A map becomes empty and a list becomes empty. |
| `delete_index(target, start, end)` | Remove list elements from `start` inclusive to `end` exclusive. `end` defaults to the next index. |
| `delete_matching_keys(target, pattern)` | Remove keys that match the regular expression. |
| `keep_matching_keys(target, pattern)` | Keep keys that match the regular expression. |
| `flatten(target, prefix, depth, resolveConflicts)` | Lift nested maps and lists. `depth` must be greater than 0. `flatten(body, depth=2)` skips the earlier optionals. |
| `limit(target, limit, priorityKeys)` | Drop entries until at most `limit` remain. Listed keys are kept. |
| `merge_maps(target, source, strategy)` | `insert`, `update`, or `upsert`. |
| `stringify_all(target)` | Turn every non-string map value into a string. A byte slice is Base64. |
| `truncate_all(target, limit, utf8Safe, truncationMarker)` | Shorten string values to `limit` UTF-8 bytes. `utf8Safe` defaults to true. |
| `replace_match(target, pattern, replacement)` | Replace the whole string when it matches the glob. `*` matches across `/`. |
| `replace_all_matches(target, pattern, replacement)` | Do that for every string value in a map. |
| `replace_pattern(target, regex, replacement)` | Replace every regex match. `$1` is a group and `$$` is a dollar. |
| `replace_all_patterns(target, mode, regex, replacement)` | Do that for every map value (`"value"`) or key (`"key"`). |

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

A converter name starts with an uppercase letter. An editor name starts with a lowercase letter, so `set(...)` is a statement and `Len(...)` is a value.

# Converters

These value functions are included. The rest of the collector converter library is not.

| Call | Result |
| --- | --- |
| `Concat(vals, delimiter)` | Joins the string form of each list item. A nil item contributes a space. |
| `Len(target)` | UTF-8 byte length of a string, or the number of entries in a list, map, or byte slice. |
| `Split(target, delimiter)` | List of pieces. An empty delimiter splits into characters. `Split("a-b", "-")[1]` is `"b"`. |
| `Substring(target, start, length, utf8_safe)` | Bytes from `start` for `length` bytes. `utf8_safe` defaults to false and snaps the window to character boundaries when true. |
| `IsMatch(target, pattern)` | True if the pattern matches anywhere. Nil is false. Patterns are Java regular expressions. |
| `Trim(target, replacement)` | Drops leading and trailing characters that appear in the cutset. The default cutset is one space. |
| `TrimPrefix(target, prefix)` | Removes one leading copy of `prefix`. |
| `TrimSuffix(target, suffix)` | Removes one trailing copy of `suffix`. |
| `ConvertCase(target, toCase)` | `lower`, `upper`, `snake`, or `camel`. `camel` produces `MyMetric`. |
| `IsString(target)`, `IsBool(target)`, `IsDouble(target)`, `IsList(target)`, `IsMap(target)` | True only for that kind of value. Nil is false. |
| `Int(target)` | Int. Floats truncate toward zero. Nil stays nil. `Int("2.0")` is an error. |
| `Double(target)` | Float. Nil stays nil. |
| `String(target)` | String. Nil stays nil. A byte slice is lowercase hex. A list or map is JSON. |

```
set(order.priority, "HIGH") where Len(order.id) > 0 and IsMatch(order.status, "^P")
set(attributes["host"], Split(attributes["host"], ":")[0])
```

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

A lambda is a value, and no function shipped with the engine calls one. Converters other than the ones listed above are not included.

`let`, `switch`, and loops are not part of the language.
