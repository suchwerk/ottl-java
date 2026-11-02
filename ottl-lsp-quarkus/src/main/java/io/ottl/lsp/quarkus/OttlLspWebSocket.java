/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lsp.quarkus;

import io.ottl.lsp.metadata.OttlMetadata;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import io.smallrye.common.annotation.Blocking;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * WebSocket transport for the OTTL Language Server at {@code /ottl/lsp}.
 *
 * <p>Keeps one {@link LspSession} (its own LSP server + document store) per connection, so multiple
 * editor sessions run concurrently and independently. Sessions are cleaned up on disconnect.
 */
@WebSocket(path = "/ottl/lsp")
@Singleton
public class OttlLspWebSocket {

    private final java.util.Map<String, LspSession> sessions = new ConcurrentHashMap<>();

    @Inject
    OttlMetadata metadata;

    @Inject
    ExecutorService executor;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        sessions.put(connection.id(), new LspSession(metadata, executor, connection));
    }

    @OnTextMessage
    @Blocking
    public void onMessage(String message, WebSocketConnection connection) {
        LspSession session = sessions.get(connection.id());
        if (session != null) {
            session.consume(message);
        }
    }

    @OnClose
    public void onClose(WebSocketConnection connection) {
        LspSession session = sessions.remove(connection.id());
        if (session != null) {
            session.close();
        }
    }
}
