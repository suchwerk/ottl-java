/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example.editor;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;

/**
 * Native integration test for the language server and editor API. Runs against the actual compiled
 * native executable and proves that LSP4J JSON-RPC (Gson reflection) and the Quarkus WebSocket
 * transport work in a native image, alongside the dev-only editor REST endpoints.
 */
@QuarkusIntegrationTest
class OttlLspNativeIT {

    @TestHTTPResource("/ottl/lsp")
    URI lspHttpUri;

    @Test
    void editorMetadataAndTestRunWorkNatively() {
        given().when().get("/editor/contexts")
                .then().statusCode(200)
                .body("contexts.name", hasItem("Order"))
                .body("functions.name", hasItem("set"));

        String body = "{\"context\":\"Order\","
                + "\"source\":\"set(order.priority, \\\"HIGH\\\") where order.amount > 1000\","
                + "\"input\":{\"id\":\"o-1\",\"amount\":1500,\"status\":\"NEW\",\"priority\":\"LOW\"}}";
        given().contentType(ContentType.JSON).body(body)
                .when().post("/editor/test")
                .then().statusCode(200)
                .body("priority", is("HIGH"));
    }

    @Test
    void lspWebSocketInitializesNatively() throws Exception {
        URI wsUri = URI.create(lspHttpUri.toString().replaceFirst("^http", "ws"));
        LinkedBlockingQueue<String> inbox = new LinkedBlockingQueue<>();

        WebSocket ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .buildAsync(wsUri, new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (last) {
                            inbox.add(buffer.toString());
                            buffer.setLength(0);
                        }
                        webSocket.request(1);
                        return null;
                    }
                }).get(10, TimeUnit.SECONDS);

        try {
            ws.sendText("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                    + "\"params\":{\"capabilities\":{}}}", true).get(10, TimeUnit.SECONDS);

            String response = inbox.poll(10, TimeUnit.SECONDS);
            assertThat(response).as("native initialize response").isNotNull();
            assertThat(response).contains("capabilities");
            assertThat(response).contains("completionProvider");
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }
}
