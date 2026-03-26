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

test('inventory list supports filters, pagination navigation, and axe checks', async ({ page }) => {
	await bootstrapSession(page, {
		sub: 'inventory-reader',
		tenant_id: 'tenant-001',
		scope: 'inventory:read inventory:write'
	});

	await page.getByLabel('Search').fill('citrus');
	await page.getByRole('button', { name: 'Apply filters' }).click();
	await expect(page).toHaveURL(/query=citrus/);
	await expect(page.getByText('Refrigerante Citrus 600ml')).toBeVisible();
	await expect(page.getByText('Cerveja Pilsen Lata 350ml')).toHaveCount(0);

	await page.goto('/inventory?page=1&limit=1');
	await expect(page.getByText('SKU-100')).toBeVisible();
	await page.getByRole('link', { name: 'Next page' }).click();

	await expect(page).toHaveURL(/page=2/);
	await expect(page.getByText('SKU-200')).toBeVisible();
	await expectMainContentFocus(page);

	await page.getByRole('link', { name: 'Previous page' }).click();
	await expect(page).toHaveURL(/page=1/);
	await expect(page.getByText('SKU-100')).toBeVisible();

	await expectNoAxeViolations(page);
});

test('inventory list exposes the empty state accessibly', async ({ page }) => {
	await bootstrapSession(page);
	await page.goto('/inventory?query=no-match');

	await expect(page.getByRole('status')).toContainText('No inventory matched');
	await expect(page.getByRole('link', { name: 'Create SKU' })).toBeVisible();
});

test('inventory list surfaces backend load failures', async ({ page }) => {
	await bootstrapSession(page);
	await page.goto('/inventory?query=trigger-load-error');

	await expect(page.getByRole('alert')).toContainText(
		'Mock backend unavailable for inventory list'
	);
});
