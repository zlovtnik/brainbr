// @vitest-environment jsdom

import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import CapabilityWorkspace from '$lib/components/CapabilityWorkspace.svelte';
import { getCapability } from '$lib/capabilities';

describe('CapabilityWorkspace', () => {
	it('promotes the primary workflow CTA into the header and limits the stat strip', () => {
		const { container } = render(CapabilityWorkspace, {
			props: {
				capability: getCapability('inventory'),
				session: {
					authenticated: true,
					scopes: ['inventory:read', 'inventory:write']
				}
			}
		});

		expect(container.querySelector('.workspace-header a')?.textContent).toContain('Open inventory');
		expect(container.querySelectorAll('.stat-strip__cell')).toHaveLength(3);
	});

	it('keeps reference payloads collapsed by default', () => {
		const { container } = render(CapabilityWorkspace, {
			props: {
				capability: getCapability('platform'),
				liveMetrics: [
					{ label: 'Service', value: 'fiscalbrain-br', detail: 'Live response.' },
					{ label: 'Embedding model', value: 'text-embedding-3-small', detail: 'Live response.' },
					{ label: 'LLM model', value: 'gpt-4o', detail: 'Live response.' }
				]
			}
		});

		expect(screen.getByText('Reference payloads')).toBeTruthy();
		const referencePayloadsDetails = screen.getByText('Reference payloads').closest('details');
		expect(referencePayloadsDetails?.hasAttribute('open')).toBe(false);
	});
});
