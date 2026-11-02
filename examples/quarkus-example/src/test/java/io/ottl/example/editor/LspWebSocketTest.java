/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.example.editor;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the Quarkus WebSocket transport speaks LSP4J JSON-RPC (one JSON message per text frame,
 * no stdio Content-Length framing) and drives the real language server.
 */
@QuarkusTest
class LspWebSocketTest {

    @TestHTTPResource("/ottl/lsp")
    URI lspHttpUri;

    @Test
    void initializeReturnsServerCapabilities() throws Exception {
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
                }).get(5, TimeUnit.SECONDS);

        try {
            String initialize = "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                    + "\"params\":{\"processId\":null,\"rootUri\":null,\"capabilities\":{}}}";
            ws.sendText(initialize, true).get(5, TimeUnit.SECONDS);

            String response = inbox.poll(5, TimeUnit.SECONDS);
            assertThat(response).as("initialize response").isNotNull();
            assertThat(response).contains("\"id\":1");
            assertThat(response).contains("capabilities");
            assertThat(response).contains("completionProvider");
            assertThat(response).contains("hoverProvider");
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }

    @Test
    void didOpenPublishesDiagnosticsForUnknownProperty() throws Exception {
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
                }).get(5, TimeUnit.SECONDS);

        try {
            ws.sendText("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\","
                    + "\"params\":{\"capabilities\":{}}}", true).get(5, TimeUnit.SECONDS);
            // drain the initialize response
            inbox.poll(5, TimeUnit.SECONDS);
            ws.sendText("{\"jsonrpc\":\"2.0\",\"method\":\"initialized\",\"params\":{}}", true)
                    .get(5, TimeUnit.SECONDS);

            String source = "# context: Order\\nset(order.bogus, 1)";
            String didOpen = "{\"jsonrpc\":\"2.0\",\"method\":\"textDocument/didOpen\",\"params\":{"
                    + "\"textDocument\":{\"uri\":\"inmemory://model/1.ottl\",\"languageId\":\"ottl\","
                    + "\"version\":1,\"text\":\"" + source + "\"}}}";
            ws.sendText(didOpen, true).get(5, TimeUnit.SECONDS);

            String diagnostics = null;
            for (int i = 0; i < 5; i++) {
                String msg = inbox.poll(5, TimeUnit.SECONDS);
                if (msg != null && msg.contains("publishDiagnostics")) {
                    diagnostics = msg;
                    break;
                }
            }
            assertThat(diagnostics).as("publishDiagnostics notification").isNotNull();
            assertThat(diagnostics).contains("bogus");
        } finally {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
        }
    }
}
