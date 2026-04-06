<script lang="ts">
	import Badge from '$lib/components/Badge.svelte';
	import Button from '$lib/components/Button.svelte';
	import EmptyState from '$lib/components/EmptyState.svelte';
	import type { InventoryListView } from '$lib/features/inventory/types';
	import { formatInventoryTimestamp } from '$lib/utils/date';

	interface Props {
		inventory: InventoryListView;
	}

	let { inventory }: Props = $props();
</script>

{#if inventory.items.length > 0}
	<div class="table-meta">
		<span class="table-count">{inventory.totalCount} SKU{inventory.totalCount !== 1 ? 's' : ''}</span>
	</div>
	<div class="table-wrap" role="region" aria-label="Inventory results">
		<table class="inv-table">
			<thead>
				<tr>
					<th scope="col">SKU</th>
					<th scope="col">Description</th>
					<th scope="col">Route</th>
					<th scope="col">Status</th>
					<th scope="col">Updated</th>
					<th scope="col"><span class="sr-only">Actions</span></th>
				</tr>
			</thead>
			<tbody>
				{#each inventory.items as item}
					<tr class="inv-row">
						<td class="cell-sku"><span class="mono">{item.skuId}</span></td>
						<td class="cell-desc">{item.description}</td>
						<td class="cell-route">
							<span class="mono route">{item.originState} &rarr; {item.destinationState}</span>
						</td>
						<td class="cell-status">
							<Badge
								text={item.isActive ? 'Active' : 'Inactive'}
								variant={item.isActive ? 'success' : 'warning'}
							/>
						</td>
						<td class="cell-date">
							<span class="mono">{formatInventoryTimestamp(item.updatedAt)}</span>
						</td>
						<td class="cell-action">
							<a
								class="row-link"
								href="/inventory/{encodeURIComponent(item.skuId)}"
								aria-label="View SKU {item.skuId}"
							>View</a>
						</td>
					</tr>
				{/each}
			</tbody>
		</table>
	</div>
{:else}
	<EmptyState
		message="Adjust your search or create the first SKU to populate the fiscal catalog."
		title="No inventory matched"
	>
		{#snippet action()}
			<Button href="/inventory/new">
				{#snippet children()}Create SKU{/snippet}
			</Button>
		{/snippet}
	</EmptyState>
{/if}

<style>
	.sr-only {
		position: absolute;
		width: 1px;
		height: 1px;
		overflow: hidden;
		clip: rect(0 0 0 0);
		white-space: nowrap;
	}

	.table-meta {
		display: flex;
		align-items: center;
		padding-bottom: 0.5rem;
	}

	.table-count {
		font-size: 0.72rem;
		font-family: var(--font-mono);
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--text-faint);
	}

	.table-wrap {
		overflow-x: auto;
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
	}

	.inv-table {
		width: 100%;
		border-collapse: collapse;
		font-size: 0.86rem;
	}

	.inv-table thead tr {
		border-bottom: 1px solid var(--border);
		background: var(--bg-1);
	}

	.inv-table th {
		padding: 0.45rem 0.85rem;
		font-size: 0.72rem;
		font-family: var(--font-mono);
		font-weight: 400;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--text-faint);
		text-align: left;
		white-space: nowrap;
	}

	.inv-row {
		border-bottom: 1px solid var(--border);
		transition: background 80ms;
	}

	.inv-row:last-child {
		border-bottom: none;
	}

	.inv-row:hover {
		background: var(--bg-2);
	}

	.inv-table td {
		padding: 0.55rem 0.85rem;
		color: var(--text-muted);
		vertical-align: middle;
	}

	.cell-sku .mono {
		color: var(--text);
		font-family: var(--font-mono);
		font-size: 0.84rem;
	}

	.cell-desc {
		color: var(--text);
		max-width: 260px;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
	}

	.route {
		font-family: var(--font-mono);
		font-size: 0.82rem;
		color: var(--text-muted);
	}

	.cell-date .mono {
		font-family: var(--font-mono);
		font-size: 0.8rem;
		color: var(--text-faint);
	}

	.cell-action {
		width: 1%;
		white-space: nowrap;
	}

	.row-link {
		display: inline-flex;
		align-items: center;
		min-height: 2rem;
		padding: 0.28rem 0.6rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		background: var(--bg-2);
		color: var(--text-muted);
		font-size: 0.8rem;
		font-family: var(--font-mono);
		text-decoration: none;
	}

	.row-link:hover {
		background: var(--bg-3);
		border-color: var(--border-strong);
		color: var(--text);
	}

	.row-link:focus-visible {
		outline: 2px solid var(--accent);
		outline-offset: 2px;
	}
</style>
