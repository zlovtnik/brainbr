import { redirect } from '@sveltejs/kit';
import type { RequestHandler } from './$types';
import { clearSession } from '$lib/server/session';

export const POST: RequestHandler = async ({ cookies }) => {
	clearSession(cookies);
	throw redirect(303, '/auth');
};
