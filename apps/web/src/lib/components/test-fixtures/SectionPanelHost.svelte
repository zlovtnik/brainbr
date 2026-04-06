<script lang="ts">
	import SectionPanel from '$lib/components/SectionPanel.svelte';

	interface Props {
		collapsible?: boolean;
		defaultOpen?: boolean;
		subtitle?: string;
		meta?: string;
	}

	let { collapsible = false, defaultOpen = true, subtitle, meta: metaText }: Props = $props();
</script>

<SectionPanel title="Panel title" {subtitle} {collapsible} {defaultOpen}>
	{#snippet children()}
		<p>Panel body</p>
	{/snippet}
	{#if metaText?.trim()}
		{#snippet meta()}
			{metaText}
		{/snippet}
	{/if}
</SectionPanel>
