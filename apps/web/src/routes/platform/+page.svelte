<script lang="ts">
	import { page } from '$app/state';
	import CapabilityWorkspace from '$lib/components/CapabilityWorkspace.svelte';
	import { getCapability } from '$lib/capabilities';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
	const capability = getCapability('platform');
	const fallbackPlatformInfo = {
		service: 'fiscalbrain-br',
		embeddingModel: 'text-embedding-3-small',
		llmModel: 'gpt-4o'
	};
	let platformInfo = $derived(data.platformInfo ?? fallbackPlatformInfo);
	let liveMetrics = $derived([
		{
			label: 'Service',
			value: platformInfo.service,
			detail: data.platformInfo
				? 'Live response.'
				: (data.platformError ?? 'Reference value while the probe is unavailable.')
		},
		{
			label: 'Embedding model',
			value: platformInfo.embeddingModel,
			detail: data.platformInfo
				? 'Live response.'
				: 'Fallback display aligned with the expected Spring configuration.'
		},
		{
			label: 'LLM model',
			value: platformInfo.llmModel,
			detail: data.platformInfo
				? 'Live response.'
				: 'Fallback display aligned with the expected Spring configuration.'
		}
	]);
</script>

<svelte:head>
	<title>Platform | BrainBR</title>
	<meta
		name="description"
		content="Inspect public backend platform metadata and use it as the front door to the BrainBR workspace."
	/>
	<link rel="canonical" href={`${page.url.origin}/platform`} />
</svelte:head>

<section class="stack">
	<CapabilityWorkspace {capability} {liveMetrics} session={data.session} />
</section>
