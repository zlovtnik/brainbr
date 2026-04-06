<script lang="ts">
	import { navigating } from '$app/state';
	import { goto } from '$app/navigation';
	import Spinner from '$lib/components/Spinner.svelte';
	import WorkspaceHeader from '$lib/components/WorkspaceHeader.svelte';
	import InventoryTable from '$lib/features/inventory/InventoryTable.svelte';
	import { getCapability } from '$lib/capabilities';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
	const capability = $derived(getCapability('inventory'));

	let isLoading = $derived(Boolean(navigating.to));

	// Local reactive state for the search bar
	let query = $state('');

	$effect(() => {
		query = data.filters.query;
	});

	// Combobox open state
	let filterOpen = $state(false);

	const SORT_OPTIONS = [
		{ value: 'updated_at:desc', label: 'Newest first' },
		{ value: 'updated_at:asc', label: 'Oldest first' },
		{ value: 'sku_id:asc', label: 'SKU A → Z' },
		{ value: 'sku_id:desc', label: 'SKU Z → A' }
	] as const;

	let sortValue = $derived(`${data.filters.sortBy}:${data.filters.sortOrder}`);

	function buildHref(overrides: Record<string, string | boolean | number> = {}): string {
		const p = new URLSearchParams({
			page: String(data.filters.page),
			sortBy: data.filters.sortBy,
			sortOrder: data.filters.sortOrder
		});
		if (data.filters.query) p.set('query', data.filters.query);
		if (data.filters.includeInactive) p.set('includeInactive', 'true');
		for (const [k, v] of Object.entries(overrides)) {
			if (v === false || v === '') p.delete(k);
			else p.set(k, String(v));
		}
		// Reset to page 1 on filter/sort change
		if (Object.keys(overrides).some((k) => k !== 'page')) p.set('page', '1');
		return `/inventory?${p.toString()}`;
	}

	function submitSearch() {
		goto(buildHref({ query: query.trim(), page: 1 }));
	}

	function applySort(val: string) {
		const [sortBy, sortOrder] = val.split(':');
		goto(buildHref({ sortBy, sortOrder }));
		filterOpen = false;
	}

	function toggleInactive() {
		goto(buildHref({ includeInactive: !data.filters.includeInactive }));
		filterOpen = false;
	}

	function buildPageHref(page: number): string {
		return buildHref({ page });
	}

	const sortLabel = $derived(
		SORT_OPTIONS.find((o) => o.value === sortValue)?.label ?? 'Sort'
	);
</script>

<svelte:head>
	<title>Inventory | BrainBR</title>
	<meta name="description" content="Search and manage your inventory catalog." />
	<link href="/inventory" rel="canonical" />
</svelte:head>

<section class="inventory-page">
	<WorkspaceHeader
		tag={['GET', '/api/v1/inventory/sku']}
		title={capability.navLabel}
		description="Filter, sort, and drill into any SKU."
		statusLabel="Fiscal catalog live"
		statusTone="success"
		primaryAction={{ href: '/inventory/new', label: 'Create SKU' }}
	/>

	<div class="inventory-body">
		<!-- Search + filter bar -->
		<div class="toolbar">
			<form class="toolbar__search" onsubmit={(e) => { e.preventDefault(); submitSearch(); }}>
				<div class="search-wrap">
					<svg class="search-icon" aria-hidden="true" viewBox="0 0 16 16" fill="none">
						<circle cx="6.5" cy="6.5" r="4" stroke="currentColor" stroke-width="1.4"/>
						<path d="M10 10l3 3" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
					</svg>
					<input
						class="search-input"
						type="search"
						name="query"
						placeholder="SKU, description, or NCM code…"
						bind:value={query}
						autocomplete="off"
						aria-label="Search inventory"
					/>
					{#if query}
						<button
							class="search-clear"
							type="button"
							aria-label="Clear search"
							onclick={() => { query = ''; submitSearch(); }}
						>×</button>
					{/if}
				</div>
				<button class="btn-search" type="submit">Search</button>
			</form>

			<!-- Filter combobox -->
			<div class="filter-combo">
				<button
					class="filter-trigger"
					class:filter-trigger--active={filterOpen}
					type="button"
					aria-haspopup="listbox"
					aria-expanded={filterOpen}
					onclick={() => (filterOpen = !filterOpen)}
				>
					<svg aria-hidden="true" viewBox="0 0 16 16" fill="none">
						<path d="M2 4h12M4 8h8M6 12h4" stroke="currentColor" stroke-width="1.4" stroke-linecap="round"/>
					</svg>
					{sortLabel}{data.filters.includeInactive ? ' · +inactive' : ''}
				</button>

				{#if filterOpen}
					<!-- svelte-ignore a11y_no_static_element_interactions -->
					<div
						class="filter-dropdown"
						role="listbox"
						tabindex="-1"
						aria-label="Filter and sort options"
						onkeydown={(e) => e.key === 'Escape' && (filterOpen = false)}
					>
						<div class="filter-section">
							<span class="filter-section__label">Sort</span>
							{#each SORT_OPTIONS as opt}
								<button
									class="filter-option"
									class:filter-option--selected={sortValue === opt.value}
									role="option"
									aria-selected={sortValue === opt.value}
									type="button"
									onclick={() => applySort(opt.value)}
								>{opt.label}</button>
							{/each}
						</div>
						<div class="filter-section filter-section--border">
							<button
								class="filter-option"
								class:filter-option--selected={data.filters.includeInactive}
								role="option"
								aria-selected={data.filters.includeInactive}
								type="button"
								onclick={toggleInactive}
							>Include inactive SKUs</button>
						</div>
					</div>
				{/if}
			</div>

			{#if data.filters.query || data.filters.includeInactive}
				<a class="clear-link" href="/inventory">Clear</a>
			{/if}
		</div>

		<!-- Results -->
		<div class="results" class:results--loading={isLoading}>
			{#if isLoading}
				<div class="results__overlay" aria-live="polite">
					<Spinner label="Refreshing" />
				</div>
			{/if}

			<InventoryTable inventory={data.inventory} />

			{#if data.inventory && (data.filters.page > 1 || data.inventory.hasMore)}
				<nav aria-label="Pagination" class="pager">
					{#if data.filters.page > 1}
						<a class="pager__btn" href={buildPageHref(data.filters.page - 1)}>← Prev</a>
					{:else}
						<span class="pager__btn pager__btn--disabled">← Prev</span>
					{/if}
					<span class="pager__current">p. {data.filters.page}</span>
					{#if data.inventory.hasMore}
						<a class="pager__btn" href={buildPageHref(data.filters.page + 1)}>Next →</a>
					{:else}
						<span class="pager__btn pager__btn--disabled">Next →</span>
					{/if}
				</nav>
			{/if}
		</div>
	</div>
</section>

<!-- Close dropdown on outside click -->
{#if filterOpen}
	<!-- svelte-ignore a11y_no_static_element_interactions -->
	<div
		class="filter-backdrop"
		onclick={() => (filterOpen = false)}
		onkeydown={(e) => e.key === 'Escape' && (filterOpen = false)}
	></div>
{/if}

<style>
	.inventory-page {
		display: grid;
		min-width: 0;
	}

	.inventory-body {
		display: grid;
		gap: 0;
		padding: 1.25rem 0 2rem;
	}

	/* ── Toolbar ── */
	.toolbar {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		padding: 0 0 1rem;
		position: relative;
	}

	.toolbar__search {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		flex: 1;
		min-width: 0;
	}

	.search-wrap {
		position: relative;
		flex: 1;
		min-width: 0;
	}

	.search-icon {
		position: absolute;
		left: 0.6rem;
		top: 50%;
		transform: translateY(-50%);
		width: 14px;
		height: 14px;
		color: var(--text-faint);
		pointer-events: none;
	}

	.search-input {
		width: 100%;
		min-height: 2rem;
		padding: 0.35rem 2rem 0.35rem 2rem;
		border: 1px solid var(--color-input-border);
		border-radius: var(--radius-sm);
		background: var(--bg-2);
		color: var(--text);
		font-size: 0.86rem;
		font-family: var(--font-sans);
		caret-color: var(--accent);
	}

	.search-input::placeholder {
		color: var(--text-faint);
	}

	.search-input:focus {
		outline: none;
		border-color: var(--accent);
		box-shadow: 0 0 0 2px var(--focus-ring);
	}

	.search-clear {
		position: absolute;
		right: 0.5rem;
		top: 50%;
		transform: translateY(-50%);
		background: none;
		border: none;
		color: var(--text-faint);
		font-size: 1rem;
		line-height: 1;
		cursor: pointer;
		padding: 0 0.2rem;
	}

	.search-clear:hover {
		color: var(--text);
	}

	.btn-search {
		min-height: 2rem;
		padding: 0.3rem 0.75rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		background: var(--bg-2);
		color: var(--text-muted);
		font-size: 0.86rem;
		cursor: pointer;
		white-space: nowrap;
	}

	.btn-search:hover {
		background: var(--bg-3);
		border-color: var(--border-strong);
		color: var(--text);
	}

	/* ── Filter combobox ── */
	.filter-combo {
		position: relative;
	}

	.filter-trigger {
		display: inline-flex;
		align-items: center;
		gap: 0.4rem;
		min-height: 2rem;
		padding: 0.3rem 0.75rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		background: var(--bg-2);
		color: var(--text-muted);
		font-size: 0.86rem;
		cursor: pointer;
		white-space: nowrap;
	}

	.filter-trigger svg {
		width: 13px;
		height: 13px;
		flex-shrink: 0;
	}

	.filter-trigger:hover,
	.filter-trigger--active {
		background: var(--bg-3);
		border-color: var(--border-strong);
		color: var(--text);
	}

	.filter-dropdown {
		position: absolute;
		top: calc(100% + 4px);
		right: 0;
		z-index: 50;
		min-width: 180px;
		background: var(--bg-2);
		border: 1px solid var(--border-strong);
		border-radius: var(--radius-md);
		box-shadow: 0 8px 24px rgba(0, 0, 0, 0.4);
		overflow: hidden;
	}

	.filter-section {
		display: flex;
		flex-direction: column;
		padding: 0.35rem 0;
	}

	.filter-section--border {
		border-top: 1px solid var(--border);
	}

	.filter-section__label {
		padding: 0.25rem 0.75rem 0.15rem;
		font-size: 0.68rem;
		font-family: var(--font-mono);
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--text-faint);
	}

	.filter-option {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		padding: 0.3rem 0.75rem;
		background: none;
		border: none;
		color: var(--text-muted);
		font-size: 0.86rem;
		text-align: left;
		cursor: pointer;
		width: 100%;
	}

	.filter-option:hover {
		background: var(--bg-3);
		color: var(--text);
	}

	.filter-option--selected {
		color: var(--accent-vivid);
	}

	.filter-option--selected::before {
		content: '✓';
		font-size: 0.75rem;
		width: 0.75rem;
		flex-shrink: 0;
	}

	.filter-option:not(.filter-option--selected)::before {
		content: '';
		width: 0.75rem;
		flex-shrink: 0;
	}

	.filter-backdrop {
		position: fixed;
		inset: 0;
		z-index: 49;
	}

	.clear-link {
		min-height: 2rem;
		padding: 0.3rem 0.6rem;
		font-size: 0.82rem;
		font-family: var(--font-mono);
		color: var(--text-faint);
		text-decoration: none;
		white-space: nowrap;
	}

	.clear-link:hover {
		color: var(--text-muted);
	}

	/* ── Results ── */
	.results {
		position: relative;
		display: grid;
		gap: 0.75rem;
	}

	.results--loading {
		opacity: 0.6;
		pointer-events: none;
	}

	.results__overlay {
		position: absolute;
		top: 0.5rem;
		left: 50%;
		transform: translateX(-50%);
		z-index: 1;
	}

	/* ── Pager ── */
	.pager {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 0.75rem;
		padding-top: 0.5rem;
		border-top: 1px solid var(--border);
	}

	.pager__btn {
		display: inline-flex;
		align-items: center;
		min-height: 2rem;
		padding: 0.3rem 0.65rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		background: var(--bg-2);
		color: var(--text-muted);
		font-size: 0.82rem;
		font-family: var(--font-mono);
		text-decoration: none;
		cursor: pointer;
	}

	.pager__btn:hover {
		background: var(--bg-3);
		border-color: var(--border-strong);
		color: var(--text);
	}

	.pager__btn--disabled {
		color: var(--text-faint);
		cursor: not-allowed;
		border-color: var(--border);
		background: transparent;
	}

	.pager__current {
		font-family: var(--font-mono);
		font-size: 0.78rem;
		color: var(--text-faint);
	}

	@media (max-width: 640px) {
		.toolbar {
			flex-wrap: wrap;
		}

		.toolbar__search {
			width: 100%;
		}
	}
</style>
