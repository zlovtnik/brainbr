<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLButtonAttributes } from 'svelte/elements';

	interface Props extends HTMLButtonAttributes {
		children: Snippet;
		variant?: 'primary' | 'secondary' | 'ghost';
	}

	let { children, variant = 'primary', type = 'button', ...rest }: Props = $props();
</script>

<button class={`button button--${variant}`} {type} {...rest}>
	{@render children()}
</button>

<style>
	.button {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: var(--space-2);
		min-height: 2.9rem;
		padding: 0.8rem 1.15rem;
		border-radius: 999px;
		border: 1px solid transparent;
		font-weight: 700;
		text-decoration: none;
		cursor: pointer;
	}

	.button:hover {
		transform: translateY(-1px);
	}

	.button--primary {
		background: linear-gradient(135deg, var(--color-accent) 0%, #15865a 100%);
		color: white;
		box-shadow: var(--shadow-soft);
	}

	.button--secondary {
		background: rgba(255, 255, 255, 0.72);
		color: var(--color-ink);
		border-color: var(--color-border-strong);
	}

	.button--ghost {
		background: transparent;
		color: var(--color-accent-strong);
		border-color: var(--color-border);
	}
	
	.button:disabled {
		opacity: 0.6;
		cursor: not-allowed;
		transform: none;
	}

	.button:disabled:hover {
		transform: none;
		box-shadow: none;
	}
</style>
