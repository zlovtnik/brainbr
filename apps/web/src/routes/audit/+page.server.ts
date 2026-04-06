import { fail, redirect } from '@sveltejs/kit';
import { ApiClientError, createApiClientFromEvent } from '$lib/server/api/client';
import { requireSession } from '$lib/server/auth';
import type { PageServerLoad, Actions } from './$types';

const VALID_LAW_TYPES = new Set(['convenio', 'protocolo', 'ajuste', 'ato']);

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

		if (law_type && !VALID_LAW_TYPES.has(law_type)) {
			return fail(422, { queryError: 'Invalid law_type value.', queryInput });
		}
		if (published_after) {
			const d = new Date(published_after);
			if (isNaN(d.getTime()) || d > new Date()) {
				return fail(422, { queryError: 'published_after must be a valid past ISO 8601 date.', queryInput });
			}
		}

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

		const idempotencyKey = `${skuId}:${Date.now()}`;

		try {
			const job = await createApiClientFromEvent(event).reAudit(skuId, { idempotencyKey });
			throw redirect(303, `/audit?skuId=${encodeURIComponent(skuId)}&jobId=${encodeURIComponent(job.id)}`);
		} catch (cause) {
			if (cause instanceof ApiClientError) {
				return fail(cause.status, { reauditError: cause.message, reauditSkuId: skuId });
			}
			throw cause;
		}
	}
};
