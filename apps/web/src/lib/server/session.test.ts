import { beforeEach, describe, expect, it } from 'vitest';
import {
	consumeFlash,
	createSessionSummary,
	isLikelyJwt,
	readSession,
	writeFlash,
	writeSession,
	type AuthSession
} from '$lib/server/session';

class MockCookies {
	private store = new Map<string, string>();

	get(name: string): string | undefined {
		return this.store.get(name);
	}

	set(name: string, value: string): void {
		this.store.set(name, value);
	}

	delete(name: string): void {
		this.store.delete(name);
	}
}

function createToken(payload: Record<string, unknown>): string {
	const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url');
	const body = Buffer.from(JSON.stringify(payload)).toString('base64url');
	return `${header}.${body}.signature`;
}

describe('session helpers', () => {
	beforeEach(() => {
		process.env.APP_SESSION_SECRET = 'test-session-secret';
	});

	it('creates a session summary from token claims', () => {
		const summary = createSessionSummary(
			createToken({
				tenant_id: 'tenant-123',
				user: 'analyst@example.com',
				scope: 'inventory:read inventory:write'
			})
		);

		expect(summary.user).toBe('analyst@example.com');
		expect(summary.tenantId).toBe('tenant-123');
		expect(summary.scopes).toEqual(['inventory:read', 'inventory:write']);
	});

	it('writes and reads a signed session cookie', () => {
		const cookies = new MockCookies();
		const token = createToken({ sub: 'demo-user', scope: ['inventory:read'] });

		const written = writeSession(cookies as never, token);
		const read = readSession(cookies as never);

		expect(written.user).toBe('demo-user');
		expect(read.valid).toBe(true);
		expect((read as { data: AuthSession }).data.token).toBe(token);
	});

	it('rejects tampered cookies', () => {
		const cookies = new MockCookies();
		writeSession(cookies as never, createToken({ sub: 'demo-user' }));

		const raw = cookies.get('brainbr_session');
		cookies.set('brainbr_session', `${raw}-tampered`);

		const result = readSession(cookies as never);
		expect(result.valid).toBe(false);
		expect(result.invalid).toBe(true);
	});

	it('recognizes basic JWT structure', () => {
		expect(isLikelyJwt(createToken({ sub: 'demo-user' }))).toBe(true);
		expect(isLikelyJwt('not-a-token')).toBe(false);
	});

	it('consumes flash messages only once', () => {
		const cookies = new MockCookies();

		writeFlash(cookies as never, {
			type: 'success',
			message: 'SKU updated successfully.'
		});

		expect(consumeFlash(cookies as never)).toEqual({
			type: 'success',
			message: 'SKU updated successfully.'
		});
		expect(consumeFlash(cookies as never)).toBeNull();
	});
});
