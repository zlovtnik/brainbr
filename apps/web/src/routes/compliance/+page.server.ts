import { ApiClientError, createApiClientFromEvent } from '$lib/server/api/client';
import { requireSession } from '$lib/server/auth';
import type { PageServerLoad, Actions } from './$types';
import { fail } from '@sveltejs/kit';

export const load: PageServerLoad = async (event) => {
	// amazonq-ignore-next-line
	requireSession(event);

	const skuId = event.url.searchParams.get('skuId')?.trim() ?? null;
	if (!skuId) return { artifact: null, skuId: null };

	try {
		const artifact = await createApiClientFromEvent(event).complianceLatest(skuId);
		return { artifact, skuId };
	} catch (cause) {
		if (cause instanceof ApiClientError) {
			return { artifact: null, skuId, artifactError: cause.message };
		}
		throw cause;
	}
};

export const actions: Actions = {
	replay: async (event) => {
		// amazonq-ignore-next-line
		requireSession(event);
		const data = await event.request.formData();
		const runId = data.get('runId')?.toString().trim() ?? '';

		if (!runId) return fail(422, { replayError: 'Run ID is required.' });

		try {
			const artifact = await createApiClientFromEvent(event).complianceByRunId(runId);
			return { replayArtifact: artifact };
		} catch (cause) {
			if (cause instanceof ApiClientError) {
				return fail(cause.status, { replayError: cause.message });
			}
			throw cause;
		}
	}
};
