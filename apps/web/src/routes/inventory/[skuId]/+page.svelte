<script lang="ts">
	import { base } from '$app/paths';
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
	<a class="back-link" href="{base}/inventory">← Back to inventory</a>

	<div class="cluster">
		<p class="eyebrow">Inventory detail</p>
		<Badge text={data.item.isActive ? 'Active' : 'Inactive'} variant={data.item.isActive ? 'success' : 'warning'} />
	</div>

	<div class="cluster detail-header">
		<div class="stack">
			<h1 class="headline mono">{data.item.skuId}</h1>
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
			<h2 class="mono">{data.item.ncmCode}</h2>
			<p class="lede">Fiscal classification code.</p>
		</Card>
		<Card>
			<p class="eyebrow">Route</p>
			<h2 class="mono">{data.item.originState} → {data.item.destinationState}</h2>
			<p class="lede">Interstate tax route.</p>
		</Card>
		<Card>
			<p class="eyebrow">Last updated</p>
			<h2 class="mono">{formatInventoryTimestamp(data.item.updatedAt)}</h2>
			<p class="lede">Last modified.</p>
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
							<dd class="mono">{value}</dd>
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
							<dd class="mono">{value}</dd>
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

	.back-link {
		font-family: var(--font-mono);
		font-size: 0.82rem;
		color: var(--text-muted);
		text-decoration: none;
	}

	.back-link:hover {
		color: var(--text);
		text-decoration: underline;
	}

	.back-link:focus-visible {
		color: var(--text);
		outline: 2px solid var(--focus-ring);
		outline-offset: 2px;
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
