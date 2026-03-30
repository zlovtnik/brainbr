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

	it('renders collapsible panels open when defaultOpen is true', () => {
		const { container } = render(SectionPanelHost, {
			props: {
				collapsible: true,
				defaultOpen: true
			}
		});

		expect(screen.getByText('Panel title')).toBeTruthy();
		expect(container.querySelector('details')?.hasAttribute('open')).toBe(true);
	});

	it('renders non-collapsible panels without details element', () => {
		const { container } = render(SectionPanelHost, {
			props: {
				collapsible: false
			}
		});

		expect(screen.getByText('Panel title')).toBeTruthy();
		expect(screen.getByText('Panel body')).toBeTruthy();
		expect(container.querySelector('details')).toBeNull();
	});

	it('renders subtitle when provided', () => {
		render(SectionPanelHost, {
			props: {
				subtitle: 'This is a subtitle'
			}
		});

		expect(screen.getByText('This is a subtitle')).toBeTruthy();
	});

	it('renders meta snippet when provided', () => {
		render(SectionPanelHost, {
			props: {
				meta: 'Meta content'
			}
		});

		expect(screen.getByText('Meta content')).toBeTruthy();
	});
});
