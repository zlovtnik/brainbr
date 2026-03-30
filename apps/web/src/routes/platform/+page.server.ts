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
		let response: Response;
		try {
			response = await fetch(`${apiBaseUrl}/api/v1/platform/info`, {
				headers: {
					accept: 'application/json'
				}
			});
		} catch {
			return {
				platformInfo: null,
				platformError: 'Unable to reach the backend platform info endpoint.'
			};
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
			return {
				platformInfo: null,
				platformError: `Platform info response could not be parsed as JSON: ${error instanceof Error ? error.message : 'Unknown parse failure'}.`
			};
		}
	} catch {
		return {
			platformInfo: null,
			platformError: 'Unable to reach the backend platform info endpoint.'
		};
	}
};
