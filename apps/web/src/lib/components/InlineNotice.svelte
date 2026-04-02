<script lang="ts">
	import { tick } from 'svelte';

	interface Props {
		title: string;
		message: string;
		variant?: 'info' | 'success' | 'error';
		id?: string;
		autofocus?: boolean;
	}

	let { title, message, variant = 'info', id, autofocus = false }: Props = $props();
	let notice = $state<HTMLElement | undefined>();
	let liveMode = $derived(variant === 'error' ? ('assertive' as const) : ('polite' as const));
	let role = $derived(variant === 'error' ? ('alert' as const) : ('status' as const));

	$effect(() => {
		if (!autofocus) {
			return;
		}

		void tick().then(() => notice?.focus());
	});
</script>

<!-- svelte-ignore a11y_no_noninteractive_tabindex -->
<section
	bind:this={notice}
	class={`notice notice--${variant}`}
	{id}
	aria-live={liveMode}
	{role}
	tabindex={autofocus ? -1 : undefined}
>
	<p class="notice__title"><strong>{title}</strong></p>
	<p>{message}</p>
</section>

<style>
	.notice {
		display: grid;
		gap: var(--space-2);
		padding: 0.9rem 1rem;
		border-radius: var(--radius-md);
		border: 1px solid transparent;
	}

	.notice__title,
	.notice p {
		margin: 0;
	}

	.notice__title {
		font-size: 0.76rem;
		font-family: var(--font-mono);
		letter-spacing: 0.08em;
		text-transform: uppercase;
	}

	.notice--info {
		background: var(--accent-soft);
		border-color: var(--accent-border);
		color: var(--text);
	}

	.notice--success {
		background: var(--success-soft);
		border-color: var(--success-border);
		color: var(--text);
	}

	.notice--error {
		background: var(--danger-soft);
		border-color: var(--danger-border);
		color: var(--text);
	}
</style>
