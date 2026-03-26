import { defineConfig } from '@playwright/test';

export default defineConfig({
	testDir: './e2e',
	testMatch: '**/*.e2e.{ts,js}',
	use: {
		baseURL: 'http://127.0.0.1:4173'
	},
	webServer: [
		{
			command: 'node e2e/mock-api-server.mjs',
			port: 5050,
			reuseExistingServer: !process.env.CI
		},
		{
			command:
				'API_BASE_URL=http://127.0.0.1:5050 APP_SESSION_SECRET=test-session-secret bun run build && API_BASE_URL=http://127.0.0.1:5050 APP_SESSION_SECRET=test-session-secret bun run preview',
			port: 4173,
			reuseExistingServer: !process.env.CI
		}
	]
});
