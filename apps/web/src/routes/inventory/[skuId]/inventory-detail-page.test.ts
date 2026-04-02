// @vitest-environment jsdom

import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import type { PageProps } from './$types';
import InventoryDetailPage from './+page.svelte';

const data = {
	session: {
		authenticated: true as const,
		user: 'inventory-reader',
		tenantId: 'tenant-001',
		scopes: ['inventory:read', 'inventory:write']
	},
	item: {
		skuId: 'SKU-001',
		description: 'Cerveja Pilsen',
		ncmCode: '22030000',
		originState: 'SP',
		destinationState: 'RJ',
		legacyTaxes: { icms: 18 },
		reformTaxes: { cbs: 12.4 },
		isActive: true,
		updatedAt: '2026-03-25T15:10:00Z'
	},
	successMessage: 'SKU updated successfully.'
} satisfies PageProps['data'];

describe('inventory detail page', () => {
	it('renders a back link and business-facing field descriptions', () => {
		render(InventoryDetailPage, {
			props: {
				data,
				form: null,
				params: {
					skuId: 'SKU-001'
				}
			}
		});

		expect(screen.getByRole('link', { name: '← Back to inventory' }).getAttribute('href')).toBe(
			'/inventory'
		);
		expect(screen.getByText('Fiscal classification code.')).toBeTruthy();
		expect(screen.getByText('Interstate tax route.')).toBeTruthy();
	});
});
