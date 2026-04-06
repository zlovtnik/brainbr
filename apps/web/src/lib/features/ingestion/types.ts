export interface IngestionJobTransport {
	job_id: string;
	status: string;
	queued_at: string;
}

export interface IngestionCreateTransport {
	law_ref: string;
	law_type: string;
	source_url?: string;
	content?: string;
	published_at?: string;
	effective_at?: string;
	tags?: string[];
}
