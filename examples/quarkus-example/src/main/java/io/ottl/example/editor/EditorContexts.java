/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example.editor;

import io.ottl.example.Customer;
import io.ottl.example.Order;
import io.ottl.example.Shipment;
import io.ottl.example.StockMovement;
import io.ottl.rules.RuleManager;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps an editor context name (e.g. {@code "Order"}) to its Java type, {@link RuleManager} and the
 * rule id used for its active rule. This is an explicit allowlist — user input can never select an
 * arbitrary Java class.
 */
@ApplicationScoped
public class EditorContexts {

    /** One approved editor context. */
    public record Ctx(String name, Class<?> type, RuleManager<?> manager, String ruleId) {
    }

    @Inject
    RuleManager<Order> orders;
    @Inject
    RuleManager<Customer> customers;
    @Inject
    RuleManager<Shipment> shipments;
    @Inject
    RuleManager<StockMovement> stock;

    private final Map<String, Ctx> byName = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        register(new Ctx("Order", Order.class, orders, "order-rules"));
        register(new Ctx("Customer", Customer.class, customers, "customer-rules"));
        register(new Ctx("Shipment", Shipment.class, shipments, "shipment-rules"));
        register(new Ctx("StockMovement", StockMovement.class, stock, "stock-rules"));
    }

    private void register(Ctx ctx) {
        byName.put(ctx.name(), ctx);
    }

    public Ctx get(String name) {
        return byName.get(name);
    }

    public Collection<Ctx> all() {
        return byName.values();
    }
}
