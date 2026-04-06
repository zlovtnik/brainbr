<script lang="ts">
	import { page } from '$app/state';
	import InlineNotice from '$lib/components/InlineNotice.svelte';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();

	let info = $derived(data.platformInfo ?? {
		service: 'fiscalbrain-br',
		embeddingModel: 'text-embedding-3-small',
		llmModel: 'gpt-4o'
	});
	let isLive = $derived(Boolean(data.platformInfo));
</script>

<svelte:head>
	<title>Platform | BrainBR</title>
	<meta name="description" content="Inspect BrainBR platform metadata and confirm the live service and model profile." />
	<link rel="canonical" href={`${page.url.origin}/platform`} />
</svelte:head>

<div class="platform-page">
	<header class="platform-header">
		<p class="eyebrow">Operations Overview</p>
		<h1>Platform metadata</h1>
		<p class="lede">See which models and services are driving the workspace right now.</p>
	</header>

	<div class="platform-body">
		{#if data.platformError}
			<InlineNotice variant="error" title="Live probe failed" message={data.platformError} />
		{/if}

		<div class="model-grid">
			<div class="model-card">
				<p class="model-card__label">Service</p>
				<p class="model-card__value mono">{info.service}</p>
				<p class="model-card__detail">{isLive ? 'Live response.' : 'Reference value.'}</p>
				<span class={`status-dot ${isLive ? 'status-dot--live' : 'status-dot--ref'}`}>
					{isLive ? 'live' : 'reference'}
				</span>
			</div>

			<div class="model-card">
				<p class="model-card__label">Embedding model</p>
				<p class="model-card__value mono">{info.embeddingModel}</p>
				<p class="model-card__detail">Powers vector retrieval and RAG search.</p>
				<span class={`status-dot ${isLive ? 'status-dot--live' : 'status-dot--ref'}`}>
					{isLive ? 'live' : 'reference'}
				</span>
			</div>

			<div class="model-card">
				<p class="model-card__label">LLM model</p>
				<p class="model-card__value mono">{info.llmModel}</p>
				<p class="model-card__detail">Drives audit reasoning and explainability output.</p>
				<span class={`status-dot ${isLive ? 'status-dot--live' : 'status-dot--ref'}`}>
					{isLive ? 'live' : 'reference'}
				</span>
			</div>
		</div>

	</div>
</div>

<style>
	h1, p { margin: 0; }

	.platform-page {
		display: grid;
		gap: 0;
		min-width: 0;
	}

	.platform-header {
		padding: 1.75rem 1.5rem 1.25rem;
		border-bottom: 1px solid var(--border);
		display: grid;
		gap: 0.35rem;
	}

	.platform-header h1 {
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

	.lede { color: var(--text-muted); font-size: 0.92rem; }

	.platform-body {
		display: grid;
		gap: 1.5rem;
		padding: 1.5rem;
	}

	.model-grid {
		display: grid;
		grid-template-columns: repeat(3, 1fr);
		gap: 1rem;
	}

	.model-card {
		display: grid;
		gap: 0.4rem;
		padding: 1.25rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
		background: var(--bg-2);
		background-color: var(--bg-2) !important;
		background-image: none !important;
		align-content: start;
	}

	.model-card__label {
		font-family: var(--font-mono);
		font-size: 0.72rem;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--text-muted);
	}

	.model-card__value {
		font-size: 1rem;
		font-weight: 500;
		color: var(--text);
	}

	.model-card__detail {
		font-size: 0.82rem;
		color: var(--text-muted);
		line-height: 1.5;
	}

	.status-dot {
		display: inline-flex;
		align-items: center;
		width: fit-content;
		gap: 0.35rem;
		font-family: var(--font-mono);
		font-size: 0.7rem;
		padding: 0.15rem 0.45rem;
		border-radius: 3px;
		border: 1px solid transparent;
	}

	.status-dot::before {
		content: '';
		width: 5px;
		height: 5px;
		border-radius: 50%;
		background: currentColor;
	}

	.status-dot--live {
		background: var(--success-soft);
		color: var(--success);
		border-color: var(--success-border);
	}

	.status-dot--ref {
		background: var(--warning-soft);
		color: var(--warning);
		border-color: var(--warning-border);
	}

	.muted { color: var(--text-muted); font-size: 0.86rem; }
	.mono { font-family: var(--font-mono); }

	@media (max-width: 720px) {
		.model-grid { grid-template-columns: 1fr; }
	}
</style>
