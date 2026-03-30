const path = require('node:path');
const { defineConfig } = require('@playwright/test');

module.exports = defineConfig({
	testDir: path.join(__dirname, 'e2e'),
	testMatch: /.*\.e2e\.(ts|js)$/,
	fullyParallel: true,
	retries: process.env.CI ? 1 : 0,
	workers: process.env.CI ? 1 : undefined,
	reporter: process.env.CI ? [['line']] : [['list']],
	use: {
		baseURL: 'http://127.0.0.1:4173',
		trace: 'retain-on-failure'
	},
	webServer: [
		{
			command: 'bun e2e/mock-api-server.mjs',
			port: 5050,
			reuseExistingServer: !process.env.CI
		},
		{
			command: 'bun run build && bun run preview',
			env: {
				API_BASE_URL: 'http://127.0.0.1:5050',
				APP_SESSION_SECRET: 'test-session-secret-with-extra-entropy-1234567890'
			},
			port: 4173,
			timeout: 120000,
			reuseExistingServer: !process.env.CI
		}
	]
});
