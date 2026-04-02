import { error } from '@sveltejs/kit';
import { createApiClientFromEvent, ApiClientError } from '$lib/server/api/client';
import { parseInventoryFilters } from '$lib/features/inventory/filters';
import { mapInventoryList } from '$lib/features/inventory/types';
import { describeProtectedApiError, requireSession } from '$lib/server/auth';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async (event) => {
	requireSession(event);

	const filters = parseInventoryFilters(event.url.searchParams);

	try {
		const inventory = await createApiClientFromEvent(event).listInventory(filters);
		return {
			filters,
			inventory: mapInventoryList(inventory),
			loadError: null
		};
	} catch (cause) {
		if (cause instanceof ApiClientError) {
			throw error(
				cause.status,
				describeProtectedApiError(event.locals.session, cause, ['inventory:read'])
			);
		}

		// Re-throw unexpected errors so SvelteKit's global error handler catches them
		throw cause;
	}
};
