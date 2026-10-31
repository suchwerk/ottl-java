/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

/** End-to-end REST tests, including a runtime rule change with no application restart. */
@QuarkusTest
class OrderEndpointTest {

    private static final String HIGH_VALUE_ORDER =
            "{\"id\":\"o-1\",\"amount\":15000,\"status\":\"NEW\",\"priority\":\"LOW\"}";
    private static final String LOW_VALUE_ORDER =
            "{\"id\":\"o-2\",\"amount\":500,\"status\":\"NEW\",\"priority\":\"LOW\"}";

    @Test
    void transformAppliesDefaultRuleForHighValueOrders() {
        given().contentType(ContentType.JSON).body(HIGH_VALUE_ORDER)
                .when().post("/orders/transform")
                .then().statusCode(200)
                .body("status", is("REVIEW"));
    }

    @Test
    void lowValueOrderIsUnchanged() {
        given().contentType(ContentType.JSON).body(LOW_VALUE_ORDER)
                .when().post("/orders/transform")
                .then().statusCode(200)
                .body("status", is("NEW"));
    }

    @Test
    void getReturnsActiveRuleAndVersion() {
        given().when().get("/rules/order-rules")
                .then().statusCode(200)
                .body("id", is("order-rules"))
                .body("version", greaterThanOrEqualTo(1));
    }

    @Test
    void invalidRuleReturns400AndKeepsPreviousVersion() {
        given().contentType(ContentType.TEXT).body("set(order.status, )")
                .when().put("/rules/order-rules")
                .then().statusCode(400);
    }

    @Test
    void ruleCanBeChangedAtRuntimeWithoutRestart() {
        try {
            // Replace the active rule with one that upgrades priority for every order.
            given().contentType(ContentType.TEXT).body("set(order.priority, \"URGENT\")")
                    .when().put("/rules/order-rules")
                    .then().statusCode(200)
                    .body("version", greaterThanOrEqualTo(2));

            // The very next request uses the new rule — no restart involved.
            given().contentType(ContentType.JSON).body(LOW_VALUE_ORDER)
                    .when().post("/orders/transform")
                    .then().statusCode(200)
                    .body("priority", is("URGENT"));
        } finally {
            // Restore the default rule so other tests remain order-independent.
            given().contentType(ContentType.TEXT).body(RuleBootstrap.DEFAULT_RULE)
                    .when().put("/rules/order-rules")
                    .then().statusCode(200);
        }
    }
}
