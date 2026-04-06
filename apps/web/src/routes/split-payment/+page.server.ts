import { fail } from '@sveltejs/kit';
import { ApiClientError, createApiClientFromEvent } from '$lib/server/api/client';
import { requireSession } from '$lib/server/auth';
import type { PageServerLoad, Actions } from './$types';

export const load: PageServerLoad = async (event) => {
	// amazonq-ignore-next-line
	requireSession(event);
	const parsedPage = parseInt(event.url.searchParams.get('page') ?? '1', 10);
	const page = Number.isNaN(parsedPage) || parsedPage < 1 ? 1 : parsedPage;
	const skuId = event.url.searchParams.get('sku_id')?.trim() || undefined;
	const eventType = event.url.searchParams.get('event_type')?.trim() || undefined;

	try {
		const list = await createApiClientFromEvent(event).listSplitPayments(page, 20, skuId, eventType);
		return { list, skuId: skuId ?? null, eventType: eventType ?? null, page };
	} catch (cause) {
		if (cause instanceof ApiClientError) {
			return { list: null, listError: cause.message, skuId: null, eventType: null, page: 1 };
		}
		throw cause;
	}
};

export const actions: Actions = {
	create: async (event) => {
		// amazonq-ignore-next-line
		requireSession(event);
		const data = await event.request.formData();

		const sku_id = data.get('sku_id')?.toString().trim() ?? '';
		const event_type = data.get('event_type')?.toString().trim() ?? '';
		const amount_raw = data.get('amount')?.toString().trim() ?? '';
		const currency = data.get('currency')?.toString().trim() || 'BRL';
		const idempotency_key = data.get('idempotency_key')?.toString().trim() ?? '';
		const timestamp = data.get('timestamp')?.toString().trim() ?? '';

		if (!sku_id || !event_type || !amount_raw || !idempotency_key || !timestamp) {
			return fail(422, { createError: 'All required fields must be filled.' });
		}

		const amount = Math.round(parseFloat(amount_raw) * 100);
		if (isNaN(amount)) return fail(422, { createError: 'Amount must be a valid number.' });

		try {
			const result = await createApiClientFromEvent(event).createSplitPayment({
				sku_id, event_type, amount, currency, idempotency_key, timestamp
			});
			return { createResult: result };
		} catch (cause) {
			if (cause instanceof ApiClientError) {
				return fail(cause.status, { createError: cause.message });
			}
			throw cause;
		}
	}
};
