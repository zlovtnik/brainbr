import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiClientError, createApiClient } from '$lib/server/api/client';

describe('api client', () => {
	beforeEach(() => {
		process.env.API_BASE_URL = 'http://example.test';
	});

	it('forwards bearer auth and inventory query params', async () => {
		const fetchMock = vi.fn(async () =>
			new Response(
				JSON.stringify({
					items: [],
					total_count: 0,
					page: 1,
					limit: 10,
					has_more: false
				})
			)
		);
		const client = createApiClient({
			token: 'demo-token',
			requestId: 'req-123',
			fetch: fetchMock as typeof fetch
		});

		await client.listInventory({
			page: 1,
			limit: 10,
			includeInactive: true,
			query: 'beer',
			sortBy: 'sku_id',
			sortOrder: 'asc'
		});

		expect(fetchMock).toHaveBeenCalledTimes(1);
		const firstCall = fetchMock.mock.calls.at(0);
		if (!firstCall) {
			throw new Error('Expected fetch to be called at least once');
		}
		const [url, init] = firstCall as unknown as [string, RequestInit];
		expect(url).toBe(
			'http://example.test/api/v1/inventory/sku?page=1&limit=10&sort_by=sku_id&sort_order=asc&query=beer&include_inactive=true'
		);
		expect((init?.headers as Headers).get('Authorization')).toBe('Bearer demo-token');
		expect((init?.headers as Headers).get('X-Request-Id')).toBe('req-123');
	});

	it('normalizes backend errors', async () => {
		const client = createApiClient({
			token: 'demo-token',
			fetch: vi.fn(async () =>
				new Response(
					JSON.stringify({
						error_code: 'BAD_REQUEST',
						message: 'Invalid inventory payload',
						request_id: 'req-abc'
					}),
					{ status: 400 }
				)
			) as typeof fetch
		});

		await expect(
			client.createInventorySku({
				sku_id: 'SKU-001',
				description: 'Beer',
				ncm_code: '22030000',
				origin_state: 'SP',
				destination_state: 'RJ',
				legacy_taxes: {}
			})
		).rejects.toEqual(new ApiClientError('Invalid inventory payload', 400, 'BAD_REQUEST', 'req-abc'));
	});
});
