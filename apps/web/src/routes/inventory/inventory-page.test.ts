// @vitest-environment jsdom

import { render, screen } from '@testing-library/svelte';
import { describe, expect, it, vi } from 'vitest';
import type { PageProps } from './$types';

vi.mock('$app/state', () => ({
	navigating: {
		to: null
	}
}));

import InventoryPage from './+page.svelte';

const data = {
	session: {
		authenticated: true as const,
		user: 'inventory-reader',
		tenantId: 'tenant-001',
		scopes: ['inventory:read', 'inventory:write']
	},
	inventory: {
		items: [
			{
				skuId: 'SKU-001',
				description: 'Cerveja Pilsen',
				ncmCode: '22030000',
				originState: 'SP',
				destinationState: 'RJ',
				legacyTaxes: { icms: 18 },
				reformTaxes: {},
				isActive: true,
				updatedAt: '2026-03-25T15:10:00Z'
			}
		],
		totalCount: 1,
		page: 1,
		limit: 10,
		hasMore: false
	},
	filters: {
		page: 1,
		limit: 10,
		sortBy: 'updated_at',
		sortOrder: 'desc',
		query: '',
		includeInactive: false
	},
	loadError: null,
	successMessage: undefined
} satisfies PageProps['data'];

describe('inventory page', () => {
	it('keeps create sku in the header and search ahead of secondary filters', () => {
		const { container } = render(InventoryPage, {
			props: {
				data,
				form: null,
				params: {}
			}
		});

		expect(container.querySelector('.workspace-header a')?.textContent).toContain('Create SKU');
		expect(container.querySelectorAll('.stat-strip__cell')).toHaveLength(3);

		const search = screen.getByLabelText('Search');
		const sortField = screen.getByLabelText('Sort field');

		expect(Boolean(search.compareDocumentPosition(sortField) & Node.DOCUMENT_POSITION_FOLLOWING)).toBe(
			true
		);
	});
});
