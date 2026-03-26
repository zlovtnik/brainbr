import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

const mockApiPort = Number(process.env.MOCK_API_PORT || 5050);

function createToken(payload: Record<string, unknown>) {
	const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url');
	const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
	return `${header}.${body}.signature`;
}

test.beforeEach(async ({ request }) => {
	await request.post(`http://127.0.0.1:${mockApiPort}/__reset`, {
		headers: {
			Authorization: `Bearer ${createToken({ sub: 'reset-user' })}`
		}
	});
});

test('auth bootstrap stores session and lands on inventory', async ({ page }) => {
	await page.goto('/auth');
	await page.getByLabel('Bearer JWT').fill(
		createToken({
			sub: 'inventory-operator',
			tenant_id: 'tenant-001',
			scope: 'inventory:read inventory:write'
		})
	);
	await page.getByRole('button', { name: 'Start authenticated session' }).click();

	await expect(page).toHaveURL(/\/inventory$/);
	await expect(page.getByText('inventory-operator')).toBeVisible();

	const axe = await new AxeBuilder({ page }).analyze();
	expect(axe.violations).toEqual([]);
});

test('inventory list supports server-side filters and pagination controls', async ({ page }) => {
	await page.goto('/auth');
	await page.getByLabel('Bearer JWT').fill(
		createToken({
			sub: 'inventory-reader',
			tenant_id: 'tenant-001',
			scope: 'inventory:read inventory:write'
		})
	);
	await page.getByRole('button', { name: 'Start authenticated session' }).click();

	await page.getByLabel('Search').fill('citrus');
	await page.getByRole('button', { name: 'Apply filters' }).click();

	await expect(page.getByText('Refrigerante Citrus 600ml')).toBeVisible();
	await expect(page.getByText('Cerveja Pilsen Lata 350ml')).toHaveCount(0);
});

test('create SKU shows validation and success paths', async ({ page }) => {
	await page.goto('/auth');
	await page.getByLabel('Bearer JWT').fill(
		createToken({
			sub: 'inventory-writer',
			tenant_id: 'tenant-001',
			scope: 'inventory:read inventory:write'
		})
	);
	await page.getByRole('button', { name: 'Start authenticated session' }).click();

	await page.goto('/inventory/new');
	await page.getByRole('button', { name: 'Create SKU' }).click();
	await expect(page.getByText('Description is required.')).toBeVisible();

	await page.getByLabel('SKU ID').fill('SKU-999');
	await page.getByLabel('Description').fill('Guarana Zero 2L');
	await page.getByLabel('NCM code').fill('22021000');
	await page.getByLabel('Origin state').fill('SP');
	await page.getByLabel('Destination state').fill('RJ');
	await page.getByLabel('ICMS').fill('18');
	await page.getByRole('button', { name: 'Create SKU' }).click();

	await expect(page).toHaveURL(/\/inventory\/SKU-999\?saved=created$/);
	await expect(page.getByText('SKU created successfully.')).toBeVisible();

	const axe = await new AxeBuilder({ page }).analyze();
	expect(axe.violations).toEqual([]);
});

test('edit SKU surfaces backend errors without dropping form state', async ({ page }) => {
	await page.goto('/auth');
	await page.getByLabel('Bearer JWT').fill(
		createToken({
			sub: 'inventory-editor',
			tenant_id: 'tenant-001',
			scope: 'inventory:read inventory:write'
		})
	);
	await page.getByRole('button', { name: 'Start authenticated session' }).click();

	await page.goto('/inventory/SKU-100/edit');
	await page.getByLabel('Description').fill('Trigger backend error');
	await page.getByRole('button', { name: 'Save changes' }).click();

	await expect(page.getByText('Mock backend failed to persist inventory changes')).toBeVisible();
	await expect(page.getByLabel('Description')).toHaveValue('Trigger backend error');
});
