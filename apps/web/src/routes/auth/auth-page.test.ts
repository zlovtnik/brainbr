// @vitest-environment jsdom

import { render, screen, waitFor } from '@testing-library/svelte';
import { describe, expect, it } from 'vitest';
import AuthPage from './+page.svelte';
import type { PageProps } from './$types';

const data = {
	session: null,
	redirectTo: '/platform'
} satisfies PageProps['data'];

describe('auth page', () => {
	it('renders the JWT sign-in form', () => {
		render(AuthPage, { props: { data, form: null, params: {} } });

		expect(screen.getByLabelText('Bearer JWT')).toBeTruthy();
		expect(screen.getByRole('button', { name: 'Sign in' })).toBeTruthy();
	});

	it('shows an error notice and focuses the token field on failure', async () => {
		render(AuthPage, {
			props: {
				data,
				params: {},
				form: { error: 'JWT is missing required scope: inventory:read.', redirectTo: '/platform' }
			}
		});

		expect(screen.getByText('JWT is missing required scope: inventory:read.')).toBeTruthy();

		await waitFor(() => {
			expect(document.activeElement?.id).toBe('token');
		});
	});
});
