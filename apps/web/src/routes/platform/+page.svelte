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
				: (data.platformError ?? 'Reference value while live telemetry is unavailable.')
		},
		{
			label: 'Embedding model',
			value: platformInfo.embeddingModel,
			detail: data.platformInfo
				? 'Live response.'
				: 'Reference display for the expected embedding setup.'
		},
		{
			label: 'LLM model',
			value: platformInfo.llmModel,
			detail: data.platformInfo
				? 'Live response.'
				: 'Reference display for the expected generation setup.'
		}
	]);
</script>

<svelte:head>
	<title>Platform | BrainBR</title>
	<meta
		name="description"
		content="Inspect BrainBR platform metadata and confirm the live service and model profile."
	/>
	<link rel="canonical" href={`${page.url.origin}/platform`} />
</svelte:head>

<section class="stack">
	<CapabilityWorkspace {capability} {liveMetrics} session={data.session} />
</section>
