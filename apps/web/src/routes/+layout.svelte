<script lang="ts">
	import { afterNavigate } from '$app/navigation';
	import { page } from '$app/state';
	import { onMount } from 'svelte';
	import '$lib/styles/app.css';
	import favicon from '$lib/assets/favicon.svg';
	import {
		capabilityList,
		getCapabilityAvailability,
		getCapabilityByPath,
		getCapabilitySummary
	} from '$lib/capabilities';
	import type { LayoutProps } from './$types';

	let { data, children }: LayoutProps = $props();
	let mainContent: HTMLElement | undefined;
	let authenticated = $derived(Boolean(data.session?.authenticated));
	let activeCapability = $derived(getCapabilityByPath(page.url.pathname));
	let capabilitySummary = $derived(getCapabilitySummary(data.session));
	let capabilityNav = $derived(
		capabilityList.map((capability) => ({
			...capability,
			availability: getCapabilityAvailability(capability, data.session?.scopes ?? [])
		}))
	);

	afterNavigate(({ from }) => {
		if (!from) {
			return;
		}

		mainContent?.focus();
	});

	onMount(() => {
		mainContent?.focus();
	});

	let sessionLabel = $derived(
		data.session?.user && data.session?.tenantId
			? `${data.session.user} · ${data.session.tenantId}`
			: data.session?.user
				? `${data.session.user} · session`
				: 'guest · no-session'
	);
	const capabilityIcons = {
		platform: '◈',
		inventory: '◫',
		audit: '◪',
		compliance: '◧',
		'split-payment': '◰',
		ingestion: '◱'
	} as const;
</script>

<svelte:head>
	<link rel="preconnect" href="https://fonts.googleapis.com" />
	<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin="anonymous" />
	<link
		href="https://fonts.googleapis.com/css2?family=IBM+Plex+Mono:wght@400;500&family=IBM+Plex+Sans:wght@300;400;500;600&display=swap"
		rel="stylesheet"
	/>
	<link rel="icon" href={favicon} />
	<title>BrainBR Control Plane</title>
</svelte:head>

<div class="page-shell shell">
	<header class="shell__topbar">
		<div class="shell__topbar-left">
			<div class="shell__brand">
				<div class="shell__brand-dot"></div>
				<span>BrainBR</span>
			</div>
			<div class="shell__divider"></div>
			<div class="shell__breadcrumb" aria-label="Breadcrumb">
				<span>control-plane</span>
				<span>/</span>
				<span class="shell__breadcrumb-active"
					>{activeCapability?.navLabel?.toLowerCase() ?? 'auth'}</span
				>
			</div>
		</div>

		<div class="shell__actions">
			<div class="shell__session-chip">
				<div class:shell__session-dot--inactive={!authenticated} class="shell__session-dot"></div>
				{sessionLabel}
			</div>
			{#if authenticated}
				<form action="/auth/logout" method="POST">
					<button class="shell__action-button" type="submit">Sign out</button>
				</form>
			{:else}
				<a class="shell__action-button" href="/auth">Sign in</a>
			{/if}
		</div>
	</header>

	<div class="shell__layout">
		<aside class="shell__sidebar">
			<div class="shell__sidebar-label">Capabilities</div>
			<nav aria-label="Backend capabilities" class="cap-nav">
				{#each capabilityNav as capability}
					<a
						aria-current={activeCapability?.id === capability.id ? 'page' : undefined}
						class:cap-nav__link--active={activeCapability?.id === capability.id}
						class:cap-nav__link--partial={capability.availability === 'partial'}
						class:cap-nav__link--locked={capability.availability === 'locked'}
						class="cap-nav__link"
						href={capability.href}
					>
						<span class="cap-nav__title-row">
							<span class="cap-nav__title-wrap">
								<span class="cap-nav__icon" aria-hidden="true"
									>{capabilityIcons[capability.id]}</span
								>
								<span class="cap-nav__title">{capability.navLabel}</span>
							</span>
							<span class={`cap-nav__badge cap-nav__badge--${capability.availability}`}>
								{capability.availability === 'public' || capability.availability === 'available'
									? 'live'
									: capability.availability}
							</span>
						</span>
					</a>
				{/each}
			</nav>

			<section class="shell__session-card" aria-label="Session state">
				<h2>Session</h2>
				<dl>
					<div>
						<dt>user</dt>
						<dd>{data.session?.user ?? 'Guest'}</dd>
					</div>
					<div>
						<dt>tenant</dt>
						<dd>{data.session?.tenantId ?? 'Unavailable'}</dd>
					</div>
					<div>
						<dt>scopes</dt>
						<dd>{data.session?.scopes?.length ?? 0}</dd>
					</div>
				</dl>

				<div class="shell__scope-list">
					{#if data.session?.scopes?.length}
						{#each data.session.scopes as scope}
							<span>{scope}</span>
						{/each}
					{:else}
						<p>Protected routes require a valid session with appropriate scopes.</p>
					{/if}
				</div>
			</section>
		</aside>

		<div class="shell__content">
			<div aria-live="polite" class="sr-only">
				{#if data.session}
					Authenticated as {data.session.user}
				{:else}
					Authentication required
				{/if}
			</div>

			<main bind:this={mainContent} id="main-content" tabindex="-1">
				{@render children()}
			</main>

			<footer class="shell__footer">
				<p>Server-rendered SvelteKit · Spring API backend · Tokens server-side only</p>
				<div class="shell__footer-right">
					<p>{capabilitySummary.available}/{capabilitySummary.total} available</p>
					<a class="shell__action-button shell__action-button--ghost" href="/platform"
						>Open workspace</a
					>
				</div>
			</footer>
		</div>
	</div>
</div>

<style>
	.shell {
		display: grid;
		grid-template-rows: auto 1fr;
		min-height: 100vh;
		background: var(--bg);
		background-color: var(--bg) !important;
	}

	.shell__topbar {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: 1.5rem;
		padding: 0 1.5rem;
		min-height: 52px;
		background: var(--bg-1);
		border-bottom: 1px solid var(--border);
		color: var(--text);
	}

	.shell__topbar-left,
	.shell__brand,
	.shell__actions {
		display: flex;
		align-items: center;
		gap: 0.75rem;
	}

	.shell__brand {
		gap: 0.5rem;
		font-family: var(--font-mono);
		font-size: 0.93rem;
		font-weight: 500;
		letter-spacing: 0.02em;
		color: var(--text);
	}

	.shell__brand-dot {
		width: 8px;
		height: 8px;
		border-radius: 50%;
		background: var(--accent);
		box-shadow: 0 0 6px rgba(79, 142, 247, 0.6);
	}

	.shell__divider {
		width: 1px;
		height: 20px;
		background: var(--border);
	}

	.shell__breadcrumb {
		display: flex;
		align-items: center;
		gap: 0.35rem;
		font-family: var(--font-mono);
		font-size: 0.82rem;
		color: var(--text-faint);
	}

	.shell__breadcrumb-active {
		color: var(--text-muted);
	}

	.shell__session-chip {
		display: inline-flex;
		align-items: center;
		gap: 0.5rem;
		padding: 0.25rem 0.65rem;
		background: var(--bg-2);
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		font-size: 0.78rem;
		color: var(--text-muted);
		font-family: var(--font-mono);
	}

	.shell__session-dot {
		width: 6px;
		height: 6px;
		border-radius: 50%;
		background: var(--success);
	}

	.shell__session-dot--inactive {
		background: var(--text-faint);
	}

	.shell__action-button {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		padding: 0.35rem 0.85rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		background: var(--bg-2);
		color: var(--text-muted);
		font-size: 0.86rem;
		cursor: pointer;
	}

	.shell__action-button:hover {
		background: var(--bg-3);
		border-color: var(--border-strong);
		color: var(--text);
	}

	.shell__action-button--ghost {
		background: transparent;
		border-color: transparent;
	}

	.shell__action-button--ghost:hover {
		background: var(--bg-2);
		border-color: var(--border);
	}

	.shell__layout {
		display: grid;
		grid-template-columns: 220px minmax(0, 1fr);
		min-height: calc(100vh - 52px);
		background: var(--bg);
		background-color: var(--bg) !important;
	}

	.shell__sidebar {
		display: flex;
		flex-direction: column;
		padding: 1rem 0;
		background: var(--bg-1);
		border-right: 1px solid var(--border);
		color: var(--text);
	}

	.shell__sidebar-label {
		padding: 0 1rem 0.4rem;
		font-size: 0.7rem;
		font-family: var(--font-mono);
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--text-faint);
	}

	.cap-nav {
		display: flex;
		flex-direction: column;
		gap: 0;
	}

	.cap-nav__link {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.75rem;
		padding: 0.5rem 1rem;
		border-left: 2px solid transparent;
		color: var(--text-muted);
	}

	.cap-nav__link:hover {
		background: rgba(255, 255, 255, 0.03);
		color: var(--text);
	}

	.cap-nav__link--active {
		background: var(--accent-soft);
		border-left-color: var(--accent);
		color: var(--text);
	}

	.cap-nav__title-row {
		display: flex;
		align-items: center;
		justify-content: space-between;
		width: 100%;
		gap: 0.75rem;
	}

	.cap-nav__title-wrap {
		display: flex;
		align-items: center;
		gap: 0.5rem;
	}

	.cap-nav__icon {
		width: 16px;
		text-align: center;
		font-size: 0.9rem;
	}

	.cap-nav__title {
		font-size: 0.93rem;
	}

	.cap-nav__badge {
		display: inline-flex;
		align-items: center;
		padding: 0.12rem 0.38rem;
		border-radius: 3px;
		font-size: 0.68rem;
		font-family: var(--font-mono);
		letter-spacing: 0.04em;
		border: 1px solid transparent;
	}

	.cap-nav__badge--public,
	.cap-nav__badge--available {
		background: var(--success-soft);
		color: var(--success);
		border-color: var(--success-border);
	}

	.cap-nav__badge--partial {
		background: var(--warning-soft);
		color: var(--warning);
		border-color: var(--warning-border);
	}

	.cap-nav__badge--locked {
		background: var(--danger-soft);
		color: var(--danger);
		border-color: var(--danger-border);
	}

	.shell__session-card {
		margin: 0.85rem 0.75rem 0;
		padding: 0.85rem;
		border-radius: var(--radius-md);
		display: grid;
		gap: 0.75rem;
		background: var(--bg-2);
		border: 1px solid var(--border);
	}

	.shell__session-card h2 {
		margin: 0;
		font-size: 0.72rem;
		font-family: var(--font-mono);
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--text-faint);
	}

	.shell__session-card dl {
		display: grid;
		gap: 0;
		margin: 0;
	}

	.shell__session-card dl div {
		display: flex;
		justify-content: space-between;
		align-items: baseline;
		gap: 0.5rem;
		padding: 0.35rem 0;
		border-bottom: 1px solid var(--border);
	}

	.shell__session-card dl div:last-child {
		border-bottom: 0;
	}

	.shell__session-card dt,
	.shell__session-card dd {
		margin: 0;
		font-family: var(--font-mono);
		font-size: 0.78rem;
	}

	.shell__session-card dt {
		color: var(--text-faint);
		text-transform: lowercase;
	}

	.shell__session-card dd {
		color: var(--text);
		text-align: right;
	}

	.shell__scope-list {
		display: flex;
		flex-wrap: wrap;
		gap: 0.45rem;
	}

	.shell__scope-list span {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		padding: 0.2rem 0.45rem;
		border-radius: 3px;
		background: var(--bg-3);
		border: 1px solid var(--border);
		font-size: 0.72rem;
		font-family: var(--font-mono);
		color: var(--text-muted);
	}

	.shell__scope-list p {
		margin: 0.25rem 0 0;
		font-size: 0.78rem;
		color: var(--text-faint);
		line-height: 1.5;
	}

	.shell__content {
		display: grid;
		grid-template-rows: 1fr auto;
		min-width: 0;
		overflow: auto;
		background: var(--bg);
		background-color: var(--bg) !important;
		color: var(--text);
	}

	main {
		display: grid;
		min-width: 0;
		background: var(--bg);
		background-color: var(--bg) !important;
		color: var(--text);
	}

	main:focus-visible {
		outline-offset: -3px;
	}

	.shell__footer {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 1rem;
		padding: 0.85rem 1.75rem;
		border-top: 1px solid var(--border);
		font-size: 0.78rem;
		font-family: var(--font-mono);
		color: var(--text-faint);
	}

	.shell__footer-right {
		display: flex;
		align-items: center;
		gap: 1rem;
	}

	@media (max-width: 1080px) {
		.shell__layout {
			grid-template-columns: 1fr;
		}

		.shell__sidebar {
			border-right: 0;
			border-bottom: 1px solid var(--border);
		}
	}

	@media (max-width: 720px) {
		.shell__topbar,
		.shell__topbar-left,
		.shell__actions {
			flex-direction: column;
			align-items: flex-start;
		}

		.shell__topbar {
			padding: 0.9rem 1rem;
		}

		.cap-nav {
			display: flex;
			flex-direction: column;
		}

		.cap-nav__title-row {
			display: flex;
			width: 100%;
		}

		.shell__session-card dl div {
			flex-direction: column;
			align-items: flex-start;
		}

		.shell__session-card dd {
			text-align: left;
		}

		.shell__footer {
			flex-direction: column;
			align-items: flex-start;
		}

		.shell__footer-right {
			flex-direction: column;
			align-items: flex-start;
		}
	}
</style>
