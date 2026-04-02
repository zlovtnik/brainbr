<script lang="ts">
	import { enhance } from '$app/forms';
	import { tick } from 'svelte';
	import Button from '$lib/components/Button.svelte';
	import InlineNotice from '$lib/components/InlineNotice.svelte';
	import SectionPanel from '$lib/components/SectionPanel.svelte';
	import Spinner from '$lib/components/Spinner.svelte';
	import StatStrip from '$lib/components/StatStrip.svelte';
	import WorkspaceHeader from '$lib/components/WorkspaceHeader.svelte';
	import type { PageProps } from './$types';

	let { data, form }: PageProps = $props();
	let usernameField = $state<HTMLInputElement | undefined>();
	let tokenField = $state<HTMLTextAreaElement | undefined>();
	let quickForm = $state<HTMLFormElement | undefined>();
	let submittingFlow = $state<'password' | 'quick' | 'token' | null>(null);
	let statItems = [
		{
			label: 'Security',
			value: 'Server-side',
			detail: 'Your session is secured on the server, not in the browser.',
			tone: 'success' as const
		},
		{
			label: 'Session',
			value: 'Signed cookie',
			detail: 'Authentication persists in an HTTP-only cookie.',
			tone: 'accent' as const
		},
		{
			label: 'Coverage',
			value: 'Tenant-ready',
			detail: 'Use credentials or a scoped JWT to unlock the operational workspace you need.',
			tone: 'default' as const
		}
	];

	function withPending(formElement: HTMLFormElement, flow: 'password' | 'quick' | 'token') {
		return enhance(formElement, () => {
			submittingFlow = flow;

			return async ({ update }) => {
				try {
					await update();
				} finally {
					submittingFlow = null;
				}
			};
		});
	}

	$effect(() => {
		if (form?.loginError) {
			void tick().then(() => usernameField?.focus());
			return;
		}
		if (form?.quickError) {
			void tick().then(() => quickForm?.querySelector('button')?.focus());
			return;
		}
		if (form?.error) {
			void tick().then(() => tokenField?.focus());
		}
	});
</script>

<svelte:head>
	<title>Sign in | BrainBR</title>
	<meta name="description" content="Authenticate and get to work. Your session is secured server-side." />
	<meta name="robots" content="noindex, nofollow" />
</svelte:head>

<section class="auth-page">
	<WorkspaceHeader
		tag={['AUTH', '/auth']}
		title="Authenticate and get to work."
		description="Your session is secured server-side. The API never touches the browser."
		statusLabel="Secure session"
		statusTone="success"
	/>

	<StatStrip items={statItems} />

	<div class="auth-checklist">
		<p class="auth-checklist__eyebrow">Before you start</p>
		<ol>
			<li>Obtain a JWT from your IdP or use the standard credential flow.</li>
			<li>Make sure the token carries <code>tenant_id</code> and the inventory scopes you need.</li>
			<li>Paste the token below only when you need a tenant-specific session.</li>
		</ol>
	</div>

	<div class="auth-panels">
		<SectionPanel
			title="Recommended sign in"
			subtitle="Use credentials for the fastest path into the console."
		>
			{#snippet meta()}
				<span class="auth-badge">Recommended</span>
			{/snippet}

			{#snippet children()}
				<form class="stack" method="POST" action="?/password" use:withPending={'password'}>
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
						<Button disabled={submittingFlow === 'password'} type="submit">
							{#snippet children()}
								{#if submittingFlow === 'password'}
									<Spinner label="Signing in with credentials" />
									Signing in…
								{:else}
									Sign in with credentials
								{/if}
							{/snippet}
						</Button>
					</div>
				</form>
			{/snippet}
		</SectionPanel>

		<SectionPanel
			title="Demo sign in"
			subtitle="Use the demo account for smoke tests and guided walkthroughs."
		>
			{#snippet children()}
				<form class="stack" method="POST" action="?/quick" use:withPending={'quick'} bind:this={quickForm}>
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
						Use this path when you need access fast and do not need a tenant-specific JWT.
					</p>

					<div class="auth-actions">
						<Button disabled={submittingFlow === 'quick'} type="submit" variant="secondary">
							{#snippet children()}
								{#if submittingFlow === 'quick'}
									<Spinner label="Starting demo session" />
									Starting demo session…
								{:else}
									Use demo account
								{/if}
							{/snippet}
						</Button>
					</div>
				</form>
			{/snippet}
		</SectionPanel>
	</div>

	<SectionPanel
		title="Advanced: use token"
		subtitle="Open this only when you need a tenant-specific JWT."
		collapsible={true}
		defaultOpen={!!form?.error}
	>
		{#snippet children()}
			<form class="stack" method="POST" action="?/token" use:withPending={'token'}>
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
						aria-describedby={form?.error ? 'token-error token-help' : 'token-help'}
						aria-invalid={Boolean(form?.error)}
						id="token"
						name="token"
						placeholder="eyJhbGciOi..."
						required
						rows="10"
						spellcheck="false">{form?.token ?? ''}</textarea
					>
					<span class="auth-field__hint" id="token-help">
						Paste a backend-issued JWT. It will be exchanged for a signed cookie on the server.
					</span>
				</label>

				<div class="auth-actions">
					<Button disabled={submittingFlow === 'token'} type="submit">
						{#snippet children()}
							{#if submittingFlow === 'token'}
								<Spinner label="Starting authenticated session" />
								Starting session…
							{:else}
								Start authenticated session
							{/if}
						{/snippet}
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
		padding-bottom: 1.5rem;
	}

	.auth-checklist {
		display: grid;
		gap: 0.5rem;
		padding: 1.25rem 1.9rem;
		border-bottom: 1px solid var(--border);
	}

	.auth-checklist__eyebrow {
		margin: 0;
		font-family: var(--font-mono);
		font-size: 0.72rem;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--text-faint);
	}

	.auth-checklist ol {
		display: grid;
		gap: 0.35rem;
		margin: 0;
		padding-left: 1.2rem;
		color: var(--text-muted);
		font-size: 0.95rem;
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
		color: var(--text-muted);
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
