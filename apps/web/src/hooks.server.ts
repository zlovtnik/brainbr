import type { Handle } from '@sveltejs/kit';
import { clearSession, readSession } from '$lib/server/session';

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
