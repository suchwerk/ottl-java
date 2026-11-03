/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lsp;

import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the LSP server lifecycle, async document handling, versions and concurrent sessions. */
class OttlLanguageServerTest {

    private final ExecutorService executor = Executors.newCachedThreadPool();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    /** Captures diagnostics the server pushes to the client. */
    static final class FakeClient implements LanguageClient {
        final LinkedBlockingQueue<PublishDiagnosticsParams> diagnostics = new LinkedBlockingQueue<>();

        @Override
        public void telemetryEvent(Object object) {
        }

        @Override
        public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
            this.diagnostics.add(diagnostics);
        }

        @Override
        public void showMessage(MessageParams messageParams) {
        }

        @Override
        public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void logMessage(MessageParams message) {
        }

        PublishDiagnosticsParams await() throws InterruptedException {
            return diagnostics.poll(3, TimeUnit.SECONDS);
        }
    }

    private OttlLanguageServer newServer(FakeClient client) {
        OttlLanguageServer server = new OttlLanguageServer(TestMetadata.build(), executor);
        server.connect(client);
        return server;
    }

    private static DidOpenTextDocumentParams open(String uri, String text, int version) {
        return new DidOpenTextDocumentParams(new TextDocumentItem(uri, "ottl", version, text));
    }

    @Test
    void setTraceAcceptsTheClientNotification() {
        OttlLanguageServer server = newServer(new FakeClient());
        server.setTrace(new SetTraceParams("off"));
        assertThat(server.trace()).isEqualTo("off");
    }

    @Test
    void initializeAdvertisesImplementedCapabilities() throws Exception {
        OttlLanguageServer server = newServer(new FakeClient());
        InitializeResult result = server.initialize(new InitializeParams()).get();
        var caps = result.getCapabilities();
        assertThat(caps.getCompletionProvider()).isNotNull();
        assertThat(caps.getHoverProvider().getLeft()).isTrue();
        assertThat(caps.getSignatureHelpProvider()).isNotNull();
    }

    @Test
    void didOpenPublishesDiagnosticsForInvalidRule() throws Exception {
        FakeClient client = new FakeClient();
        OttlLanguageServer server = newServer(client);
        server.getTextDocumentService().didOpen(
                open("mem://a.ottl", "# context: Order\nset(order.nope, \"X\")", 1));

        PublishDiagnosticsParams published = client.await();
        assertThat(published).isNotNull();
        assertThat(published.getVersion()).isEqualTo(1);
        assertThat(published.getDiagnostics()).anyMatch(d -> d.getMessage().contains("Unknown property"));
    }

    @Test
    void didChangeRepublishesWithNewVersionAndClears() throws Exception {
        FakeClient client = new FakeClient();
        OttlLanguageServer server = newServer(client);
        var docs = server.getTextDocumentService();

        docs.didOpen(open("mem://b.ottl", "# context: Order\nset(order.nope, \"X\")", 1));
        PublishDiagnosticsParams first = client.await();
        assertThat(first.getDiagnostics()).isNotEmpty();

        docs.didChange(new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier("mem://b.ottl", 2),
                List.of(new TextDocumentContentChangeEvent("# context: Order\nset(order.status, \"X\")"))));

        PublishDiagnosticsParams second = client.await();
        assertThat(second.getVersion()).isEqualTo(2);
        assertThat(second.getDiagnostics()).isEmpty();
        assertThat(server.textDocuments().document("mem://b.ottl").version()).isEqualTo(2);
    }

    @Test
    void completionRequestReturnsProperties() throws Exception {
        OttlLanguageServer server = newServer(new FakeClient());
        var docs = server.getTextDocumentService();
        docs.didOpen(open("mem://c.ottl", "# context: Order\nset(order.", 1));

        CompletionParams params = new CompletionParams(
                new TextDocumentIdentifier("mem://c.ottl"), new Position(1, "set(order.".length()));
        Either<List<?>, CompletionList> result = cast(server.getTextDocumentService().completion(params).get());
        assertThat(result.getRight().getItems()).extracting("label")
                .contains("id", "amount", "status", "priority", "customer");
    }

    @Test
    void concurrentSessionsKeepSeparateDocumentState() throws Exception {
        OttlLanguageServer s1 = newServer(new FakeClient());
        OttlLanguageServer s2 = newServer(new FakeClient());

        s1.getTextDocumentService().didOpen(open("mem://same.ottl", "# context: Order\nset(order.status, \"A\")", 1));
        s2.getTextDocumentService().didOpen(open("mem://same.ottl", "# context: Order\nset(order.priority, \"B\")", 1));

        assertThat(s1.textDocuments().document("mem://same.ottl").text()).contains("status");
        assertThat(s2.textDocuments().document("mem://same.ottl").text()).contains("priority");
    }

    @SuppressWarnings("unchecked")
    private static Either<List<?>, CompletionList> cast(Object o) {
        return (Either<List<?>, CompletionList>) o;
    }
}
