<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		children: Snippet;
		caption: string;
	}

	let { children, caption }: Props = $props();
</script>

<div class="table-shell">
	<table>
		<caption>{caption}</caption>
		{@render children()}
	</table>
</div>

<style>
	.table-shell {
		overflow-x: auto;
		border-radius: var(--radius-md);
		border: 1px solid var(--border);
		background: var(--bg-2);
		background-color: var(--bg-2) !important;
		background-image: none !important;
	}

	table {
		width: 100%;
		border-collapse: collapse;
		background: var(--bg-2);
		background-color: var(--bg-2) !important;
	}

	caption {
		text-align: left;
		padding: var(--space-4);
		font-weight: 500;
		font-family: var(--font-mono);
		color: var(--text-faint);
		background: var(--bg-3);
		background-color: var(--bg-3) !important;
	}

	:global(th),
	:global(td) {
		padding: 1rem;
		text-align: left;
		border-top: 1px solid var(--border);
		background: var(--bg-2);
		background-color: var(--bg-2) !important;
		color: var(--text);
	}

	:global(th) {
		font-size: 0.82rem;
		font-family: var(--font-mono);
		color: var(--text-faint);
	}
</style>
