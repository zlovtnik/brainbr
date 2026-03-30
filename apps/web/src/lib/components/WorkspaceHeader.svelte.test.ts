// @vitest-environment jsdom

import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import WorkspaceHeader from '$lib/components/WorkspaceHeader.svelte';

describe('WorkspaceHeader', () => {
	it('renders with and without a primary action', async () => {
		const { rerender } = render(WorkspaceHeader, {
			props: {
				tag: ['GET', '/inventory'],
				title: 'Inventory',
				description: 'Search inventory records.',
				statusLabel: 'Ready',
				statusTone: 'success',
				primaryAction: {
					label: 'Create SKU',
					href: '/inventory/new'
				}
			}
		});

		expect(screen.getByRole('heading', { name: 'Inventory' })).toBeTruthy();
		expect(screen.getByRole('link', { name: 'Create SKU' }).getAttribute('href')).toBe(
			'/inventory/new'
		);

		await rerender({
			tag: ['GET', '/inventory'],
			title: 'Inventory',
			description: 'Search inventory records.',
			statusLabel: 'Ready',
			statusTone: 'success',
			primaryAction: undefined
		});

		expect(screen.queryByRole('link', { name: 'Create SKU' })).toBeNull();
	});
});
