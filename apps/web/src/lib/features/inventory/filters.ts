import type { InventoryFilters } from '$lib/features/inventory/types';

const DEFAULT_LIMIT = 10;

function parsePositiveInteger(value: string | null, fallback: number): number {
	const parsed = Number(value);
	return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback;
}

export function parseInventoryFilters(searchParams: URLSearchParams): InventoryFilters {
	return {
		page: parsePositiveInteger(searchParams.get('page'), 1),
		limit: parsePositiveInteger(searchParams.get('limit'), DEFAULT_LIMIT),
		includeInactive: searchParams.get('includeInactive') === 'true',
		query: searchParams.get('query')?.trim() ?? '',
		sortBy: searchParams.get('sortBy') === 'sku_id' ? 'sku_id' : 'updated_at',
		sortOrder: searchParams.get('sortOrder') === 'asc' ? 'asc' : 'desc'
	};
}
