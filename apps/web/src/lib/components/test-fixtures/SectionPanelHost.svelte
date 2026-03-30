<script lang="ts">
	import SectionPanel from '$lib/components/SectionPanel.svelte';

	interface Props {
		collapsible?: boolean;
		defaultOpen?: boolean;
	}

	let { collapsible = false, defaultOpen = true }: Props = $props();
</script>

<SectionPanel
	title="Panel title"
	subtitle="Panel subtitle"
	{collapsible}
	{defaultOpen}
>
	{#snippet children()}
		<p>Panel body</p>
	{/snippet}
</SectionPanel>
