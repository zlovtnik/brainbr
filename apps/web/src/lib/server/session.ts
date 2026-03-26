import { dev } from '$app/environment';
import { env } from '$env/dynamic/private';
import type { Cookies } from '@sveltejs/kit';
import { createHmac, timingSafeEqual } from 'node:crypto';

export const SESSION_COOKIE_NAME = 'brainbr_session';

interface SessionEnvelope {
	token: string;
	issuedAt: string;
}

export interface SessionSummary {
	authenticated: true;
	user: string;
	tenantId?: string;
	scopes: string[];
}

export interface AuthSession extends SessionSummary {
	token: string;
	issuedAt: string;
}

type SessionReadResult =
	| { valid: true; invalid: false; data: AuthSession }
	| { valid: false; invalid: boolean };

interface JwtPayload {
	tenant_id?: string;
	user?: string;
	sub?: string;
	scope?: string | string[];
	scp?: string | string[];
}

function getSessionSecret(): string {
	const secret = env.APP_SESSION_SECRET?.trim() || process.env.APP_SESSION_SECRET?.trim();
	if (!secret) {
		throw new Error('Missing APP_SESSION_SECRET for SvelteKit session cookies');
	}
	return secret;
}

function toBase64Url(value: string): string {
	return Buffer.from(value, 'utf8').toString('base64url');
}

function fromBase64Url(value: string): string {
	return Buffer.from(value, 'base64url').toString('utf8');
}

function sign(serialized: string): string {
	return createHmac('sha256', getSessionSecret()).update(serialized).digest('base64url');
}

function parseJwtPayload(token: string): JwtPayload {
	const [, payload] = token.split('.');
	if (!payload) {
		throw new Error('Token is missing a JWT payload section');
	}
	return JSON.parse(Buffer.from(payload, 'base64url').toString('utf8')) as JwtPayload;
}

function normalizeScopes(payload: JwtPayload): string[] {
	const raw = payload.scope ?? payload.scp;
	if (Array.isArray(raw)) {
		return raw.map((scope) => scope.trim()).filter(Boolean);
	}
	if (typeof raw === 'string') {
		return raw
			.split(/\s+/)
			.map((scope) => scope.trim())
			.filter(Boolean);
	}
	return [];
}

function toAuthSession(envelope: SessionEnvelope): AuthSession {
	const payload = parseJwtPayload(envelope.token);
	const user = payload.user?.trim() || payload.sub?.trim() || 'Authenticated user';

	return {
		authenticated: true,
		user,
		tenantId: payload.tenant_id?.trim(),
		scopes: normalizeScopes(payload),
		token: envelope.token,
		issuedAt: envelope.issuedAt
	};
}

function seal(envelope: SessionEnvelope): string {
	const serialized = toBase64Url(JSON.stringify(envelope));
	return `${serialized}.${sign(serialized)}`;
}

function unseal(cookie: string): SessionEnvelope {
	const [serialized, providedSignature] = cookie.split('.');
	if (!serialized || !providedSignature) {
		throw new Error('Invalid session cookie format');
	}

	const expectedSignature = sign(serialized);
	const providedBuffer = Buffer.from(providedSignature);
	const expectedBuffer = Buffer.from(expectedSignature);
	if (
		providedBuffer.length !== expectedBuffer.length ||
		!timingSafeEqual(providedBuffer, expectedBuffer)
	) {
		throw new Error('Invalid session cookie signature');
	}

	return JSON.parse(fromBase64Url(serialized)) as SessionEnvelope;
}

export function createSessionSummary(token: string): SessionSummary {
	const session = toAuthSession({ token, issuedAt: new Date(0).toISOString() });
	return {
		authenticated: session.authenticated,
		user: session.user,
		tenantId: session.tenantId,
		scopes: session.scopes
	};
}

export function isLikelyJwt(token: string): boolean {
	try {
		parseJwtPayload(token.trim());
		return true;
	} catch {
		return false;
	}
}

export function writeSession(cookies: Cookies, token: string): AuthSession {
	const envelope: SessionEnvelope = {
		token: token.trim(),
		issuedAt: new Date().toISOString()
	};
	const session = toAuthSession(envelope);

	cookies.set(SESSION_COOKIE_NAME, seal(envelope), {
		httpOnly: true,
		path: '/',
		sameSite: 'lax',
		secure: !dev,
		maxAge: 60 * 60 * 8
	});

	return session;
}

export function readSession(cookies: Cookies): SessionReadResult {
	const cookie = cookies.get(SESSION_COOKIE_NAME);
	if (!cookie) {
		return { valid: false, invalid: false };
	}

	try {
		return {
			valid: true,
			invalid: false,
			data: toAuthSession(unseal(cookie))
		};
	} catch {
		return { valid: false, invalid: true };
	}
}

export function clearSession(cookies: Cookies): void {
	cookies.delete(SESSION_COOKIE_NAME, {
		path: '/'
	});
}
