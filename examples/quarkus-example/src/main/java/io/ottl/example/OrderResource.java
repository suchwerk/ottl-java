/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** REST endpoint that applies the active OTTL rules to an order. */
@Path("/orders")
public class OrderResource {

    @Inject
    OrderService service;

    @POST
    @Path("/transform")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Order transform(Order order) {
        return service.process(order);
    }
}
