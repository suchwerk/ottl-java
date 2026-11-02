import { defineConfig, devices } from '@playwright/test';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const thisDir = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(thisDir, '..', '..');
const javaHome = process.env.JAVA_HOME ?? `${process.env.HOME}/.sdkman/candidates/java/21.0.8-tem`;

/**
 * End-to-end tests drive the real browser against the Vue editor, which talks to a live Quarkus
 * dev server (REST + LSP WebSocket). Both servers are started automatically unless already running.
 */
export default defineConfig({
    testDir: './e2e',
    timeout: 180_000,
    expect: { timeout: 20_000 },
    fullyParallel: false,
    workers: 1,
    reporter: 'list',
    use: {
        baseURL: 'http://localhost:5173',
        trace: 'on-first-retry'
    },
    projects: [
        { name: 'chromium', use: { ...devices['Desktop Chrome'] } }
    ],
    webServer: [
        {
            // Quarkus dev server providing /editor/* REST and /ottl/lsp WebSocket.
            command: './mvnw -q -pl examples/quarkus-example quarkus:dev -Dquarkus.http.port=8080',
            cwd: repoRoot,
            url: 'http://localhost:8080/editor/contexts',
            reuseExistingServer: true,
            timeout: 240_000,
            env: { JAVA_HOME: javaHome }
        },
        {
            // Vite dev server for the Vue/Quasar front end (proxies REST + WS to Quarkus).
            command: 'npm run dev',
            cwd: thisDir,
            url: 'http://localhost:5173',
            reuseExistingServer: true,
            timeout: 120_000
        }
    ]
});
