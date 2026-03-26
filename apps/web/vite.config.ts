import { sveltekit } from '@sveltejs/kit/vite';
import { svelteTesting } from '@testing-library/svelte/vite';
import { defineConfig } from 'vite';

export default defineConfig({
	plugins: [sveltekit(), svelteTesting()],
	test: {
		environment: 'node',
		include: ['src/**/*.{test,spec}.{ts,js}'],
		setupFiles: ['./vitest.setup.ts']
	}
});
