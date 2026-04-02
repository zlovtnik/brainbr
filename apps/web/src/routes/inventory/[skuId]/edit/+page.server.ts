import { error, fail, redirect } from '@sveltejs/kit';
import { ApiClientError, createApiClientFromEvent } from '$lib/server/api/client';
import { parseInventoryForm } from '$lib/features/inventory/forms';
import { mapInventoryRecord, toInventoryFormValues } from '$lib/features/inventory/types';
import { describeProtectedApiError, requireSession } from '$lib/server/auth';
import { writeFlash } from '$lib/server/session';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async (event) => {
	requireSession(event);

	try {
		const item = await createApiClientFromEvent(event).getInventorySku(event.params.skuId, true);
		const mapped = mapInventoryRecord(item);
		return {
			item: mapped,
			initialValues: toInventoryFormValues(mapped)
		};
	} catch (cause) {
		if (cause instanceof ApiClientError && cause.status === 404) {
			throw error(404, `SKU ${event.params.skuId} was not found.`);
		}
		if (cause instanceof ApiClientError) {
			throw error(
				cause.status,
				describeProtectedApiError(event.locals.session, cause, ['inventory:read'])
			);
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
		const { sku_id: _skuId, ...payload } = parsed.data;

		try {
			await createApiClientFromEvent(event).updateInventorySku(event.params.skuId, payload);
		} catch (cause) {
			if (cause instanceof ApiClientError) {
				return fail(cause.status, {
					...parsed,
					success: false,
					errors: {
						...parsed.errors,
						_form: describeProtectedApiError(event.locals.session, cause, ['inventory:write'])
					}
				});
			}
			throw cause;
		}

		writeFlash(event.cookies, {
			type: 'success',
			message: 'SKU updated successfully.'
		});
		throw redirect(303, `/inventory/${encodeURIComponent(event.params.skuId)}`);
	}
};
