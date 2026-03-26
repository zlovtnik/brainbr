// @vitest-environment jsdom

import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import InventoryEditor from '$lib/features/inventory/InventoryEditor.svelte';

describe('InventoryEditor', () => {
	it('renders current values and form errors', () => {
		render(InventoryEditor, {
			props: {
				title: 'Edit SKU-001',
				description: 'Update an existing SKU.',
				submitLabel: 'Save changes',
				cancelHref: '/inventory/SKU-001',
				isEdit: true,
				formError: 'The backend rejected the update.',
				values: {
					skuId: 'SKU-001',
					description: 'Beer',
					ncmCode: '22030000',
					originState: 'SP',
					destinationState: 'RJ',
					legacyTaxes: {
						icms: '18',
						pis: '',
						cofins: '',
						iss: ''
					}
				},
				errors: {
					description: 'Description is required.'
				}
			}
		});

		expect(screen.getByDisplayValue('SKU-001')).toBeTruthy();
		expect(screen.getByText('The backend rejected the update.')).toBeTruthy();
		expect(screen.getByText('Description is required.')).toBeTruthy();
		expect(screen.getByText('Fix the following fields before continuing.')).toBeTruthy();
		expect(
			screen.getByRole('link', { name: 'Description: Description is required.' })
		).toBeTruthy();
	});
});
