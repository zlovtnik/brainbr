<script lang="ts">
	import Badge from '$lib/components/Badge.svelte';
	import EmptyState from '$lib/components/EmptyState.svelte';
	import InlineNotice from '$lib/components/InlineNotice.svelte';
	import TableShell from '$lib/components/TableShell.svelte';
	import type { InventoryListView } from '$lib/features/inventory/types';
	import { formatInventoryTimestamp } from '$lib/utils/date';

	interface Props {
		inventory: InventoryListView | null;
		loadError?: string;
		successMessage?: string;
	}

	let { inventory, loadError, successMessage }: Props = $props();
</script>

{#if successMessage}
	<InlineNotice message={successMessage} title="Inventory updated" variant="success" />
{/if}

{#if loadError}
	<InlineNotice message={loadError} title="Unable to load inventory" variant="error" />
{:else if inventory && inventory.items.length > 0}
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
					<td><strong>{item.skuId}</strong></td>
					<td>{item.description}</td>
					<td>{item.originState} to {item.destinationState}</td>
					<td>
						<Badge
							text={item.isActive ? 'Active' : 'Inactive'}
							variant={item.isActive ? 'success' : 'warning'}
						/>
					</td>
					<td>{formatInventoryTimestamp(item.updatedAt)}</td>
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
		message="Adjust your filters or create the first SKU to populate this workspace."
		title="No inventory matched"
	>
		{#snippet action()}
			<a class="table-link" href="/inventory/new">Create SKU</a>
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
