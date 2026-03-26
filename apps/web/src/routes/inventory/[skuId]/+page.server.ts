import { error } from '@sveltejs/kit';
import { ApiClientError, createApiClientFromEvent } from '$lib/server/api/client';
import { mapInventoryRecord } from '$lib/features/inventory/types';
import { requireSession } from '$lib/server/auth';
import type { PageServerLoad } from './$types';

function getSuccessMessage(saved: string | null): string | null {
	const messages = {
		created: 'SKU created successfully.',
		updated: 'SKU updated successfully.'
	} as const;

	return saved ? messages[saved as keyof typeof messages] ?? null : null;
}

export const load: PageServerLoad = async (event) => {
	requireSession(event);

	try {
		const item = await createApiClientFromEvent(event).getInventorySku(event.params.skuId, true);
		return {
			item: mapInventoryRecord(item),
			successMessage: getSuccessMessage(event.url.searchParams.get('saved'))
		};
	} catch (cause) {
		if (cause instanceof ApiClientError && cause.status === 404) {
			throw error(404, `SKU ${event.params.skuId} was not found.`);
		}
		if (cause instanceof ApiClientError) {
			throw error(cause.status, cause.message);
		}
		throw cause;
	}
};
