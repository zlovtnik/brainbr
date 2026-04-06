export interface SplitPaymentEventTransport {
	id: string;
	sku_id: string;
	event_type: string;
	amount: number;
	currency: string;
	idempotency_key: string;
	timestamp: string;
	integration_metadata: Record<string, unknown>;
	event_payload: Record<string, unknown>;
	created_at: string;
}

export interface SplitPaymentListTransport {
	items: SplitPaymentEventTransport[];
	total_count: number;
	page: number;
	limit: number;
	has_more: boolean;
}

export interface SplitPaymentCreateTransport {
	sku_id: string;
	event_type: string;
	amount: number;
	currency: string;
	idempotency_key: string;
	timestamp: string;
	integration_metadata?: Record<string, unknown>;
	event_payload?: Record<string, unknown>;
}

export interface SplitPaymentCreateResultTransport {
	id: string;
	sku_id: string;
	status: string;
	created_at: string;
}
