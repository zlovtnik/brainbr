<script lang="ts">
	import Badge from '$lib/components/Badge.svelte';
	import Card from '$lib/components/Card.svelte';
	import InlineNotice from '$lib/components/InlineNotice.svelte';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
</script>

<svelte:head>
	<title>{data.item.skuId} | Inventory</title>
	<meta name="description" content="Inventory detail for SKU {data.item.skuId}: {data.item.description}" />
	<link rel="canonical" href="/inventory/{data.item.skuId}" />
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
		<a class="detail-link" href={`/inventory/${data.item.skuId}/edit`}>Edit SKU</a>
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
			<h2>{new Date(data.item.updatedAt).toLocaleString()}</h2>
			<p class="lede">Timestamp returned by the backend inventory contract.</p>
		</Card>
	</div>

	<div class="tax-grid">
		<Card>
			{#snippet header()}
				<h2>Legacy taxes</h2>
			{/snippet}
			<dl class="tax-list">
				{#each Object.entries(data.item.legacyTaxes) as [name, value]}
					<div>
						<dt>{name.toUpperCase()}</dt>
						<dd>{value}</dd>
					</div>
				{:else}
					<p class="lede">No legacy taxes were supplied for this SKU.</p>
				{/each}
			</dl>
		</Card>
		<Card>
			{#snippet header()}
				<h2>Reform taxes</h2>
			{/snippet}
			<dl class="tax-list">
				{#each Object.entries(data.item.reformTaxes) as [name, value]}
					<div>
						<dt>{name.toUpperCase()}</dt>
						<dd>{value}</dd>
					</div>
				{:else}
					<p class="lede">No reform tax output is available yet for this SKU.</p>
				{/each}
			</dl>
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

	.detail-link {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-height: 2.9rem;
		padding: 0.8rem 1.15rem;
		border-radius: 999px;
		background: linear-gradient(135deg, var(--color-accent) 0%, #15865a 100%);
		color: white;
		text-decoration: none;
		font-weight: 700;
		box-shadow: var(--shadow-soft);
	}
</style>
