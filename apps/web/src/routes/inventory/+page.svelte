<script lang="ts">
	import { navigating } from '$app/state';
	import SectionPanel from '$lib/components/SectionPanel.svelte';
	import Spinner from '$lib/components/Spinner.svelte';
	import StatStrip from '$lib/components/StatStrip.svelte';
	import WorkspaceHeader from '$lib/components/WorkspaceHeader.svelte';
	import { getCapability } from '$lib/capabilities';
	import Select from '$lib/components/Select.svelte';
	import InventoryTable from '$lib/features/inventory/InventoryTable.svelte';
	import type { PageProps } from './$types';

	type InventoryStatTone = 'accent' | 'success' | 'warning' | 'default';

	let { data }: PageProps = $props();
	const capability = getCapability('inventory');

	let isLoading = $derived(Boolean(navigating.to));
	let statItems = $derived<
		{ label: string; value: string; detail: string; tone: InventoryStatTone }[]
	>([
		{
			label: 'Access',
			value: data.loadError ? 'Blocked' : 'Scoped',
			detail: data.loadError
				? 'Current session is missing the required inventory scopes.'
				: 'Session-gated inventory surface is available.',
			tone: data.loadError ? 'warning' : 'success'
		},
		{
			label: 'Visible results',
			value: String(data.inventory?.items.length ?? 0),
			detail: `${data.inventory?.totalCount ?? 0} total matches for the current filters.`,
			tone: 'accent'
		},
		{
			label: 'Current view',
			value: `P${data.filters.page} · ${data.filters.sortBy}:${data.filters.sortOrder}`,
			detail: `Limit ${data.filters.limit} per request.`,
			tone: 'default'
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
	<WorkspaceHeader
		tag={['GET', '/api/v1/inventory/sku']}
		title={capability.navLabel}
		description="Search the tenant catalog, inspect tax payloads, and move directly into edits through the server-rendered inventory boundary."
		statusLabel={data.loadError ? 'Scope or API issue' : 'Server-rendered inventory'}
		statusTone={data.loadError ? 'warning' : 'success'}
		primaryAction={{ href: '/inventory/new', label: 'Create SKU' }}
	/>

	<StatStrip items={statItems} />

	<div class="inventory-stack">
		<SectionPanel
			title="Filters"
			subtitle="Keep search first, then tune sorting and visibility before reloading the result set."
		>
			{#snippet meta()}
				{#if isLoading}
					<Spinner label="Refreshing inventory" />
				{/if}
			{/snippet}

			{#snippet children()}
			<form aria-describedby="inventory-filter-help" class="filters" method="GET" role="search">
				<input type="hidden" name="limit" value={data.filters.limit} />
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

				<div class="filters__secondary">
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
				</div>

				<div class="filters__actions">
					<button class="text-link text-link--primary" type="submit">Apply filters</button>
				</div>

				<p class="sr-only" id="inventory-filter-help">
					Search by SKU, description, or NCM code, then apply filters to reload the current page.
				</p>
			</form>
			{/snippet}
		</SectionPanel>

		<SectionPanel
			title="Results"
			subtitle="Keep the current page of inventory visible immediately after filter changes."
		>
			{#snippet children()}
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
						<span class="pager__current">Page {data.filters.page}</span>
						{#if data.inventory.hasMore}
							<a class="text-link" href={buildPageHref(data.filters.page + 1)}>Next page</a>
						{:else}
							<span aria-disabled="true" class="text-link text-link--disabled" tabindex="-1"
								>Next page</span
							>
						{/if}
					</nav>
				{/if}
			{/snippet}
		</SectionPanel>
	</div>
</section>

<style>
	.inventory-page {
		display: grid;
		min-width: 0;
		background: var(--bg);
		background-color: var(--bg) !important;
	}

	.inventory-stack {
		display: grid;
	}

	.inventory-stack :global(.section-panel + .section-panel) {
		border-top: 1px solid var(--border);
	}

	.filters {
		display: grid;
		gap: var(--space-4);
	}

	.filters__search,
	.filters__toggle {
		display: grid;
		gap: var(--space-2);
	}

	.filters__search span,
	.filters__toggle span {
		font-weight: 500;
		color: var(--text);
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

	.filters__secondary {
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		gap: var(--space-4);
	}

	.filters :global(.field) {
		padding: 0.85rem 0.9rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		background: var(--bg-3);
		background-color: var(--bg-3) !important;
	}

	.filters__toggle {
		grid-auto-flow: column;
		grid-template-columns: auto 1fr;
		align-items: center;
		align-self: end;
		min-height: 3.2rem;
		padding: 0.9rem 1rem;
		border-radius: var(--radius-sm);
		border: 1px solid var(--border);
		background: var(--bg-3);
		background-color: var(--bg-3) !important;
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

	.text-link {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-height: 2.75rem;
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

	.text-link--primary {
		cursor: pointer;
	}

	.text-link--disabled {
		color: var(--text-faint);
		cursor: not-allowed;
		border-color: var(--border);
		background: var(--bg-2);
	}

	.pager {
		display: grid;
		grid-template-columns: repeat(3, minmax(0, auto));
		align-items: center;
		justify-content: space-between;
		gap: var(--space-4);
		margin-top: 1rem;
		color: var(--text-muted);
	}

	.pager__current {
		justify-self: center;
		font-family: var(--font-mono);
		font-size: 0.82rem;
		color: var(--text-faint);
	}

	@media (max-width: 860px) {
		.filters__secondary {
			grid-template-columns: 1fr;
		}
	}

	@media (max-width: 720px) {
		.filters__actions .text-link {
			width: 100%;
		}

		.pager {
			grid-template-columns: 1fr;
		}

		.pager__current {
			justify-self: start;
		}
	}
</style>
