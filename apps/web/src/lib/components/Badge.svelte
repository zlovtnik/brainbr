<script lang="ts">
	interface Props {
		text: string;
		variant?: 'neutral' | 'success' | 'warning';
	}

	let { text, variant = 'neutral' }: Props = $props();
</script>

<span class={`badge badge--${variant}`}>{text}</span>

<style>
	.badge {
		display: inline-flex;
		align-items: center;
		padding: 0.35rem 0.7rem;
		border-radius: 999px;
		font-size: 0.85rem;
		font-weight: 700;
	}

	.badge--neutral {
		background: rgba(16, 49, 30, 0.08);
		color: var(--color-ink);
	}

	.badge--success {
		background: var(--color-success-soft);
		color: var(--color-success);
	}

	.badge--warning {
		background: var(--color-warning-soft);
		color: var(--color-warning);
	}
</style>
