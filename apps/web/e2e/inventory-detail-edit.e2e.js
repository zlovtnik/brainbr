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

test('inventory detail renders expected data and edit navigation keeps focus in main content', async ({
	page
}) => {
	await bootstrapSession(page, {
		sub: 'inventory-editor',
		tenant_id: 'tenant-001',
		scope: 'inventory:read inventory:write'
	});

	await page.goto('/inventory/SKU-100');
	await expect(page.getByRole('heading', { name: 'SKU-100' })).toBeVisible();
	await expect(page.getByText('Cerveja Pilsen Lata 350ml')).toBeVisible();

	await expectNoAxeViolations(page);

	await page.getByRole('link', { name: 'Edit SKU' }).click();
	await expect(page).toHaveURL(/\/inventory\/SKU-100\/edit$/);
	await expectMainContentFocus(page);
	await expect(page.getByRole('heading', { name: 'Edit SKU-100' })).toBeVisible();

	await expectNoAxeViolations(page);
});

test('edit SKU redirects to the detail page on success', async ({ page }) => {
	await bootstrapSession(page, {
		sub: 'inventory-editor',
		tenant_id: 'tenant-001',
		scope: 'inventory:read inventory:write'
	});

	await page.goto('/inventory/SKU-100/edit');
	await page.getByLabel('Description').fill('Cerveja Pilsen Lata 350ml Premium');
	await page.getByRole('button', { name: 'Save changes' }).click();

	await expect(page).toHaveURL(/\/inventory\/SKU-100\?saved=updated$/);
	await expect(page.getByText('SKU updated successfully.')).toBeVisible();
	await expect(page.getByText('Cerveja Pilsen Lata 350ml Premium')).toBeVisible();
});

test('edit SKU surfaces backend errors without dropping form state', async ({ page }) => {
	await bootstrapSession(page, {
		sub: 'inventory-editor',
		tenant_id: 'tenant-001',
		scope: 'inventory:read inventory:write'
	});

	await page.goto('/inventory/SKU-100/edit');
	await page.getByLabel('Description').fill('Trigger backend error');
	await page.getByRole('button', { name: 'Save changes' }).click();

	await expect(page.getByRole('alert')).toContainText(
		'Mock backend failed to persist inventory changes'
	);
	await expect(page.getByRole('alert')).toBeFocused();
	await expect(page.getByLabel('Description')).toHaveValue('Trigger backend error');

	await expectNoAxeViolations(page);
});
