<script lang="ts">
	import { page } from '$app/state';
	import Badge from '$lib/components/Badge.svelte';
	import Button from '$lib/components/Button.svelte';
	import Card from '$lib/components/Card.svelte';
	import InlineNotice from '$lib/components/InlineNotice.svelte';
	import { formatInventoryTimestamp } from '$lib/utils/date';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
</script>

<svelte:head>
	<title>{data.item.skuId} | Inventory</title>
	<meta
		name="description"
		content={`Inventory detail for SKU ${data.item.skuId}: ${data.item.description}`}
	/>
	<link rel="canonical" href={`${page.url.origin}/inventory/${encodeURIComponent(data.item.skuId)}`} />
</svelte:head>

<section class="stack">
	<div class="cluster">
		<p class="eyebrow">Inventory detail</p>
		<Badge text={data.item.isActive ? 'Active' : 'Inactive'} variant={data.item.isActive ? 'success' : 'warning'} />
	</div>

	<div class="cluster detail-header">
		<div class="stack">
			<h1 class="headline">{data.item.skuId}</h1>
			<p class="lede">{data.item.description}</p>
		</div>
		<Button href={`/inventory/${encodeURIComponent(data.item.skuId)}/edit`}>
			{#snippet children()}Edit SKU{/snippet}
		</Button>
	</div>

	{#if data.successMessage}
		<InlineNotice message={data.successMessage} title="Inventory saved" variant="success" />
	{/if}

	<div class="meta-grid">
		<Card>
			<p class="eyebrow">Classification</p>
			<h2>{data.item.ncmCode}</h2>
			<p class="lede">NCM code used for inventory transition analysis.</p>
		</Card>
		<Card>
			<p class="eyebrow">Route</p>
			<h2>{data.item.originState} to {data.item.destinationState}</h2>
			<p class="lede">Origin and destination states carried to the API payload.</p>
		</Card>
		<Card>
			<p class="eyebrow">Last updated</p>
			<h2>{formatInventoryTimestamp(data.item.updatedAt)}</h2>
			<p class="lede">Timestamp returned by the backend inventory contract.</p>
		</Card>
	</div>

	<div class="tax-grid">
		<Card>
			{#snippet header()}
				<h2>Legacy taxes</h2>
			{/snippet}
			{#if Object.keys(data.item.legacyTaxes).length > 0}
				<dl class="tax-list">
					{#each Object.entries(data.item.legacyTaxes) as [name, value]}
						<div>
							<dt>{name.toUpperCase()}</dt>
							<dd>{value}</dd>
						</div>
					{/each}
				</dl>
			{:else}
				<p class="lede">No legacy taxes were supplied for this SKU.</p>
			{/if}
		</Card>
		<Card>
			{#snippet header()}
				<h2>Reform taxes</h2>
			{/snippet}
			{#if Object.keys(data.item.reformTaxes).length > 0}
				<dl class="tax-list">
					{#each Object.entries(data.item.reformTaxes) as [name, value]}
						<div>
							<dt>{name.toUpperCase()}</dt>
							<dd>{value}</dd>
						</div>
					{/each}
				</dl>
			{:else}
				<p class="lede">No reform tax output is available yet for this SKU.</p>
			{/if}
		</Card>
	</div>
</section>

<style>
	h2 {
		margin: 0;
		font-size: 1.75rem;
	}

	.detail-header {
		justify-content: space-between;
	}

	.tax-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
		gap: var(--space-5);
	}

	.tax-list {
		display: grid;
		gap: var(--space-3);
		margin: 0;
	}

	.tax-list div {
		display: flex;
		justify-content: space-between;
		gap: var(--space-3);
		padding-bottom: var(--space-3);
		border-bottom: 1px solid var(--color-border);
	}

	dt,
	dd {
		margin: 0;
	}
</style>
