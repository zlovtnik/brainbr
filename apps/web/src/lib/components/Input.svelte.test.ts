// @vitest-environment jsdom

import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import Input from '$lib/components/Input.svelte';

describe('Input', () => {
	it('renders label, hint, and error text', () => {
		render(Input, {
			props: {
				id: 'description',
				name: 'description',
				label: 'Description',
				value: 'Sparkling water',
				hint: 'Visible to analysts.',
				error: 'Description is required.'
			}
		});

		const input = screen.getByRole('textbox');
		expect(input).toBeTruthy();
		expect((input as HTMLInputElement).value).toBe('Sparkling water');
		expect(screen.getByText('Visible to analysts.')).toBeTruthy();
		expect(screen.getByText('Description is required.')).toBeTruthy();
	});
});
