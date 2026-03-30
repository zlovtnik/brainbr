import { redirect, type RequestEvent } from '@sveltejs/kit';
import { ApiClientError } from '$lib/server/api/client';
import type { AuthSession } from '$lib/server/session';

export function sanitizeRedirectTo(
	redirectTo: string | null | undefined,
	fallback = '/platform'
): string {
	if (
		redirectTo &&
		redirectTo.startsWith('/') &&
		!redirectTo.startsWith('//') &&
		!redirectTo.includes('\\')
	) {
		return redirectTo;
	}

	return fallback;
}

export function requireSession(
	event: RequestEvent,
	redirectTo = event.url.pathname + event.url.search
) {
	const safeRedirectTo = sanitizeRedirectTo(redirectTo);

	if (!event.locals.session) {
		throw redirect(303, `/auth?redirectTo=${encodeURIComponent(safeRedirectTo)}`);
	}

	return event.locals.session;
}

export function describeProtectedApiError(
	session: Pick<AuthSession, 'scopes'> | null | undefined,
	error: ApiClientError,
	requiredScopes: string[]
): string {
	const currentScopes = session?.scopes ?? [];
	const missingScopes = requiredScopes.filter((scope) => !currentScopes.includes(scope));

	if (error.status === 401) {
		if (missingScopes.length > 0) {
			return `Your session token is not usable for this backend route. The JWT payload is missing required scope${missingScopes.length === 1 ? '' : 's'}: ${missingScopes.join(', ')}. Sign in again with a JWT signed by the configured issuer or JWKS and carrying the required scopes.`;
		}

		return 'Your session token was rejected by the backend API. Sign in again with a JWT signed by the configured issuer or JWKS.';
	}

	if (error.status === 403) {
		if (missingScopes.length > 0) {
			return `Your session is authenticated but missing required scope${missingScopes.length === 1 ? '' : 's'}: ${missingScopes.join(', ')}.`;
		}

		return 'Your session is authenticated, but the backend denied access to this route.';
	}

	return error.message;
}
