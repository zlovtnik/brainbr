import { error, fail, redirect } from '@sveltejs/kit';
import { ApiClientError, createApiClientFromEvent } from '$lib/server/api/client';
import { parseInventoryForm } from '$lib/features/inventory/forms';
import { mapInventoryRecord, toInventoryFormValues } from '$lib/features/inventory/types';
import { requireSession } from '$lib/server/auth';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async (event) => {
	requireSession(event);

	try {
		const item = await createApiClientFromEvent(event).getInventorySku(event.params.skuId, true);
		return {
			item: mapInventoryRecord(item),
			initialValues: toInventoryFormValues(mapInventoryRecord(item))
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

export const actions: Actions = {
	default: async (event) => {
		requireSession(event);

		const formData = await event.request.formData();
		const parsed = parseInventoryForm(formData, 'edit');
		if (!parsed.success) {
			return fail(400, parsed);
		}
		const parsedData = parsed.data;
		if (!parsedData) {
			throw new Error('Expected inventory payload after successful form parsing');
		}

		const { sku_id: _skuId, ...payload } = parsedData;

		try {
			await createApiClientFromEvent(event).updateInventorySku(event.params.skuId, payload);
			throw redirect(303, `/inventory/${event.params.skuId}?saved=updated`);
		} catch (cause) {
			if (cause instanceof ApiClientError) {
				return fail(cause.status, {
					...parsed,
					success: false,
					errors: { ...parsed.errors, _form: cause.message }
				});
			}
			throw cause;
		}
	}
};
