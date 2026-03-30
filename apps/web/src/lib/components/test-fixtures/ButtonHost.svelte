<script lang="ts">
	import Button from '$lib/components/Button.svelte';
</script>

<Button disabled={true} href="/inventory/new">
	{#snippet children()}Create SKU{/snippet}
</Button>
