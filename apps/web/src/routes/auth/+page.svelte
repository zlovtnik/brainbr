<script lang="ts">
	import { tick } from 'svelte';
	import Button from '$lib/components/Button.svelte';
	import InlineNotice from '$lib/components/InlineNotice.svelte';
	import SectionPanel from '$lib/components/SectionPanel.svelte';
	import StatStrip from '$lib/components/StatStrip.svelte';
	import WorkspaceHeader from '$lib/components/WorkspaceHeader.svelte';
	import type { PageProps } from './$types';

	let { data, form }: PageProps = $props();
	let usernameField = $state<HTMLInputElement | undefined>();
	let tokenField = $state<HTMLTextAreaElement | undefined>();
	let statItems = [
		{
			label: 'Boundary',
			value: 'Server only',
			detail: 'The browser never sends tokens directly to the Spring API.',
			tone: 'success' as const
		},
		{
			label: 'Session',
			value: 'Signed cookie',
			detail: 'Authentication state is stored in an HTTP-only cookie.',
			tone: 'accent' as const
		},
		{
			label: 'Coverage',
			value: 'Tenant scopes',
			detail: 'Inventory, audit, compliance, split payment, and ingestion can be bootstrapped here.',
			tone: 'default' as const
		}
	];

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
	<WorkspaceHeader
		tag={['AUTH', '/auth']}
		title="Sign in to BrainBR"
		description="Start with username and password for the standard path. Use the demo account for fast smoke tests, or open the advanced token flow when you need tenant-specific JWT scopes."
		statusLabel="Server-side session bootstrap"
		statusTone="success"
	/>

	<StatStrip items={statItems} />

	<div class="auth-panels">
		<SectionPanel
			title="Recommended sign in"
			subtitle="Use the standard credential flow for the clearest, lowest-friction path into the app."
		>
			{#snippet meta()}
				<span class="auth-badge">Recommended</span>
			{/snippet}

			{#snippet children()}
				<form class="stack" method="POST" action="?/password">
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
						These development credentials are temporary and should not survive beyond the bootstrap
						phase.
					</p>

					<div class="auth-actions">
						<Button type="submit">
							{#snippet children()}Sign in with credentials{/snippet}
						</Button>
					</div>
				</form>
			{/snippet}
		</SectionPanel>

		<SectionPanel
			title="Demo sign in"
			subtitle="Use the demo account when you want to move quickly through smoke tests or walkthroughs."
		>
			{#snippet children()}
				<form class="stack" method="POST" action="?/quick">
					{#if form?.quickError}
						<InlineNotice
							id="quick-error"
							message={form.quickError}
							title="Quick login unavailable"
							variant="error"
						/>
					{/if}

					<input name="redirectTo" type="hidden" value={form?.redirectTo ?? data.redirectTo} />

					<p class="auth-copy">
						The demo path is useful for server-side verification, inventory walkthroughs, and broad
						regression checks.
					</p>

					<div class="auth-actions">
						<Button type="submit" variant="secondary">
							{#snippet children()}Use demo account{/snippet}
						</Button>
					</div>
				</form>
			{/snippet}
		</SectionPanel>
	</div>

	<SectionPanel
		title="Advanced: use token"
		subtitle="Open this only when you need to paste a JWT with tenant-specific scopes."
		collapsible={true}
		defaultOpen={false}
	>
		{#snippet children()}
			<form class="stack" method="POST" action="?/token">
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
			</form>
		{/snippet}
	</SectionPanel>
</section>

<style>
	.auth-page {
		display: grid;
		gap: 0;
	}

	.auth-panels {
		display: grid;
		grid-template-columns: minmax(0, 1.3fr) minmax(0, 0.9fr);
	}

	.auth-panels :global(.section-panel:first-child) {
		border-right: 1px solid var(--border);
	}

	.auth-page > :global(.section-panel:last-child) {
		border-top: 1px solid var(--border);
	}

	.auth-badge {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-height: 2rem;
		padding: 0.18rem 0.55rem;
		border-radius: var(--radius-sm);
		border: 1px solid var(--success-border);
		background: var(--success-soft);
		color: var(--success);
		font-size: 0.72rem;
		font-family: var(--font-mono);
		letter-spacing: 0.04em;
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

	.auth-field input,
	.auth-field textarea {
		min-height: 3rem;
		padding: 0.95rem 1rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		background: var(--bg-3);
		background-color: var(--bg-3) !important;
		color: var(--text);
		caret-color: var(--text);
	}

	.auth-field textarea {
		min-height: 15rem;
		font-family: 'SFMono-Regular', 'SF Mono', 'Consolas', monospace;
		font-size: 0.92rem;
		line-height: 1.55;
		resize: vertical;
	}

	.auth-field__hint,
	.auth-copy {
		margin: 0;
		color: var(--text-faint);
		font-size: 0.84rem;
		line-height: 1.5;
	}

	.auth-actions {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-3);
	}

	@media (max-width: 980px) {
		.auth-panels {
			grid-template-columns: 1fr;
		}

		.auth-panels :global(.section-panel:first-child) {
			border-right: 0;
			border-bottom: 1px solid var(--border);
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
