import type { LayoutServerLoad } from './$types';

export const load: LayoutServerLoad = async ({ locals }) => ({
	session: locals.session
		? {
				authenticated: true as const,
				user: locals.session.user,
				tenantId: locals.session.tenantId,
				scopes: locals.session.scopes
			}
		: null
});
