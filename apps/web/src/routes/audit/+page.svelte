<script lang="ts">
	import { enhance } from '$app/forms';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import Button from '$lib/components/Button.svelte';
	import Card from '$lib/components/Card.svelte';
	import InlineNotice from '$lib/components/InlineNotice.svelte';
	import Input from '$lib/components/Input.svelte';
	import Select from '$lib/components/Select.svelte';
	import Spinner from '$lib/components/Spinner.svelte';
	import type { PageProps } from './$types';

	let { data, form }: PageProps = $props();

	let explainSkuId = $state('');
	$effect(() => { if (data.skuId) explainSkuId = data.skuId; });
	let explainLoading = $state(false);

	let queryLoading = $state(false);
	let reauditLoading = $state(false);
	let reauditSkuId = $state('');

	const LAW_TYPE_OPTIONS = [
		{ value: '', label: 'Any type' },
		{ value: 'lei', label: 'Lei' },
		{ value: 'decreto', label: 'Decreto' },
		{ value: 'convenio', label: 'Convênio' },
		{ value: 'instrucao_normativa', label: 'Instrução Normativa' },
		{ value: 'resolucao', label: 'Resolução' }
	];

	const K_OPTIONS = [
		{ value: '3', label: '3 results' },
		{ value: '5', label: '5 results' },
		{ value: '10', label: '10 results' },
		{ value: '20', label: '20 results' }
	];

	function confidenceTone(confidence: number): string {
		if (confidence >= 0.8) return 'high';
		if (confidence >= 0.5) return 'medium';
		return 'low';
	}

	function pct(v: number) {
		return `${Math.round(v * 100)}%`;
	}

	function handleExplainSubmit(e: SubmitEvent) {
		e.preventDefault();
		const id = explainSkuId.trim();
		if (!id) return;
		explainLoading = true;
		goto(`/audit?skuId=${encodeURIComponent(id)}`).finally(() => {
			explainLoading = false;
		});
	}
</script>

<svelte:head>
	<title>Audit | BrainBR</title>
	<meta name="description" content="Explain, query, and re-audit fiscal decisions." />
	<link rel="canonical" href={`${page.url.origin}/audit`} />
</svelte:head>

<div class="audit-page">
	<header class="audit-header">
		<p class="eyebrow">Audit Intelligence</p>
		<h1>Explain, search, and trigger</h1>
		<p class="lede">Understand why a fiscal decision was made and what to review next.</p>
	</header>

	<div class="audit-grid">
		<!-- ── EXPLAIN ── -->
		<section class="panel">
			<h2 class="panel__title">Explain a SKU</h2>
			<p class="panel__desc">Inspect the reasoning, tax output, and legal basis behind one SKU.</p>

			<form class="inline-form" onsubmit={handleExplainSubmit}>
				<Input
					id="explain-sku"
					name="skuId"
					label="SKU ID"
					placeholder="e.g. SKU-123"
					bind:value={explainSkuId}
					required
				/>
				<Button type="submit" disabled={explainLoading}>
					{#snippet children()}
						{#if explainLoading}<Spinner />{/if}
						Explain
					{/snippet}
				</Button>
			</form>

			{#if data.explainError}
				<InlineNotice variant="error" title="Explain failed" message={data.explainError} />
			{/if}

			{#if data.explain}
				{@const ex = data.explain}
				<div class="explain-result">
					<div class="explain-meta">
						<span class="mono">{ex.sku_id}</span>
						<span class={`confidence confidence--${confidenceTone(ex.confidence)}`}>
							{pct(ex.confidence)} confidence
						</span>
						<span class="chip">{ex.llm_model}</span>
					</div>

					{#if ex.audit_reasoning}
						<div class="reasoning-block">
							<p class="block-label">Reasoning</p>
							<p class="reasoning-text">{ex.audit_reasoning}</p>
						</div>
					{/if}

					<div class="tax-grid">
						<div class="tax-block">
							<p class="block-label">Reform taxes</p>
							{#if ex.reform_taxes && Object.keys(ex.reform_taxes).length > 0}
								<dl class="tax-list">
									{#each Object.entries(ex.reform_taxes) as [name, value]}
										<div>
											<dt>{name.toUpperCase()}</dt>
											<dd class="mono">{value}</dd>
										</div>
									{/each}
								</dl>
							{:else}
								<p class="muted">No reform taxes computed.</p>
							{/if}
						</div>

						<div class="tax-block">
							<p class="block-label">Source law</p>
							{#if ex.source_law && Object.keys(ex.source_law).length > 0}
								<dl class="tax-list">
									{#each Object.entries(ex.source_law) as [k, v]}
										<div>
											<dt>{k}</dt>
											<dd class="mono">{String(v)}</dd>
										</div>
									{/each}
								</dl>
							{:else}
								<p class="muted">No source law metadata.</p>
							{/if}
						</div>
					</div>

					<p class="timestamp muted">Audited {new Date(ex.created_at).toLocaleString('pt-BR')}</p>
				</div>
			{/if}
		</section>

		<!-- ── QUERY ── -->
		<section class="panel">
			<h2 class="panel__title">Semantic law query</h2>
			<p class="panel__desc">Search the audit knowledge base with filters for the legal context you need.</p>

			<form
				method="POST"
				action="?/query"
				class="query-form"
				use:enhance={() => {
					queryLoading = true;
					return async ({ update }) => {
						await update();
						queryLoading = false;
					};
				}}
			>
				<Input
					id="query-text"
					name="query"
					label="Query"
					placeholder="e.g. ICMS monofásico combustível interestadual"
					value={form?.queryInput?.query ?? ''}
					required
				/>
				<div class="query-filters">
					<Select
						id="query-law-type"
						name="law_type"
						label="Law type"
						value={form?.queryInput?.law_type ?? ''}
						options={LAW_TYPE_OPTIONS}
					/>
					<Input
						id="query-after"
						name="published_after"
						label="Published after"
						type="date"
						value={form?.queryInput?.published_after ?? ''}
					/>
					<Select
						id="query-k"
						name="k"
						label="Results"
						value={String(form?.queryInput?.k ?? 5)}
						options={K_OPTIONS}
					/>
				</div>

				<Button type="submit" disabled={queryLoading}>
					{#snippet children()}
						{#if queryLoading}<Spinner />{/if}
						Search
					{/snippet}
				</Button>
			</form>

			{#if form?.queryError}
				<InlineNotice variant="error" title="Query failed" message={form.queryError} />
			{/if}

			{#if form?.queryResults}
				{@const qr = form.queryResults}
				<div class="query-results">
					<p class="results-meta muted">{qr.total} result{qr.total !== 1 ? 's' : ''} for "{qr.query}"</p>
					{#each qr.results as result}
						<article class="result-item">
							<div class="result-item__top">
								<span class="chip">{result.law_type}</span>
								<span class="result-score muted">{pct(result.score)}</span>
								{#if result.published_at}
									<span class="muted mono">{result.published_at}</span>
								{/if}
							</div>
							<p class="result-ref">{result.law_ref}</p>
							<p class="result-content muted">{result.content}</p>
						</article>
					{/each}
				</div>
			{/if}
		</section>

		<!-- ── RE-AUDIT ── -->
		<section class="panel panel--reaudit">
			<h2 class="panel__title">Trigger re-audit</h2>
			<p class="panel__desc">Queue a fresh audit after catalog or legal inputs change.</p>

			<form
				method="POST"
				action="?/reaudit"
				class="inline-form"
				use:enhance={() => {
					reauditLoading = true;
					return async ({ update }) => {
						await update();
						reauditLoading = false;
					};
				}}
			>
				<Input
					id="reaudit-sku"
					name="skuId"
					label="SKU ID"
					placeholder="e.g. SKU-123"
					bind:value={reauditSkuId}
					required
				/>
				<Button type="submit" variant="secondary" disabled={reauditLoading}>
					{#snippet children()}
						{#if reauditLoading}<Spinner />{/if}
						Queue re-audit
					{/snippet}
				</Button>
			</form>

			{#if form?.reauditError}
				<InlineNotice variant="error" title="Re-audit failed" message={form.reauditError} />
			{/if}

			{#if form?.reauditJob}
				{@const job = form.reauditJob}
				<div class="reaudit-result">
					<InlineNotice
						variant="success"
						title="Re-audit queued"
						message={`Job ${job.job_id} queued for ${job.sku_id} — status: ${job.status}`}
					/>
					<dl class="job-meta">
						<div><dt>Job ID</dt><dd class="mono">{job.job_id}</dd></div>
						<div><dt>SKU</dt><dd class="mono">{job.sku_id}</dd></div>
						<div><dt>Queued at</dt><dd class="mono">{new Date(job.queued_at).toLocaleString('pt-BR')}</dd></div>
					</dl>
				</div>
			{/if}
		</section>
	</div>
</div>

<style>
	h1, h2, p { margin: 0; }

	.audit-page {
		display: grid;
		gap: 0;
		min-width: 0;
	}

	.audit-header {
		padding: 1.75rem 1.5rem 1.25rem;
		border-bottom: 1px solid var(--border);
		display: grid;
		gap: 0.35rem;
	}

	.audit-header h1 {
		font-size: 1.5rem;
		font-weight: 600;
	}

	.eyebrow {
		font-family: var(--font-mono);
		font-size: 0.72rem;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--text-muted);
	}

	.lede {
		color: var(--text-muted);
		font-size: 0.92rem;
	}

	.audit-grid {
		display: grid;
		grid-template-columns: 1fr 1fr;
		grid-template-rows: auto auto;
	}

	.panel {
		display: grid;
		gap: 1rem;
		padding: 1.5rem;
		align-content: start;
		border-bottom: 1px solid var(--border);
	}

	.panel:nth-child(odd) {
		border-right: 1px solid var(--border);
	}

	.panel--reaudit {
		grid-column: 1 / -1;
		grid-template-columns: minmax(0, 480px) 1fr;
		border-bottom: none;
	}

	.panel--reaudit > h2,
	.panel--reaudit > p {
		grid-column: 1 / -1;
	}

	.panel__title {
		font-size: 1rem;
		font-weight: 600;
	}

	.panel__desc {
		font-size: 0.86rem;
		color: var(--text-muted);
		margin-top: -0.5rem;
	}

	.inline-form {
		display: grid;
		gap: 0.75rem;
	}

	.query-form {
		display: grid;
		gap: 0.75rem;
	}

	.query-filters {
		display: grid;
		grid-template-columns: 1fr 1fr 1fr;
		gap: 0.75rem;
	}

	/* Explain result */
	.explain-result {
		display: grid;
		gap: 1rem;
		padding: 1rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
		background: var(--bg-2);
		background-color: var(--bg-2) !important;
		background-image: none !important;
	}

	.explain-meta {
		display: flex;
		align-items: center;
		gap: 0.65rem;
		flex-wrap: wrap;
	}

	.confidence {
		font-family: var(--font-mono);
		font-size: 0.75rem;
		padding: 0.18rem 0.5rem;
		border-radius: var(--radius-sm);
		border: 1px solid transparent;
	}

	.confidence--high {
		background: var(--success-soft);
		color: var(--success);
		border-color: var(--success-border);
	}

	.confidence--medium {
		background: var(--warning-soft);
		color: var(--warning);
		border-color: var(--warning-border);
	}

	.confidence--low {
		background: var(--danger-soft);
		color: var(--danger);
		border-color: var(--danger-border);
	}

	.chip {
		font-family: var(--font-mono);
		font-size: 0.72rem;
		padding: 0.18rem 0.42rem;
		border-radius: 3px;
		background: var(--bg-3);
		border: 1px solid var(--border);
		color: var(--text-muted);
	}

	.reasoning-block,
	.tax-block {
		display: grid;
		gap: 0.5rem;
	}

	.block-label {
		font-family: var(--font-mono);
		font-size: 0.72rem;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--text-muted);
	}

	.reasoning-text {
		font-size: 0.88rem;
		line-height: 1.6;
		color: var(--text);
	}

	.tax-grid {
		display: grid;
		grid-template-columns: 1fr 1fr;
		gap: 1rem;
	}

	.tax-list {
		display: grid;
		gap: 0.4rem;
		margin: 0;
	}

	.tax-list div {
		display: flex;
		justify-content: space-between;
		gap: 0.5rem;
		padding-bottom: 0.4rem;
		border-bottom: 1px solid var(--border);
		font-size: 0.86rem;
	}

	dt, dd { margin: 0; }

	.timestamp { font-size: 0.78rem; }

	/* Query results */
	.query-results {
		display: grid;
		gap: 0.65rem;
	}

	.results-meta {
		font-size: 0.82rem;
	}

	.result-item {
		display: grid;
		gap: 0.35rem;
		padding: 0.85rem 1rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
		background: var(--bg-2);
		background-color: var(--bg-2) !important;
		background-image: none !important;
	}

	.result-item__top {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		flex-wrap: wrap;
	}

	.result-score {
		font-family: var(--font-mono);
		font-size: 0.75rem;
	}

	.result-ref {
		font-size: 0.9rem;
		font-weight: 500;
		color: var(--text);
	}

	.result-content {
		font-size: 0.84rem;
		line-height: 1.5;
		display: -webkit-box;
		-webkit-line-clamp: 3;
		line-clamp: 3;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}

	/* Re-audit */
	.reaudit-result {
		display: grid;
		gap: 0.75rem;
	}

	.job-meta {
		display: grid;
		gap: 0.4rem;
		margin: 0;
		font-size: 0.86rem;
	}

	.job-meta div {
		display: flex;
		gap: 0.75rem;
	}

	.muted { color: var(--text-muted); }
	.mono { font-family: var(--font-mono); }

	@media (max-width: 960px) {
		.audit-grid {
			grid-template-columns: 1fr;
		}

		.panel:nth-child(odd) {
			border-right: none;
		}

		.panel--reaudit {
			grid-template-columns: 1fr;
		}

		.query-filters {
			grid-template-columns: 1fr 1fr;
		}

		.tax-grid {
			grid-template-columns: 1fr;
		}
	}

	@media (max-width: 600px) {
		.query-filters {
			grid-template-columns: 1fr;
		}
	}
</style>
