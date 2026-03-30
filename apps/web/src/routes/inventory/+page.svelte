<script lang="ts">
	import { navigating } from '$app/state';
	import { getCapability } from '$lib/capabilities';
	import Button from '$lib/components/Button.svelte';
	import Select from '$lib/components/Select.svelte';
	import Spinner from '$lib/components/Spinner.svelte';
	import InventoryTable from '$lib/features/inventory/InventoryTable.svelte';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
	const capability = getCapability('inventory');

	let isLoading = $derived(Boolean(navigating.to));
	let metricItems = $derived([
		{
			label: 'Auth',
			value: 'Scoped',
			detail: data.loadError
				? 'Current session is missing the required inventory scopes.'
				: 'Session-gated inventory surface.'
		},
		{
			label: 'Visible',
			value: String(data.inventory?.items.length ?? 0),
			detail: 'Rows currently rendered on this page.'
		},
		{
			label: 'Total matches',
			value: String(data.inventory?.totalCount ?? 0),
			detail: 'All records matching the current filters.'
		},
		{
			label: 'Page',
			value: String(data.filters.page),
			detail: `Limit ${data.filters.limit} per request.`
		},
		{
			label: 'Sort',
			value: `${data.filters.sortBy}:${data.filters.sortOrder}`,
			detail: 'Server-side ordering applied by the Spring API.'
		},
		{
			label: 'Status',
			value: data.loadError ? 'Blocked' : 'Live',
			detail: data.loadError ?? 'Inventory route is connected to the backend list endpoint.'
		}
	]);

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

<section class="inventory-page">
	<div class="page-header">
		<div class="page-header__copy">
			<div class="page-tag">
				<span>GET</span>
				<span>/api/v1/inventory/sku</span>
			</div>
			<h1 class="page-title">{capability.navLabel}</h1>
			<p class="page-desc">
				Search the tenant catalog, inspect tax payloads, and move directly into edits through the
				server-rendered inventory boundary.
			</p>
		</div>
		<div class={`status-pill ${data.loadError ? 'status-pill--warning' : 'status-pill--success'}`}>
			<div class="status-pill__dot"></div>
			{data.loadError ? 'Scope or API issue' : 'Protected surface'}
		</div>
	</div>

	<div class="metrics-bar">
		{#each metricItems as metric}
			<div class="metric-cell">
				<p class="metric-label">{metric.label}</p>
				<h2
					class={`metric-value ${metric.value === 'Live' ? 'metric-value--success' : metric.value === 'Blocked' ? 'metric-value--warning' : ''}`}
				>
					{metric.value}
				</h2>
				<p class="metric-sub">{metric.detail}</p>
			</div>
		{/each}
	</div>

	<div class="body-grid">
		<section class="body-panel">
			<div class="panel-title">
				<span>Filters</span>
				{#if isLoading}
					<Spinner label="Refreshing inventory" />
				{/if}
			</div>

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

				<div class="filters__actions">
					<Button type="submit">
						{#snippet children()}Apply filters{/snippet}
					</Button>
					<a class="text-link" href="/inventory/new">Create SKU</a>
				</div>

				<p class="sr-only" id="inventory-filter-help">
					Search by SKU, description, or NCM code, then apply filters to reload the current page.
				</p>
			</form>
		</section>

		<section class="body-panel">
			<div class="panel-title">Results</div>

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
	</div>
</section>

<style>
	h1,
	h2 {
		margin: 0;
	}

	.inventory-page {
		display: grid;
		min-width: 0;
		background: var(--bg);
		background-color: var(--bg) !important;
	}

	.page-header {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		gap: 1.5rem;
		padding: 1.5rem 1.75rem 1.25rem;
		border-bottom: 1px solid var(--border);
		background: var(--bg);
	}

	.page-header__copy {
		display: grid;
		gap: 0.4rem;
	}

	.page-tag {
		display: inline-flex;
		align-items: center;
		gap: 0.35rem;
		font-family: var(--font-mono);
		font-size: 0.7rem;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--text-faint);
	}

	.page-title {
		font-size: 1.3rem;
		font-weight: 500;
		letter-spacing: -0.01em;
		color: var(--text);
	}

	.page-desc {
		max-width: 56ch;
		font-size: 0.93rem;
		line-height: 1.5;
		color: var(--text-muted);
	}

	.status-pill {
		display: inline-flex;
		align-items: center;
		gap: 0.35rem;
		padding: 0.28rem 0.7rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		font-size: 0.78rem;
		font-family: var(--font-mono);
	}

	.status-pill__dot {
		width: 5px;
		height: 5px;
		border-radius: 50%;
		background: currentColor;
	}

	.status-pill--success {
		border-color: var(--success-border);
		background: var(--success-soft);
		color: var(--success);
	}

	.status-pill--warning {
		border-color: var(--warning-border);
		background: var(--warning-soft);
		color: var(--warning);
	}

	.metrics-bar {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
		border-bottom: 1px solid var(--border);
		background: var(--bg);
	}

	.metric-cell {
		display: grid;
		gap: 0.2rem;
		padding: 1rem 1.25rem;
		border-right: 1px solid var(--border);
		background: var(--bg);
	}

	.metric-cell:last-child {
		border-right: 0;
	}

	.metric-label {
		font-size: 0.7rem;
		font-family: var(--font-mono);
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--text-faint);
	}

	.metric-value {
		font-size: 1rem;
		font-weight: 500;
		font-family: var(--font-mono);
		color: var(--text);
		word-break: break-word;
	}

	.metric-value--success {
		color: var(--success);
	}

	.metric-value--warning {
		color: var(--warning);
	}

	.metric-sub {
		font-size: 0.78rem;
		font-family: var(--font-mono);
		color: var(--text-faint);
	}

	.body-grid {
		display: grid;
		grid-template-columns: minmax(300px, 0.95fr) minmax(0, 1.45fr);
	}

	.body-panel {
		padding: 1.5rem 1.75rem;
		background: var(--bg);
	}

	.body-panel:first-child {
		border-right: 1px solid var(--border);
	}

	.panel-title {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		margin-bottom: 1rem;
		font-size: 0.78rem;
		font-family: var(--font-mono);
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--text-faint);
	}

	.panel-title::after {
		content: '';
		flex: 1;
		height: 1px;
		background: var(--border);
	}

	.filters {
		display: grid;
		grid-template-columns: 1fr;
		gap: var(--space-4);
		padding: 1rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
		background: var(--bg-2);
		background-color: var(--bg-2) !important;
	}

	.filters__search,
	.filters__toggle {
		display: grid;
		gap: var(--space-2);
	}

	.filters__search input {
		min-height: 3.2rem;
		padding: 0.95rem 1rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		background: var(--bg-3);
		background-color: var(--bg-3) !important;
		color: var(--text);
		caret-color: var(--text);
	}

	.filters__search input::placeholder {
		color: var(--text-faint);
	}

	.filters__toggle {
		grid-auto-flow: column;
		grid-template-columns: auto 1fr;
		align-items: center;
		align-self: center;
		min-height: 3.2rem;
		padding: 0.9rem 1rem;
		border-radius: var(--radius-sm);
		border: 1px solid var(--border);
		background: var(--bg-3);
		background-color: var(--bg-3) !important;
		font-weight: 500;
	}

	.filters__toggle input {
		width: 1rem;
		height: 1rem;
		accent-color: var(--color-accent-strong);
	}

	.filters__actions {
		display: flex;
		flex-wrap: wrap;
		gap: var(--space-3);
	}

	.filters :global(.field) {
		padding: 0.85rem 0.9rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		background: var(--bg-3);
		background-color: var(--bg-3) !important;
	}

	.text-link {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-height: 2rem;
		padding: 0.38rem 0.85rem;
		border-radius: var(--radius-sm);
		border: 1px solid var(--border);
		font-weight: 500;
		color: var(--text-muted);
		text-decoration: none;
		background: var(--bg-2);
	}

	.text-link:hover {
		background: var(--bg-3);
		border-color: var(--border-strong);
		color: var(--text);
	}

	.text-link:focus,
	.text-link:focus-visible {
		background: var(--bg-3);
		border-color: var(--border-strong);
		color: var(--text);
		outline: 2px solid var(--color-accent-strong);
		outline-offset: 2px;
	}

	.text-link--disabled {
		color: var(--text-faint);
		cursor: not-allowed;
		border-color: var(--border);
		background: var(--bg-2);
	}

	.pager {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: var(--space-4);
		color: var(--text-muted);
		margin-top: 1rem;
	}

	@media (max-width: 860px) {
		.body-grid {
			grid-template-columns: 1fr;
		}

		.body-panel:first-child {
			border-right: 0;
			border-bottom: 1px solid var(--border);
		}
	}

	@media (max-width: 720px) {
		.page-header,
		.panel-title {
			flex-direction: column;
			align-items: flex-start;
		}

		.pager {
			flex-direction: column;
			align-items: stretch;
		}
	}
</style>
