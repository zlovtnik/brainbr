// @vitest-environment jsdom

import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import InlineNotice from '$lib/components/InlineNotice.svelte';

describe('InlineNotice', () => {
	it('announces errors without adding a heading to the document outline', () => {
		const { container } = render(InlineNotice, {
			props: {
				title: 'Unable to save',
				message: 'Check the highlighted fields.',
				variant: 'error'
			}
		});

		expect(screen.getByRole('alert')).toBeTruthy();
		expect(screen.getByText('Unable to save')).toBeTruthy();
		expect(container.querySelector('h2')).toBeNull();
	});
});
