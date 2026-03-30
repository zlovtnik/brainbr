<script lang="ts">
	import { tick } from 'svelte';
	import Button from '$lib/components/Button.svelte';
	import Card from '$lib/components/Card.svelte';
	import InlineNotice from '$lib/components/InlineNotice.svelte';
	import type { PageProps } from './$types';

	let { data, form }: PageProps = $props();
	let usernameField = $state<HTMLInputElement | undefined>();
	let tokenField = $state<HTMLTextAreaElement | undefined>();

	$effect(() => {
		if (form?.loginError) {
			void tick().then(() => usernameField?.focus());
			return;
		}
		if (form?.error) {
			void tick().then(() => tokenField?.focus());
		}
	});
</script>

<section class="auth-page">
	<div class="auth-page__intro stack">
		<p class="eyebrow">Bootstrap auth</p>
		<h1 class="headline">Launch a BrainBR session without looking like a temporary admin hack.</h1>
		<p class="lede">
			This bootstrap flow stores auth in an HTTP-only cookie so the browser never speaks to the
			Spring API directly. Use credentials for speed, or paste a JWT when you need tenant-specific
			scopes for inventory, audit, compliance, split payment, and ingestion immediately.
		</p>

		<div class="auth-signals">
			<div>
				<span class="auth-signals__label">Boundary</span>
				<p>Server-only API calls</p>
			</div>
			<div>
				<span class="auth-signals__label">Cookie</span>
				<p>Signed and HTTP-only</p>
			</div>
			<div>
				<span class="auth-signals__label">Scopes</span>
				<p>Inventory, audit, compliance, split payment, ingestion</p>
			</div>
		</div>
	</div>

	<div class="auth-panels">
		<form class="stack" method="POST" action="?/password">
			<Card>
				{#snippet header()}
					<div class="stack">
						<h2>Username and password</h2>
						<p class="lede">Use the temporary frontend credentials for the fastest way in.</p>
					</div>
				{/snippet}

				{#if form?.loginError}
					<InlineNotice
						id="login-error"
						message={form.loginError}
						title="Login failed"
						variant="error"
					/>
				{/if}

				<input name="redirectTo" type="hidden" value={form?.redirectTo ?? data.redirectTo} />
				<div class="auth-form-grid">
					<label class="auth-field" for="username">
						<span class="auth-field__label">Username</span>
						<input
							bind:this={usernameField}
							aria-describedby={form?.loginError
								? 'login-error login-credentials'
								: 'login-credentials'}
							autocomplete="username"
							id="username"
							name="username"
							required
							value={form?.username ?? data.hardcodedUsername}
						/>
					</label>
					<label class="auth-field" for="password">
						<span class="auth-field__label">Password</span>
						<input
							autocomplete="current-password"
							id="password"
							name="password"
							required
							type="password"
						/>
					</label>
				</div>
				<p class="auth-field__hint" id="login-credentials">
					These credentials are hardcoded for development only and should not survive beyond this
					bootstrap phase.
				</p>

				<div class="auth-actions">
					<Button type="submit">
						{#snippet children()}Login with credentials{/snippet}
					</Button>
				</div>
			</Card>
		</form>

		<form class="stack" method="POST" action="?/quick">
			<Card>
				{#snippet header()}
					<div class="stack">
						<h2>Demo shortcut</h2>
						<p class="lede">Skip typing and bootstrap the session with the demo account.</p>
					</div>
				{/snippet}

				{#if form?.quickError}
					<InlineNotice
						id="quick-error"
						message={form.quickError}
						title="Quick login unavailable"
						variant="error"
					/>
				{/if}

				<input name="redirectTo" type="hidden" value={form?.redirectTo ?? data.redirectTo} />

				<div class="auth-shortcut">
					<p>Useful for smoke tests, inventory walkthroughs, and quick server-side verification.</p>
					<Button type="submit" variant="ghost">
						{#snippet children()}Auto login with demo account{/snippet}
					</Button>
				</div>
			</Card>
		</form>

		<form class="stack auth-token-form" method="POST" action="?/token">
			<Card>
				{#snippet header()}
					<div class="stack">
						<h2>Direct session token</h2>
						<p class="lede">
							Paste a JWT carrying `tenant_id` plus the `inventory:read` and `inventory:write`
							scopes.
						</p>
					</div>
				{/snippet}

				{#if form?.error}
					<InlineNotice
						id="token-error"
						message={form.error}
						title="Invalid bootstrap token"
						variant="error"
					/>
				{/if}

				<input name="redirectTo" type="hidden" value={form?.redirectTo ?? data.redirectTo} />
				<label class="auth-field" for="token">
					<span class="auth-field__label">Bearer JWT</span>
					<textarea
						bind:this={tokenField}
						aria-describedby={form?.error ? 'token-help token-error' : 'token-help'}
						aria-invalid={Boolean(form?.error)}
						id="token"
						name="token"
						placeholder="eyJhbGciOi..."
						required
						rows="10"
						spellcheck="false">{form?.token ?? ''}</textarea
					>
					<span class="auth-field__hint" id="token-help">
						The token never lands in browser storage. It is exchanged for a signed cookie on the
						server.
					</span>
				</label>

				<div class="auth-actions">
					<Button type="submit">
						{#snippet children()}Start authenticated session{/snippet}
					</Button>
				</div>
			</Card>
		</form>
	</div>
</section>

<style>
	h2 {
		margin: 0;
		font-size: 1.65rem;
		letter-spacing: -0.03em;
	}

	.auth-page {
		display: grid;
		gap: var(--space-5);
	}

	.auth-page__intro {
		padding: 1.5rem;
		border-radius: var(--radius-md);
		background: var(--bg-2);
		border: 1px solid var(--border);
	}

	.auth-signals {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
		gap: var(--space-3);
	}

	.auth-signals div {
		padding: 1rem;
		border-radius: var(--radius-sm);
		background: var(--bg-3);
		border: 1px solid var(--border);
	}

	.auth-signals p {
		margin: 0.35rem 0 0;
		font-weight: 500;
		color: var(--text);
	}

	.auth-signals__label {
		font-size: 0.72rem;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		font-family: var(--font-mono);
		color: var(--text-faint);
	}

	.auth-panels {
		display: grid;
		gap: var(--space-4);
	}

	.auth-form-grid {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
		gap: var(--space-4);
	}

	.auth-field {
		display: grid;
		gap: var(--space-2);
	}

	.auth-field__label {
		font-weight: 500;
		color: var(--text);
	}

	.auth-field__hint {
		color: var(--text-faint);
		font-size: 0.84rem;
	}

	.auth-actions {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-3);
	}

	.auth-shortcut {
		display: grid;
		gap: var(--space-4);
		padding: 1rem;
		border-radius: var(--radius-sm);
		background: var(--bg-3);
		border: 1px solid var(--border);
	}

	.auth-shortcut p {
		margin: 0;
		color: var(--text-muted);
	}

	.auth-token-form textarea {
		min-height: 15rem;
		padding: 1rem;
		font-family: 'SFMono-Regular', 'SF Mono', 'Consolas', monospace;
		font-size: 0.92rem;
		line-height: 1.55;
		resize: vertical;
	}

	@media (min-width: 980px) {
		.auth-page {
			grid-template-columns: minmax(0, 1.05fr) minmax(0, 1.15fr);
			align-items: start;
		}

		.auth-page__intro {
			position: sticky;
			top: var(--space-5);
		}

		.auth-panels {
			grid-template-columns: repeat(2, minmax(0, 1fr));
		}

		.auth-token-form {
			grid-column: 1 / -1;
		}
	}

	@media (max-width: 760px) {
		.auth-form-grid {
			grid-template-columns: 1fr;
		}

		.auth-actions > :global(.button) {
			width: 100%;
		}
	}
</style>
