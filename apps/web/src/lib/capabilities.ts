export type CapabilityId =
	| 'platform'
	| 'inventory'
	| 'audit'
	| 'compliance'
	| 'split-payment'
	| 'ingestion';

export type CapabilityAvailability = 'public' | 'available' | 'partial' | 'locked';

export interface CapabilityMetric {
	label: string;
	value: string;
	detail: string;
}

export interface CapabilityWorkflow {
	title: string;
	description: string;
	status: 'live' | 'guided';
	ctaLabel?: string;
	href?: string;
}

export interface CapabilityEndpoint {
	method: 'GET' | 'POST' | 'PUT' | 'DELETE';
	path: string;
	scope?: string;
	summary: string;
	detail: string;
}

export interface CapabilitySample {
	title: string;
	language: string;
	code: string;
}

export interface CapabilityDefinition {
	id: CapabilityId;
	title: string;
	navLabel: string;
	href: string;
	eyebrow: string;
	headline: string;
	summary: string;
	availability: 'public' | 'protected';
	scopes: string[];
	metrics: CapabilityMetric[];
	workflows: CapabilityWorkflow[];
	endpoints: CapabilityEndpoint[];
	samples?: CapabilitySample[];
}

export interface SessionShape {
	authenticated?: boolean;
	scopes?: string[];
}

const capabilityList: CapabilityDefinition[] = [
	{
		id: 'platform',
		title: 'Platform Info',
		navLabel: 'Platform',
		href: '/platform',
		eyebrow: 'Public service surface',
		headline: 'Start from the runtime contract instead of guessing what the backend exposes.',
		summary:
			'Public platform metadata reports the service identity plus the embedding and LLM models currently configured on the Spring side.',
		availability: 'public',
		scopes: [],
		metrics: [
			{
				label: 'Auth',
				value: 'Public',
				detail: 'No bearer token required for `/api/v1/platform/info`.'
			},
			{
				label: 'Endpoints',
				value: '1',
				detail: 'Service info is exposed directly from the platform controller.'
			},
			{
				label: 'Use case',
				value: 'Runtime health',
				detail: 'Useful for validating which models are active before testing workflows.'
			}
		],
		workflows: [
			{
				title: 'Check active models',
				description:
					'Read the service, embedding model, and LLM model that the backend reports right now.',
				status: 'live'
			}
		],
		endpoints: [
			{
				method: 'GET',
				path: '/api/v1/platform/info',
				summary: 'Return runtime platform metadata.',
				detail: 'Includes the service name plus the configured embedding and LLM models.'
			}
		],
		samples: [
			{
				title: 'Platform response payload',
				language: 'json',
				code: `{
  "service": "fiscalbrain-br",
  "embeddingModel": "text-embedding-3-small",
  "llmModel": "gpt-4o"
}`
			}
		]
	},
	{
		id: 'inventory',
		title: 'Inventory Workspace',
		navLabel: 'Inventory',
		href: '/inventory',
		eyebrow: 'Tenant catalog control',
		headline:
			'Search, inspect, create, and edit tenant SKUs without leaking the bearer token to the browser.',
		summary:
			'The inventory UI already talks to the Spring inventory API through the server-only SvelteKit boundary, with list, detail, create, and update flows in place.',
		availability: 'protected',
		scopes: ['inventory:read', 'inventory:write'],
		metrics: [
			{
				label: 'Auth',
				value: 'Scoped',
				detail: 'Requires `inventory:read` for reads and `inventory:write` for writes.'
			},
			{
				label: 'Endpoints',
				value: '5+',
				detail: 'List, detail, create, update, delete, and re-audit route family.'
			},
			{
				label: 'UI state',
				value: 'Live',
				detail: 'This is the most complete workflow currently implemented in the frontend.'
			}
		],
		workflows: [
			{
				title: 'Browse tenant catalog',
				description:
					'Filter and sort the SKU list directly from the server-rendered inventory index.',
				status: 'live',
				ctaLabel: 'Open inventory',
				href: '/inventory'
			},
			{
				title: 'Create a SKU',
				description:
					'Send a new inventory payload through the server boundary into the Spring write API.',
				status: 'live',
				ctaLabel: 'Create SKU',
				href: '/inventory/new'
			},
			{
				title: 'Inspect reform taxes',
				description:
					'Open any SKU detail to review legacy and reform tax maps with update timestamps.',
				status: 'guided',
				ctaLabel: 'View records',
				href: '/inventory'
			}
		],
		endpoints: [
			{
				method: 'GET',
				path: '/api/v1/inventory/sku',
				scope: 'inventory:read',
				summary: 'List SKU records.',
				detail: 'Supports paging, search, sorting, and inactive record filtering.'
			},
			{
				method: 'GET',
				path: '/api/v1/inventory/sku/{skuId}',
				scope: 'inventory:read',
				summary: 'Load a single SKU record.',
				detail: 'Returns the inventory payload plus computed reform tax output.'
			},
			{
				method: 'POST',
				path: '/api/v1/inventory/sku',
				scope: 'inventory:write',
				summary: 'Create a SKU.',
				detail: 'Used by the existing create flow in the frontend.'
			},
			{
				method: 'PUT',
				path: '/api/v1/inventory/sku/{skuId}',
				scope: 'inventory:write',
				summary: 'Update a SKU.',
				detail: 'Used by the edit screen to persist inventory changes.'
			},
			{
				method: 'POST',
				path: '/api/v1/inventory/sku/{skuId}/re-audit',
				scope: 'audit:trigger',
				summary: 'Queue a re-audit for a SKU.',
				detail:
					'This write lives under the inventory route family but belongs to the audit workflow.'
			}
		]
	},
	{
		id: 'audit',
		title: 'Audit Intelligence',
		navLabel: 'Audit',
		href: '/audit',
		eyebrow: 'Explain, search, and trigger',
		headline:
			'Audit is more than a button. It already exposes explainability, semantic query, and re-run triggers.',
		summary:
			'The backend supports direct SKU explain responses, vector-style audit queries, and explicit re-audit trigger endpoints with separate scope gates.',
		availability: 'protected',
		scopes: ['audit:read', 'audit:query', 'audit:trigger'],
		metrics: [
			{
				label: 'Auth',
				value: '3 scopes',
				detail: 'Read, semantic query, and trigger permissions are separated.'
			},
			{
				label: 'Endpoints',
				value: '3',
				detail: 'Explain, query, and re-audit are distinct operations.'
			},
			{
				label: 'Best entry',
				value: 'Inventory + query',
				detail: 'Re-audit ties back to SKU state, while search lets you inspect legal context.'
			}
		],
		workflows: [
			{
				title: 'Explain a SKU',
				description:
					'Retrieve reform taxes, model metadata, and source law details for a specific SKU.',
				status: 'guided',
				ctaLabel: 'Start from inventory',
				href: '/inventory'
			},
			{
				title: 'Run semantic law query',
				description:
					'Query the audit corpus with `k` results and optional filters for law type or publication window.',
				status: 'live'
			},
			{
				title: 'Trigger re-audit',
				description:
					'Recompute audit output for a SKU when classification or legal inputs changed.',
				status: 'guided',
				ctaLabel: 'Open inventory',
				href: '/inventory'
			}
		],
		endpoints: [
			{
				method: 'GET',
				path: '/api/v1/audit/explain/{skuId}',
				scope: 'audit:read',
				summary: 'Explain audit output for one SKU.',
				detail: 'Returns reform taxes, confidence, LLM model, and source law payload.'
			},
			{
				method: 'POST',
				path: '/api/v1/audit/query',
				scope: 'audit:query',
				summary: 'Query the audit knowledge base.',
				detail: 'Accepts free text plus optional filters and a result count up to 20.'
			},
			{
				method: 'POST',
				path: '/api/v1/inventory/sku/{skuId}/re-audit',
				scope: 'audit:trigger',
				summary: 'Queue a re-audit job.',
				detail: 'Mounted under the inventory API family but protected by the audit trigger scope.'
			}
		],
		samples: [
			{
				title: 'Audit query payload',
				language: 'json',
				code: `{
  "query": "ICMS monofasico combustivel interestadual",
  "k": 5,
  "filters": {
    "law_type": "decreto",
    "published_after": "2025-01-01"
  }
}`
			}
		]
	},
	{
		id: 'compliance',
		title: 'Compliance Artifacts',
		navLabel: 'Compliance',
		href: '/compliance',
		eyebrow: 'Replay and evidence',
		headline:
			'Compliance exposes artifact retrieval so audit output can be inspected, replayed, and evidenced.',
		summary:
			'Artifact endpoints expose run metadata, request IDs, vector references, replay context, and full RAG output for explainability investigations.',
		availability: 'protected',
		scopes: ['compliance:read'],
		metrics: [
			{
				label: 'Auth',
				value: 'Read only',
				detail: 'All compliance artifact retrieval is protected by `compliance:read`.'
			},
			{
				label: 'Endpoints',
				value: '2',
				detail: 'Latest artifact by SKU and direct lookup by run ID.'
			},
			{
				label: 'Payload',
				value: 'Deep trace',
				detail: 'Includes replay context, source metadata, vector IDs, and timestamps.'
			}
		],
		workflows: [
			{
				title: 'Retrieve latest artifact',
				description: 'Inspect the newest explainability artifact for a given SKU.',
				status: 'live'
			},
			{
				title: 'Replay by run ID',
				description:
					'Open a historical artifact directly when you already have the run identifier.',
				status: 'live'
			}
		],
		endpoints: [
			{
				method: 'GET',
				path: '/api/v1/audit/explain/{skuId}/artifact/latest',
				scope: 'compliance:read',
				summary: 'Fetch the latest explainability artifact for a SKU.',
				detail: 'Useful when you want the current evidence bundle behind a SKU explanation.'
			},
			{
				method: 'GET',
				path: '/api/v1/audit/explain/artifact/runs/{runId}',
				scope: 'compliance:read',
				summary: 'Fetch a specific historical artifact.',
				detail: 'Useful for replay and audit trail reconstruction when a run ID is known.'
			}
		],
		samples: [
			{
				title: 'Artifact fields returned',
				language: 'text',
				code: `run_id, sku_id, job_id, request_id, artifact_version, schema_version,
artifact_digest, llm_model_used, vector_id, audit_confidence,
source, replay_context, rag_output, created_at`
			}
		]
	},
	{
		id: 'split-payment',
		title: 'Split Payment Events',
		navLabel: 'Split Payment',
		href: '/split-payment',
		eyebrow: 'Event ingestion and history',
		headline:
			'Split payment already supports both write-side event intake and read-side tenant event history.',
		summary:
			'The backend accepts structured split payment events with idempotency keys and exposes a paged event history filtered by SKU or event type.',
		availability: 'protected',
		scopes: ['split_payment:read', 'split_payment:write'],
		metrics: [
			{
				label: 'Auth',
				value: 'Read + write',
				detail: 'Listing and event creation are split into separate scopes.'
			},
			{
				label: 'Endpoints',
				value: '2',
				detail: 'Event create and paged list endpoints are both live.'
			},
			{
				label: 'Data model',
				value: 'Idempotent',
				detail: 'Create payload requires an idempotency key plus event metadata.'
			}
		],
		workflows: [
			{
				title: 'Create event payloads',
				description:
					'Submit structured payment events with timestamps, integration metadata, and arbitrary payload data.',
				status: 'live'
			},
			{
				title: 'Review tenant history',
				description: 'List events with paging and optional SKU or event-type filters.',
				status: 'live'
			}
		],
		endpoints: [
			{
				method: 'POST',
				path: '/api/v1/split-payment/events',
				scope: 'split_payment:write',
				summary: 'Create a split payment event.',
				detail: 'Persists one event and returns identifiers, integration status, and creation time.'
			},
			{
				method: 'GET',
				path: '/api/v1/split-payment/events?page=1&limit=50&sku_id=...&event_type=...',
				scope: 'split_payment:read',
				summary: 'List split payment events.',
				detail: 'Supports paging and optional filters for SKU and event type.'
			}
		],
		samples: [
			{
				title: 'Split payment create payload',
				language: 'json',
				code: `{
  "sku_id": "SKU-123",
  "event_type": "split_payment_authorized",
  "amount": 15490,
  "currency": "BRL",
  "idempotency_key": "split-SKU-123-20260326T120000Z",
  "timestamp": "2026-03-26T12:00:00Z",
  "integration_metadata": {
    "provider": "erp-bridge"
  },
  "event_payload": {
    "order_id": "ORD-991"
  }
}`
			}
		]
	},
	{
		id: 'ingestion',
		title: 'Regulatory Ingestion',
		navLabel: 'Ingestion',
		href: '/ingestion',
		eyebrow: 'Queue new legal inputs',
		headline:
			'Ingestion is already queue-backed at the API layer, ready to accept new law references and source material.',
		summary:
			'The backend validates source URLs or raw content, binds the job to the current tenant company, and returns an accepted queue response with a job ID.',
		availability: 'protected',
		scopes: ['ingestion:write'],
		metrics: [
			{
				label: 'Auth',
				value: 'Write only',
				detail: 'Queue access is currently exposed through `ingestion:write`.'
			},
			{
				label: 'Endpoints',
				value: '1',
				detail: 'Ingestion currently exposes a single queueing entry point.'
			},
			{
				label: 'Queue mode',
				value: 'Accepted',
				detail: 'The API returns `202 Accepted` with a job identifier and queued status.'
			}
		],
		workflows: [
			{
				title: 'Queue legal content',
				description:
					'Submit a law reference with either a source URL or raw content and optional dates and tags.',
				status: 'live'
			}
		],
		endpoints: [
			{
				method: 'POST',
				path: '/api/v1/ingestion/jobs',
				scope: 'ingestion:write',
				summary: 'Enqueue an ingestion job.',
				detail:
					'Validates source input, resolves tenant company ID, and returns a queued job response.'
			}
		],
		samples: [
			{
				title: 'Ingestion queue payload',
				language: 'json',
				code: `{
  "law_ref": "Convenio ICMS 17/2026",
  "law_type": "convenio",
  "source_url": "https://www.confaz.fazenda.gov.br/legislacao/convenios/2026/CV017_26",
  "published_at": "2026-03-21",
  "effective_at": "2026-04-01",
  "tags": ["combustiveis", "icms"]
}`
			}
		]
	}
];

export { capabilityList };

export function getCapability(id: CapabilityId): CapabilityDefinition {
	const capability = capabilityList.find((entry) => entry.id === id);
	if (!capability) {
		throw new Error(`Unknown capability: ${id}`);
	}
	return capability;
}

export function getCapabilityByPath(pathname: string): CapabilityDefinition | null {
	return (
		capabilityList.find(
			(entry) => pathname === entry.href || pathname.startsWith(`${entry.href}/`)
		) ?? null
	);
}

export function getCapabilityAvailability(
	capability: CapabilityDefinition,
	scopes: string[] = []
): CapabilityAvailability {
	if (capability.availability === 'public') {
		return 'public';
	}

	if (capability.scopes.length === 0) {
		return 'available';
	}

	const owned = capability.scopes.filter((scope) => scopes.includes(scope)).length;
	if (owned === capability.scopes.length) {
		return 'available';
	}
	if (owned > 0) {
		return 'partial';
	}
	return 'locked';
}

export function getCapabilitySummary(session: SessionShape | null | undefined) {
	const scopes = session?.scopes ?? [];
	let available = 0;
	let partial = 0;
	let locked = 0;

	for (const capability of capabilityList) {
		const availability = getCapabilityAvailability(capability, scopes);
		if (availability === 'available' || availability === 'public') {
			available += 1;
		} else if (availability === 'partial') {
			partial += 1;
		} else {
			locked += 1;
		}
	}

	return {
		available,
		partial,
		locked,
		total: capabilityList.length
	};
}
