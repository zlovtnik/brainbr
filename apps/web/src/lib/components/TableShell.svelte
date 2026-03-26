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
		border: 1px solid var(--color-border);
	}

	table {
		width: 100%;
		border-collapse: collapse;
	}

	caption {
		text-align: left;
		padding: var(--space-4);
		font-weight: 700;
		background: rgba(16, 49, 30, 0.03);
	}

	:global(th),
	:global(td) {
		padding: 1rem;
		text-align: left;
		border-top: 1px solid var(--color-border);
	}

	:global(th) {
		font-size: 0.9rem;
		color: var(--color-ink-muted);
	}
</style>
