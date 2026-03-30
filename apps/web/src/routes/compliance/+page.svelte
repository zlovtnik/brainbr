<script lang="ts">
	import { page } from '$app/state';
	import CapabilityWorkspace from '$lib/components/CapabilityWorkspace.svelte';
	import { getCapability } from '$lib/capabilities';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
	const capability = getCapability('compliance');
</script>

<svelte:head>
	<title>Compliance | BrainBR</title>
	<meta name="description" content="Review explainability artifact retrieval and replay surfaces." />
	<link rel="canonical" href={`${page.url.origin}/compliance`} />
</svelte:head>

<CapabilityWorkspace capability={capability} session={data.session} />
