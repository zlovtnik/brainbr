<script lang="ts">
	import Badge from '$lib/components/Badge.svelte';
	import Button from '$lib/components/Button.svelte';
	import EmptyState from '$lib/components/EmptyState.svelte';
	import TableShell from '$lib/components/TableShell.svelte';
	import type { InventoryListView } from '$lib/features/inventory/types';
	import { formatInventoryTimestamp } from '$lib/utils/date';

	interface Props {
		inventory: InventoryListView | null;
	}

	let { inventory }: Props = $props();
</script>

{#if inventory && inventory.items.length > 0}
	<TableShell caption={`Inventory results (${inventory.totalCount})`}>
		<thead>
			<tr>
				<th scope="col">SKU</th>
				<th scope="col">Description</th>
				<th scope="col">Route</th>
				<th scope="col">Status</th>
				<th scope="col">Updated</th>
				<th scope="col">Action</th>
			</tr>
		</thead>
		<tbody>
			{#each inventory.items as item}
				<tr>
					<td><strong class="mono">{item.skuId}</strong></td>
					<td>{item.description}</td>
					<td><span class="mono">{item.originState} → {item.destinationState}</span></td>
					<td>
						<Badge
							text={item.isActive ? 'Active' : 'Inactive'}
							variant={item.isActive ? 'success' : 'warning'}
						/>
					</td>
					<td><span class="mono">{formatInventoryTimestamp(item.updatedAt)}</span></td>
					<td
						><a class="table-link" href={`/inventory/${encodeURIComponent(item.skuId)}`}>View</a
						></td
					>
				</tr>
			{/each}
		</tbody>
	</TableShell>
{:else}
	<EmptyState
		message="Adjust your filters or create the first SKU to populate the fiscal catalog."
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
	.table-link {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-height: 2.75rem;
		padding: 0.65rem 0.95rem;
		border-radius: var(--radius-sm);
		border: 1px solid var(--border);
		text-decoration: none;
		font-weight: 500;
		color: var(--text-muted);
		background: var(--bg-2);
	}

	.table-link:hover {
		background: var(--bg-3);
		border-color: var(--border-strong);
		color: var(--text);
	}
</style>
