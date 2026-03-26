import { env } from '$env/dynamic/private';
import { randomUUID } from 'node:crypto';
import type { RequestEvent } from '@sveltejs/kit';
import type {
	InventoryFilters,
	InventoryListTransport,
	InventorySkuTransport,
	InventoryWriteTransport,
	InventoryWriteResultTransport
} from '$lib/features/inventory/types';

interface ErrorPayload {
	error_code?: string;
	message?: string;
	request_id?: string;
}

interface ApiClientOptions {
	fetch?: typeof fetch;
	token?: string;
	requestId?: string;
}

export class ApiClientError extends Error {
	constructor(
		message: string,
		readonly status: number,
		readonly code: string,
		readonly requestId?: string,
		cause?: unknown
	) {
		super(message, cause ? { cause } : undefined);
		this.name = 'ApiClientError';
	}
}

function getApiBaseUrl(): string {
	const baseUrl = env.API_BASE_URL?.trim() || process.env.API_BASE_URL?.trim();
	if (!baseUrl) {
		throw new Error('Missing API_BASE_URL for the SvelteKit backend proxy');
	}
	return baseUrl.replace(/\/$/, '');
}

function createHeaders(token: string, requestId: string, hasBody: boolean): Headers {
	const headers = new Headers({
		Accept: 'application/json',
		Authorization: `Bearer ${token}`,
		'X-Request-Id': requestId
	});

	if (hasBody) {
		headers.set('Content-Type', 'application/json');
	}

	return headers;
}

async function parseError(response: Response): Promise<ApiClientError> {
	let payload: ErrorPayload | null = null;
	try {
		payload = (await response.json()) as ErrorPayload;
	} catch {
		payload = null;
	}

	return new ApiClientError(
		payload?.message || `API request failed with status ${response.status}`,
		response.status,
		payload?.error_code || 'API_ERROR',
		payload?.request_id
	);
}

export function createApiClientFromEvent(event: RequestEvent) {
	const token = event.locals.session?.token;
	if (!token) {
		throw new Error('Missing authenticated session token');
	}

	return createApiClient({
		token,
		requestId: event.request.headers.get('x-request-id') ?? randomUUID()
	});
}

export function createApiClient(options: ApiClientOptions) {
	const token = options.token?.trim();
	if (!token) {
		throw new Error('Missing bearer token for API client');
	}

	const authToken = token;
	const apiBase = getApiBaseUrl();
	const runFetch = options.fetch ?? globalThis.fetch;
	const requestId = options.requestId ?? randomUUID();

	async function request<T>(path: string, init?: RequestInit): Promise<T> {
		const controller = new AbortController();
		const timeoutId = setTimeout(() => controller.abort(), 30_000);

		try {
			let response: Response;
			try {
				response = await runFetch(`${apiBase}${path}`, {
					...init,
					signal: controller.signal,
					headers: createHeaders(authToken, requestId, Boolean(init?.body))
				});
			} catch (cause) {
				if (
					(cause instanceof DOMException && cause.name === 'AbortError') ||
					(typeof cause === 'object' && cause !== null && 'name' in cause && cause.name === 'AbortError')
				) {
					throw new ApiClientError(
						'API request aborted or timed out',
						408,
						'REQUEST_ABORTED',
						requestId,
						cause
					);
				}

				throw new ApiClientError(
					'Unable to reach the backend API',
					503,
					'API_NETWORK_ERROR',
					requestId,
					cause
				);
			}

			if (!response.ok) {
				throw await parseError(response);
			}

			return (await response.json()) as T;
		} finally {
			clearTimeout(timeoutId);
		}
	}

	return {
		listInventory(filters: InventoryFilters): Promise<InventoryListTransport> {
			const params = new URLSearchParams({
				page: filters.page.toString(),
				limit: filters.limit.toString(),
				sort_by: filters.sortBy,
				sort_order: filters.sortOrder
			});

			if (filters.query) {
				params.set('query', filters.query);
			}
			if (filters.includeInactive) {
				params.set('include_inactive', 'true');
			}

			return request<InventoryListTransport>(`/api/v1/inventory/sku?${params.toString()}`);
		},
		getInventorySku(skuId: string, includeInactive = false): Promise<InventorySkuTransport> {
			const params = new URLSearchParams();
			if (includeInactive) {
				params.set('include_inactive', 'true');
			}
			const suffix = params.size > 0 ? `?${params.toString()}` : '';
			return request<InventorySkuTransport>(
				`/api/v1/inventory/sku/${encodeURIComponent(skuId)}${suffix}`
			);
		},
		createInventorySku(payload: InventoryWriteTransport): Promise<InventoryWriteResultTransport> {
			return request<InventoryWriteResultTransport>('/api/v1/inventory/sku', {
				method: 'POST',
				body: JSON.stringify(payload)
			});
		},
		updateInventorySku(
			skuId: string,
			payload: Omit<InventoryWriteTransport, 'sku_id'>
		): Promise<InventoryWriteResultTransport> {
			return request<InventoryWriteResultTransport>(
				`/api/v1/inventory/sku/${encodeURIComponent(skuId)}`,
				{
					method: 'PUT',
					body: JSON.stringify(payload)
				}
			);
		}
	};
}
