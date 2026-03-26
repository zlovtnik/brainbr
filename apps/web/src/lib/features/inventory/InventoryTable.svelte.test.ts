// @vitest-environment jsdom

import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import InventoryTable from '$lib/features/inventory/InventoryTable.svelte';

const inventory = {
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
};

describe('InventoryTable', () => {
	it('renders inventory rows', () => {
		render(InventoryTable, {
			props: {
				inventory
			}
		});

		expect(screen.getByText('SKU-001')).toBeTruthy();
		expect(screen.getByText('Cerveja Pilsen')).toBeTruthy();
		expect(screen.getByText('Active')).toBeTruthy();
	});

	it('renders empty and error states', () => {
		const { rerender } = render(InventoryTable, {
			props: {
				inventory: { ...inventory, items: [], totalCount: 0 }
			}
		});

		expect(screen.getByText('No inventory matched')).toBeTruthy();

		rerender({
			inventory: null,
			loadError: 'Backend unavailable'
		});

		expect(screen.getByText('Unable to load inventory')).toBeTruthy();
		expect(screen.getByText('Backend unavailable')).toBeTruthy();
	});
});
