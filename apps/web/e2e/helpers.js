import AxeBuilder from '@axe-core/playwright';
import { expect } from '@playwright/test';

export const mockApiPort = Number(process.env.MOCK_API_PORT || 5050);

export function createToken(payload) {
	const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url');
	const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
	return `${header}.${body}.signature`;
}

export async function resetMockApi(request) {
	await request.post(`http://127.0.0.1:${mockApiPort}/__reset`, {
		headers: {
			Authorization: `Bearer ${createToken({ sub: 'reset-user' })}`
		}
	});
}

export async function bootstrapSession(
	page,
	payload = {
		sub: 'inventory-operator',
		tenant_id: 'tenant-001',
		scope: 'inventory:read inventory:write'
	}
) {
	await page.goto('/auth');
	await page.getByLabel('Bearer JWT').fill(createToken(payload));
	await page.getByRole('button', { name: 'Start authenticated session' }).click();
	await expect(page).toHaveURL(/\/inventory$/);
}

export async function expectNoAxeViolations(page) {
	const axe = await new AxeBuilder({ page }).analyze();
	expect(axe.violations).toEqual([]);
}

export async function expectMainContentFocus(page) {
	await expect(page.locator('#main-content')).toBeFocused();
}
