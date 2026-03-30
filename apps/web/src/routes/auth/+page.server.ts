import { fail, redirect } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';
import { createSessionSummary, isLikelyJwt, writeSession } from '$lib/server/session';
import { sanitizeRedirectTo } from '$lib/server/auth';
import { env } from '$env/dynamic/private';

const DEFAULT_DEMO_USERNAME = 'demo';
const DEFAULT_DEMO_PASSWORD = 'demo123';
const REQUIRED_BOOTSTRAP_SCOPES = ['inventory:read', 'inventory:write'];

function resolveHardcodedLoginToken(): string | null {
	const providedToken = env.APP_AUTH_HARDCODED_TOKEN?.trim();
	if (providedToken) {
		return providedToken;
	}
	return null;
}

function validateBootstrapTokenClaims(token: string): string | null {
	let summary;
	try {
		summary = createSessionSummary(token);
	} catch {
		return 'The JWT payload could not be parsed. Replace it with a valid backend-issued JWT.';
	}

	if (!summary.tenantId) {
		return 'The JWT must include a `tenant_id` claim so tenant-scoped backend routes can resolve the company context.';
	}

	const missingScopes = REQUIRED_BOOTSTRAP_SCOPES.filter(
		(scope) => !summary.scopes.includes(scope)
	);
	if (missingScopes.length > 0) {
		return `The JWT is missing required scope${missingScopes.length === 1 ? '' : 's'}: ${missingScopes.join(', ')}.`;
	}

	return null;
}

export const load: PageServerLoad = async ({ locals, url }) => {
	if (locals.session) {
		throw redirect(303, '/platform');
	}

	return {
		redirectTo: sanitizeRedirectTo(url.searchParams.get('redirectTo')),
		hardcodedUsername: env.APP_AUTH_HARDCODED_USERNAME?.trim() || DEFAULT_DEMO_USERNAME
	};
};

export const actions: Actions = {
	quick: async ({ request, cookies }) => {
		const formData = await request.formData();
		const redirectTo = sanitizeRedirectTo(formData.get('redirectTo')?.toString());
		const token = resolveHardcodedLoginToken();
		if (!token) {
			return fail(500, {
				quickError:
					'Quick login requires APP_AUTH_HARDCODED_TOKEN to be set to a real JWT signed by the issuer or JWKS configured for the backend API.',
				redirectTo
			});
		}
		const claimsError = validateBootstrapTokenClaims(token);
		if (claimsError) {
			return fail(500, {
				quickError: `APP_AUTH_HARDCODED_TOKEN is misconfigured. ${claimsError}`,
				redirectTo
			});
		}
		writeSession(cookies, token);
		throw redirect(303, redirectTo);
	},
	password: async ({ request, cookies }) => {
		const formData = await request.formData();
		const username = formData.get('username')?.toString().trim() ?? '';
		const password = formData.get('password')?.toString() ?? '';
		const redirectTo = sanitizeRedirectTo(formData.get('redirectTo')?.toString());

		const expectedUsername = env.APP_AUTH_HARDCODED_USERNAME?.trim() || DEFAULT_DEMO_USERNAME;
		const expectedPassword = env.APP_AUTH_HARDCODED_PASSWORD?.trim() || DEFAULT_DEMO_PASSWORD;

		if (username !== expectedUsername || password !== expectedPassword) {
			return fail(401, {
				loginError: 'Invalid username or password.',
				username,
				redirectTo
			});
		}

		const token = resolveHardcodedLoginToken();
		if (!token) {
			return fail(500, {
				loginError:
					'Password login requires APP_AUTH_HARDCODED_TOKEN to be set to a real JWT signed by the issuer or JWKS configured for the backend API.',
				username,
				redirectTo
			});
		}
		const claimsError = validateBootstrapTokenClaims(token);
		if (claimsError) {
			return fail(500, {
				loginError: `APP_AUTH_HARDCODED_TOKEN is misconfigured. ${claimsError}`,
				username,
				redirectTo
			});
		}
		writeSession(cookies, token);
		throw redirect(303, redirectTo);
	},
	token: async ({ request, cookies }) => {
		const formData = await request.formData();
		const token = formData.get('token')?.toString().trim() ?? '';
		const redirectTo = sanitizeRedirectTo(formData.get('redirectTo')?.toString());

		if (!token) {
			return fail(400, {
				error: 'Paste a bearer JWT to start the session.',
				token,
				redirectTo
			});
		}

		if (!isLikelyJwt(token)) {
			return fail(400, {
				error: 'The token must be a JWT with a readable payload section.',
				token,
				redirectTo
			});
		}
		const claimsError = validateBootstrapTokenClaims(token);
		if (claimsError) {
			return fail(400, {
				error: `${claimsError} Use a backend-issued JWT signed by the configured issuer or JWKS.`,
				token,
				redirectTo
			});
		}

		writeSession(cookies, token);
		throw redirect(303, redirectTo);
	}
};
