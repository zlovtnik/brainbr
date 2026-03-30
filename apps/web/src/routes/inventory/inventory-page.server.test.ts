import { describe, expect, it, vi } from 'vitest';

const { createApiClientFromEvent, ApiClientError } = vi.hoisted(() => {
	class MockApiClientError extends Error {
		status: number;

		constructor(status: number, message: string) {
			super(message);
			this.status = status;
		}
	}

	return {
		createApiClientFromEvent: vi.fn(),
		ApiClientError: MockApiClientError
	};
});

const { describeProtectedApiError, requireSession } = vi.hoisted(() => ({
	describeProtectedApiError: vi.fn(() => 'Missing inventory scope.'),
	requireSession: vi.fn()
}));

vi.mock('$lib/server/api/client', () => ({
	createApiClientFromEvent,
	ApiClientError
}));

vi.mock('$lib/server/auth', () => ({
	describeProtectedApiError,
	requireSession
}));

import { load } from './+page.server';

describe('inventory page server load', () => {
	it('throws an HTTP error when the inventory request fails', async () => {
		createApiClientFromEvent.mockReturnValue({
			listInventory: vi.fn().mockRejectedValue(new ApiClientError(403, 'Forbidden'))
		});

		await expect(
			load({
				url: new URL('https://brainbr.local/inventory?page=1'),
				locals: {
					session: {
						scopes: ['inventory:read']
					}
				}
			} as never)
		).rejects.toMatchObject({
			status: 403,
			body: {
				message: 'Missing inventory scope.'
			}
		});

		expect(requireSession).toHaveBeenCalled();
		expect(describeProtectedApiError).toHaveBeenCalled();
	});
});
