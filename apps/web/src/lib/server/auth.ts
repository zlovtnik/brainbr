import { redirect, type RequestEvent } from '@sveltejs/kit';

export function sanitizeRedirectTo(redirectTo: string | null | undefined, fallback = '/inventory'): string {
	if (redirectTo && redirectTo.startsWith('/') && !redirectTo.startsWith('//')) {
		return redirectTo;
	}

	return fallback;
}

export function requireSession(event: RequestEvent, redirectTo = event.url.pathname + event.url.search) {
	const safeRedirectTo = sanitizeRedirectTo(redirectTo);

	if (!event.locals.session) {
		throw redirect(303, `/auth?redirectTo=${encodeURIComponent(safeRedirectTo)}`);
	}

	return event.locals.session;
}
