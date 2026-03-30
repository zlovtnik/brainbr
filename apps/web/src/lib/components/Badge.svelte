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
		padding: 0.18rem 0.5rem;
		border-radius: 3px;
		font-size: 0.72rem;
		font-weight: 500;
		font-family: var(--font-mono);
		letter-spacing: 0.04em;
		border: 1px solid transparent;
	}

	.badge--neutral {
		background: var(--danger-soft);
		color: var(--danger);
		border-color: var(--danger-border);
	}

	.badge--success {
		background: var(--success-soft);
		color: var(--success);
		border-color: var(--success-border);
	}

	.badge--warning {
		background: var(--warning-soft);
		color: var(--warning);
		border-color: var(--warning-border);
	}
</style>
