import { expect, test, type Page } from '@playwright/test';

/**
 * The selected rule must stay on the active context. Switching contexts keeps unsaved drafts and
 * must not activate an Order rule id against Customer.
 */

async function editorReady(page: Page) {
    await expect(page.locator('.monaco-editor .view-lines').first()).toBeVisible({ timeout: 60_000 });
    await expect(page.getByTestId('lsp-status')).toContainText('connected', { timeout: 60_000 });
}

async function setEditorText(page: Page, text: string) {
    await page.locator('.monaco-editor .view-lines').first().click();
    await page.keyboard.press('ControlOrMeta+A');
    await page.keyboard.press('Delete');
    await page.keyboard.insertText(text);
}

async function chooseContext(page: Page, name: string) {
    await page.getByTestId('context-select').click();
    await page.getByRole('option', { name, exact: true }).click();
}

test('context switch keeps the rule of that context and preserves drafts', async ({ page }) => {
    await page.goto('/');
    await editorReady(page);

    await expect(page.getByTestId('selected-rule')).toContainText('order-rules');
    await expect(page.getByTestId('rule-Order-order-rules')).toHaveClass(/q-item--active/);

    const orderDraft = '# context: Order\nset(order.priority, "DRAFT")';
    await setEditorText(page, orderDraft);
    await expect(page.getByTestId('unsaved-indicator')).toBeVisible();

    await chooseContext(page, 'Customer');
    await expect(page.getByTestId('selected-rule')).toContainText('customer-rules');
    await expect(page.getByTestId('rule-Customer-customer-rules')).toHaveClass(/q-item--active/);
    await expect(page.locator('.monaco-editor .view-lines').first()).toContainText('customer.tier');
    await expect(page.getByTestId('unsaved-indicator')).toHaveCount(0);

    await chooseContext(page, 'Order');
    await expect(page.getByTestId('selected-rule')).toContainText('order-rules');
    await expect(page.locator('.monaco-editor .view-lines').first()).toContainText('DRAFT');
    await expect(page.getByTestId('unsaved-indicator')).toBeVisible();

    await page.getByTestId('revert-btn').click();
    await expect(page.getByTestId('unsaved-indicator')).toHaveCount(0);
    await expect(page.locator('.monaco-editor .view-lines').first()).toContainText('IsHighValue');

    await chooseContext(page, 'Customer');
    await setEditorText(page, '# context: Customer\nset(customer.tier, "GOLD")');
    await page.getByTestId('activate-btn').click();
    await expect(page.getByTestId('unsaved-indicator')).toHaveCount(0, { timeout: 20_000 });
    await expect(page.getByTestId('selected-rule')).toContainText('customer-rules');

    await chooseContext(page, 'Order');
    await expect(page.locator('.monaco-editor .view-lines').first()).not.toContainText('GOLD');
    await expect(page.locator('.monaco-editor .view-lines').first()).toContainText('IsHighValue');

    // Invalid source must not replace the active customer rule or move the selection.
    await chooseContext(page, 'Customer');
    const versionBefore = (await page.getByTestId('selected-rule').innerText()).replace(/\s+/g, ' ').trim();
    await setEditorText(page, '# context: Customer\nset(customer.tier, )');
    await page.getByTestId('activate-btn').click();
    await expect(page.locator('.q-notification').last()).toContainText(/Parse error|Expected/i, { timeout: 20_000 });
    await expect(page.getByTestId('selected-rule')).toHaveText(versionBefore);
    await expect(page.getByTestId('unsaved-indicator')).toBeVisible();

    await setEditorText(page, '# context: Customer\nset(customer.tier, "STANDARD")');
    await page.getByTestId('activate-btn').click();
    await expect(page.getByTestId('unsaved-indicator')).toHaveCount(0, { timeout: 20_000 });
});
