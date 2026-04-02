// @vitest-environment jsdom

import { render, screen } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import StatStrip from '$lib/components/StatStrip.svelte';

describe('StatStrip', () => {
	it('caps the visible stats at three items', () => {
		const { container } = render(StatStrip, {
			props: {
				items: [
					{ label: 'One', value: '1', detail: 'First' },
					{ label: 'Two', value: '2', detail: 'Second' },
					{ label: 'Three', value: '3', detail: 'Third' },
					{ label: 'Four', value: '4', detail: 'Fourth' }
				]
			}
		});

		expect(container.querySelectorAll('.stat-strip__cell')).toHaveLength(3);
		expect(screen.queryByText('Four')).toBeNull();
	});
});
