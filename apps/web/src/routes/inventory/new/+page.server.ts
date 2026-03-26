import { fail, redirect } from '@sveltejs/kit';
import { ApiClientError, createApiClientFromEvent } from '$lib/server/api/client';
import { parseInventoryForm } from '$lib/features/inventory/forms';
import { toInventoryFormValues } from '$lib/features/inventory/types';
import { requireSession } from '$lib/server/auth';
import type { Actions, PageServerLoad } from './$types';

export const load: PageServerLoad = async (event) => {
	requireSession(event);
	return {
		initialValues: toInventoryFormValues()
	};
};

export const actions: Actions = {
	default: async (event) => {
		requireSession(event);

		const formData = await event.request.formData();
		const parsed = parseInventoryForm(formData, 'create');
		if (!parsed.success) {
			return fail(400, parsed);
		}
		const payload = parsed.data;
		if (!payload) {
			throw new Error('Expected inventory payload after successful form parsing');
		}

		try {
			const result = await createApiClientFromEvent(event).createInventorySku(payload);
			throw redirect(303, `/inventory/${result.sku_id}?saved=created`);
		} catch (error) {
			if (error instanceof ApiClientError) {
				return fail(error.status, {
					...parsed,
					success: false,
					errors: { ...parsed.errors, _form: error.message }
				});
			}
			throw error;
		}
	}
};
