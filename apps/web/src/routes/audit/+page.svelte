<script lang="ts">
	import { page } from '$app/state';
	import CapabilityWorkspace from '$lib/components/CapabilityWorkspace.svelte';
	import { getCapability } from '$lib/capabilities';
	import type { PageProps } from './$types';

	let { data }: PageProps = $props();
	const capability = getCapability('audit');
</script>

<svelte:head>
	<title>Audit | BrainBR</title>
	<meta name="description" content="Explore explainability, query, and re-audit backend capabilities." />
	<link rel="canonical" href={`${page.url.origin}/audit`} />
</svelte:head>

<CapabilityWorkspace capability={capability} session={data.session} />
