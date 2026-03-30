// @vitest-environment jsdom

import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import SectionPanelHost from '$lib/components/test-fixtures/SectionPanelHost.svelte';

describe('SectionPanel', () => {
	it('renders collapsible panels closed by default', () => {
		const { container } = render(SectionPanelHost, {
			props: {
				collapsible: true,
				defaultOpen: false
			}
		});

		expect(screen.getByText('Panel title')).toBeTruthy();
		expect(screen.getByText('Panel body')).toBeTruthy();
		expect(container.querySelector('details')?.hasAttribute('open')).toBe(false);
	});
});
