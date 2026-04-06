import AxeBuilder from '@axe-core/playwright';
import { expect } from '@playwright/test';

const _rawPort = Number(process.env.MOCK_API_PORT || 5050);
export const mockApiPort = Number.isInteger(_rawPort) && _rawPort > 0 && _rawPort < 65536 ? _rawPort : 5050;

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

const ALLOWED_SCOPES = new Set([
	'inventory:read', 'inventory:write',
	'audit:read', 'audit:write',
	'report:read', 'report:write'
]);

const SUB_RE = /^[\w-]{1,64}$/;
const TENANT_RE = /^[\w-]{1,64}$/;

function validateSessionPayload(payload) {
	if (!SUB_RE.test(payload.sub)) throw new Error(`bootstrapSession: invalid sub "${payload.sub}"`);
	if (!TENANT_RE.test(payload.tenant_id)) throw new Error(`bootstrapSession: invalid tenant_id "${payload.tenant_id}"`);
	const scopes = payload.scope.split(' ');
	for (const s of scopes) {
		if (!ALLOWED_SCOPES.has(s)) throw new Error(`bootstrapSession: disallowed scope "${s}"`);
	}
	return { sub: payload.sub, tenant_id: payload.tenant_id, scope: scopes.join(' ') };
}

export async function bootstrapSession(
	page,
	payload = {
		sub: 'inventory-operator',
		tenant_id: 'tenant-001',
		scope: 'inventory:read inventory:write'
	}
) {
	const safePayload = validateSessionPayload(payload);
	await page.goto('/auth');
	await page.getByRole('button', { name: 'Advanced: use token' }).click();
	await page.getByLabel('Bearer JWT').fill(createToken(safePayload));
	await page.getByRole('button', { name: 'Start authenticated session' }).click();
	await expect(page).toHaveURL(/\/platform$/);
}

export async function expectNoAxeViolations(page) {
	const axe = await new AxeBuilder({ page }).analyze();
	expect(axe.violations).toEqual([]);
}

export async function expectMainContentFocus(page) {
	await expect(page.locator('#main-content')).toBeFocused();
}
