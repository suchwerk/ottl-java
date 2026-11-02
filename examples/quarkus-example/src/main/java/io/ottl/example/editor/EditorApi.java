/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example.editor;

import com.fasterxml.jackson.databind.JsonNode;
import io.quarkus.runtime.annotations.RegisterForReflection;

import java.util.List;

/**
 * Data-transfer objects for the development-only editor API. Registered for reflection so they
 * (de)serialize in a native image.
 */
public final class EditorApi {

    private EditorApi() {
    }

    @RegisterForReflection
    public record PropertyDto(String name, String type, boolean readable, boolean writable) {
    }

    @RegisterForReflection
    public record ContextDto(String name, String variable, List<PropertyDto> properties) {
    }

    @RegisterForReflection
    public record FunctionDto(String name, String signature, String documentation,
                              String category, int minArgs, int maxArgs) {
    }

    @RegisterForReflection
    public record MetadataDto(List<ContextDto> contexts, List<FunctionDto> functions) {
    }

    @RegisterForReflection
    public record ValidateRequest(String context, String source) {
    }

    @RegisterForReflection
    public record ValidateResponse(boolean valid, String error) {
    }

    @RegisterForReflection
    public record TestRequest(String context, String source, JsonNode input) {
    }

    @RegisterForReflection
    public record ActivateRequest(String context, String ruleId, String source) {
    }

    @RegisterForReflection
    public record RuleDto(String context, String ruleId, String source, int version) {
    }
}
