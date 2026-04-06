<script lang="ts">
	import { page } from '$app/state';
	import type { NavItem } from '$lib/capabilities';
	import { worstStatus } from '$lib/capabilities';

	let { item }: { item: NavItem } = $props();

	const isLeafActive = (path: string) =>
		page.url.pathname === path || page.url.pathname.startsWith(path + '/');

	const hasActiveChild = (node: NavItem): boolean =>
		node.children?.some((c) => (c.path ? isLeafActive(c.path) : hasActiveChild(c))) ?? false;

	let open = $state(item.defaultOpen ?? false);

	$effect(() => {
		if (hasActiveChild(item)) open = true;
	});

	const badgeStatus = $derived(item.children ? worstStatus(item.children) : (item.status ?? 'live'));
	const badgeLabel = $derived(
		badgeStatus === 'live' || badgeStatus === 'available' ? 'live' : badgeStatus
	);

	function onKeydown(e: KeyboardEvent) {
		if (e.key === 'ArrowRight' || e.key === 'Enter') {
			open = true;
		} else if (e.key === 'ArrowLeft') {
			open = false;
		}
	}
</script>

<li role="treeitem" aria-expanded={open} class="nav-group" class:nav-group--collapsed={!open}>
	<button
		class="nav-group__trigger"
		class:nav-group__trigger--parent-active={hasActiveChild(item)}
		onclick={() => (open = !open)}
		onkeydown={onKeydown}
		aria-controls={open ? `nav-children-${item.id}` : undefined}
	>
		<span class="nav-group__title-wrap">
			{#if item.icon}<span class="nav-group__icon" aria-hidden="true">{item.icon}</span>{/if}
			<span class="nav-group__label">{item.label}</span>
		</span>
		<span class="nav-group__right">
			<span class={`cap-nav__badge cap-nav__badge--${badgeStatus}`}>{badgeLabel}</span>
			<span class="nav-group__chevron" class:nav-group__chevron--open={open} aria-hidden="true"
				>›</span
			>
		</span>
	</button>

	{#if open}
		<ul
			id={`nav-children-${item.id}`}
			role="group"
			class="nav-group__children"
		>
			{#each item.children ?? [] as child (child.id)}
				{#if child.children}
					<NavGroup item={child} />
				{:else}
					<li role="treeitem" aria-current={child.path && isLeafActive(child.path) ? 'page' : undefined}>
						<a
							href={child.path}
							class="nav-group__child-link"
							class:nav-group__child-link--active={child.path && isLeafActive(child.path)}
						>
							{child.label}
						</a>
					</li>
				{/if}
			{/each}
		</ul>
	{/if}
</li>

<style>
	.nav-group {
		list-style: none;
	}

	.nav-group__trigger {
		display: flex;
		align-items: center;
		justify-content: space-between;
		width: 100%;
		padding: 0.28rem 0.75rem;
		border: 0;
		border-left: 2px solid transparent;
		background: transparent;
		color: var(--text-faint);
		cursor: pointer;
		text-align: left;
		gap: 0.5rem;
		font-size: 0.8rem;
		letter-spacing: 0.03em;
		text-transform: uppercase;
		font-family: var(--font-mono);
	}

	.nav-group__trigger:focus-visible {
		outline: 2px solid var(--accent);
		outline-offset: -2px;
		border-radius: 2px;
	}

	.nav-group__trigger:hover {
		color: var(--text-muted);
	}

	.nav-group__trigger--parent-active {
		color: var(--text-muted);
	}

	.nav-group__title-wrap {
		display: flex;
		align-items: center;
		gap: 0.4rem;
	}

	.nav-group__icon {
		width: 14px;
		text-align: center;
		font-size: 0.75rem;
		opacity: 0.6;
	}

	.nav-group__label {
		font-size: 0.72rem;
	}

	.nav-group__right {
		display: flex;
		align-items: center;
		gap: 0.35rem;
	}

	/* badge only visible when collapsed */
	.nav-group__right :global(.cap-nav__badge) {
		display: none;
	}

	.nav-group--collapsed .nav-group__right :global(.cap-nav__badge) {
		display: inline-flex;
	}

	.nav-group__chevron {
		font-size: 0.7rem;
		color: var(--text-faint);
		transition: transform 0.12s ease;
		display: inline-block;
		opacity: 0.5;
	}

	.nav-group__chevron--open {
		transform: rotate(90deg);
	}

	.nav-group__children {
		padding: 0 0 0.15rem 0;
		margin: 0 0 0.25rem 0;
		list-style: none;
	}

	.nav-group__child-link {
		display: block;
		padding: 0.3rem 0.75rem 0.3rem 1.85rem;
		font-size: 0.86rem;
		color: var(--text-muted);
		text-decoration: none;
		border-left: 2px solid transparent;
	}

	.nav-group__child-link:hover {
		color: var(--text);
		border-left-color: var(--border-strong);
	}

	.nav-group__child-link--active {
		color: var(--accent-vivid);
		border-left-color: var(--accent);
		background: var(--accent-soft);
	}

	:global(.cap-nav__badge) {
		display: inline-flex;
		align-items: center;
		padding: 0.1rem 0.3rem;
		border-radius: 3px;
		font-size: 0.62rem;
		font-family: var(--font-mono);
		letter-spacing: 0.04em;
		border: 1px solid transparent;
	}

	:global(.cap-nav__badge--live),
	:global(.cap-nav__badge--available) {
		background: var(--success-soft);
		color: var(--success);
		border-color: var(--success-border);
	}

	:global(.cap-nav__badge--partial) {
		background: var(--warning-soft);
		color: var(--warning);
		border-color: var(--warning-border);
	}

	:global(.cap-nav__badge--locked) {
		background: var(--danger-soft);
		color: var(--danger);
		border-color: var(--danger-border);
	}
</style>
