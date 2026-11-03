/*
 * Copyright The OTTL Java Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.ottl.lsp;

import io.ottl.lsp.analysis.Analyzer;
import io.ottl.lsp.metadata.OttlMetadata;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.SetTraceParams;
import org.eclipse.lsp4j.SignatureHelpOptions;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.TraceValue;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * The OTTL {@link LanguageServer}. Transport-agnostic: the same instance works over stdio (tests) or
 * a Quarkus WebSocket (the editor). It advertises only the capabilities it actually implements.
 */
public class OttlLanguageServer implements LanguageServer, LanguageClientAware {

    private final OttlTextDocumentService textDocumentService;
    private final OttlWorkspaceService workspaceService = new OttlWorkspaceService();
    private volatile boolean shutdown;
    private volatile String trace = TraceValue.Off;

    public OttlLanguageServer(OttlMetadata metadata, Executor executor) {
        this.textDocumentService = new OttlTextDocumentService(new Analyzer(metadata), executor);
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);

        CompletionOptions completion = new CompletionOptions(false, List.of(".", "(", ",", " "));
        capabilities.setCompletionProvider(completion);
        capabilities.setHoverProvider(true);
        capabilities.setSignatureHelpProvider(new SignatureHelpOptions(List.of("(", ",")));

        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    @Override
    public void initialized(org.eclipse.lsp4j.InitializedParams params) {
        // nothing further to register
    }

    /**
     * Monaco sends {@code $/setTrace} right after connect. The lsp4j default throws
     * {@link UnsupportedOperationException}, which the JSON-RPC layer logs as a failed notification.
     */
    @Override
    public void setTrace(SetTraceParams params) {
        if (params != null && params.getValue() != null) {
            trace = params.getValue();
        }
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        shutdown = true;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        // The transport owns process/session lifecycle; nothing to do here.
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textDocumentService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public void connect(LanguageClient client) {
        textDocumentService.connect(client);
    }

    public boolean isShutdown() {
        return shutdown;
    }

    /** Latest {@code $/setTrace} value. {@code off}, {@code messages}, or {@code verbose}. */
    public String trace() {
        return trace;
    }

    public OttlTextDocumentService textDocuments() {
        return textDocumentService;
    }
}
