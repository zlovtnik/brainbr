<script lang="ts">
	import { page } from '$app/state';
	import CapabilityWorkspace from '$lib/components/CapabilityWorkspace.svelte';
	import { getCapability } from '$lib/capabilities';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
	const capability = getCapability('split-payment');
</script>

<svelte:head>
	<title>Split Payment | BrainBR</title>
	<meta name="description" content="Inspect split payment event write and read backend capabilities." />
	<link rel="canonical" href={`${page.url.origin}/split-payment`} />
</svelte:head>

<CapabilityWorkspace capability={capability} session={data.session} />
