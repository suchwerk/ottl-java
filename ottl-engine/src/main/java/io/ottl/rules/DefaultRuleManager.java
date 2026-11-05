/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.rules;

import io.ottl.OttlEngine;
import io.ottl.OttlException;
import io.ottl.OttlProgram;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Default {@link RuleManager} implementation.
 *
 * <p>Active programs are stored in a {@link ConcurrentHashMap}. Activation uses
 * {@link ConcurrentHashMap#compute} so that version bumping and the swap are atomic, and crucially
 * the <em>new program is compiled before the swap</em>: if compilation throws, the map is left
 * unchanged and the previously active version is retained. Reads ({@link #apply}) are lock-free, and
 * each {@link OttlProgram} is immutable, so concurrent reads during an update are safe.
 */
public final class DefaultRuleManager<T> implements RuleManager<T> {

    /** An active rule: its definition plus its immutable compiled program. */
    private record ActiveRule<T>(RuleDefinition definition, OttlProgram<T> program) {
    }

    private final OttlEngine engine;
    private final Class<T> type;
    private final RuleRepository repository;
    private final ConcurrentMap<String, ActiveRule<T>> active = new ConcurrentHashMap<>();

    public DefaultRuleManager(OttlEngine engine, Class<T> type, RuleRepository repository) {
        this.engine = engine;
        this.type = type;
        this.repository = repository;
    }

    @Override
    public void activate(String id, String source) {
        active.compute(id, (key, previous) -> {
            int nextVersion = nextVersion(previous, id);
            // Compile first. If this throws, compute() leaves the mapping untouched (previous kept)
            // and the repository is not updated.
            OttlProgram<T> program = engine.compile(source, type);
            RuleDefinition definition = new RuleDefinition(id, source, nextVersion, Instant.now());
            repository.save(definition);
            return new ActiveRule<>(definition, program);
        });
    }

    /**
     * Continue the version sequence. A rule that lives only in the repository (a fresh manager, or
     * a process that has not activated it yet) must not restart at 1.
     */
    private int nextVersion(ActiveRule<T> previous, String id) {
        if (previous != null) {
            return previous.definition().version() + 1;
        }
        return repository.find(id).map(definition -> definition.version() + 1).orElse(1);
    }

    @Override
    public T apply(String id, T input) {
        ActiveRule<T> rule = active.get(id);
        if (rule == null) {
            rule = loadFromRepository(id);
        }
        if (rule == null) {
            throw new OttlException("No active rule for id '" + id + "'");
        }
        return rule.program().apply(input);
    }

    @Override
    public Optional<RuleDefinition> active(String id) {
        ActiveRule<T> rule = active.get(id);
        if (rule != null) {
            return Optional.of(rule.definition());
        }
        return repository.find(id);
    }

    /** Lazily compile a rule that exists in the repository but has not been activated in-process. */
    private ActiveRule<T> loadFromRepository(String id) {
        return repository.find(id)
                .map(def -> active.computeIfAbsent(id, k ->
                        new ActiveRule<>(def, engine.compile(def.source(), type))))
                .orElse(null);
    }
}
