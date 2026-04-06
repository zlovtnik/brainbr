<script lang="ts">
	interface Option {
		value: string;
		label: string;
	}

	interface Props {
		id: string;
		name: string;
		label: string;
		value: string;
		options: Option[];
		error?: string;
	}

	let { id, name, label, value = $bindable(), options, error }: Props = $props();
	let errorId = $derived(error ? `${id}-error` : undefined);
</script>

<div class="field">
	<label class="field__label" for={id}>{label}</label>
	<select
		bind:value
		class="field__select"
		class:field__select--error={Boolean(error)}
		{id}
		{name}
		aria-errormessage={errorId}
		aria-describedby={errorId}
		aria-invalid={Boolean(error)}
	>
		{#each options as option}
			<option value={option.value}>
				{option.label}
			</option>
		{/each}
	</select>
	{#if error}
		<span class="field__error" id={errorId}>{error}</span>
	{/if}
</div>

<style>
	.field {
		display: grid;
		gap: var(--space-2);
	}

	.field__label {
		font-size: 0.8rem;
		font-weight: 400;
		font-family: var(--font-mono);
		letter-spacing: 0.04em;
		color: var(--text-muted);
	}

	.field__select {
		appearance: none;
		width: 100%;
		min-height: 2rem;
		padding: 0.35rem 2.2rem 0.35rem 0.65rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		background-color: var(--bg-2) !important;
		font-size: 0.86rem;
		color: var(--text);
		background-image:
			linear-gradient(45deg, transparent 50%, var(--text-faint) 50%),
			linear-gradient(135deg, var(--text-faint) 50%, transparent 50%);
		background-position:
			calc(100% - 1rem) calc(50% - 0.1rem),
			calc(100% - 0.68rem) calc(50% - 0.1rem);
		background-repeat: no-repeat;
		background-size: 0.38rem 0.38rem;
		box-shadow: none;
	}

	.field__select--error {
		border-color: var(--danger-border);
	}

	.field__error {
		font-size: 0.92rem;
		color: var(--danger);
	}
</style>
