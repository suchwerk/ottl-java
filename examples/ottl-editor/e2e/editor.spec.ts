import { expect, test, type Page } from '@playwright/test';

/**
 * Full-flow e2e: open the editor, type invalid OTTL and see diagnostics, fix it and see them clear,
 * trigger completion, run a valid rule, then activate a modified rule and confirm the next run uses
 * the updated rule.
 */

async function editorReady(page: Page) {
    // Monaco renders the text lines once the workbench is ready.
    await expect(page.locator('.monaco-editor .view-lines').first()).toBeVisible({ timeout: 60_000 });
    // Wait for the language client to connect.
    await expect(page.getByTestId('lsp-status')).toContainText('connected', { timeout: 60_000 });
}

async function focusEditor(page: Page) {
    // Click the rendered lines to focus the editor's hidden input (do not target the textarea
    // directly: Monaco's input textarea is not "actionable" for Playwright typing).
    await page.locator('.monaco-editor .view-lines').first().click();
}

async function setEditorText(page: Page, text: string) {
    await focusEditor(page);
    await page.keyboard.press('ControlOrMeta+A');
    await page.keyboard.press('Delete');
    await page.keyboard.insertText(text);
}

test('OTTL editor: diagnostics, completion, run and activate', async ({ page }) => {
    await page.goto('/');
    await editorReady(page);

    // 1. Invalid OTTL -> a diagnostic (red squiggle) appears.
    await setEditorText(page, '# context: Order\nset(order.bogus, "X")');
    await expect(page.locator('.monaco-editor .squiggly-error').first()).toBeVisible({ timeout: 30_000 });

    // 2. Fix it -> diagnostics disappear.
    await setEditorText(page, '# context: Order\nset(order.priority, "HIGH") where order.amount > 1000');
    await expect(page.locator('.monaco-editor .squiggly-error')).toHaveCount(0, { timeout: 30_000 });

    // 3. Completion: typing "order." offers properties from the Java type registry.
    await focusEditor(page);
    await page.keyboard.press('ControlOrMeta+A');
    await page.keyboard.press('Delete');
    await page.keyboard.insertText('# context: Order\nset(order.');
    await page.keyboard.press('Control+Space');
    await expect(page.locator('.suggest-widget')).toBeVisible({ timeout: 20_000 });
    await expect(page.locator('.suggest-widget')).toContainText('amount');
    await page.keyboard.press('Escape');

    // Reset to a clean, valid rule.
    await setEditorText(page, '# context: Order\nset(order.priority, "HIGH") where order.amount > 1000');

    // 4. Run against sample input -> priority becomes HIGH.
    await page.getByTestId('run-btn').click();
    await expect(page.getByTestId('run-result')).toContainText('"priority": "HIGH"', { timeout: 20_000 });

    // 5. Activate a modified rule, then verify the next run uses it.
    await setEditorText(page, '# context: Order\nset(order.priority, "URGENT")');
    await page.getByTestId('activate-btn').click();
    await page.getByTestId('run-btn').click();
    await expect(page.getByTestId('run-result')).toContainText('"priority": "URGENT"', { timeout: 20_000 });

    // Restore the default rule so the environment stays clean.
    await setEditorText(page, '# context: Order\nset(order.status, OrderStatus.PROCESSING) where IsHighValue(order.amount)');
    await page.getByTestId('activate-btn').click();
});
