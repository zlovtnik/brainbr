<script lang="ts">
	import { enhance } from '$app/forms';
	import { tick } from 'svelte';
	import Button from '$lib/components/Button.svelte';
	import InlineNotice from '$lib/components/InlineNotice.svelte';
	import WorkspaceHeader from '$lib/components/WorkspaceHeader.svelte';
	import Spinner from '$lib/components/Spinner.svelte';
	import type { PageProps } from './$types';

	let { data, form }: PageProps = $props();
	let tokenField = $state<HTMLTextAreaElement | undefined>();
	let submitting = $state(false);

	$effect(() => {
		if (form?.error) void tick().then(() => tokenField?.focus());
	});
</script>

<svelte:head>
	<title>Sign in | BrainBR</title>
	<meta name="description" content="Sign in with a bearer JWT issued by your identity provider." />
	<meta name="robots" content="noindex, nofollow" />
</svelte:head>

<section class="auth-page">
	<WorkspaceHeader
		tag={['AUTH', '/auth']}
		title="Sign in"
		description="Paste a bearer JWT issued by your identity provider. Your session is secured server-side."
		statusLabel="Secure session"
		statusTone="success"
	/>

	<div class="auth-body">
		<form
			class="auth-form"
			method="POST"
			use:enhance={() => {
				submitting = true;
				return async ({ update }) => {
					try {
						await update();
					} finally {
						submitting = false;
					}
				};
			}}
		>
			{#if form?.error}
				<InlineNotice id="auth-error" message={form.error} title="Sign-in failed" variant="error" />
			{/if}

			<input name="redirectTo" type="hidden" value={form?.redirectTo ?? data.redirectTo} />

			<label class="auth-field" for="token">
				<span class="auth-field__label">Bearer JWT</span>
				<textarea
					bind:this={tokenField}
					aria-describedby={form?.error ? 'auth-error token-hint' : 'token-hint'}
					aria-invalid={Boolean(form?.error)}
					autocomplete="off"
					id="token"
					name="token"
					placeholder="eyJhbGciOi..."
					required
					rows="10"
					spellcheck="false"
				></textarea>
				<span class="auth-field__hint" id="token-hint">
					Obtain a JWT from your IdP. It must carry <code>tenant_id</code> and the required
					inventory scopes.
				</span>
			</label>

			<div class="auth-actions">
				<Button disabled={submitting} type="submit">
					{#snippet children()}
						{#if submitting}
							<Spinner label="Signing in" />
							Signing in…
						{:else}
							Sign in
						{/if}
					{/snippet}
				</Button>
			</div>
		</form>
	</div>
</section>

<style>
	.auth-page {
		display: grid;
		gap: 0;
		padding-bottom: 1.5rem;
	}

	.auth-body {
		padding: 2rem 1.9rem;
		max-width: 560px;
	}

	.auth-form {
		display: grid;
		gap: var(--space-5);
	}

	.auth-field {
		display: grid;
		gap: var(--space-2);
	}

	.auth-field__label {
		font-weight: 500;
		color: var(--text);
	}

	.auth-field textarea {
		min-height: 15rem;
		padding: 0.95rem 1rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		background: var(--bg-3);
		color: var(--text);
		caret-color: var(--text);
		font-family: 'SFMono-Regular', 'SF Mono', 'Consolas', monospace;
		font-size: 0.92rem;
		line-height: 1.55;
		resize: vertical;
	}

	.auth-field__hint {
		margin: 0;
		color: var(--text-muted);
		font-size: 0.84rem;
		line-height: 1.5;
	}

	.auth-actions {
		display: flex;
	}

	@media (max-width: 760px) {
		.auth-body {
			padding: 1.5rem 1rem;
		}

		.auth-actions > :global(.button) {
			width: 100%;
		}
	}
</style>
