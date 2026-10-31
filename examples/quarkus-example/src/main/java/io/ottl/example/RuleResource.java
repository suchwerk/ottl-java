/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import io.ottl.OttlException;
import io.ottl.rules.RuleManager;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/**
 * Development-only management API for rules.
 *
 * <p><strong>WARNING:</strong> these endpoints compile and activate arbitrary rule source and are
 * intended for local development and demos only. They MUST be secured (authn/authz, network policy)
 * before any production use.
 */
@Path("/rules")
public class RuleResource {

    @Inject
    RuleManager<Order> rules;

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response activate(@PathParam("id") String id, String source) {
        try {
            rules.activate(id, source);
        } catch (OttlException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
        return rules.active(id)
                .map(def -> Response.ok(new RuleView(def.id(), def.source(), def.version())).build())
                .orElseGet(() -> Response.serverError().build());
    }

    @GET
    @Path("/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get(@PathParam("id") String id) {
        return rules.active(id)
                .map(def -> Response.ok(new RuleView(def.id(), def.source(), def.version())).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }
}
