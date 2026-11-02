/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

/**
 * Native integration tests. These run against the <strong>actual compiled native executable</strong>
 * (via {@link QuarkusIntegrationTest}) and exercise the full acceptance scenario: runtime rule
 * compilation and hot swapping inside a GraalVM/Mandrel native image, with build-time generated
 * reflection-free accessors for the registered {@code @OttlAccessible} domain types.
 */
@QuarkusIntegrationTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OttlNativeIT {

    private static final String HIGH_VALUE_ORDER =
            "{\"id\":\"o-1\",\"amount\":15000,\"status\":\"NEW\",\"priority\":\"LOW\"}";
    private static final String LOW_VALUE_ORDER =
            "{\"id\":\"o-2\",\"amount\":500,\"status\":\"NEW\",\"priority\":\"LOW\"}";

    // ----- Test 1: initial rule -----

    @Test
    @Order(1)
    void initialRuleTransformsOrder() {
        given().contentType(ContentType.TEXT).body("set(order.priority, \"HIGH\") where order.amount > 1000")
                .when().put("/rules/order-rules")
                .then().statusCode(200);

        given().contentType(ContentType.JSON).body(HIGH_VALUE_ORDER)
                .when().post("/orders/transform")
                .then().statusCode(200)
                .body("priority", is("HIGH"));

        given().contentType(ContentType.JSON).body(LOW_VALUE_ORDER)
                .when().post("/orders/transform")
                .then().statusCode(200)
                .body("priority", is("LOW"));
    }

    // ----- Test 2: runtime modification (hot swap after startup) -----

    @Test
    @Order(2)
    void ruleCanBeChangedAtRuntime() {
        given().contentType(ContentType.TEXT).body("set(order.priority, \"URGENT\")")
                .when().put("/rules/order-rules")
                .then().statusCode(200)
                .body("version", greaterThanOrEqualTo(2));

        // The very next request uses the new rule — the native process did not restart.
        given().contentType(ContentType.JSON).body(LOW_VALUE_ORDER)
                .when().post("/orders/transform")
                .then().statusCode(200)
                .body("priority", is("URGENT"));
    }

    // ----- Test 3: invalid rule keeps the previous active version -----

    @Test
    @Order(3)
    void invalidRuleIsRejectedAndPreviousRemainsActive() {
        given().contentType(ContentType.TEXT).body("set(order.priority, \"STABLE\")")
                .when().put("/rules/order-rules")
                .then().statusCode(200);

        int versionBefore = given().when().get("/rules/order-rules")
                .then().statusCode(200).extract().path("version");

        // Unknown property -> rejected at compile time, before activation.
        given().contentType(ContentType.TEXT).body("set(order.unknownField, \"X\")")
                .when().put("/rules/order-rules")
                .then().statusCode(400);

        // Previous version is untouched and still works.
        given().when().get("/rules/order-rules")
                .then().statusCode(200)
                .body("version", is(versionBefore))
                .body("source", is("set(order.priority, \"STABLE\")"));

        given().contentType(ContentType.JSON).body(LOW_VALUE_ORDER)
                .when().post("/orders/transform")
                .then().statusCode(200)
                .body("priority", is("STABLE"));
    }

    // ----- Test 4: POJO (JavaBean) mutation -----

    @Test
    @Order(4)
    void pojoIsMutatedThroughSetters() {
        given().contentType(ContentType.TEXT)
                .body("set(customer.tier, \"GOLD\") where customer.loyaltyPoints > 1000")
                .when().put("/customers/rules")
                .then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body("{\"id\":\"c-1\",\"name\":\"Ada\",\"tier\":\"BRONZE\",\"loyaltyPoints\":5000}")
                .when().post("/customers/transform")
                .then().statusCode(200)
                .body("tier", is("GOLD"))
                .body("name", is("Ada"));

        given().contentType(ContentType.JSON)
                .body("{\"id\":\"c-2\",\"name\":\"Bob\",\"tier\":\"BRONZE\",\"loyaltyPoints\":100}")
                .when().post("/customers/transform")
                .then().statusCode(200)
                .body("tier", is("BRONZE"));
    }

    // ----- Test 5: record COPY semantics -----

    @Test
    @Order(5)
    void recordIsCopiedImmutably() {
        given().contentType(ContentType.TEXT).body("set(order.status, \"ARCHIVED\")")
                .when().put("/rules/order-rules")
                .then().statusCode(200);

        given().contentType(ContentType.JSON).body(HIGH_VALUE_ORDER)
                .when().post("/orders/transform")
                .then().statusCode(200)
                .body("status", is("ARCHIVED"))
                .body("id", is("o-1")) // untouched components are preserved by the canonical copy
                .body("amount", is(15000));
    }

    // ----- Test 6: nested property access with COPY bubbling -----

    @Test
    @Order(6)
    void nestedRecordUpdateReconstructsParents() {
        given().contentType(ContentType.TEXT).body("set(shipment.address.city, \"Berlin\")")
                .when().put("/shipments/rules")
                .then().statusCode(200);

        given().contentType(ContentType.JSON)
                .body("{\"id\":\"s-1\",\"carrier\":\"DHL\","
                        + "\"address\":{\"street\":\"Main\",\"city\":\"Munich\",\"zip\":\"80331\"}}")
                .when().post("/shipments/transform")
                .then().statusCode(200)
                .body("address.city", is("Berlin"))
                .body("address.street", is("Main")) // sibling preserved
                .body("address.zip", is("80331"))
                .body("carrier", is("DHL")); // parent sibling preserved
    }

    // ----- Test 7: concurrent execution during rule replacement -----

    @Test
    @Order(7)
    void concurrentTransformsAlwaysSeeACompleteRuleVersion() throws Exception {
        // Two complete, valid rule versions; every response must reflect exactly one of them.
        String ruleA = "set(order.priority, \"AAA\")";
        String ruleB = "set(order.priority, \"BBB\")";
        given().contentType(ContentType.TEXT).body(ruleA).when().put("/rules/order-rules")
                .then().statusCode(200);

        Set<String> allowed = Set.of("AAA", "BBB");
        AtomicBoolean stop = new AtomicBoolean(false);
        ConcurrentLinkedQueue<String> bad = new ConcurrentLinkedQueue<>();
        ExecutorService pool = Executors.newFixedThreadPool(9);
        CountDownLatch done = new CountDownLatch(8);

        // 1 swapper thread flips the active rule as fast as it can.
        pool.submit(() -> {
            boolean a = false;
            while (!stop.get()) {
                given().contentType(ContentType.TEXT).body(a ? ruleA : ruleB)
                        .when().put("/rules/order-rules").then().statusCode(200);
                a = !a;
            }
        });

        // 8 worker threads transform orders and record any priority that is not a complete version.
        for (int t = 0; t < 8; t++) {
            pool.submit(() -> {
                try {
                    for (int i = 0; i < 40; i++) {
                        String priority = given().contentType(ContentType.JSON).body(LOW_VALUE_ORDER)
                                .when().post("/orders/transform")
                                .then().statusCode(200).extract().path("priority");
                        if (!allowed.contains(priority)) {
                            bad.add(String.valueOf(priority));
                        }
                    }
                } finally {
                    done.countDown();
                }
            });
        }

        assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
        stop.set(true);
        pool.shutdownNow();
        assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        assertThat(bad).as("every response must observe one complete rule version").isEmpty();
    }

    // ----- Test 8: native security restrictions -----

    @Test
    @Order(8)
    void unknownPropertiesAndFunctionsAreRejected() {
        // Unknown property on a registered type.
        given().contentType(ContentType.TEXT).body("set(order.doesNotExist, \"x\")")
                .when().put("/rules/order-rules")
                .then().statusCode(400);

        // Unregistered function.
        given().contentType(ContentType.TEXT).body("set(order.status, NoSuchFunction(order.id))")
                .when().put("/rules/order-rules")
                .then().statusCode(400);

        // Unknown property reached through a nested path.
        given().contentType(ContentType.TEXT).body("set(shipment.address.country, \"DE\")")
                .when().put("/shipments/rules")
                .then().statusCode(400);
    }
}
