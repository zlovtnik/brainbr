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
		disabled = false,
		class: className = '',
		...rest
	}: Props = $props();

	let classes = $derived(`button button--${variant}${className ? ` ${className}` : ''}`);

	function handleAnchorClick(event: MouseEvent) {
		if (!disabled) {
			return;
		}

		event.preventDefault();
		event.stopPropagation();
	}
</script>

{#if href}
	<a
		aria-disabled={disabled ? 'true' : undefined}
		class={classes}
		data-disabled={disabled ? 'true' : undefined}
		{href}
		onclick={handleAnchorClick}
		tabindex={disabled ? -1 : undefined}
		{...rest}
	>
		{@render children()}
	</a>
{:else}
	<button class={classes} {disabled} {type} {...rest}>
		{@render children()}
	</button>
{/if}

<style>
	.button {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		gap: var(--space-2);
		min-height: 2.75rem;
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

	.button:is([aria-disabled='true'], :disabled) {
		opacity: 0.6;
		cursor: not-allowed;
	}

	.button:is([aria-disabled='true'], :disabled):hover {
		transform: none;
		box-shadow: none;
	}

	.button--primary {
		background: rgba(79, 142, 247, 0.18);
		color: #8bb5ff;
		border-color: var(--accent-border);
	}

	.button--primary:hover {
		background: rgba(122, 177, 255, 0.28);
		color: #d1e5ff;
		border-color: var(--color-accent-vivid);
	}

	.button--primary[aria-disabled='true']:hover {
		background: rgba(79, 142, 247, 0.18);
		color: #8bb5ff;
		border-color: var(--accent-border);
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

	.button--secondary[aria-disabled='true']:hover {
		background: var(--bg-2);
		color: var(--text);
		border-color: var(--border);
	}

	.button--ghost[aria-disabled='true']:hover {
		background: transparent;
		color: var(--text-muted);
		border-color: var(--border);
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
