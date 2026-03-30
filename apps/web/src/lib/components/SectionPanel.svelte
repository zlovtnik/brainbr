<script lang="ts">
	import type { Snippet } from 'svelte';

	interface Props {
		title: string;
		subtitle?: string;
		collapsible?: boolean;
		defaultOpen?: boolean;
		children: Snippet;
		meta?: Snippet;
	}

	let {
		title,
		subtitle,
		collapsible = false,
		defaultOpen = true,
		children,
		meta
	}: Props = $props();
</script>

{#if collapsible}
	<details class="section-panel section-panel--collapsible" open={defaultOpen}>
		<summary class="section-panel__summary">
			<div class="section-panel__heading">
				<h2 class="section-panel__title">{title}</h2>
				{#if subtitle}
					<p class="section-panel__subtitle">{subtitle}</p>
				{/if}
			</div>
			{#if meta}
				<div class="section-panel__meta">
					{@render meta()}
				</div>
			{/if}
		</summary>

		<div class="section-panel__content">
			{@render children()}
		</div>
	</details>
{:else}
	<section class="section-panel">
		<div class="section-panel__header">
			<div class="section-panel__heading">
				<h2 class="section-panel__title">{title}</h2>
				{#if subtitle}
					<p class="section-panel__subtitle">{subtitle}</p>
				{/if}
			</div>
			{#if meta}
				<div class="section-panel__meta">
					{@render meta()}
				</div>
			{/if}
		</div>

		<div class="section-panel__content">
			{@render children()}
		</div>
	</section>
{/if}

<style>
	.section-panel {
		display: grid;
		gap: 1rem;
		padding: 1.5rem 1.75rem;
		background: var(--bg);
		background-color: var(--bg) !important;
	}

	.section-panel--collapsible {
		gap: 0;
	}

	.section-panel__header,
	.section-panel__summary {
		display: flex;
		align-items: flex-start;
		justify-content: space-between;
		gap: 1rem;
	}

	.section-panel__summary {
		list-style: none;
		cursor: pointer;
	}

	.section-panel__summary::-webkit-details-marker {
		display: none;
	}

	.section-panel__heading {
		display: grid;
		gap: 0.2rem;
		min-width: 0;
	}

	.section-panel__title-row {
		display: grid;
		gap: 0.2rem;
	}

	.section-panel__title {
		margin: 0;
		font-size: 0.78rem;
		font-family: var(--font-mono);
		letter-spacing: 0.08em;
		text-transform: uppercase;
		color: var(--text-faint);
	}

	.section-panel__subtitle {
		margin: 0;
		font-size: 0.84rem;
		line-height: 1.5;
		color: var(--text-muted);
	}

	.section-panel__header::after,
	.section-panel__summary::after {
		content: '';
		flex: 1;
		align-self: center;
		height: 1px;
		background: var(--border);
	}

	.section-panel__meta {
		display: inline-flex;
		align-items: center;
		gap: 0.5rem;
	}

	.section-panel__content {
		display: grid;
		gap: 0.75rem;
	}

	.section-panel__summary + .section-panel__content {
		padding-top: 1rem;
	}

	.section-panel--collapsible > .section-panel__summary::before {
		content: '+';
		order: 3;
		display: inline-flex;
		align-items: center;
		justify-content: center;
		width: 1.5rem;
		height: 1.5rem;
		border-radius: 999px;
		border: 1px solid var(--border);
		font-family: var(--font-mono);
		color: var(--text-muted);
	}

	.section-panel--collapsible[open] > .section-panel__summary::before {
		content: '−';
	}

	@media (max-width: 720px) {
		.section-panel__header,
		.section-panel__summary {
			flex-direction: column;
		}

		.section-panel__header::after,
		.section-panel__summary::after {
			width: 100%;
		}
	}
</style>
