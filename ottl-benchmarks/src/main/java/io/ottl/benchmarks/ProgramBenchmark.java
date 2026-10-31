/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.benchmarks;

import io.ottl.OttlEngine;
import io.ottl.OttlProfile;
import io.ottl.OttlProgram;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Micro-benchmarks demonstrating that compilation is a one-time cost and that
 * {@link OttlProgram#apply(Object)} is cheap and allocation-light on the hot path.
 *
 * <p>Run with: {@code mvn -q -pl ottl-benchmarks -am package && java -cp <classpath> \
 * org.openjdk.jmh.Main io.ottl.benchmarks.ProgramBenchmark}
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ProgramBenchmark {

    private OttlEngine engine;
    private OttlProgram<Map<String, Object>> program;
    private static final String SOURCE =
            "set(attributes[\"env\"], \"prod\") where attributes[\"amount\"] > 10000";

    @SuppressWarnings("unchecked")
    @Setup
    public void setup() {
        engine = OttlEngine.builder().profile(OttlProfile.STANDARD).build();
        program = engine.compile(SOURCE, (Class<Map<String, Object>>) (Class<?>) Map.class);
    }

    @Benchmark
    public OttlProgram<?> compile() {
        return engine.compile(SOURCE, (Class<Map<String, Object>>) (Class<?>) Map.class);
    }

    @Benchmark
    public Object applyHotPath() {
        Map<String, Object> root = new HashMap<>();
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("amount", 15000L);
        root.put("attributes", attrs);
        return program.apply(root);
    }
}
