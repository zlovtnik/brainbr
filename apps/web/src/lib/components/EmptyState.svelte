<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		title: string;
		message: string;
		action?: Snippet;
	}

	let { title, message, action }: Props = $props();
</script>

<section class="empty-state">
	<h2>{title}</h2>
	<p>{message}</p>
	{#if action}
		<div class="empty-state__action">
			{@render action()}
		</div>
	{/if}
</section>

<style>
	.empty-state {
		display: grid;
		gap: var(--space-3);
		padding: var(--space-6);
		border-radius: var(--radius-lg);
		border: 1px dashed var(--color-border-strong);
		background: rgba(255, 255, 255, 0.45);
	}

	h2,
	p {
		margin: 0;
	}

	p {
		color: var(--color-ink-muted);
	}
</style>
