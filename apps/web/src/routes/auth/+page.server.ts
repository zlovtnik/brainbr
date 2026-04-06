import { fail, redirect } from '@sveltejs/kit';
import type { Actions, PageServerLoad } from './$types';
import { createSessionSummary, isLikelyJwt, writeSession } from '$lib/server/session';
import { sanitizeRedirectTo } from '$lib/server/auth';

const REQUIRED_SCOPES = ['inventory:read', 'inventory:write'];

export const load: PageServerLoad = async ({ locals, url }) => {
	if (locals.session) throw redirect(303, '/platform');
	return { redirectTo: sanitizeRedirectTo(url.searchParams.get('redirectTo')) };
};

export const actions: Actions = {
	default: async ({ request, cookies }) => {
		const formData = await request.formData();
		const token = formData.get('token')?.toString().trim() ?? '';
		const redirectTo = sanitizeRedirectTo(formData.get('redirectTo')?.toString());

		if (!token) {
			return fail(400, { error: 'A bearer JWT is required.', redirectTo });
		}

		if (!isLikelyJwt(token)) {
			return fail(400, { error: 'The value provided is not a valid JWT.', redirectTo });
		}

		let summary;
		try {
			summary = createSessionSummary(token);
		} catch {
			return fail(400, { error: 'The JWT payload could not be parsed.', redirectTo });
		}

		if (!summary.tenantId) {
			return fail(400, {
				error: 'The JWT must include a `tenant_id` claim.',
				redirectTo
			});
		}

		const scopes = summary.scopes ?? [];
		const missing = REQUIRED_SCOPES.filter((s) => !scopes.includes(s));
		if (missing.length) {
			return fail(400, {
				error: `JWT is missing required scope${missing.length > 1 ? 's' : ''}: ${missing.join(', ')}.`,
				redirectTo
			});
		}

		writeSession(cookies, token);
		throw redirect(303, redirectTo);
	}
};
