/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.context.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.ottl.OttlEvaluationException;
import io.ottl.spi.PropertyResolver;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * {@link PropertyResolver} for Jackson {@link JsonNode} trees.
 *
 * <p>Container nodes ({@link ObjectNode}/{@link ArrayNode}) are returned as-is so navigation can
 * continue into them, while scalar nodes are unwrapped to plain Java values (String, Number,
 * Boolean, null) so they compare naturally against OTTL literals. Writes mutate the tree in place.
 */
public final class JsonNodeResolver implements PropertyResolver {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    @Override
    public boolean canResolve(Object target) {
        return target instanceof JsonNode;
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public Object getField(Object target, String name) {
        if (target instanceof ObjectNode obj) {
            return unwrap(obj.get(name));
        }
        throw new OttlEvaluationException("Cannot read field '" + name + "' on JSON "
                + ((JsonNode) target).getNodeType());
    }

    @Override
    public Object setField(Object target, String name, Object value) {
        if (target instanceof ObjectNode obj) {
            obj.set(name, toNode(value));
            return obj;
        }
        throw new OttlEvaluationException("Cannot set field '" + name + "' on JSON "
                + ((JsonNode) target).getNodeType());
    }

    @Override
    public Object getKey(Object target, Object key) {
        if (target instanceof ObjectNode obj && key instanceof String s) {
            return unwrap(obj.get(s));
        }
        if (target instanceof ArrayNode arr && key instanceof Number n) {
            return unwrap(arr.get(n.intValue()));
        }
        throw new OttlEvaluationException("Unsupported JSON keyed access with key " + key);
    }

    @Override
    public Object setKey(Object target, Object key, Object value) {
        if (target instanceof ObjectNode obj && key instanceof String s) {
            obj.set(s, toNode(value));
            return obj;
        }
        if (target instanceof ArrayNode arr && key instanceof Number n) {
            arr.set(n.intValue(), toNode(value));
            return arr;
        }
        throw new OttlEvaluationException("Unsupported JSON keyed write with key " + key);
    }

    @Override
    public Object deleteKey(Object target, Object key) {
        if (target instanceof ObjectNode obj && key instanceof String s) {
            obj.remove(s);
            return obj;
        }
        throw new OttlEvaluationException("delete_key requires a JSON object and string key");
    }

    @Override
    public Object keepKeys(Object target, Collection<Object> keys) {
        if (target instanceof ObjectNode obj) {
            Set<String> keep = new HashSet<>();
            for (Object k : keys) {
                keep.add(String.valueOf(k));
            }
            obj.retain(keep);
            return obj;
        }
        throw new OttlEvaluationException("keep_keys requires a JSON object");
    }

    private static Object unwrap(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isObject() || node.isArray()) {
            return node; // keep container for further navigation
        }
        if (node.isTextual()) {
            return node.textValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.numberValue();
        }
        return node.asText();
    }

    private static JsonNode toNode(Object value) {
        if (value == null) {
            return NODES.nullNode();
        }
        if (value instanceof JsonNode node) {
            return node;
        }
        if (value instanceof String s) {
            return NODES.textNode(s);
        }
        if (value instanceof Boolean b) {
            return NODES.booleanNode(b);
        }
        if (value instanceof Integer i) {
            return NODES.numberNode(i);
        }
        if (value instanceof Long l) {
            return NODES.numberNode(l);
        }
        if (value instanceof Double d) {
            return NODES.numberNode(d);
        }
        if (value instanceof Float f) {
            return NODES.numberNode(f);
        }
        if (value instanceof BigDecimal bd) {
            return NODES.numberNode(bd);
        }
        if (value instanceof BigInteger bi) {
            return NODES.numberNode(bi);
        }
        if (value instanceof List<?> list) {
            ArrayNode arr = NODES.arrayNode(list.size());
            for (Object e : list) {
                arr.add(toNode(e));
            }
            return arr;
        }
        return NODES.textNode(value.toString());
    }
}
