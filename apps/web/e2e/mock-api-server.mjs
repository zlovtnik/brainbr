import { createServer } from 'node:http';

const PORT = Number(process.env.MOCK_API_PORT || 5050);

const seedInventory = () => [
	{
		sku_id: 'SKU-100',
		description: 'Cerveja Pilsen Lata 350ml',
		ncm_code: '22030000',
		origin_state: 'SP',
		destination_state: 'RJ',
		legacy_taxes: { icms: 18, pis: 1.65 },
		reform_taxes: {},
		is_active: true,
		updated_at: '2026-03-25T15:10:00Z'
	},
	{
		sku_id: 'SKU-200',
		description: 'Refrigerante Citrus 600ml',
		ncm_code: '22021000',
		origin_state: 'MG',
		destination_state: 'BA',
		legacy_taxes: { icms: 17 },
		reform_taxes: { ibs: 9.3 },
		is_active: true,
		updated_at: '2026-03-24T10:15:00Z'
	}
];

let inventory = seedInventory();

function sendJson(response, status, body) {
	response.writeHead(status, { 'content-type': 'application/json' });
	response.end(JSON.stringify(body));
}

function getSkuIdFromPath(pathname) {
	const normalizedPath = pathname.replace(/\/+$/, '');
	const segments = normalizedPath.split('/').filter(Boolean);
	const skuSegment = segments.at(-1);
	return skuSegment ? decodeURIComponent(skuSegment) : '';
}

function requireAuth(request, response) {
	if (!request.headers.authorization?.startsWith('Bearer ')) {
		sendJson(response, 401, {
			error_code: 'UNAUTHORIZED',
			message: 'Missing or invalid credentials',
			request_id: 'mock-req'
		});
		return false;
	}
	return true;
}

function applyFilters(searchParams) {
	const query = searchParams.get('query')?.toLowerCase() ?? '';
	const sortBy = searchParams.get('sort_by') === 'sku_id' ? 'sku_id' : 'updated_at';
	const sortOrder = searchParams.get('sort_order') === 'asc' ? 'asc' : 'desc';
	const includeInactive = searchParams.get('include_inactive') === 'true';
	const page = Number(searchParams.get('page') || '1');
	const limit = Number(searchParams.get('limit') || '10');

	let items = inventory.filter((item) => includeInactive || item.is_active);
	if (query) {
		items = items.filter((item) =>
			[item.sku_id, item.description, item.ncm_code].some((value) =>
				value.toLowerCase().includes(query)
			)
		);
	}

	items.sort((left, right) => {
		const direction = sortOrder === 'asc' ? 1 : -1;
		if (sortBy === 'sku_id') {
			return left.sku_id.localeCompare(right.sku_id) * direction;
		}
		return (new Date(left.updated_at).getTime() - new Date(right.updated_at).getTime()) * direction;
	});

	const offset = (page - 1) * limit;
	const paged = items.slice(offset, offset + limit);

	return {
		items: paged,
		total_count: items.length,
		page,
		limit,
		has_more: offset + limit < items.length
	};
}

async function readBody(request) {
	const chunks = [];
	for await (const chunk of request) {
		chunks.push(chunk);
	}
	if (chunks.length === 0) {
		return {};
	}

	try {
		return JSON.parse(Buffer.concat(chunks).toString('utf8'));
	} catch {
		throw new SyntaxError('Invalid JSON body');
	}
}

const server = createServer(async (request, response) => {
	try {
		const url = new URL(request.url || '/', `http://127.0.0.1:${PORT}`);

		if (request.method === 'POST' && url.pathname === '/__reset') {
			inventory = seedInventory();
			return sendJson(response, 200, { ok: true });
		}

		if (!requireAuth(request, response)) {
			return;
		}

		if (request.method === 'GET' && url.pathname === '/api/v1/inventory/sku') {
			if (url.searchParams.get('query')?.toLowerCase() === 'trigger-load-error') {
				return sendJson(response, 503, {
					error_code: 'API_NETWORK_ERROR',
					message: 'Mock backend unavailable for inventory list',
					request_id: 'mock-req'
				});
			}

			return sendJson(response, 200, applyFilters(url.searchParams));
		}

		if (request.method === 'POST' && url.pathname === '/api/v1/inventory/sku') {
			const body = await readBody(request);
			const existing = inventory.find((item) => item.sku_id === body.sku_id);
			const timestamp = new Date().toISOString();
			const record = {
				sku_id: body.sku_id,
				description: body.description,
				ncm_code: body.ncm_code,
				origin_state: body.origin_state,
				destination_state: body.destination_state,
				legacy_taxes: body.legacy_taxes ?? {},
				reform_taxes: {},
				is_active: true,
				updated_at: timestamp
			};

			if (existing) {
				Object.assign(existing, record);
			} else {
				inventory.unshift(record);
			}

			return sendJson(response, 200, {
				sku_id: body.sku_id,
				status: existing ? 'updated' : 'created'
			});
		}

		if (request.method === 'GET' && url.pathname.startsWith('/api/v1/inventory/sku/')) {
			const skuId = getSkuIdFromPath(url.pathname);
			if (!skuId) {
				return sendJson(response, 400, {
					error_code: 'BAD_REQUEST',
					message: 'SKU path segment is required',
					request_id: 'mock-req'
				});
			}
			const item = inventory.find((entry) => entry.sku_id === skuId);
			if (!item) {
				return sendJson(response, 404, {
					error_code: 'SKU_NOT_FOUND',
					message: `SKU ${skuId} not found`,
					request_id: 'mock-req'
				});
			}
			return sendJson(response, 200, item);
		}

		if (request.method === 'PUT' && url.pathname.startsWith('/api/v1/inventory/sku/')) {
			const skuId = getSkuIdFromPath(url.pathname);
			if (!skuId) {
				return sendJson(response, 400, {
					error_code: 'BAD_REQUEST',
					message: 'SKU path segment is required',
					request_id: 'mock-req'
				});
			}
			const item = inventory.find((entry) => entry.sku_id === skuId);
			if (!item) {
				return sendJson(response, 404, {
					error_code: 'SKU_NOT_FOUND',
					message: `SKU ${skuId} not found`,
					request_id: 'mock-req'
				});
			}

			const body = await readBody(request);
			if (body.description === 'Trigger backend error') {
				return sendJson(response, 500, {
					error_code: 'INTERNAL_SERVER_ERROR',
					message: 'Mock backend failed to persist inventory changes',
					request_id: 'mock-req'
				});
			}

			Object.assign(item, {
				description: body.description,
				ncm_code: body.ncm_code,
				origin_state: body.origin_state,
				destination_state: body.destination_state,
				legacy_taxes: body.legacy_taxes ?? {},
				updated_at: new Date().toISOString()
			});

			return sendJson(response, 200, { sku_id: skuId, status: 'updated' });
		}

		sendJson(response, 404, {
			error_code: 'NOT_FOUND',
			message: 'Mock endpoint not found',
			request_id: 'mock-req'
		});
	} catch (error) {
		if (error instanceof SyntaxError && error.message === 'Invalid JSON body') {
			return sendJson(response, 400, {
				error_code: 'BAD_REQUEST',
				message: 'Invalid JSON body',
				request_id: 'mock-req'
			});
		}

		return sendJson(response, 500, {
			error_code: 'INTERNAL_SERVER_ERROR',
			message: 'Mock API server failed unexpectedly',
			request_id: 'mock-req'
		});
	}
});

server.listen(PORT, '127.0.0.1');
