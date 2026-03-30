<script lang="ts">
	import type { Snippet } from 'svelte';
	import type { HTMLAnchorAttributes, HTMLButtonAttributes } from 'svelte/elements';

	type Props = Omit<HTMLButtonAttributes, 'children' | 'class'> &
		Omit<HTMLAnchorAttributes, 'children' | 'class' | 'href'> & {
			children: Snippet;
			variant?: 'primary' | 'secondary' | 'ghost';
			href?: string;
			class?: string;
		};

	let {
		children,
		variant = 'primary',
		type = 'button',
		href,
		class: className = '',
		...rest
	}: Props = $props();

	let classes = $derived(`button button--${variant}${className ? ` ${className}` : ''}`);
</script>

{#if href}
	<a class={classes} {href} {...rest}>
		{@render children()}
	</a>
{:else}
	<button class={classes} {type} {...rest}>
		{@render children()}
	</button>
{/if}

<style>
	.button {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: var(--space-2);
		min-height: 2rem;
		padding: 0.38rem 0.85rem;
		border-radius: var(--radius-sm);
		border: 1px solid var(--border);
		font-weight: 500;
		font-size: 0.86rem;
		text-decoration: none;
		cursor: pointer;
		background: var(--bg-2);
		color: var(--text-muted);
	}

	.button:hover {
		transform: none;
		background: var(--bg-3);
		border-color: var(--border-strong);
		color: var(--text);
	}

	.button--primary {
		background: rgba(79, 142, 247, 0.18);
		color: #8bb5ff;
		border-color: var(--accent-border);
	}

	.button--primary:hover {
		background: rgba(79, 142, 247, 0.24);
		color: #b6d1ff;
		border-color: var(--accent);
	}

	.button--secondary {
		background: var(--bg-2);
		color: var(--text);
	}

	.button--ghost {
		background: transparent;
		color: var(--text-muted);
		border-color: var(--border);
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

	.button:focus-visible {
		outline: none;
		box-shadow: 0 0 0 3px var(--color-focus-ring);
	}

	.button--secondary:focus-visible,
	.button--ghost:focus-visible {
		box-shadow: 0 0 0 3px var(--color-focus-ring);
	}
</style>
