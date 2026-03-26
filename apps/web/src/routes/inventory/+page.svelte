<script lang="ts">
	import { navigating } from '$app/state';
	import Button from '$lib/components/Button.svelte';
	import Card from '$lib/components/Card.svelte';
	import Select from '$lib/components/Select.svelte';
	import Spinner from '$lib/components/Spinner.svelte';
	import InventoryTable from '$lib/features/inventory/InventoryTable.svelte';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	let isLoading = $derived(Boolean(navigating.to));

	function buildPageHref(page: number): string {
		const params = new URLSearchParams({
			page: String(page),
			limit: String(data.filters.limit),
			sortBy: data.filters.sortBy,
			sortOrder: data.filters.sortOrder
		});
		if (data.filters.query) {
			params.set('query', data.filters.query);
		}
		if (data.filters.includeInactive) {
			params.set('includeInactive', 'true');
		}
		return `/inventory?${params.toString()}`;
	}
</script>

<svelte:head>
	<title>Inventory | BrainBR</title>
	<meta
		name="description"
		content="Search and manage your inventory catalog with filters, sorting, and pagination."
	/>
	<link href="/inventory" rel="canonical" />
</svelte:head>

<section class="stack">
	<div class="stack">
		<p class="eyebrow">Inventory workflow</p>
		<h1 class="headline">
			Search the tenant catalog, inspect tax payloads, and move directly into edits.
		</h1>
		<p class="lede">
			The list is server-rendered from the Spring API and keeps query, sort, and activity filters in
			the URL.
		</p>
	</div>

	<Card>
		{#snippet header()}
			<div class="cluster">
				<h2>Filters</h2>
				{#if isLoading}
					<Spinner label="Refreshing inventory" />
				{/if}
			</div>
		{/snippet}

		<form aria-describedby="inventory-filter-help" class="filters" method="GET" role="search">
			<label class="filters__search" for="query">
				<span>Search</span>
				<input
					id="query"
					name="query"
					placeholder="SKU, description, or NCM code"
					type="search"
					value={data.filters.query}
				/>
			</label>

			<Select
				id="sortBy"
				label="Sort field"
				name="sortBy"
				options={[
					{ value: 'updated_at', label: 'Updated time' },
					{ value: 'sku_id', label: 'SKU ID' }
				]}
				value={data.filters.sortBy}
			/>

			<Select
				id="sortOrder"
				label="Sort direction"
				name="sortOrder"
				options={[
					{ value: 'desc', label: 'Newest first' },
					{ value: 'asc', label: 'Oldest / A-Z first' }
				]}
				value={data.filters.sortOrder}
			/>

			<label class="filters__toggle">
				<input
					checked={data.filters.includeInactive}
					name="includeInactive"
					type="checkbox"
					value="true"
				/>
				<span>Include inactive SKUs</span>
			</label>

			<div class="cluster">
				<Button type="submit">
					{#snippet children()}Apply filters{/snippet}
				</Button>
				<a class="text-link" href="/inventory/new">Create SKU</a>
			</div>

			<p class="sr-only" id="inventory-filter-help">
				Search by SKU, description, or NCM code, then apply filters to reload the current page.
			</p>
		</form>
	</Card>

	<div class="meta-grid">
		<Card>
			<p class="eyebrow">Visible records</p>
			<h2>{data.inventory?.items.length ?? 0}</h2>
			<p class="lede">Items currently rendered for this page.</p>
		</Card>
		<Card>
			<p class="eyebrow">Total matches</p>
			<h2>{data.inventory?.totalCount ?? 0}</h2>
			<p class="lede">All server-side matches for the active filter set.</p>
		</Card>
	</div>

	<InventoryTable
		inventory={data.inventory}
		loadError={data.loadError ?? undefined}
		successMessage={data.successMessage ?? undefined}
	/>

	{#if data.inventory}
		<nav aria-label="Pagination" class="pager">
			{#if data.filters.page > 1}
				<a class="text-link" href={buildPageHref(data.filters.page - 1)}>Previous page</a>
			{:else}
				<span aria-disabled="true" class="text-link text-link--disabled" tabindex="-1"
					>Previous page</span
				>
			{/if}
			<span>Page {data.filters.page}</span>
			{#if data.inventory.hasMore}
				<a class="text-link" href={buildPageHref(data.filters.page + 1)}>Next page</a>
			{:else}
				<span aria-disabled="true" class="text-link text-link--disabled" tabindex="-1"
					>Next page</span
				>
			{/if}
		</nav>
	{/if}
</section>

<style>
	h2 {
		margin: 0;
		font-size: 2.1rem;
	}

	.filters {
		display: grid;
		grid-template-columns: minmax(240px, 2fr) repeat(2, minmax(180px, 1fr));
		gap: var(--space-4);
		align-items: end;
	}

	.filters__search,
	.filters__toggle {
		display: grid;
		gap: var(--space-2);
	}

	.filters__search input {
		min-height: 3rem;
		padding: 0.85rem 1rem;
		border-radius: var(--radius-md);
		border: 1px solid var(--color-border);
		background: rgba(255, 255, 255, 0.84);
	}

	.filters__toggle {
		align-self: center;
		font-weight: 700;
	}

	.text-link {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-height: 2.75rem;
		padding: 0.55rem 0.95rem;
		border-radius: 999px;
		border: 1px solid transparent;
		font-weight: 700;
		color: var(--color-accent-strong);
		text-decoration: none;
	}

	.text-link--disabled {
		color: var(--color-ink-muted);
		cursor: not-allowed;
		border-color: var(--color-border);
		background: rgba(255, 255, 255, 0.4);
	}

	.pager {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: var(--space-4);
	}

	@media (max-width: 860px) {
		.filters {
			grid-template-columns: 1fr;
		}
	}
</style>
