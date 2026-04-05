import { env } from '$env/dynamic/private';
import type { PageServerLoad } from './$types';

interface PlatformInfoResponse {
	service: string;
	embeddingModel: string;
	llmModel: string;
}

function getApiBaseUrl(): string | null {
	const baseUrl = env.API_BASE_URL?.trim() || process.env.API_BASE_URL?.trim();
	return baseUrl ? baseUrl.replace(/\/$/, '') : null;
}

export const load: PageServerLoad = async ({ fetch }) => {
	const apiBaseUrl = getApiBaseUrl();
	if (!apiBaseUrl) {
		return {
			platformInfo: null,
			platformError: 'API_BASE_URL is missing, so the platform probe cannot run.'
		};
	}

	try {
		const controller = new AbortController();
		const timeoutId = setTimeout(() => controller.abort(), 3000);
		let response: Response;
		try {
			response = await fetch(`${apiBaseUrl}/api/v1/platform/info`, {
				headers: {
					accept: 'application/json'
				},
				signal: controller.signal
			});
		} finally {
			clearTimeout(timeoutId);
		}

		if (!response.ok) {
			return {
				platformInfo: null,
				platformError: `Platform info request failed with status ${response.status}.`
			};
		}

		try {
			return {
				platformInfo: (await response.json()) as PlatformInfoResponse,
				platformError: null
			};
		} catch (error) {
			console.error('Platform info JSON parse failure:', error);
			return {
				platformInfo: null,
				platformError: 'Unable to load platform information.'
			};
		}
	} catch (error) {
		console.error('Platform info fetch failure:', error);
		return {
			platformInfo: null,
			platformError: 'Unexpected error loading platform information.'
		};
	}
};
