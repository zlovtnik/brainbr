// @vitest-environment jsdom

import { render } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import type { PageProps as EditPageProps } from './[skuId]/edit/$types';
import type { PageProps as NewPageProps } from './new/$types';
import InventoryEditPage from './[skuId]/edit/+page.svelte';
import InventoryNewPage from './new/+page.svelte';

describe('inventory form pages', () => {
	it('uses an explicit default action on the create page form', () => {
		const { container } = render(InventoryNewPage, {
			props: {
				data: {
					session: {
						authenticated: true as const,
						user: 'inventory-writer',
						tenantId: 'tenant-001',
						scopes: ['inventory:read', 'inventory:write']
					},
					initialValues: {
						skuId: '',
						description: '',
						ncmCode: '',
						originState: '',
						destinationState: '',
						legacyTaxes: {
							icms: '',
							pis: '',
							cofins: '',
							iss: ''
						}
					}
				} satisfies NewPageProps['data'],
				form: null,
				params: {}
			}
		});

		expect(container.querySelector('form')?.getAttribute('action')).toBe('?/default');
	});

	it('uses an explicit default action on the edit page form', () => {
		const { container } = render(InventoryEditPage, {
			props: {
				data: {
					session: {
						authenticated: true as const,
						user: 'inventory-writer',
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
						reformTaxes: {},
						isActive: true,
						updatedAt: '2026-03-25T15:10:00Z'
					},
					initialValues: {
						skuId: 'SKU-001',
						description: 'Cerveja Pilsen',
						ncmCode: '22030000',
						originState: 'SP',
						destinationState: 'RJ',
						legacyTaxes: {
							icms: '18',
							pis: '',
							cofins: '',
							iss: ''
						}
					}
				} satisfies EditPageProps['data'],
				form: null,
				params: {
					skuId: 'SKU-001'
				}
			}
		});

		expect(container.querySelector('form')?.getAttribute('action')).toBe('?/default');
	});
});
