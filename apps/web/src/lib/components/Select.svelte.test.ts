// @vitest-environment jsdom

import { fireEvent, render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import SelectHost from '$lib/components/test-fixtures/SelectHost.svelte';

describe('Select', () => {
	it('supports bind:value for user and programmatic updates', async () => {
		render(SelectHost);

		const select = screen.getByLabelText('Sort field') as HTMLSelectElement;
		expect(select).toHaveValue('updated_at');
		expect(screen.getByText('Current: updated_at')).toBeInTheDocument();

		await fireEvent.change(select, { target: { value: 'sku_id' } });
		expect(select).toHaveValue('sku_id');
		expect(screen.getByText('Current: sku_id')).toBeInTheDocument();

		await fireEvent.change(select, { target: { value: 'updated_at' } });
		expect(select).toHaveValue('updated_at');

		await fireEvent.click(screen.getByRole('button', { name: 'Switch' }));
		expect(select).toHaveValue('sku_id');
		expect(screen.getByText('Current: sku_id')).toBeInTheDocument();
	});
});
