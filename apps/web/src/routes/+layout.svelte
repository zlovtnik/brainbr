<script lang="ts">
	import '$lib/styles/app.css';
	import favicon from '$lib/assets/favicon.svg';
	import type { LayoutProps } from './$types';

	let { data, children }: LayoutProps = $props();
	let authenticated = $derived(Boolean(data.session?.authenticated));
</script>

<svelte:head>
	<link rel="icon" href={favicon} />
	<title>BrainBR Inventory Console</title>
</svelte:head>

<a class="skip-link" href="#main-content">Skip to content</a>

<div class="page-shell">
	<header class="shell-header">
		<div class="shell-header__brand">
			<p class="eyebrow">BrainBR</p>
			<p class="shell-header__title">Inventory transition workspace</p>
		</div>

		<nav aria-label="Primary navigation" class="shell-nav">
			<a class="shell-link" href={authenticated ? '/inventory' : '/auth'}>
				{authenticated ? 'Inventory' : 'Auth bootstrap'}
			</a>
			{#if authenticated}
				<form action="/auth/logout" method="POST">
					<button class="shell-link shell-link--button" type="submit">Sign out</button>
				</form>
			{/if}
		</nav>
	</header>

	<div aria-live="polite" class="sr-only">
		{#if data.session}
			Authenticated as {data.session.user}
		{:else}
			Authentication required
		{/if}
	</div>

	{#if data.session}
		<section class="session-strip" aria-label="Current session">
			<p><strong>User:</strong> {data.session.user}</p>
			<p><strong>Tenant:</strong> {data.session.tenantId ?? 'Not present in token'}</p>
			<p><strong>Scopes:</strong> {data.session.scopes?.join(', ') || 'None detected'}</p>
		</section>
	{/if}

	<main id="main-content">
		{@render children()}
	</main>

	<footer class="shell-footer">
		<p>Server-rendered SvelteKit frontend with a server-only API boundary for FiscalBrain-BR.</p>
	</footer>
</div>

<style>
	.shell-header,
	.session-strip,
	.shell-footer {
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		background: rgba(255, 255, 255, 0.74);
		box-shadow: var(--shadow-soft);
	}

	.shell-header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: var(--space-4);
		padding: var(--space-5);
		margin-bottom: var(--space-5);
	}

	.shell-header__title,
	.shell-footer p,
	.session-strip p {
		margin: 0;
	}

	.shell-header__title {
		font-size: 1.15rem;
		font-weight: 700;
	}

	.shell-nav {
		display: flex;
		flex-wrap: wrap;
		align-items: center;
		gap: var(--space-3);
	}

	.shell-link {
		text-decoration: none;
		font-weight: 700;
		color: var(--color-accent-strong);
	}

	.shell-link--button {
		background: none;
		border: none;
		padding: 0;
		cursor: pointer;
	}

	.session-strip {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
		gap: var(--space-4);
		padding: var(--space-4) var(--space-5);
		margin-bottom: var(--space-5);
	}

	main {
		display: grid;
		gap: var(--space-5);
	}

	.shell-footer {
		padding: var(--space-4) var(--space-5);
		margin-top: var(--space-5);
		color: var(--color-ink-muted);
	}

	@media (max-width: 720px) {
		.shell-header {
			flex-direction: column;
			align-items: flex-start;
		}
	}
</style>
