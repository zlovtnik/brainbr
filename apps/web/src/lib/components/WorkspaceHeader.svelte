<script lang="ts">
	import Button from '$lib/components/Button.svelte';

	interface HeaderAction {
		label: string;
		href: string;
		variant?: 'primary' | 'secondary' | 'ghost';
	}

	interface Props {
		tag?: string[];
		title: string;
		description: string;
		statusLabel: string;
		statusTone?: 'success' | 'warning' | 'neutral';
		primaryAction?: HeaderAction;
	}

	let {
		tag = [],
		title,
		description,
		statusLabel,
		statusTone = 'neutral',
		primaryAction
	}: Props = $props();
</script>

<div class="workspace-header">
	<div class="workspace-header__copy">
		{#if tag.length}
			<div class="workspace-header__tag">
				{#each tag as item}
					<span>{item}</span>
				{/each}
			</div>
		{/if}

		<h1 class="workspace-header__title">{title}</h1>
		<p class="workspace-header__description">{description}</p>
	</div>

	<div class="workspace-header__meta">
		<div class={`workspace-header__status workspace-header__status--${statusTone}`} role="status">
			<div class="workspace-header__status-dot" aria-hidden="true"></div>
			{statusLabel}
		</div>

		{#if primaryAction}
			<Button href={primaryAction.href} variant={primaryAction.variant ?? 'primary'}>
				{#snippet children()}{primaryAction.label}{/snippet}
			</Button>
		{/if}
	</div>
</div>

<style>
	.workspace-header {
		display: flex;
		justify-content: space-between;
		align-items: flex-start;
		gap: 1.5rem;
		padding: 1.5rem 1.75rem 1.25rem;
		border-bottom: 1px solid var(--border);
		background: var(--bg);
		background-color: var(--bg) !important;
	}

	.workspace-header__copy {
		display: grid;
		gap: 0.4rem;
		min-width: 0;
	}

	.workspace-header__tag {
		display: inline-flex;
		flex-wrap: wrap;
		align-items: center;
		gap: 0.35rem;
		font-family: var(--font-mono);
		font-size: 0.7rem;
		letter-spacing: 0.1em;
		text-transform: uppercase;
		color: var(--text-faint);
	}

	.workspace-header__title {
		margin: 0;
		font-size: 1.3rem;
		font-weight: 500;
		letter-spacing: -0.01em;
		color: var(--text);
	}

	.workspace-header__description {
		margin: 0;
		max-width: 56ch;
		font-size: 0.93rem;
		line-height: 1.5;
		color: var(--text-muted);
	}

	.workspace-header__meta {
		display: flex;
		flex-wrap: wrap;
		justify-content: flex-end;
		align-items: center;
		gap: 0.75rem;
	}

	.workspace-header__status {
		display: inline-flex;
		align-items: center;
		gap: 0.35rem;
		min-height: 2.75rem;
		padding: 0.28rem 0.7rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		font-size: 0.78rem;
		font-family: var(--font-mono);
	}

	.workspace-header__status-dot {
		width: 5px;
		height: 5px;
		border-radius: 50%;
		background: currentColor;
	}

	.workspace-header__status--success {
		border-color: var(--success-border);
		background: var(--success-soft);
		color: var(--success);
	}

	.workspace-header__status--warning {
		border-color: var(--warning-border);
		background: var(--warning-soft);
		color: var(--warning);
	}

	.workspace-header__status--neutral {
		border-color: var(--border);
		background: var(--bg-soft);
		color: var(--text-muted);
	}

	@media (max-width: 720px) {
		.workspace-header,
		.workspace-header__meta {
			flex-direction: column;
			align-items: flex-start;
		}

		.workspace-header__meta {
			width: 100%;
		}

		.workspace-header__meta :global(.button) {
			width: 100%;
		}
	}
</style>
