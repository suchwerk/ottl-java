import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
import { quasar, transformAssetUrls } from '@quasar/vite-plugin';
import vsixPlugin from '@codingame/monaco-vscode-rollup-vsix-plugin';

// Backend (Quarkus dev) host; REST + WebSocket are proxied here so the browser talks same-origin.
const backend = process.env.OTTL_BACKEND ?? 'http://localhost:8080';

export default defineConfig({
    plugins: [
        vue({ template: { transformAssetUrls } }),
        quasar({
            sassVariables: fileURLToPath(new URL('./src/quasar-variables.sass', import.meta.url))
        }),
        vsixPlugin()
    ],
    server: {
        port: 5173,
        // monaco-vscode-api language features use SharedArrayBuffer -> cross-origin isolation.
        headers: {
            'Cross-Origin-Opener-Policy': 'same-origin',
            'Cross-Origin-Embedder-Policy': 'credentialless',
            'Cross-Origin-Resource-Policy': 'cross-origin'
        },
        proxy: {
            // Language server WebSocket.
            '/ottl': { target: backend.replace(/^http/, 'ws'), ws: true, changeOrigin: true },
            // Development-only editor REST API.
            '/editor': { target: backend, changeOrigin: true }
        }
    },
    worker: {
        format: 'es'
    },
    optimizeDeps: {
        // monaco-vscode-api ships ESM that must not be pre-bundled into CommonJS.
        esbuildOptions: {
            target: 'es2022'
        }
    },
    build: {
        target: 'es2022'
    }
});
