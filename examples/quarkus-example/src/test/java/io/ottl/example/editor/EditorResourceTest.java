/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example.editor;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

/** Tests for the development-only editor API that backs the Vue/Quasar front end. */
@QuarkusTest
class EditorResourceTest {

    @Test
    void contextsExposeApprovedTypeMetadataFromTheRegistry() {
        given().when().get("/editor/contexts")
                .then().statusCode(200)
                .body("contexts.name", hasItem("Order"))
                .body("contexts.name", hasItem("Customer"))
                .body("contexts.name", not(hasItem("OrderStatus")))
                .body("functions.name", hasItem("set"));
    }

    @Test
    void validateReportsCompilationErrors() {
        given().contentType(ContentType.JSON)
                .body("{\"context\":\"Order\",\"source\":\"set(order.status, )\"}")
                .when().post("/editor/validate")
                .then().statusCode(200)
                .body("valid", is(false))
                .body("error", notNullValue());
    }

    @Test
    void validateAcceptsGoodRule() {
        given().contentType(ContentType.JSON)
                .body("{\"context\":\"Order\",\"source\":\"set(order.priority, \\\"HIGH\\\") where order.amount > 1000\"}")
                .when().post("/editor/validate")
                .then().statusCode(200)
                .body("valid", is(true));
    }

    @Test
    void enumConstantIsUsableInAnExpressionAndIsNotAContext() {
        given().contentType(ContentType.JSON)
                .body("{\"context\":\"Order\",\"source\":\"set(order.status, OrderStatus.COMPLETED) where order.status == OrderStatus.PENDING\"}")
                .when().post("/editor/validate")
                .then().statusCode(200)
                .body("valid", is(true));

        given().contentType(ContentType.JSON)
                .body("{\"context\":\"OrderStatus\",\"source\":\"set(order.status, OrderStatus.PENDING)\"}")
                .when().post("/editor/validate")
                .then().statusCode(200)
                .body("valid", is(false));

        given().contentType(ContentType.JSON)
                .body("{\"context\":\"Order\",\"source\":\"set(order.status, OrderStatus.NOPE)\"}")
                .when().post("/editor/validate")
                .then().statusCode(200)
                .body("valid", is(false))
                .body("error", containsString("Unknown enum constant"));
    }

    @Test
    void testRunsRuleAgainstSampleInput() {
        // The spec example: a high-value order gets priority HIGH.
        String body = "{\"context\":\"Order\","
                + "\"source\":\"set(order.priority, \\\"HIGH\\\") where order.amount > 1000\","
                + "\"input\":{\"id\":\"o-1\",\"amount\":1500,\"status\":\"PENDING\",\"priority\":\"LOW\"}}";
        given().contentType(ContentType.JSON).body(body)
                .when().post("/editor/test")
                .then().statusCode(200)
                .body("priority", is("HIGH"));
    }

    @Test
    void testRejectsUnknownContext() {
        given().contentType(ContentType.JSON)
                .body("{\"context\":\"java.lang.Runtime\",\"source\":\"set(x.y, 1)\",\"input\":{}}")
                .when().post("/editor/test")
                .then().statusCode(400);
    }

    @Test
    void activateUpdatesTheRuntimeRule() {
        try {
            given().contentType(ContentType.JSON)
                    .body("{\"context\":\"Order\",\"ruleId\":\"order-rules\","
                            + "\"source\":\"set(order.priority, \\\"URGENT\\\")\"}")
                    .when().post("/editor/activate")
                    .then().statusCode(200)
                    .body("version", greaterThanOrEqualTo(2));

            // The next execution uses the updated rule.
            given().contentType(ContentType.JSON)
                    .body("{\"id\":\"o-2\",\"amount\":500,\"status\":\"PENDING\",\"priority\":\"LOW\"}")
                    .when().post("/orders/transform")
                    .then().statusCode(200)
                    .body("priority", is("URGENT"));
        } finally {
            // Restore the default rule so other tests stay order-independent.
            given().contentType(ContentType.JSON)
                    .body("{\"context\":\"Order\",\"ruleId\":\"order-rules\","
                            + "\"source\":\"set(order.status, OrderStatus.PROCESSING) where IsHighValue(order.amount)\"}")
                    .when().post("/editor/activate")
                    .then().statusCode(200);
        }
    }
}
