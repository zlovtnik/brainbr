import { fail, redirect } from '@sveltejs/kit';
import { ApiClientError, createApiClientFromEvent } from '$lib/server/api/client';
import { parseInventoryForm } from '$lib/features/inventory/forms';
import { toInventoryFormValues } from '$lib/features/inventory/types';
import { describeProtectedApiError, requireSession } from '$lib/server/auth';
import { writeFlash } from '$lib/server/session';
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

		try {
			const result = await createApiClientFromEvent(event).createInventorySku(parsed.data);
			writeFlash(event.cookies, {
				type: 'success',
				message: 'SKU created successfully.'
			});
			throw redirect(303, `/inventory/${encodeURIComponent(result.sku_id)}`);
		} catch (error) {
			if (error instanceof ApiClientError) {
				return fail(error.status, {
					...parsed,
					success: false,
					errors: {
						...parsed.errors,
						_form: describeProtectedApiError(event.locals.session, error, ['inventory:write'])
					}
				});
			}
			throw error;
		}
	}
};
