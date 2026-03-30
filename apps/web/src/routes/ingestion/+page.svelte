<script lang="ts">
	import { page } from '$app/state';
	import CapabilityWorkspace from '$lib/components/CapabilityWorkspace.svelte';
	import { getCapability } from '$lib/capabilities';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
	const capability = getCapability('ingestion');
</script>

<svelte:head>
	<title>Ingestion | BrainBR</title>
	<meta name="description" content="Queue legal ingestion jobs and inspect the current ingestion contract." />
	<link rel="canonical" href={`${page.url.origin}/ingestion`} />
</svelte:head>

<CapabilityWorkspace capability={capability} session={data.session} />
