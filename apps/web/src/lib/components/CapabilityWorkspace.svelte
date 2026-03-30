<script lang="ts">
	import Button from '$lib/components/Button.svelte';
	import type { CapabilityDefinition, CapabilityMetric, SessionShape } from '$lib/capabilities';
	import { getCapabilityAvailability } from '$lib/capabilities';

	interface LiveMetric extends CapabilityMetric {
		isLive?: boolean;
	}

	interface Props {
		capability: CapabilityDefinition;
		session?: SessionShape | null;
		liveMetrics?: LiveMetric[];
	}

	type BadgeVariant = 'success' | 'warning' | 'neutral';

	function toAvailabilityVariant(
		availability: ReturnType<typeof getCapabilityAvailability>
	): BadgeVariant {
		if (availability === 'available' || availability === 'public') {
			return 'success';
		}

		if (availability === 'partial') {
			return 'warning';
		}

		return 'neutral';
	}

	let { capability, session = null, liveMetrics = [] }: Props = $props();

	let availability = $derived(getCapabilityAvailability(capability, session?.scopes ?? []));
	let metrics = $derived([...capability.metrics, ...liveMetrics]);
	let availabilityText = $derived(
		availability === 'public'
			? 'Public surface'
			: availability === 'available'
				? 'All required scopes present'
				: availability === 'partial'
					? 'Partial scope coverage'
					: 'Scopes missing'
	);
	let primaryEndpoint = $derived(capability.endpoints[0]);

	function metricTone(metric: CapabilityMetric): 'accent' | 'success' | 'warning' | 'default' {
		const label = metric.label.toLowerCase();
		const value = metric.value.toLowerCase();

		if (label.includes('auth') || value.includes('public') || value.includes('live')) {
			return 'success';
		}

		if (label.includes('service') || label.includes('model')) {
			return 'accent';
		}

		if (
			label.includes('use case') ||
			label.includes('best entry') ||
			label.includes('queue mode')
		) {
			return 'warning';
		}

		return 'default';
	}

	function endpointMethodClass(method: string): string {
		return method.toLowerCase();
	}

	function workflowStateText(status: 'live' | 'guided'): string {
		return status === 'live' ? 'Live' : 'Guided';
	}
</script>

<section class="capability-page">
	<div class="page-header">
		<div class="page-header__copy">
			<div class="page-tag">
				<span>{primaryEndpoint?.method ?? 'GET'}</span>
				<span>{primaryEndpoint?.path ?? capability.href}</span>
			</div>
			<h1 class="page-title">{capability.navLabel}</h1>
			<p class="page-desc">{capability.summary}</p>
		</div>
		<div class={`status-pill status-pill--${toAvailabilityVariant(availability)}`}>
			<div class="status-pill__dot"></div>
			{availabilityText}
		</div>
	</div>

	<div class="metrics-bar">
		{#each metrics as metric}
			<div class="metric-cell">
				<p class="metric-label">{metric.label}</p>
				<h2 class={`metric-value metric-value--${metricTone(metric)}`}>{metric.value}</h2>
				<p class="metric-sub">{metric.detail}</p>
			</div>
		{/each}
	</div>

	<div class="body-grid">
		<section class="body-panel">
			<div class="panel-title">Workflows</div>
			<div class="workflow-list">
				{#each capability.workflows as workflow}
					<article class="workflow-item">
						<div class="workflow-item__header">
							<h3>{workflow.title}</h3>
							<span class={`workflow-state workflow-state--${workflow.status}`}>
								<div class="workflow-state__dot"></div>
								{workflowStateText(workflow.status)}
							</span>
						</div>
						<p class="workflow-item__desc">{workflow.description}</p>
						{#if workflow.href && workflow.ctaLabel}
							<div class="workflow-item__action">
								<Button href={workflow.href} variant="ghost">
									{#snippet children()}{workflow.ctaLabel}{/snippet}
								</Button>
							</div>
						{/if}
					</article>
				{/each}
			</div>
		</section>

		<section class="body-panel">
			<div class="panel-title">Endpoint surface</div>
			<div class="endpoint-list">
				{#each capability.endpoints as endpoint}
					<article class="endpoint-item">
						<div class="endpoint-item__top">
							<span class={`method-tag method-tag--${endpointMethodClass(endpoint.method)}`}
								>{endpoint.method}</span
							>
							<span class="endpoint-path">{endpoint.path}</span>
						</div>
						<h3 class="endpoint-name">{endpoint.summary}</h3>
						<p class="endpoint-desc">{endpoint.detail}</p>
						{#if endpoint.scope}
							<div class="endpoint-scope">{endpoint.scope}</div>
						{/if}
					</article>
				{/each}
			</div>
		</section>
	</div>

	{#if capability.samples?.length}
		<section class="sample-panel">
			<div class="panel-title">Reference payloads</div>
			<div class="sample-list">
				{#each capability.samples as sample}
					<article class="sample-item">
						<div class="sample-item__header">
							<h3>{sample.title}</h3>
							<span class="sample-language">{sample.language}</span>
						</div>
						<pre class="sample-code"><code>{sample.code}</code></pre>
					</article>
				{/each}
			</div>
		</section>
	{/if}

	{#if capability.id === 'platform' && liveMetrics.length}
		<section class="sample-panel">
			<div class="panel-title">Live response</div>
			<div class="sample-list">
				<article class="sample-item">
					<div class="sample-item__header">
						<h3>Runtime platform metadata</h3>
						<span class="sample-language">json</span>
					</div>
					<pre class="sample-code"><code
							>{JSON.stringify({
								service: liveMetrics.find((metric) => metric.label === 'Service')?.value ?? '',
								embeddingModel: liveMetrics.find((metric) => metric.label === 'Embedding model')?.value ?? '',
								llmModel: liveMetrics.find((metric) => metric.label === 'LLM model')?.value ?? ''
							}, null, 2)}</code
						></pre>
				</article>
			</div>
		</section>
	{/if}
</section>

<style>
	h1,
	h2,
	h3,
	p {
		margin: 0;
	}

	.capability-page {
		display: grid;
		min-width: 0;
		background: var(--bg);
		background-color: var(--bg) !important;
	}

	.page-header {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		gap: 1.5rem;
		padding: 1.5rem 1.75rem 1.25rem;
		border-bottom: 1px solid var(--border);
		background: var(--bg);
		background-color: var(--bg) !important;
	}

	.page-header__copy {
		display: grid;
		gap: 0.4rem;
	}

	.page-tag {
		display: inline-flex;
		align-items: center;
		gap: 0.35rem;
		font-family: var(--font-mono);
		font-size: 0.7rem;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--text-faint);
	}

	.page-title {
		font-size: 1.3rem;
		font-weight: 500;
		letter-spacing: -0.01em;
		color: var(--text);
	}

	.page-desc {
		max-width: 56ch;
		font-size: 0.93rem;
		line-height: 1.5;
		color: var(--text-muted);
	}

	.status-pill {
		display: inline-flex;
		align-items: center;
		gap: 0.35rem;
		padding: 0.28rem 0.7rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		font-size: 0.78rem;
		font-family: var(--font-mono);
	}

	.status-pill__dot {
		width: 5px;
		height: 5px;
		border-radius: 50%;
		background: currentColor;
	}

	.status-pill--success {
		border-color: var(--success-border);
		background: var(--success-soft);
		color: var(--success);
	}

	.status-pill--warning {
		border-color: var(--warning-border);
		background: var(--warning-soft);
		color: var(--warning);
	}

	.status-pill--neutral {
		border-color: var(--danger-border);
		background: var(--danger-soft);
		color: var(--danger);
	}

	.metrics-bar {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
		border-bottom: 1px solid var(--border);
		background: var(--bg);
		background-color: var(--bg) !important;
	}

	.metric-cell {
		display: grid;
		gap: 0.2rem;
		padding: 1rem 1.25rem;
		border-right: 1px solid var(--border);
		background: var(--bg);
		background-color: var(--bg) !important;
	}

	.metric-cell:last-child {
		border-right: 0;
	}

	.metric-label {
		font-size: 0.7rem;
		font-family: var(--font-mono);
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--text-faint);
	}

	.metric-value {
		font-size: 1rem;
		font-weight: 500;
		font-family: var(--font-mono);
		color: var(--text);
		word-break: break-word;
	}

	.metric-value--accent {
		color: var(--accent);
	}

	.metric-value--success {
		color: var(--success);
	}

	.metric-value--warning {
		color: var(--warning);
	}

	.metric-sub {
		font-size: 0.78rem;
		font-family: var(--font-mono);
		color: var(--text-faint);
	}

	.body-grid {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
	}

	.body-panel,
	.sample-panel {
		padding: 1.5rem 1.75rem;
		background: var(--bg);
		background-color: var(--bg) !important;
	}

	.body-panel:first-child {
		border-right: 1px solid var(--border);
	}

	.sample-panel {
		border-top: 1px solid var(--border);
	}

	.panel-title {
		display: flex;
		align-items: center;
		gap: 0.5rem;
		margin-bottom: 1rem;
		font-size: 0.78rem;
		font-family: var(--font-mono);
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--text-faint);
	}

	.panel-title::after {
		content: '';
		flex: 1;
		height: 1px;
		background: var(--border);
	}

	.workflow-list,
	.endpoint-list,
	.sample-list {
		display: grid;
		gap: 0.75rem;
	}

	.workflow-item,
	.endpoint-item,
	.sample-item {
		display: grid;
		gap: 0.45rem;
		padding: 0.95rem 1rem;
		background: var(--bg-2);
		background-color: var(--bg-2) !important;
		background-image: none !important;
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
	}

	.workflow-item:hover,
	.endpoint-item:hover,
	.sample-item:hover {
		border-color: var(--border-strong);
	}

	.workflow-item__header,
	.sample-item__header {
		display: flex;
		justify-content: space-between;
		align-items: center;
		gap: 0.75rem;
	}

	.workflow-item h3,
	.sample-item h3 {
		font-size: 0.95rem;
		font-weight: 500;
		color: var(--text);
	}

	.workflow-item__desc,
	.endpoint-desc {
		font-size: 0.86rem;
		line-height: 1.5;
		color: var(--text-muted);
	}

	.workflow-state {
		display: inline-flex;
		align-items: center;
		gap: 0.3rem;
		padding: 0.2rem 0.5rem;
		border-radius: var(--radius-sm);
		font-size: 0.72rem;
		font-family: var(--font-mono);
		border: 1px solid var(--border);
	}

	.workflow-state__dot {
		width: 5px;
		height: 5px;
		border-radius: 50%;
		background: currentColor;
	}

	.workflow-state--live {
		color: var(--success);
		background: var(--success-soft);
		border-color: var(--success-border);
	}

	.workflow-state--guided {
		color: var(--warning);
		background: var(--warning-soft);
		border-color: var(--warning-border);
	}

	.workflow-item__action {
		display: flex;
	}

	.endpoint-item__top {
		display: flex;
		align-items: center;
		gap: 0.65rem;
		flex-wrap: wrap;
	}

	.method-tag {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-width: 38px;
		padding: 0.16rem 0.45rem;
		border-radius: var(--radius-sm);
		font-family: var(--font-mono);
		font-size: 0.68rem;
		letter-spacing: 0.06em;
		border: 1px solid transparent;
	}

	.method-tag--get {
		background: var(--success-soft);
		color: var(--success);
		border-color: var(--success-border);
	}

	.method-tag--post,
	.method-tag--put {
		background: var(--accent-soft);
		color: var(--accent);
		border-color: var(--accent-border);
	}

	.method-tag--delete {
		background: var(--danger-soft);
		color: var(--danger);
		border-color: var(--danger-border);
	}

	.endpoint-path,
	.endpoint-scope,
	.sample-language,
	.sample-code code {
		font-family: var(--font-mono);
	}

	.endpoint-path {
		font-size: 0.82rem;
		color: var(--text);
		word-break: break-all;
	}

	.endpoint-name {
		font-size: 0.9rem;
		font-weight: 500;
		color: var(--text-muted);
	}

	.endpoint-scope,
	.sample-language {
		display: inline-flex;
		width: fit-content;
		padding: 0.18rem 0.42rem;
		border-radius: 3px;
		font-size: 0.72rem;
		color: var(--text-muted);
		background: var(--bg-3);
		border: 1px solid var(--border);
	}

	.sample-code {
		margin: 0;
		padding: 0.85rem 1rem;
		border-radius: var(--radius-sm);
		background: var(--bg);
		background-color: var(--bg) !important;
		background-image: none !important;
		border: 1px solid var(--border);
		overflow-x: auto;
	}

	.sample-code code {
		font-size: 0.8rem;
		line-height: 1.5;
		color: #a8c4f0;
		white-space: pre;
	}

	@media (max-width: 960px) {
		.body-grid {
			grid-template-columns: 1fr;
		}

		.body-panel:first-child {
			border-right: 0;
			border-bottom: 1px solid var(--border);
		}
	}

	@media (max-width: 720px) {
		.page-header,
		.workflow-item__header,
		.sample-item__header {
			flex-direction: column;
			align-items: flex-start;
		}

		.metric-cell {
			border-right: 0;
			border-bottom: 1px solid var(--border);
		}

		.metrics-bar .metric-cell:last-child {
			border-bottom: 0;
		}
	}
</style>
