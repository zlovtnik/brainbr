import { expect, test } from '@playwright/test';
import {
	bootstrapSession,
	expectMainContentFocus,
	expectNoAxeViolations,
	resetMockApi
} from './helpers.js';

test.beforeEach(async ({ request }) => {
	await resetMockApi(request);
});

test('auth bootstrap rejects invalid JWT input and keeps focus on the field', async ({ page }) => {
	await page.goto('/auth');
	await page.getByLabel('Bearer JWT').fill('not-a-token');
	await page.getByRole('button', { name: 'Start authenticated session' }).click();

	await expect(page.getByRole('alert')).toContainText(
		'The token must be a JWT with a readable payload section.'
	);
	await expect(page.getByLabel('Bearer JWT')).toBeFocused();
	await expect(page.getByLabel('Bearer JWT')).toHaveAttribute('aria-invalid', 'true');
});

test('auth bootstrap stores session, moves focus to main content, and passes axe', async ({
	page
}) => {
	await bootstrapSession(page, {
		sub: 'inventory-operator',
		tenant_id: 'tenant-001',
		scope: 'inventory:read inventory:write'
	});

	await expect(page.getByText('inventory-operator')).toBeVisible();
	await expectMainContentFocus(page);

	await page.keyboard.press('Tab');
	await expect(page.getByLabel('Search')).toBeFocused();

	await expectNoAxeViolations(page);
});
