// See https://svelte.dev/docs/kit/types#app.d.ts
// for information about these interfaces
declare global {
	namespace App {
		interface Locals {
			session: import('$lib/server/session').AuthSession | null;
		}
		// interface PageState {}
		// interface Platform {}
	}
}

export {};
