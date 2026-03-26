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
		border: 1px solid var(--color-border);
		border-radius: var(--radius-lg);
		background: var(--color-surface);
		backdrop-filter: blur(10px);
		box-shadow: var(--shadow-card);
	}

	.card--padded {
		padding: var(--space-5);
	}

	.card__header {
		margin-bottom: var(--space-4);
	}

	.card__body {
		display: grid;
		gap: var(--space-4);
	}
</style>
