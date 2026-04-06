export interface ComplianceArtifactTransport {
	run_id: string;
	sku_id: string;
	job_id: string;
	request_id: string;
	artifact_version: string;
	schema_version: string;
	artifact_digest: string;
	llm_model_used: string;
	vector_id: string;
	audit_confidence: number;
	source: Record<string, unknown>;
	replay_context: Record<string, unknown>;
	rag_output: Record<string, unknown>;
	created_at: string;
}
