<script lang="ts">
	import { enhance } from '$app/forms';
	import { page } from '$app/state';
	import Button from '$lib/components/Button.svelte';
	import InlineNotice from '$lib/components/InlineNotice.svelte';
	import Input from '$lib/components/Input.svelte';
	import Select from '$lib/components/Select.svelte';
	import Spinner from '$lib/components/Spinner.svelte';
	import type { PageProps } from './$types';

	let { form }: PageProps = $props();

	let loading = $state(false);

	const LAW_TYPE_OPTIONS = [
		{ value: '', label: 'Select type…' },
		{ value: 'lei', label: 'Lei' },
		{ value: 'decreto', label: 'Decreto' },
		{ value: 'convenio', label: 'Convênio' },
		{ value: 'instrucao_normativa', label: 'Instrução Normativa' },
		{ value: 'resolucao', label: 'Resolução' },
		{ value: 'portaria', label: 'Portaria' }
	];
</script>

<svelte:head>
	<title>Ingestion | BrainBR</title>
	<meta name="description" content="Queue legal ingestion jobs for the BrainBR knowledge base." />
	<link rel="canonical" href={`${page.url.origin}/ingestion`} />
</svelte:head>

<div class="ing-page">
	<header class="ing-header">
		<p class="eyebrow">Regulatory Ingestion</p>
		<h1>Queue legal inputs</h1>
		<p class="lede">Bring new law references into the workspace without breaking operational flow.</p>
	</header>

	<div class="ing-body">
		<section class="panel">
			<h2 class="panel__title">Queue legal content</h2>
			<p class="panel__desc">Submit a law reference with either a source URL or raw content and optional dates and tags.</p>

			<form
				method="POST"
				action="?/queue"
				class="queue-form"
				use:enhance={() => {
					loading = true;
					return async ({ update }) => { await update(); loading = false; };
				}}
			>
				<div class="row-2">
					<Input id="law-ref" name="law_ref" label="Law reference" placeholder="Convênio ICMS 17/2026" required value={form?.law_ref ?? ''} />
					<Select id="law-type" name="law_type" label="Law type" value={form?.law_type ?? ''} options={LAW_TYPE_OPTIONS} />
				</div>

				<Input id="source-url" name="source_url" label="Source URL" placeholder="https://www.confaz.fazenda.gov.br/…" value={form?.source_url ?? ''} />

				<div class="field">
					<label class="field__label" for="content">Raw content <span class="optional">(or paste text)</span></label>
					<textarea id="content" name="content" rows="4" placeholder="Paste the full legal text here if no URL is available…">{form?.content ?? ''}</textarea>
				</div>

				<div class="row-2">
					<Input id="published-at" name="published_at" label="Published at" type="date" value={form?.published_at ?? ''} />
					<Input id="effective-at" name="effective_at" label="Effective at" type="date" value={form?.effective_at ?? ''} />
				</div>

				<Input id="tags" name="tags" label="Tags" placeholder="combustiveis, icms, interestadual" hint="Comma-separated" value={form?.tags ?? ''} />

				<Button type="submit" disabled={loading}>
					{#snippet children()}
						{#if loading}<Spinner />{/if}
						Queue job
					{/snippet}
				</Button>
			</form>

			{#if form?.queueError}
				<InlineNotice variant="error" title="Queue failed" message={form.queueError} />
			{/if}

			{#if form?.job}
				{@const job = form.job}
				<div class="job-result">
					<InlineNotice variant="success" title="Job queued" message={`Job ${job.job_id} accepted — status: ${job.status}`} />
					<dl class="job-meta">
						<div><dt>Job ID</dt><dd class="mono">{job.job_id}</dd></div>
						<div><dt>Status</dt><dd class="mono">{job.status}</dd></div>
						<div><dt>Queued at</dt><dd class="mono">{new Date(job.queued_at).toLocaleString('pt-BR')}</dd></div>
					</dl>
				</div>
			{/if}
		</section>

		<aside class="info-panel">
			<h2 class="panel__title">How it works</h2>
			<p class="panel__desc">The API returns <span class="mono">202 Accepted</span> with a job identifier. Processing happens in the background — chunking, embedding, and upsert into the vector store.</p>

			<div class="endpoint-card">
				<div class="endpoint-card__top">
					<span class="method-tag method-tag--post">POST</span>
					<span class="mono">/api/v1/ingestion/jobs</span>
					<span class="chip">ingestion:write</span>
				</div>
				<p class="muted">Validates source input, resolves tenant company ID, and returns a queued job response.</p>
			</div>

			<div class="pipeline-steps">
				<div class="step"><span class="step__num">1</span><span>Submit law reference + source</span></div>
				<div class="step"><span class="step__num">2</span><span>Worker chunks and embeds content</span></div>
				<div class="step"><span class="step__num">3</span><span>Vectors upserted into pgvector store</span></div>
				<div class="step"><span class="step__num">4</span><span>Available for RAG audit queries</span></div>
			</div>
		</aside>
	</div>
</div>

<style>
	h1, h2, p { margin: 0; }

	.ing-page { display: grid; gap: 0; min-width: 0; }

	.ing-header {
		padding: 1.75rem 1.5rem 1.25rem;
		border-bottom: 1px solid var(--border);
		display: grid;
		gap: 0.35rem;
	}

	.ing-header h1 { font-size: 1.5rem; font-weight: 600; }

	.eyebrow {
		font-family: var(--font-mono);
		font-size: 0.72rem;
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--text-muted);
	}

	.lede { color: var(--text-muted); font-size: 0.92rem; }

	.ing-body {
		display: grid;
		grid-template-columns: 1fr 360px;
	}

	.panel {
		display: grid;
		gap: 1rem;
		padding: 1.5rem;
		align-content: start;
		border-right: 1px solid var(--border);
	}

	.info-panel {
		display: grid;
		gap: 1.25rem;
		padding: 1.5rem;
		align-content: start;
	}

	.panel__title { font-size: 1rem; font-weight: 600; }
	.panel__desc { font-size: 0.86rem; color: var(--text-muted); margin-top: -0.5rem; line-height: 1.55; }

	.queue-form { display: grid; gap: 0.75rem; }
	.row-2 { display: grid; grid-template-columns: 1fr 1fr; gap: 0.75rem; }

	.field { display: grid; gap: var(--space-2); }

	.field__label {
		font-weight: 500;
		color: var(--text);
	}

	.optional {
		font-weight: 400;
		color: var(--text-faint);
		font-size: 0.88em;
	}

	textarea {
		resize: vertical;
		min-height: 6rem;
		padding: 0.75rem 1rem;
		line-height: 1.5;
	}

	.job-result { display: grid; gap: 0.75rem; }

	.job-meta {
		display: grid;
		gap: 0.35rem;
		margin: 0;
		font-size: 0.86rem;
	}

	.job-meta div { display: flex; gap: 0.75rem; }
	dt, dd { margin: 0; }
	dt { color: var(--text-muted); min-width: 5rem; }

	.endpoint-card {
		display: grid;
		gap: 0.5rem;
		padding: 1rem 1.25rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
		background: var(--bg-2);
		background-color: var(--bg-2) !important;
		background-image: none !important;
	}

	.endpoint-card__top {
		display: flex;
		align-items: center;
		gap: 0.65rem;
		flex-wrap: wrap;
	}

	.method-tag {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-width: 44px;
		padding: 0.16rem 0.45rem;
		border-radius: var(--radius-sm);
		font-family: var(--font-mono);
		font-size: 0.68rem;
		letter-spacing: 0.06em;
		border: 1px solid transparent;
	}

	.method-tag--post {
		background: var(--accent-soft);
		color: var(--accent);
		border-color: var(--accent-border);
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

	.pipeline-steps {
		display: grid;
		gap: 0.5rem;
	}

	.step {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		font-size: 0.86rem;
		color: var(--text-muted);
	}

	.step__num {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 1.4rem;
		height: 1.4rem;
		border-radius: 50%;
		background: var(--bg-3);
		border: 1px solid var(--border);
		font-family: var(--font-mono);
		font-size: 0.7rem;
		color: var(--text-faint);
		flex-shrink: 0;
	}

	.muted { color: var(--text-muted); font-size: 0.86rem; }
	.mono { font-family: var(--font-mono); }

	@media (max-width: 960px) {
		.ing-body { grid-template-columns: 1fr; }
		.panel { border-right: none; border-bottom: 1px solid var(--border); }
		.row-2 { grid-template-columns: 1fr; }
	}
</style>
