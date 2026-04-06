<script lang="ts">
	import { enhance } from '$app/forms';
	import { goto } from '$app/navigation';
	import { page } from '$app/state';
	import Button from '$lib/components/Button.svelte';
	import InlineNotice from '$lib/components/InlineNotice.svelte';
	import Input from '$lib/components/Input.svelte';
	import Spinner from '$lib/components/Spinner.svelte';
	import type { ComplianceArtifactTransport } from '$lib/features/compliance/types';
	import type { PageProps } from './$types';

	let { data, form }: PageProps = $props();

	let latestSkuId = $state('');
	let latestLoading = $state(false);
	let replayLoading = $state(false);

	$effect(() => { if (data.skuId) latestSkuId = data.skuId; });

	function handleLatestSubmit(e: SubmitEvent) {
		e.preventDefault();
		const id = latestSkuId.trim();
		if (!id) return;
		latestLoading = true;
		goto(`/compliance?skuId=${encodeURIComponent(id)}`).finally(() => {
			latestLoading = false;
		});
	}

	function confidenceTone(v: number) {
		if (v >= 0.8) return 'high';
		if (v >= 0.5) return 'medium';
		return 'low';
	}

	function pct(v: number) { return `${Math.round(v * 100)}%`; }

	let copiedRunId = $state<string | null>(null);

	async function handleCopyRunId(runId: string) {
		try {
			await navigator.clipboard.writeText(runId);
			copiedRunId = runId;
			setTimeout(() => { copiedRunId = null; }, 1500);
		} catch {
			copiedRunId = null;
		}
	}

	function renderArtifact(artifact: ComplianceArtifactTransport) {
		return artifact;
	}
</script>

<svelte:head>
	<title>Compliance | BrainBR</title>
	<meta name="description" content="Review and replay explainability artifacts." />
	<link rel="canonical" href={`${page.url.origin}/compliance`} />
</svelte:head>

<div class="compliance-page">
	<header class="compliance-header">
		<p class="eyebrow">Compliance Artifacts</p>
		<h1>Replay and evidence</h1>
		<p class="lede">Pull the evidence trail behind every explainability run.</p>
	</header>

	<div class="compliance-grid">
		<!-- ── LATEST ARTIFACT ── -->
		<section class="panel">
			<h2 class="panel__title">Latest artifact</h2>
			<p class="panel__desc">Inspect the newest explainability artifact for a given SKU.</p>

			<form class="inline-form" onsubmit={handleLatestSubmit}>
				<Input
					id="latest-sku"
					name="skuId"
					label="SKU ID"
					placeholder="e.g. SKU-123"
					bind:value={latestSkuId}
					required
				/>
				<Button type="submit" disabled={latestLoading}>
					{#snippet children()}
						{#if latestLoading}<Spinner />{/if}
						Fetch latest
					{/snippet}
				</Button>
			</form>

			{#if data.artifactError}
				<InlineNotice variant="error" title="Fetch failed" message={data.artifactError} />
			{/if}

			{#if data.artifact}
				{@const a = renderArtifact(data.artifact)}
				<div class="artifact-card">
					<div class="artifact-ids">
						<div class="id-row">
							<span class="field-label">Run ID</span>
							<button
								class="mono copyable"
								type="button"
								aria-label="Copy run ID"
								onclick={() => handleCopyRunId(a.run_id)}
							>{copiedRunId === a.run_id ? "Copied" : a.run_id}</button>
						</div>
						<div class="id-row">
							<span class="field-label">SKU</span>
							<span class="mono">{a.sku_id}</span>
						</div>
						<div class="id-row">
							<span class="field-label">Job ID</span>
							<span class="mono">{a.job_id}</span>
						</div>
						<div class="id-row">
							<span class="field-label">Request ID</span>
							<span class="mono">{a.request_id}</span>
						</div>
					</div>

					<div class="artifact-meta-row">
						<span class={`confidence confidence--${confidenceTone(a.audit_confidence)}`}>
							{pct(a.audit_confidence)} confidence
						</span>
						<span class="chip">{a.llm_model_used}</span>
						<span class="chip">v{a.artifact_version}</span>
						<span class="chip">schema {a.schema_version}</span>
					</div>

					<div class="artifact-sections">
						<details class="artifact-section">
							<summary>Source</summary>
							<pre class="json-block"><code>{JSON.stringify(a.source, null, 2)}</code></pre>
						</details>
						<details class="artifact-section">
							<summary>Replay context</summary>
							<pre class="json-block"><code>{JSON.stringify(a.replay_context, null, 2)}</code></pre>
						</details>
						<details class="artifact-section">
							<summary>RAG output</summary>
							<pre class="json-block"><code>{JSON.stringify(a.rag_output, null, 2)}</code></pre>
						</details>
					</div>

					<div class="artifact-footer">
						<span class="field-label">Digest</span>
						<span class="mono digest">{a.artifact_digest}</span>
						<span class="muted mono">{new Date(a.created_at).toLocaleString('pt-BR')}</span>
					</div>
				</div>
			{/if}
		</section>

		<!-- ── REPLAY BY RUN ID ── -->
		<section class="panel">
			<h2 class="panel__title">Replay by run ID</h2>
			<p class="panel__desc">Open a historical artifact directly when you already have the run identifier.</p>

			<form
				method="POST"
				action="?/replay"
				class="inline-form"
				use:enhance={() => {
					replayLoading = true;
					return async ({ update }) => {
						await update();
						replayLoading = false;
					};
				}}
			>
				<Input
					id="replay-run-id"
					name="runId"
					label="Run ID"
					placeholder="e.g. 01JXXXXXXXXXXXXXXXX"
					value={form?.replayArtifact?.run_id ?? ''}
					required
				/>
				<Button type="submit" variant="secondary" disabled={replayLoading}>
					{#snippet children()}
						{#if replayLoading}<Spinner />{/if}
						Load artifact
					{/snippet}
				</Button>
			</form>

			{#if form?.replayError}
				<InlineNotice variant="error" title="Replay failed" message={form.replayError} />
			{/if}

			{#if form?.replayArtifact}
				{@const a = renderArtifact(form.replayArtifact)}
				<div class="artifact-card">
					<div class="artifact-ids">
						<div class="id-row">
							<span class="field-label">Run ID</span>
							<button
								class="mono copyable"
								type="button"
								aria-label="Copy run ID"
								onclick={() => handleCopyRunId(a.run_id)}
							>{copiedRunId === a.run_id ? "Copied" : a.run_id}</button>
						</div>
						<div class="id-row">
							<span class="field-label">SKU</span>
							<span class="mono">{a.sku_id}</span>
						</div>
						<div class="id-row">
							<span class="field-label">Job ID</span>
							<span class="mono">{a.job_id}</span>
						</div>
						<div class="id-row">
							<span class="field-label">Request ID</span>
							<span class="mono">{a.request_id}</span>
						</div>
					</div>

					<div class="artifact-meta-row">
						<span class={`confidence confidence--${confidenceTone(a.audit_confidence)}`}>
							{pct(a.audit_confidence)} confidence
						</span>
						<span class="chip">{a.llm_model_used}</span>
						<span class="chip">v{a.artifact_version}</span>
						<span class="chip">schema {a.schema_version}</span>
					</div>

					<div class="artifact-sections">
						<details class="artifact-section">
							<summary>Source</summary>
							<pre class="json-block"><code>{JSON.stringify(a.source, null, 2)}</code></pre>
						</details>
						<details class="artifact-section">
							<summary>Replay context</summary>
							<pre class="json-block"><code>{JSON.stringify(a.replay_context, null, 2)}</code></pre>
						</details>
						<details class="artifact-section">
							<summary>RAG output</summary>
							<pre class="json-block"><code>{JSON.stringify(a.rag_output, null, 2)}</code></pre>
						</details>
					</div>

					<div class="artifact-footer">
						<span class="field-label">Digest</span>
						<span class="mono digest">{a.artifact_digest}</span>
						<span class="muted mono">{new Date(a.created_at).toLocaleString('pt-BR')}</span>
					</div>
				</div>
			{/if}
		</section>
	</div>
</div>

<style>
	h1, h2, p { margin: 0; }

	.compliance-page {
		display: grid;
		gap: 0;
		min-width: 0;
	}

	.compliance-header {
		padding: 1.75rem 1.5rem 1.25rem;
		border-bottom: 1px solid var(--border);
		display: grid;
		gap: 0.35rem;
	}

	.compliance-header h1 {
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

	.compliance-grid {
		display: grid;
		grid-template-columns: 1fr 1fr;
	}

	.panel {
		display: grid;
		gap: 1rem;
		padding: 1.5rem;
		align-content: start;
	}

	.panel:first-child {
		border-right: 1px solid var(--border);
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

	/* Artifact card */
	.artifact-card {
		display: grid;
		gap: 1rem;
		padding: 1rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
		background: var(--bg-2);
		background-color: var(--bg-2) !important;
		background-image: none !important;
	}

	.artifact-ids {
		display: grid;
		gap: 0.4rem;
	}

	.id-row {
		display: flex;
		gap: 0.75rem;
		align-items: baseline;
		font-size: 0.86rem;
	}

	.field-label {
		font-family: var(--font-mono);
		font-size: 0.72rem;
		letter-spacing: 0.06em;
		text-transform: uppercase;
		color: var(--text-muted);
		min-width: 6rem;
		flex-shrink: 0;
	}

	.artifact-meta-row {
		display: flex;
		align-items: center;
		gap: 0.5rem;
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

	.artifact-sections {
		display: grid;
		gap: 0.5rem;
	}

	.artifact-section {
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		overflow: hidden;
	}

	.artifact-section summary {
		padding: 0.55rem 0.85rem;
		font-family: var(--font-mono);
		font-size: 0.78rem;
		letter-spacing: 0.04em;
		color: var(--text-muted);
		cursor: pointer;
		user-select: none;
		background: var(--bg-3);
		background-color: var(--bg-3) !important;
		background-image: none !important;
	}

	.artifact-section summary:hover {
		color: var(--text);
	}

	.artifact-section[open] summary {
		border-bottom: 1px solid var(--border);
	}

	.json-block {
		margin: 0;
		padding: 0.85rem 1rem;
		overflow-x: auto;
		background: var(--bg);
		background-color: var(--bg) !important;
		background-image: none !important;
	}

	.json-block code {
		font-family: var(--font-mono);
		font-size: 0.78rem;
		line-height: 1.55;
		color: var(--color-syntax, var(--text));
		white-space: pre;
	}

	.artifact-footer {
		display: flex;
		align-items: center;
		gap: 0.75rem;
		flex-wrap: wrap;
		padding-top: 0.5rem;
		border-top: 1px solid var(--border);
		font-size: 0.82rem;
	}

	.digest {
		font-size: 0.72rem;
		color: var(--text-muted);
		word-break: break-all;
		flex: 1;
	}

	.copyable {
		background: none;
		border: none;
		padding: 0;
		cursor: pointer;
		color: inherit;
		font-family: inherit;
		font-size: inherit;
		text-align: left;
		word-break: break-all;
	}

	.copyable:hover {
		color: var(--accent-vivid);
	}

	.copyable:focus-visible {
		outline: 2px solid var(--accent);
		outline-offset: 2px;
		border-radius: 2px;
	}

	.muted { color: var(--text-muted); }
	.mono { font-family: var(--font-mono); }

	@media (max-width: 960px) {
		.compliance-grid {
			grid-template-columns: 1fr;
		}

		.panel:first-child {
			border-right: none;
			border-bottom: 1px solid var(--border);
		}
	}
</style>
