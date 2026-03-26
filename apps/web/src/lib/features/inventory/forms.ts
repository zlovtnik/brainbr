import type {
	InventoryFormErrors,
	InventoryFormValues,
	InventoryLegacyTaxFields,
	InventoryWriteTransport
} from '$lib/features/inventory/types';

type InventoryFormMode = 'create' | 'edit';

export interface InventoryFormResult {
	success: boolean;
	values: InventoryFormValues;
	errors: InventoryFormErrors;
	data?: InventoryWriteTransport;
}

function getString(formData: FormData, key: string): string {
	return formData.get(key)?.toString().trim() ?? '';
}

function parseRate(
	values: InventoryLegacyTaxFields,
	errors: InventoryFormErrors,
	key: keyof InventoryLegacyTaxFields
): number | undefined {
	const raw = values[key];
	if (!raw) {
		return undefined;
	}

	const parsed = Number(raw);
	if (!Number.isFinite(parsed) || parsed < 0) {
		errors.legacyTaxes = { ...errors.legacyTaxes, [key]: 'Enter a valid non-negative rate.' };
		return undefined;
	}

	return parsed;
}

export function parseInventoryForm(formData: FormData, mode: InventoryFormMode): InventoryFormResult {
	const values: InventoryFormValues = {
		skuId: getString(formData, 'skuId'),
		description: getString(formData, 'description'),
		ncmCode: getString(formData, 'ncmCode'),
		originState: getString(formData, 'originState').toUpperCase(),
		destinationState: getString(formData, 'destinationState').toUpperCase(),
		legacyTaxes: {
			icms: getString(formData, 'legacyTax.icms'),
			pis: getString(formData, 'legacyTax.pis'),
			cofins: getString(formData, 'legacyTax.cofins'),
			iss: getString(formData, 'legacyTax.iss')
		}
	};

	const errors: InventoryFormErrors = {};

	if (mode === 'create' && !values.skuId) {
		errors.skuId = 'SKU ID is required.';
	}
	if (values.skuId && values.skuId.length > 50) {
		errors.skuId = 'SKU ID must be 50 characters or fewer.';
	}
	if (!values.description) {
		errors.description = 'Description is required.';
	}
	if (!values.ncmCode) {
		errors.ncmCode = 'NCM code is required.';
	} else if (!/^\d{8}$/.test(values.ncmCode)) {
		errors.ncmCode = 'NCM code must contain 8 digits.';
	}
	if (!/^[A-Z]{2}$/.test(values.originState)) {
		errors.originState = 'Origin state must be a 2-letter code.';
	}
	if (!/^[A-Z]{2}$/.test(values.destinationState)) {
		errors.destinationState = 'Destination state must be a 2-letter code.';
	}

	const legacyTaxes: Record<string, number> = {};
	for (const key of Object.keys(values.legacyTaxes) as Array<keyof InventoryLegacyTaxFields>) {
		const parsed = parseRate(values.legacyTaxes, errors, key);
		if (parsed !== undefined) {
			legacyTaxes[key] = parsed;
		}
	}

	if (Object.keys(errors).length > 0) {
		return { success: false, values, errors };
	}

	return {
		success: true,
		values,
		errors,
		data: {
			sku_id: values.skuId,
			description: values.description,
			ncm_code: values.ncmCode,
			origin_state: values.originState,
			destination_state: values.destinationState,
			legacy_taxes: legacyTaxes
		}
	};
}
