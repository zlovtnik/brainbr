import { fail } from '@sveltejs/kit';
import { ApiClientError, createApiClientFromEvent } from '$lib/server/api/client';
import { requireSession } from '$lib/server/auth';
import type { PageServerLoad, Actions } from './$types';

export const load: PageServerLoad = async (event) => {
	// amazonq-ignore-next-line
	requireSession(event);

	const skuId = event.url.searchParams.get('skuId')?.trim() ?? null;
	if (!skuId) return { explain: null, skuId: null };

	try {
		const explain = await createApiClientFromEvent(event).auditExplain(skuId);
		return { explain, skuId };
	} catch (cause) {
		if (cause instanceof ApiClientError) {
			return { explain: null, skuId, explainError: cause.message };
		}
		throw cause;
	}
};

export const actions: Actions = {
	query: async (event) => {
		// amazonq-ignore-next-line
			requireSession(event);
			const data = await event.request.formData();
			const query = data.get('query')?.toString().trim() ?? '';
			const rawK = parseInt(data.get('k')?.toString() ?? '', 10);
			const k = Number.isFinite(rawK) && !Number.isNaN(rawK) ? Math.min(20, Math.max(1, rawK)) : 5;
			const law_type = data.get('law_type')?.toString().trim() || undefined;
			const published_after = data.get('published_after')?.toString().trim() || undefined;
			const queryInput = { query, k, law_type, published_after };

			if (!query) return fail(422, { queryError: 'Query text is required.', queryInput });

		try {
			const results = await createApiClientFromEvent(event).auditQuery({
				query,
				k,
				filters: law_type || published_after ? { law_type, published_after } : undefined
			});
				return { queryResults: results, queryInput };
			} catch (cause) {
				if (cause instanceof ApiClientError) {
					return fail(cause.status, { queryError: cause.message, queryInput });
				}
				throw cause;
			}
	},

	reaudit: async (event) => {
		// amazonq-ignore-next-line
		requireSession(event);
		const data = await event.request.formData();
		const skuId = data.get('skuId')?.toString().trim() ?? '';

		if (!skuId) return fail(422, { reauditError: 'SKU ID is required.' });

		try {
			const job = await createApiClientFromEvent(event).reAudit(skuId);
			return { reauditJob: job };
		} catch (cause) {
			if (cause instanceof ApiClientError) {
				return fail(cause.status, { reauditError: cause.message, reauditSkuId: skuId });
			}
			throw cause;
		}
	}
};
