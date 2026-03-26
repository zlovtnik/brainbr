<script lang="ts">
	import Button from '$lib/components/Button.svelte';
	import Card from '$lib/components/Card.svelte';
	import InlineNotice from '$lib/components/InlineNotice.svelte';
	import type { PageProps } from './$types';

	let { data, form }: PageProps = $props();
</script>

<section class="hero stack">
	<p class="eyebrow">Bootstrap auth</p>
	<h1 class="headline">Paste a tenant JWT and step directly into inventory operations.</h1>
	<p class="lede">
		This temporary auth bootstrap keeps the token in an HTTP-only cookie so browser code never talks
		to the Spring API directly.
	</p>
</section>

<form class="stack" method="POST">
	<Card>
		{#snippet header()}
			<div class="stack">
				<h2>Session token</h2>
				<p class="lede">Use a JWT carrying `tenant_id` plus the `inventory:read` and `inventory:write` scopes.</p>
			</div>
		{/snippet}

		{#if form?.error}
			<InlineNotice message={form.error} title="Invalid bootstrap token" variant="error" />
		{/if}

		<input name="redirectTo" type="hidden" value={form?.redirectTo ?? data.redirectTo} />
		<label class="auth-field" for="token">
			<span class="auth-field__label">Bearer JWT</span>
			<textarea
				aria-describedby="token-help"
				class="auth-field__textarea"
				id="token"
				name="token"
				placeholder="eyJhbGciOi..."
				required
			>{form?.token ?? ''}</textarea>
			<span class="auth-field__hint" id="token-help">The token remains server-side in a signed cookie after submission.</span>
		</label>

		<Button type="submit">
			{#snippet children()}Start authenticated session{/snippet}
		</Button>
	</Card>
</form>

<style>
	h2 {
		margin: 0;
	}

	.hero {
		margin-bottom: var(--space-5);
	}

	.auth-field {
		display: grid;
		gap: var(--space-2);
	}

	.auth-field__label {
		font-weight: 700;
	}

	.auth-field__textarea {
		min-height: 12rem;
		padding: 1rem;
		border-radius: var(--radius-md);
		border: 1px solid var(--color-border);
		background: rgba(255, 255, 255, 0.84);
	}

	.auth-field__hint {
		color: var(--color-ink-muted);
		font-size: 0.92rem;
	}
</style>
