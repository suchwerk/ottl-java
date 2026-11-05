/**
 * Wires Monaco (via monaco-vscode-api) to the OTTL language server over a WebSocket, following the
 * official monaco-languageclient v11 "extended" example. All language intelligence is served by the
 * Java LSP; this file only bootstraps the editor and the transport.
 */
import * as vscode from 'vscode';
import * as monaco from '@codingame/monaco-vscode-editor-api';
import { LogLevel } from '@codingame/monaco-vscode-api';
import {
    RegisteredFileSystemProvider,
    RegisteredMemoryFile,
    registerFileSystemOverlay
} from '@codingame/monaco-vscode-files-service-override';
import { type ConnectionConfigOptions } from 'monaco-languageclient/common';
import { EditorApp, type EditorAppConfig } from 'monaco-languageclient/editorApp';
import {
    LanguageClientWrapper,
    LcWebSocket,
    type LanguageClientConfig
} from 'monaco-languageclient/lcwrapper';
import {
    MonacoVscodeApiWrapper,
    type MonacoVscodeApiConfig
} from 'monaco-languageclient/vscodeApiWrapper';
import { configureDefaultWorkerFactory } from 'monaco-languageclient/workerFactory';

import { OTTL_LANGUAGE_ID, ottlLanguageConfiguration, ottlMonarchTokens } from './monarch';

export type LspStatus = 'connecting' | 'connected' | 'disconnected';

export interface OttlEditorHandle {
    getText(): string;
    setText(text: string): void;
    onContentChanged(listener: (text: string) => void): void;
    dispose(): Promise<void>;
}

const DOC_PATH = '/workspace/rule.ottl';

let apiWrapper: MonacoVscodeApiWrapper | undefined;
let apiStarted: Promise<void> | undefined;

/** Starts the shared monaco-vscode-api exactly once and registers the OTTL language. */
async function ensureApiStarted(container: HTMLElement, initialCode: string): Promise<void> {
    if (apiStarted) {
        return apiStarted;
    }

    const uri = vscode.Uri.file(DOC_PATH);
    const fileSystemProvider = new RegisteredFileSystemProvider(false);
    fileSystemProvider.registerFile(new RegisteredMemoryFile(uri, initialCode));
    registerFileSystemOverlay(1, fileSystemProvider);

    const config: MonacoVscodeApiConfig = {
        $type: 'extended',
        viewsConfig: {
            $type: 'EditorService',
            htmlContainer: container
        },
        logLevel: LogLevel.Info,
        userConfiguration: {
            json: JSON.stringify({
                'workbench.colorTheme': 'Default Dark Modern',
                'editor.wordBasedSuggestions': 'off',
                'editor.minimap.enabled': false,
                'editor.automaticLayout': true
            })
        },
        monacoWorkerFactory: configureDefaultWorkerFactory
    };

    apiWrapper = new MonacoVscodeApiWrapper(config);
    const starting = apiWrapper.start().then(() => {
        monaco.languages.register({
            id: OTTL_LANGUAGE_ID,
            extensions: ['.ottl'],
            aliases: ['OTTL', 'ottl']
        });
        monaco.languages.setLanguageConfiguration(OTTL_LANGUAGE_ID, ottlLanguageConfiguration);
        monaco.languages.setMonarchTokensProvider(OTTL_LANGUAGE_ID, ottlMonarchTokens);
    });
    apiStarted = starting;
    try {
        await starting;
    } catch (err) {
        if (apiStarted === starting) {
            apiStarted = undefined;
            apiWrapper = undefined;
        }
        throw err;
    }
}

function ottlModel(): monaco.editor.ITextModel | undefined {
    return monaco.editor.getModels().find((m) => m.uri.path.endsWith('rule.ottl'));
}

export interface StartOptions {
    container: HTMLElement;
    initialCode: string;
    wsUrl: string;
    onStatus: (status: LspStatus) => void;
}

/** One editor and one language client at a time. The next start waits until the previous dispose. */
let editorQueue: Promise<void> = Promise.resolve();

export async function startOttlEditor(options: StartOptions): Promise<OttlEditorHandle> {
    let releaseQueue!: () => void;
    const gate = new Promise<void>((resolve) => {
        releaseQueue = resolve;
    });
    const previous = editorQueue;
    editorQueue = previous.then(() => gate);
    await previous;

    let released = false;
    const releaseOnce = () => {
        if (!released) {
            released = true;
            releaseQueue();
        }
    };

    const { container, initialCode, wsUrl, onStatus } = options;
    try {
        onStatus('connecting');
        await ensureApiStarted(container, initialCode);

        const uri = vscode.Uri.file(DOC_PATH);

        const editorAppConfig: EditorAppConfig = {
            codeResources: {
                modified: {
                    text: initialCode,
                    uri: uri.path
                }
            }
        };

        const connectionConfigOptions: ConnectionConfigOptions = {
            $family: 'WebSocket',
            realization: () => new LcWebSocket(),
            webSocketUrl: wsUrl,
            startOptions: {
                onCall: () => onStatus('connected'),
                reportStatus: true
            },
            stopOptions: {
                onCall: () => onStatus('disconnected'),
                reportStatus: true
            }
        };

        const languageClientConfig: LanguageClientConfig = {
            languageId: OTTL_LANGUAGE_ID,
            connection: { options: connectionConfigOptions },
            clientOptions: {
                documentSelector: [OTTL_LANGUAGE_ID],
                workspaceFolder: {
                    index: 0,
                    name: 'workspace',
                    uri: vscode.Uri.file('/workspace')
                }
            }
        };

        const editorApp = new EditorApp(editorAppConfig);
        const lcWrapper = new LanguageClientWrapper(languageClientConfig);

        await editorApp.start(container);
        const existing = ottlModel();
        if (existing && existing.getValue() !== initialCode) {
            existing.setValue(initialCode);
        }

        let disposed = false;
        let reconnectTimer: ReturnType<typeof setTimeout> | undefined;

        const connect = async () => {
            if (disposed) {
                return;
            }
            try {
                onStatus('connecting');
                await lcWrapper.start();
                await vscode.workspace.openTextDocument(uri);
                onStatus('connected');
            } catch (err) {
                console.error('OTTL language client failed to start', err);
                onStatus('disconnected');
                if (!disposed) {
                    reconnectTimer = setTimeout(connect, 3000);
                }
            }
        };

        await connect();

        const changeListeners: Array<(text: string) => void> = [];
        const model = ottlModel();
        const contentSubscription = model?.onDidChangeContent(() => {
            const text = model.getValue();
            changeListeners.forEach((listener) => listener(text));
        });

        return {
            getText: () => ottlModel()?.getValue() ?? '',
            setText: (text: string) => {
                const current = ottlModel();
                if (current && current.getValue() !== text) {
                    current.setValue(text);
                }
            },
            onContentChanged: (listener) => changeListeners.push(listener),
            dispose: async () => {
                if (disposed) {
                    return;
                }
                disposed = true;
                if (reconnectTimer) {
                    clearTimeout(reconnectTimer);
                }
                contentSubscription?.dispose();
                try {
                    await lcWrapper.dispose();
                    await editorApp.dispose();
                } finally {
                    releaseOnce();
                }
            }
        };
    } catch (err) {
        releaseOnce();
        throw err;
    }
}
