	<script lang="ts">
		import { enhance } from '$app/forms';
		import { goto } from '$app/navigation';
		import { page } from '$app/state';
		import { onMount } from 'svelte';
		import Button from '$lib/components/Button.svelte';
	import InlineNotice from '$lib/components/InlineNotice.svelte';
	import Input from '$lib/components/Input.svelte';
	import Spinner from '$lib/components/Spinner.svelte';
	import type { PageProps } from './$types';

	let { data, form }: PageProps = $props();

		let createLoading = $state(false);
		let filterLoading = $state(false);
		let filterSku = $state('');
		let filterType = $state('');
		let timestampValue = $state('');

		$effect(() => {
			filterSku = data.skuId ?? '';
			filterType = data.eventType ?? '';
		});

	function formatAmount(cents: number, currency: string) {
		return new Intl.NumberFormat('pt-BR', { style: 'currency', currency }).format(cents / 100);
	}

	function applyFilters(e: SubmitEvent) {
		e.preventDefault();
		filterLoading = true;
		const params = new URLSearchParams();
		if (filterSku.trim()) params.set('sku_id', filterSku.trim());
		if (filterType.trim()) params.set('event_type', filterType.trim());
		goto(`/split-payment?${params}`).finally(() => { filterLoading = false; });
	}

	function nextPageHref() {
		const p = new URLSearchParams({ page: String(data.page + 1) });
		if (data.skuId) p.set('sku_id', data.skuId);
		if (data.eventType) p.set('event_type', data.eventType);
		return `/split-payment?${p}`;
	}

		function defaultTimestamp() {
			const now = new Date();
			now.setMinutes(now.getMinutes() - now.getTimezoneOffset());
			return now.toISOString().slice(0, 16);
		}

		onMount(() => {
			timestampValue = defaultTimestamp();
		});
	</script>

<svelte:head>
	<title>Split Payment | BrainBR</title>
	<meta name="description" content="Track split-payment events from intake through tenant history." />
	<link rel="canonical" href={`${page.url.origin}/split-payment`} />
</svelte:head>

<div class="sp-page">
	<header class="sp-header">
		<p class="eyebrow">Split Payment Events</p>
		<h1>Events and history</h1>
		<p class="lede">Track split-payment events from intake through tenant history.</p>
	</header>

	<div class="sp-grid">
		<!-- ── CREATE ── -->
		<section class="panel">
			<h2 class="panel__title">Create event</h2>
			<p class="panel__desc">Submit a structured payment event with timestamps and integration metadata.</p>

			<form
				method="POST"
				action="?/create"
				class="create-form"
				use:enhance={() => {
					createLoading = true;
					return async ({ update }) => { await update(); createLoading = false; };
				}}
			>
				<Input id="sp-sku" name="sku_id" label="SKU ID" placeholder="SKU-123" required value={form?.createResult?.sku_id ?? ''} />
				<Input id="sp-type" name="event_type" label="Event type" placeholder="split_payment_authorized" required value="" />
				<div class="row-2">
						<Input id="sp-amount" name="amount" label="Amount (BRL)" type="number" placeholder="154.90" required step="0.01" value="" />
					<Input id="sp-currency" name="currency" label="Currency" value="BRL" />
				</div>
				<Input id="sp-idem" name="idempotency_key" label="Idempotency key" placeholder="split-SKU-123-…" required value="" />
					<Input id="sp-ts" name="timestamp" label="Timestamp" type="datetime-local" required value={timestampValue} />

				<Button type="submit" disabled={createLoading}>
					{#snippet children()}
						{#if createLoading}<Spinner />{/if}
						Create event
					{/snippet}
				</Button>
			</form>

			{#if form?.createError}
				<InlineNotice variant="error" title="Create failed" message={form.createError} />
			{/if}

			{#if form?.createResult}
				{@const r = form.createResult}
				<div class="result-card">
					<InlineNotice variant="success" title="Event created" message={`ID ${r.id} — status: ${r.status}`} />
					<dl class="result-meta">
						<div><dt>ID</dt><dd class="mono">{r.id}</dd></div>
						<div><dt>SKU</dt><dd class="mono">{r.sku_id}</dd></div>
						<div><dt>Created</dt><dd class="mono">{new Date(r.created_at).toLocaleString('pt-BR')}</dd></div>
					</dl>
				</div>
			{/if}
		</section>

		<!-- ── LIST ── -->
		<section class="panel">
			<h2 class="panel__title">Event history</h2>
			<p class="panel__desc">List events with paging and optional SKU or event-type filters.</p>

			<form class="filter-form" onsubmit={applyFilters}>
				<Input id="filter-sku" name="sku_id" label="Filter by SKU" placeholder="SKU-123" bind:value={filterSku} />
				<Input id="filter-type" name="event_type" label="Filter by type" placeholder="split_payment_authorized" bind:value={filterType} />
				<Button type="submit" variant="secondary" disabled={filterLoading}>
					{#snippet children()}
						{#if filterLoading}<Spinner />{/if}
						Filter
					{/snippet}
				</Button>
			</form>

			{#if data.listError}
				<InlineNotice variant="error" title="Load failed" message={data.listError} />
			{/if}

			{#if data.list}
				{@const list = data.list}
				<div class="list-meta muted">
					{list.total_count} event{list.total_count !== 1 ? 's' : ''}
					{#if data.skuId} · SKU {data.skuId}{/if}
					{#if data.eventType} · {data.eventType}{/if}
				</div>

				{#if list.items.length === 0}
					<p class="muted">No events found.</p>
				{:else}
					<div class="event-list">
						{#each list.items as ev}
							<article class="event-item">
								<div class="event-item__top">
									<span class="chip">{ev.event_type}</span>
									<span class="amount">{formatAmount(ev.amount, ev.currency)}</span>
									<span class="muted mono">{new Date(ev.timestamp).toLocaleString('pt-BR')}</span>
								</div>
								<div class="event-item__ids">
									<span class="muted mono">{ev.sku_id}</span>
									<span class="muted mono faint">{ev.id}</span>
								</div>
							</article>
						{/each}
					</div>

					{#if list.has_more}
						<a class="page-link" href={nextPageHref()}>Next page →</a>
					{/if}
				{/if}
			{/if}
		</section>
	</div>
</div>

<style>
	h1, h2, p { margin: 0; }

	.sp-page { display: grid; gap: 0; min-width: 0; }

	.sp-header {
		padding: 1.75rem 1.5rem 1.25rem;
		border-bottom: 1px solid var(--border);
		display: grid;
		gap: 0.35rem;
	}

	.sp-header h1 { font-size: 1.5rem; font-weight: 600; }

	.eyebrow {
		font-family: var(--font-mono);
		font-size: 0.72rem;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--text-muted);
	}

	.lede { color: var(--text-muted); font-size: 0.92rem; }

	.sp-grid {
		display: grid;
		grid-template-columns: 1fr 1fr;
	}

	.panel {
		display: grid;
		gap: 1rem;
		padding: 1.5rem;
		align-content: start;
	}

	.panel:first-child { border-right: 1px solid var(--border); }

	.panel__title { font-size: 1rem; font-weight: 600; }
	.panel__desc { font-size: 0.86rem; color: var(--text-muted); margin-top: -0.5rem; }

	.create-form, .filter-form { display: grid; gap: 0.75rem; }

	.row-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; }

	.result-card { display: grid; gap: 0.75rem; }

	.result-meta {
		display: grid;
		gap: 0.35rem;
		margin: 0;
		font-size: 0.86rem;
	}

	.result-meta div { display: flex; gap: 0.75rem; }
	dt, dd { margin: 0; }
	dt { color: var(--text-muted); min-width: 4rem; }

	.list-meta { font-size: 0.82rem; }

	.event-list { display: grid; gap: 0.5rem; }

	.event-item {
		display: grid;
		gap: 0.3rem;
		padding: 0.75rem 1rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
		background: var(--bg-2);
		background-color: var(--bg-2) !important;
		background-image: none !important;
	}

	.event-item__top {
		display: flex;
		align-items: center;
		gap: 0.65rem;
		flex-wrap: wrap;
	}

	.event-item__ids {
		display: flex;
		gap: 1rem;
		flex-wrap: wrap;
	}

	.amount {
		font-family: var(--font-mono);
		font-size: 0.9rem;
		font-weight: 500;
		color: var(--text);
	}

	.chip {
		font-family: var(--font-mono);
		font-size: 0.72rem;
		padding: 0.18rem 0.42rem;
		border-radius: 3px;
		background: var(--accent-soft);
		border: 1px solid var(--accent-border);
		color: var(--accent);
	}

	.page-link {
		font-family: var(--font-mono);
		font-size: 0.82rem;
		color: var(--accent);
	}

	.faint { color: var(--text-faint) !important; font-size: 0.75rem; }
	.muted { color: var(--text-muted); }
	.mono { font-family: var(--font-mono); }

	@media (max-width: 960px) {
		.sp-grid { grid-template-columns: 1fr; }
		.panel:first-child { border-right: none; border-bottom: 1px solid var(--border); }
	}
</style>
