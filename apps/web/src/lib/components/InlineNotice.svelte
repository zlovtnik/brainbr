<script lang="ts">
	interface Props {
		title: string;
		message: string;
		variant?: 'info' | 'success' | 'error';
	}

	let { title, message, variant = 'info' }: Props = $props();
</script>

<section class={`notice notice--${variant}`} aria-live="polite">
	<h2>{title}</h2>
	<p>{message}</p>
</section>

<style>
	.notice {
		display: grid;
		gap: var(--space-2);
		padding: 1rem 1.1rem;
		border-radius: var(--radius-md);
		border: 1px solid transparent;
	}

	.notice h2,
	.notice p {
		margin: 0;
	}

	.notice h2 {
		font-size: 1rem;
	}

	.notice--info {
		background: var(--color-accent-soft);
		border-color: var(--color-info-border);
	}

	.notice--success {
		background: var(--color-success-soft);
		border-color: var(--color-success-border);
	}

	.notice--error {
		background: var(--color-danger-soft);
		border-color: var(--color-error-border);
	}
</style>
