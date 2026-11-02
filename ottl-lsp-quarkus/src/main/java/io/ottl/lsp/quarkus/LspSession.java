/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lsp.quarkus;

import io.ottl.lsp.OttlLanguageServer;
import io.ottl.lsp.metadata.OttlMetadata;
import io.quarkus.websockets.next.WebSocketConnection;
import org.eclipse.lsp4j.jsonrpc.Endpoint;
import org.eclipse.lsp4j.jsonrpc.MessageConsumer;
import org.eclipse.lsp4j.jsonrpc.RemoteEndpoint;
import org.eclipse.lsp4j.jsonrpc.json.JsonRpcMethod;
import org.eclipse.lsp4j.jsonrpc.json.MessageJsonHandler;
import org.eclipse.lsp4j.jsonrpc.messages.Message;
import org.eclipse.lsp4j.jsonrpc.services.ServiceEndpoints;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.jboss.logging.Logger;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Bridges one WebSocket connection to a dedicated {@link OttlLanguageServer} using LSP4J JSON-RPC.
 *
 * <p>Each WebSocket <em>text frame</em> is exactly one JSON-RPC message — there is deliberately
 * <strong>no stdio {@code Content-Length} framing</strong>. Every session has its own server and
 * document store, so concurrent editor sessions never share state.
 */
final class LspSession {

    private static final Logger LOG = Logger.getLogger(LspSession.class);

    private final OttlLanguageServer server;
    private final MessageJsonHandler jsonHandler;
    private final RemoteEndpoint remoteEndpoint;
    private final WebSocketConnection connection;
    private final Object sendLock = new Object();

    LspSession(OttlMetadata metadata, Executor executor, WebSocketConnection connection) {
        this.connection = connection;
        this.server = new OttlLanguageServer(metadata, executor);

        Map<String, JsonRpcMethod> methods = new LinkedHashMap<>();
        methods.putAll(ServiceEndpoints.getSupportedMethods(LanguageServer.class));
        methods.putAll(ServiceEndpoints.getSupportedMethods(LanguageClient.class));
        this.jsonHandler = new MessageJsonHandler(methods);

        MessageConsumer outgoing = message -> {
            String json = jsonHandler.serialize(message);
            synchronized (sendLock) {
                connection.sendTextAndAwait(json);
            }
        };

        Endpoint localEndpoint = ServiceEndpoints.toEndpoint(server);
        this.remoteEndpoint = new RemoteEndpoint(outgoing, localEndpoint);
        this.jsonHandler.setMethodProvider(remoteEndpoint);

        // Proxy so the server can call back the client (e.g. publishDiagnostics).
        LanguageClient client = ServiceEndpoints.toServiceObject(remoteEndpoint, LanguageClient.class);
        server.connect(client);
    }

    /** Handle one inbound JSON-RPC text frame. */
    void consume(String text) {
        try {
            Message message = jsonHandler.parseMessage(text);
            remoteEndpoint.consume(message);
        } catch (RuntimeException e) {
            LOG.warnf(e, "Failed to process LSP message on connection %s", connection.id());
        }
    }

    void close() {
        try {
            server.shutdown();
        } catch (RuntimeException e) {
            LOG.debugf(e, "Error shutting down LSP server for connection %s", connection.id());
        }
    }
}
