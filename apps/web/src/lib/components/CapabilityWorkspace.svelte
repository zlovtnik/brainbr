<script lang="ts">
	import Button from '$lib/components/Button.svelte';
	import SectionPanel from '$lib/components/SectionPanel.svelte';
	import StatStrip from '$lib/components/StatStrip.svelte';
	import WorkspaceHeader from '$lib/components/WorkspaceHeader.svelte';
	import type {
		CapabilityDefinition,
		CapabilityMetric,
		CapabilitySample,
		SessionShape
	} from '$lib/capabilities';
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

	function isAuthMetric(metric: CapabilityMetric): boolean {
		return metric.label.toLowerCase().includes('auth');
	}

	function isEndpointMetric(metric: CapabilityMetric): boolean {
		return metric.label.toLowerCase().includes('endpoint');
	}

	let { capability, session = null, liveMetrics = [] }: Props = $props();

	let availability = $derived(getCapabilityAvailability(capability, session?.scopes ?? []));
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
	let primaryWorkflowAction = $derived.by(() =>
		capability.workflows.find(
			(workflow) => workflow.status === 'live' && workflow.href && workflow.ctaLabel
		)
	);
	let authMetric = $derived.by(
		() =>
			capability.metrics.find(isAuthMetric) ?? {
				label: 'Access',
				value: availability === 'public' ? 'Public' : availabilityText,
				detail:
					availability === 'public'
						? 'No authentication required for the primary backend surface.'
						: 'Route access depends on session scope coverage.'
			}
	);
	let operationalMetric = $derived.by(
		() =>
			liveMetrics[0] ??
			capability.metrics.find((metric) => !isAuthMetric(metric) && !isEndpointMetric(metric)) ?? {
				label: 'Status',
				value: capability.workflows.some((workflow) => workflow.status === 'live') ? 'Live' : 'Guided',
				detail: 'Use the workflow list below to move into the next backend capability.'
			}
	);
	let statItems = $derived([
		{
			label: authMetric.label,
			value: authMetric.value,
			detail: authMetric.detail,
			tone: metricTone(authMetric)
		},
		{
			label: 'Endpoints',
			value: String(capability.endpoints.length),
			detail:
				capability.endpoints.length === 1
					? 'One backend route exposed for this capability.'
					: `${capability.endpoints.length} backend routes exposed for this capability.`,
			tone: 'accent' as const
		},
		{
			label: operationalMetric.label,
			value: operationalMetric.value,
			detail: operationalMetric.detail,
			tone: metricTone(operationalMetric)
		}
	]);
	let referenceSamples = $derived.by(() => {
		const samples: CapabilitySample[] = [...(capability.samples ?? [])];

		if (capability.id === 'platform' && liveMetrics.length) {
			samples.push({
				title: 'Runtime platform metadata',
				language: 'json',
				code: JSON.stringify(
					{
						service: liveMetrics.find((metric) => metric.label === 'Service')?.value ?? '',
						embeddingModel:
							liveMetrics.find((metric) => metric.label === 'Embedding model')?.value ?? '',
						llmModel: liveMetrics.find((metric) => metric.label === 'LLM model')?.value ?? ''
					},
					null,
					2
				)
			});
		}

		return samples;
	});
</script>

<section class="capability-page">
	<WorkspaceHeader
		tag={[primaryEndpoint?.method ?? 'GET', primaryEndpoint?.path ?? capability.href]}
		title={capability.navLabel}
		description={capability.summary}
		statusLabel={availabilityText}
		statusTone={toAvailabilityVariant(availability)}
		primaryAction={primaryWorkflowAction
			? {
					label: primaryWorkflowAction.ctaLabel!,
					href: primaryWorkflowAction.href!
				}
			: undefined}
	/>

	<StatStrip items={statItems} />

	<div class="body-grid">
		<SectionPanel
			title="Workflow guidance"
			subtitle="Lead with the primary user path and keep support actions attached to each workflow."
		>
			{#snippet children()}
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
									<Button
										href={workflow.href}
										variant={workflow === primaryWorkflowAction ? 'secondary' : 'ghost'}
									>
										{#snippet children()}{workflow.ctaLabel}{/snippet}
									</Button>
								</div>
							{/if}
						</article>
					{/each}
				</div>
			{/snippet}
		</SectionPanel>

		<SectionPanel
			title="Endpoint summaries"
			subtitle="Expose the backend surface with one compact card per route."
		>
			{#snippet children()}
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
			{/snippet}
		</SectionPanel>
	</div>

	{#if referenceSamples.length}
		<SectionPanel
			title="Reference payloads"
			subtitle="Keep example payloads and runtime metadata available without crowding the first screen."
			collapsible={true}
			defaultOpen={false}
		>
			{#snippet children()}
				<div class="sample-list">
					{#each referenceSamples as sample}
						<article class="sample-item">
							<div class="sample-item__header">
								<h3>{sample.title}</h3>
								<span class="sample-language">{sample.language}</span>
							</div>
							<pre class="sample-code"><code>{sample.code}</code></pre>
						</article>
					{/each}
				</div>
			{/snippet}
		</SectionPanel>
	{/if}
</section>

<style>
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

	.body-grid {
		display: grid;
		grid-template-columns: repeat(2, minmax(0, 1fr));
	}

	.body-grid :global(.section-panel:first-child) {
		border-right: 1px solid var(--border);
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
		min-height: 2rem;
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
		min-width: 44px;
		min-height: 2rem;
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
		align-items: center;
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

		.body-grid :global(.section-panel:first-child) {
			border-right: 0;
			border-bottom: 1px solid var(--border);
		}
	}

	@media (max-width: 720px) {
		.workflow-item__header,
		.sample-item__header {
			flex-direction: column;
			align-items: flex-start;
		}

		.workflow-item__action :global(.button) {
			width: 100%;
		}
	}
</style>
