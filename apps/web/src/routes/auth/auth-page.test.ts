// @vitest-environment jsdom

import { render, screen, waitFor } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import AuthPage from './+page.svelte';
import type { PageProps } from './$types';

const data = {
	session: null,
	redirectTo: '/platform',
	hardcodedUsername: 'demo-user'
} satisfies PageProps['data'];

describe('auth page', () => {
	it('defaults the token flow to a collapsed advanced section', () => {
		const { container } = render(AuthPage, {
			props: {
				data,
				form: null,
				params: {}
			}
		});

		expect(screen.getByText('Recommended')).toBeTruthy();
		expect(screen.getByRole('button', { name: 'Sign in with credentials' })).toBeTruthy();
		expect(container.querySelector('details')?.hasAttribute('open')).toBe(false);
	});

	it('keeps focus on the username field after a login error', async () => {
		render(AuthPage, {
			props: {
				data,
				params: {},
				form: {
					loginError: 'Invalid credentials.',
					redirectTo: '/platform',
					username: 'demo-user'
				}
			}
		});

		await waitFor(() => {
			expect(document.activeElement?.id).toBe('username');
		});
	});
});
