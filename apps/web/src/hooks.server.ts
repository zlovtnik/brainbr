import type { Handle, HandleError } from '@sveltejs/kit';
import { clearSession, readSession } from '$lib/server/session';

function sanitizeMessage(raw: unknown): string {
	if (typeof raw !== 'string') return 'An unexpected error occurred';
	return raw.slice(0, 200) || 'An unexpected error occurred';
}

export const handleError: HandleError = ({ error }) => {
	const message = error instanceof Error ? error.message : String(error ?? '');
	return { message: sanitizeMessage(message) };
};

export const handle: Handle = async ({ event, resolve }) => {
	const session = readSession(event.cookies);

	if (session.valid) {
		event.locals.session = session.data;
	} else {
		event.locals.session = null;
		if (session.invalid) {
			clearSession(event.cookies);
		}
	}

	return resolve(event);
};
