export type InventorySortBy = 'updated_at' | 'sku_id';
export type InventorySortOrder = 'asc' | 'desc';

export interface InventoryFilters {
	page: number;
	limit: number;
	includeInactive: boolean;
	query: string;
	sortBy: InventorySortBy;
	sortOrder: InventorySortOrder;
}

export interface InventorySkuTransport {
	sku_id: string;
	description: string;
	ncm_code: string;
	origin_state: string;
	destination_state: string;
	legacy_taxes: Record<string, number>;
	reform_taxes: Record<string, number>;
	is_active: boolean;
	updated_at: string;
}

export interface InventoryListTransport {
	items: InventorySkuTransport[];
	total_count: number;
	page: number;
	limit: number;
	has_more: boolean;
}

export interface InventoryWriteTransport {
	sku_id: string;
	description: string;
	ncm_code: string;
	origin_state: string;
	destination_state: string;
	legacy_taxes: Record<string, number>;
}

export interface InventoryWriteResultTransport {
	sku_id: string;
	status: string;
}

export interface InventoryRecordView {
	skuId: string;
	description: string;
	ncmCode: string;
	originState: string;
	destinationState: string;
	legacyTaxes: Record<string, number>;
	reformTaxes: Record<string, number>;
	isActive: boolean;
	updatedAt: string;
}

export interface InventoryListView {
	items: InventoryRecordView[];
	totalCount: number;
	page: number;
	limit: number;
	hasMore: boolean;
}

export interface InventoryLegacyTaxFields {
	icms: string;
	pis: string;
	cofins: string;
	iss: string;
}

export interface InventoryFormValues {
	skuId: string;
	description: string;
	ncmCode: string;
	originState: string;
	destinationState: string;
	legacyTaxes: InventoryLegacyTaxFields;
}

export interface InventoryFormErrors {
	skuId?: string;
	description?: string;
	ncmCode?: string;
	originState?: string;
	destinationState?: string;
	legacyTaxes?: Partial<Record<keyof InventoryLegacyTaxFields, string>>;
	_form?: string;
}

export function mapInventoryRecord(record: InventorySkuTransport): InventoryRecordView {
	return {
		skuId: record.sku_id,
		description: record.description,
		ncmCode: record.ncm_code,
		originState: record.origin_state,
		destinationState: record.destination_state,
		legacyTaxes: record.legacy_taxes,
		reformTaxes: record.reform_taxes,
		isActive: record.is_active,
		updatedAt: record.updated_at
	};
}

export function mapInventoryList(response: InventoryListTransport): InventoryListView {
	return {
		items: response.items.map(mapInventoryRecord),
		totalCount: response.total_count,
		page: response.page,
		limit: response.limit,
		hasMore: response.has_more
	};
}

export function toInventoryFormValues(record?: InventoryRecordView): InventoryFormValues {
	return {
		skuId: record?.skuId ?? '',
		description: record?.description ?? '',
		ncmCode: record?.ncmCode ?? '',
		originState: record?.originState ?? '',
		destinationState: record?.destinationState ?? '',
		legacyTaxes: {
			icms: record?.legacyTaxes.icms?.toString() ?? '',
			pis: record?.legacyTaxes.pis?.toString() ?? '',
			cofins: record?.legacyTaxes.cofins?.toString() ?? '',
			iss: record?.legacyTaxes.iss?.toString() ?? ''
		}
	};
}
