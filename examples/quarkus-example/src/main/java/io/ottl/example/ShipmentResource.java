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
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;

/** REST endpoint demonstrating nested record access with COPY bubbling. */
@Path("/shipments")
public class ShipmentResource {

    static final String RULE_ID = "shipment-rules";

    @Inject
    RuleManager<Shipment> rules;

    @POST
    @Path("/transform")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Shipment transform(Shipment shipment) {
        return rules.apply(RULE_ID, shipment);
    }

    @PUT
    @Path("/rules")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    public Response activate(String source) {
        try {
            rules.activate(RULE_ID, source);
        } catch (OttlException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Map.of("error", e.getMessage()))
                    .build();
        }
        return rules.active(RULE_ID)
                .map(def -> Response.ok(new RuleView(def.id(), def.source(), def.version())).build())
                .orElseGet(() -> Response.serverError().build());
    }

    @GET
    @Path("/rules")
    @Produces(MediaType.APPLICATION_JSON)
    public Response get() {
        return rules.active(RULE_ID)
                .map(def -> Response.ok(new RuleView(def.id(), def.source(), def.version())).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }
}
