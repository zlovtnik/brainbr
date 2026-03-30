<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		children: Snippet;
		header?: Snippet;
		padded?: boolean;
	}

	let { children, header, padded = true }: Props = $props();
</script>

<section class:card--padded={padded} class="card">
	{#if header}
		<header class="card__header">{@render header()}</header>
	{/if}
	<div class="card__body">
		{@render children()}
	</div>
</section>

<style>
	.card {
		border: 1px solid var(--border);
		border-radius: var(--radius-md);
		background: var(--bg-2);
		background-color: var(--bg-2) !important;
		background-image: none !important;
		box-shadow: none;
	}

	.card--padded {
		padding: 1.25rem;
	}

	.card__header {
		margin-bottom: 1rem;
	}

	.card__body {
		display: grid;
		gap: 1rem;
	}
</style>
