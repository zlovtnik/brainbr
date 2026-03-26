import AxeBuilder from '@axe-core/playwright';
import { expect } from '@playwright/test';

export const mockApiPort = Number(process.env.MOCK_API_PORT || 5050);

export function createToken(payload) {
	const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url');
	const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
	return `${header}.${body}.signature`;
}

export async function resetMockApi(request) {
	const url = `http://127.0.0.1:${mockApiPort}/__reset`;
	const headers = { Authorization: `Bearer ${createToken({ sub: 'reset-user' })}` };

	try {
		const response = await request.post(url, { headers });
		if (!response.ok()) {
			const bodyText = await response.text();
			throw new Error(`resetMockApi: POST ${url} failed with status ${response.status()}: ${bodyText}`);
		}
	} catch (error) {
		const isConnectivity = error instanceof TypeError || error?.name === 'FetchError';
		if (isConnectivity) {
			const message = error instanceof Error ? error.message : String(error);
			throw new Error(`resetMockApi: unable to reach mock API at ${url}: ${message}`);
		}
		throw error;
	}
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
