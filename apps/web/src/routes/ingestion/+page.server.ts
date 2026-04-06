import { requireSession } from '$lib/server/auth';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async (event) => {
	// amazonq-ignore-next-line
	requireSession(event);
	return {};
};
