export interface AuditExplainTransport {
	sku_id: string;
	reform_taxes: Record<string, number>;
	confidence: number;
	llm_model: string;
	source_law: Record<string, unknown>;
	audit_reasoning?: string;
	created_at: string;
}

export interface AuditQueryResultTransport {
	id: string;
	score: number;
	law_ref: string;
	law_type: string;
	content: string;
	published_at?: string;
}

export interface AuditQueryTransport {
	results: AuditQueryResultTransport[];
	query: string;
	total: number;
}

export interface ReAuditTransport {
	job_id: string;
	sku_id: string;
	status: string;
	queued_at: string;
}

export interface AuditQueryFilters {
	law_type?: string;
	published_after?: string;
}

export interface AuditQueryPayload {
	query: string;
	k: number;
	filters?: AuditQueryFilters;
}
