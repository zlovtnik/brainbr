import { fail, redirect } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';
import { isLikelyJwt, writeSession } from '$lib/server/session';
import { sanitizeRedirectTo } from '$lib/server/auth';

export const load: PageServerLoad = async ({ locals, url }) => {
	if (locals.session) {
		throw redirect(303, '/inventory');
	}

	return {
		redirectTo: sanitizeRedirectTo(url.searchParams.get('redirectTo'))
	};
};

export const actions: Actions = {
	default: async ({ request, cookies }) => {
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

		writeSession(cookies, token);
		throw redirect(303, redirectTo);
	}
};
