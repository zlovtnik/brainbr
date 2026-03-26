import { expect, test } from '@playwright/test';
import { bootstrapSession, expectNoAxeViolations, resetMockApi } from './helpers.js';

test.beforeEach(async ({ request }) => {
	await resetMockApi(request);
});

test('create SKU shows validation summary and success redirect', async ({ page }) => {
	await bootstrapSession(page, {
		sub: 'inventory-writer',
		tenant_id: 'tenant-001',
		scope: 'inventory:read inventory:write'
	});

	await page.goto('/inventory/new');
	await expectNoAxeViolations(page);

	await page.getByRole('button', { name: 'Create SKU' }).click();

	await expect(page.locator('#inventory-form-errors')).toBeFocused();
	await expect(page.getByText('Description is required.')).toBeVisible();
	await expect(page.getByLabel('Description')).toHaveAttribute('aria-invalid', 'true');

	await page.getByLabel('SKU ID').fill('SKU-999');
	await page.getByLabel('Description').fill('Guarana Zero 2L');
	await page.getByLabel('NCM code').fill('22021000');
	await page.getByLabel('Origin state').fill('SP');
	await page.getByLabel('Destination state').fill('RJ');
	await page.getByLabel('ICMS').fill('18');
	await page.getByRole('button', { name: 'Create SKU' }).click();

	await expect(page).toHaveURL(/\/inventory\/SKU-999\?saved=created$/);
	await expect(page.getByText('SKU created successfully.')).toBeVisible();
	await expectNoAxeViolations(page);
});
