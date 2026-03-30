<script lang="ts">
	interface Props {
		label?: string;
	}

	let { label = 'Loading' }: Props = $props();
</script>

<div aria-live="polite" class="spinner-wrap" role="status">
	<span class="sr-only">{label}</span>
	<span aria-hidden="true" class="spinner"></span>
</div>

<style>
	.spinner-wrap {
		display: inline-flex;
		align-items: center;
		justify-content: center;
	}

	.spinner {
		width: 1.2rem;
		height: 1.2rem;
		border-radius: 999px;
		border: 3px solid rgba(255, 255, 255, 0.12);
		border-top-color: var(--color-accent-vivid);
		animation: spin 0.9s linear infinite;
	}

	@keyframes spin {
		to {
			transform: rotate(360deg);
		}
	}
</style>
