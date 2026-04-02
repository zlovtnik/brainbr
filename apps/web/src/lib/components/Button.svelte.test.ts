// @vitest-environment jsdom

import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import ButtonHost from '$lib/components/test-fixtures/ButtonHost.svelte';

describe('Button', () => {
	it('renders an accessible disabled anchor variant', () => {
		render(ButtonHost);

		const link = screen.getByRole('link', { name: 'Create SKU' });
		const event = new MouseEvent('click', { bubbles: true, cancelable: true });

		expect(link.getAttribute('aria-disabled')).toBe('true');
		expect(link.dispatchEvent(event)).toBe(false);
	});
});
