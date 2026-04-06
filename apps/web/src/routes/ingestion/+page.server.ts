import { fail } from '@sveltejs/kit';
import { ApiClientError, createApiClientFromEvent } from '$lib/server/api/client';
import { requireSession } from '$lib/server/auth';
import type { PageServerLoad, Actions } from './$types';

export const load: PageServerLoad = async (event) => {
	// amazonq-ignore-next-line
	requireSession(event);
	return {};
};

export const actions: Actions = {
	queue: async (event) => {
		// amazonq-ignore-next-line
		requireSession(event);
		const data = await event.request.formData();

		const law_ref = data.get('law_ref')?.toString().trim() ?? '';
		const law_type = data.get('law_type')?.toString().trim() ?? '';
		const source_url = data.get('source_url')?.toString().trim() || undefined;
		const content = data.get('content')?.toString().trim() || undefined;
		const published_at = data.get('published_at')?.toString().trim() || undefined;
		const effective_at = data.get('effective_at')?.toString().trim() || undefined;
		const tags_raw = data.get('tags')?.toString().trim() || '';
		const tags = tags_raw ? tags_raw.split(',').map((t) => t.trim()).filter(Boolean) : undefined;

		if (!law_ref || !law_type) {
			return fail(422, { queueError: 'Law reference and type are required.', law_ref, law_type, source_url, content, published_at, effective_at, tags: tags_raw });
		}
		if (!source_url && !content) {
			return fail(422, { queueError: 'Provide either a source URL or raw content.', law_ref, law_type, source_url, content, published_at, effective_at, tags: tags_raw });
		}

		try {
			const job = await createApiClientFromEvent(event).createIngestionJob({
				law_ref, law_type, source_url, content, published_at, effective_at, tags
			});
			return { job };
		} catch (cause) {
			if (cause instanceof ApiClientError) {
				return fail(cause.status, { queueError: cause.message, law_ref, law_type, source_url, content, published_at, effective_at, tags: tags_raw });
			}
			throw cause;
		}
	}
};
