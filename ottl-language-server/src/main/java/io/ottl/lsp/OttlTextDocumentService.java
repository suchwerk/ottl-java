/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lsp;

import io.ottl.lsp.analysis.Analyzer;
import io.ottl.lsp.analysis.TextDocument;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.SignatureHelp;
import org.eclipse.lsp4j.SignatureHelpParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Document lifecycle + language features for one client session. Document state is cached per URI;
 * analysis runs off the request-dispatch thread and diagnostics carry the document version so stale
 * results are discarded by the client.
 */
public class OttlTextDocumentService implements TextDocumentService {

    private final Analyzer analyzer;
    private final Executor executor;
    private final Map<String, TextDocument> documents = new ConcurrentHashMap<>();
    private volatile LanguageClient client;

    public OttlTextDocumentService(Analyzer analyzer, Executor executor) {
        this.analyzer = analyzer;
        this.executor = executor;
    }

    void connect(LanguageClient client) {
        this.client = client;
    }

    // ----- lifecycle -----

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        var item = params.getTextDocument();
        TextDocument doc = new TextDocument(item.getUri(), item.getText(), item.getVersion());
        documents.put(doc.uri(), doc);
        publishDiagnostics(doc);
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        Integer version = params.getTextDocument().getVersion();
        List<TextDocumentContentChangeEvent> changes = params.getContentChanges();
        if (changes.isEmpty()) {
            return;
        }
        // Full-document synchronization: the last change carries the whole text.
        String text = changes.get(changes.size() - 1).getText();
        TextDocument doc = new TextDocument(uri, text, version == null ? 0 : version);
        documents.put(uri, doc);
        publishDiagnostics(doc);
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        documents.remove(uri);
        if (client != null) {
            client.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
        }
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // no-op: diagnostics are already live on change
    }

    // ----- language features (async, off the dispatcher) -----

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            TextDocument doc = documents.get(params.getTextDocument().getUri());
            if (doc == null) {
                return Either.forRight(new CompletionList(false, List.of()));
            }
            return Either.forRight(analyzer.complete(doc, params.getPosition()));
        }, executor);
    }

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        return CompletableFuture.supplyAsync(() -> {
            TextDocument doc = documents.get(params.getTextDocument().getUri());
            return doc == null ? null : analyzer.hover(doc, params.getPosition());
        }, executor);
    }

    @Override
    public CompletableFuture<SignatureHelp> signatureHelp(SignatureHelpParams params) {
        return CompletableFuture.supplyAsync(() -> {
            TextDocument doc = documents.get(params.getTextDocument().getUri());
            return doc == null ? new SignatureHelp() : analyzer.signatureHelp(doc, params.getPosition());
        }, executor);
    }

    // ----- internals -----

    /** Visible for tests: the current cached document, if any. */
    public TextDocument document(String uri) {
        return documents.get(uri);
    }

    private void publishDiagnostics(TextDocument doc) {
        LanguageClient c = this.client;
        if (c == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            // Only publish if this is still the latest version we hold for the URI.
            TextDocument current = documents.get(doc.uri());
            if (current == null || current.version() != doc.version()) {
                return;
            }
            PublishDiagnosticsParams params =
                    new PublishDiagnosticsParams(doc.uri(), analyzer.diagnose(doc));
            params.setVersion(doc.version());
            c.publishDiagnostics(params);
        }, executor);
    }
}
