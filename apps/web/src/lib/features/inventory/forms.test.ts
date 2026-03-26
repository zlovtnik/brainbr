import { describe, expect, it } from 'vitest';
import { parseInventoryForm } from '$lib/features/inventory/forms';

function buildFormData(entries: Record<string, string>) {
	const formData = new FormData();
	for (const [key, value] of Object.entries(entries)) {
		formData.set(key, value);
	}
	return formData;
}

describe('inventory form parser', () => {
	it('parses valid inventory writes', () => {
		const result = parseInventoryForm(
			buildFormData({
				skuId: 'SKU-900',
				description: 'Premium soda',
				ncmCode: '22030000',
				originState: 'sp',
				destinationState: 'rj',
				'legacyTax.icms': '18',
				'legacyTax.pis': '1.65'
			}),
			'create'
		);

		expect(result.success).toBe(true);
		if (!result.success) {
			throw new Error('Expected successful inventory form parsing');
		}
		expect(result.data).toEqual({
			sku_id: 'SKU-900',
			description: 'Premium soda',
			ncm_code: '22030000',
			origin_state: 'SP',
			destination_state: 'RJ',
			legacy_taxes: {
				icms: 18,
				pis: 1.65
			}
		});
	});

	it('returns field errors for invalid input', () => {
		const result = parseInventoryForm(
			buildFormData({
				skuId: '',
				description: '',
				ncmCode: 'abc',
				originState: 'S',
				destinationState: 'RIO',
				'legacyTax.icms': '-1'
			}),
			'create'
		);

		expect(result.success).toBe(false);
		expect(result.errors.skuId).toBeTruthy();
		expect(result.errors.description).toBeTruthy();
		expect(result.errors.ncmCode).toBeTruthy();
		expect(result.errors.originState).toBeTruthy();
		expect(result.errors.destinationState).toBeTruthy();
		expect(result.errors.legacyTaxes?.icms).toBeTruthy();
	});
});
