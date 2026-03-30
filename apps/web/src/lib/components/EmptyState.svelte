<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		title: string;
		message: string;
		action?: Snippet;
	}

	let { title, message, action }: Props = $props();
</script>

<section class="empty-state" role="status">
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
		border-radius: var(--radius-md);
		border: 1px dashed var(--border-strong);
		background: var(--bg-2);
		background-color: var(--bg-2) !important;
		background-image: none !important;
	}

	h2,
	p {
		margin: 0;
	}

	p {
		color: var(--text-muted);
	}
</style>
