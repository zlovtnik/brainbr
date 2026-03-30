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
