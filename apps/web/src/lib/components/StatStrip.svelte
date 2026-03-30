<script lang="ts">
	interface StatItem {
		label: string;
		value: string;
		detail: string;
		tone?: 'accent' | 'success' | 'warning' | 'default';
	}

	interface Props {
		items: StatItem[];
	}

	let { items }: Props = $props();
	let visibleItems = $derived(items.slice(0, 3));
</script>

<div class="stat-strip">
	{#each visibleItems as item, index}
		<div class="stat-strip__cell">
			<p class="stat-strip__label">{item.label}</p>
			<h2 class={`stat-strip__value stat-strip__value--${item.tone ?? 'default'}`}>{item.value}</h2>
			<p class="stat-strip__detail">{item.detail}</p>
		</div>
	{/each}
</div>

<style>
	.stat-strip {
		display: grid;
		grid-template-columns: repeat(3, minmax(0, 1fr));
		border-bottom: 1px solid var(--border);
		background: var(--bg);
		background-color: var(--bg) !important;
	}

	.stat-strip__cell {
		display: grid;
		gap: 0.2rem;
		padding: 1rem 1.25rem;
		border-right: 1px solid var(--border);
		background: var(--bg);
		background-color: var(--bg) !important;
	}

	.stat-strip__cell:last-child {
		border-right: 0;
	}

	.stat-strip__label {
		margin: 0;
		font-size: 0.7rem;
		font-family: var(--font-mono);
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--text-faint);
	}

	.stat-strip__value {
		margin: 0;
		font-size: 1rem;
		font-weight: 500;
		font-family: var(--font-mono);
		color: var(--text);
		word-break: break-word;
	}

	.stat-strip__value--accent {
		color: var(--accent);
	}

	.stat-strip__value--success {
		color: var(--success);
	}

	.stat-strip__value--warning {
		color: var(--warning);
	}

	.stat-strip__detail {
		margin: 0;
		font-size: 0.78rem;
		font-family: var(--font-mono);
		color: var(--text-faint);
	}

	@media (max-width: 720px) {
		.stat-strip {
			grid-template-columns: 1fr;
		}

		.stat-strip__cell {
			border-right: 0;
			border-bottom: 1px solid var(--border);
		}

		.stat-strip__cell:last-child {
			border-bottom: 0;
		}
	}
</style>
