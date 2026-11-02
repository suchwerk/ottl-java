/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import io.ottl.rules.RuleManager;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/** Activates default rules at startup so the app is useful out of the box. */
@ApplicationScoped
public class RuleBootstrap {

    static final String DEFAULT_RULE =
            "set(order.status, \"REVIEW\") where IsHighValue(order.amount)";
    static final String DEFAULT_CUSTOMER_RULE =
            "set(customer.tier, \"STANDARD\")";
    static final String DEFAULT_SHIPMENT_RULE =
            "set(shipment.carrier, \"DHL\")";
    static final String DEFAULT_STOCK_RULE =
            "set(stockMovement.direction, \"IN\") where stockMovement.quantity > 0";

    void onStart(@Observes StartupEvent event,
                 RuleManager<Order> orders,
                 RuleManager<Customer> customers,
                 RuleManager<Shipment> shipments,
                 RuleManager<StockMovement> stock) {
        orders.activate("order-rules", DEFAULT_RULE);
        customers.activate(CustomerResource.RULE_ID, DEFAULT_CUSTOMER_RULE);
        shipments.activate(ShipmentResource.RULE_ID, DEFAULT_SHIPMENT_RULE);
        stock.activate("stock-rules", DEFAULT_STOCK_RULE);
    }
}
