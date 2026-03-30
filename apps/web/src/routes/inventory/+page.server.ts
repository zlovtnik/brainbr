import { createApiClientFromEvent, ApiClientError } from '$lib/server/api/client';
import { parseInventoryFilters } from '$lib/features/inventory/filters';
import { mapInventoryList } from '$lib/features/inventory/types';
import { describeProtectedApiError, requireSession } from '$lib/server/auth';
import type { PageServerLoad } from './$types';

export const load: PageServerLoad = async (event) => {
	requireSession(event);

	const filters = parseInventoryFilters(event.url.searchParams);
	const successMessage =
		event.url.searchParams.get('saved') === 'created'
			? 'SKU created successfully.'
			: event.url.searchParams.get('saved') === 'updated'
				? 'SKU updated successfully.'
				: undefined;

	try {
		const inventory = await createApiClientFromEvent(event).listInventory(filters);
		return {
			filters,
			inventory: mapInventoryList(inventory),
			loadError: null,
			successMessage
		};
	} catch (error) {
		const loadError =
			error instanceof ApiClientError
				? describeProtectedApiError(event.locals.session, error, ['inventory:read'])
				: 'A server error prevented inventory loading.';
		return {
			filters,
			inventory: null,
			loadError,
			successMessage
		};
	}
};
