/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example.editor;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.ottl.OttlEngine;
import io.ottl.OttlException;
import io.ottl.OttlProgram;
import io.ottl.lsp.metadata.OttlMetadata;
import io.ottl.lsp.metadata.TypeInfo;
import io.ottl.rules.RuleManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Development-only API backing the Vue/Quasar editor. It reuses the engine and the existing
 * {@link RuleManager}s — it does not duplicate the execution engine — and exposes only approved
 * context metadata.
 *
 * <p><strong>WARNING:</strong> these endpoints compile and run arbitrary rule source and expose
 * type metadata. Secure them (authn/authz, network policy) before any production use.
 */
@Path("/editor")
public class EditorResource {

    @Inject
    OttlEngine engine;
    @Inject
    OttlMetadata metadata;
    @Inject
    EditorContexts contexts;
    @Inject
    ObjectMapper mapper;

    @GET
    @Path("/contexts")
    @Produces(MediaType.APPLICATION_JSON)
    public EditorApi.MetadataDto contexts() {
        List<EditorApi.ContextDto> ctxDtos = new ArrayList<>();
        for (EditorContexts.Ctx c : contexts.all()) {
            TypeInfo t = metadata.context(c.name()).orElse(null);
            if (t == null) {
                continue;
            }
            List<EditorApi.PropertyDto> props = t.properties().stream()
                    .map(p -> new EditorApi.PropertyDto(p.name(), p.typeSimpleName(), p.readable(), p.writable()))
                    .toList();
            ctxDtos.add(new EditorApi.ContextDto(t.simpleName(), metadata.contextVariable(t), props));
        }
        List<EditorApi.FunctionDto> fnDtos = metadata.functions().stream()
                .map(f -> new EditorApi.FunctionDto(f.name(), f.signatureLabel(), f.documentation(),
                        f.category(), f.minArgs(), f.maxArgs()))
                .toList();
        return new EditorApi.MetadataDto(ctxDtos, fnDtos);
    }

    @POST
    @Path("/validate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public EditorApi.ValidateResponse validate(EditorApi.ValidateRequest req) {
        EditorContexts.Ctx ctx = contexts.get(req.context());
        if (ctx == null) {
            return new EditorApi.ValidateResponse(false, "Unknown context: " + req.context());
        }
        try {
            engine.compile(req.source(), ctx.type());
            return new EditorApi.ValidateResponse(true, null);
        } catch (OttlException e) {
            return new EditorApi.ValidateResponse(false, e.getMessage());
        }
    }

    @POST
    @Path("/test")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response test(EditorApi.TestRequest req) {
        EditorContexts.Ctx ctx = contexts.get(req.context());
        if (ctx == null) {
            return badRequest("Unknown context: " + req.context());
        }
        try {
            Object result = runTest(ctx.type(), req.source(), req);
            return Response.ok(result).build();
        } catch (OttlException e) {
            return badRequest(e.getMessage());
        } catch (IllegalArgumentException e) {
            return badRequest("Invalid sample input: " + e.getMessage());
        }
    }

    @POST
    @Path("/activate")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response activate(EditorApi.ActivateRequest req) {
        EditorContexts.Ctx ctx = contexts.get(req.context());
        if (ctx == null) {
            return badRequest("Unknown context: " + req.context());
        }
        // Each context has one canonical rule. A rule id from another context must not be
        // activated on this context's RuleManager.
        if (req.ruleId() != null && !req.ruleId().isBlank() && !req.ruleId().equals(ctx.ruleId())) {
            return badRequest("Rule '" + req.ruleId() + "' does not belong to context '" + ctx.name() + "'");
        }
        String ruleId = ctx.ruleId();
        try {
            ctx.manager().activate(ruleId, req.source());
        } catch (OttlException e) {
            return badRequest(e.getMessage());
        }
        int version = ctx.manager().active(ruleId).map(d -> d.version()).orElse(-1);
        return Response.ok(new EditorApi.RuleDto(ctx.name(), ruleId, req.source(), version)).build();
    }

    @GET
    @Path("/rules")
    @Produces(MediaType.APPLICATION_JSON)
    public List<EditorApi.RuleDto> rules() {
        List<EditorApi.RuleDto> out = new ArrayList<>();
        for (EditorContexts.Ctx c : contexts.all()) {
            c.manager().active(c.ruleId()).ifPresent(d ->
                    out.add(new EditorApi.RuleDto(c.name(), d.id(), d.source(), d.version())));
        }
        return out;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object runTest(Class<?> type, String source, EditorApi.TestRequest req) {
        Object typed = mapper.convertValue(req.input(), type);
        OttlProgram program = engine.compile(source, (Class) type);
        return program.apply(typed);
    }

    private static Response badRequest(String message) {
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(Map.of("error", message))
                .build();
    }
}
