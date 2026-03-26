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

	let { id, name, label, value, options, error }: Props = $props();
	let errorId = $derived(error ? `${id}-error` : undefined);
</script>

<label class="field" for={id}>
	<span class="field__label">{label}</span>
	<select
		class="field__select"
		class:field__select--error={Boolean(error)}
		{id}
		{name}
		aria-errormessage={errorId}
		aria-describedby={errorId}
		aria-invalid={Boolean(error)}
	>
		{#each options as option}
			<option selected={option.value === value} value={option.value}>
				{option.label}
			</option>
		{/each}
	</select>
	{#if error}
		<span class="field__error" id={errorId}>{error}</span>
	{/if}
</label>

<style>
	.field {
		display: grid;
		gap: var(--space-2);
	}

	.field__label {
		font-weight: 700;
	}

	.field__select {
		width: 100%;
		min-height: 3rem;
		padding: 0.85rem 1rem;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: rgba(255, 255, 255, 0.84);
	}

	.field__select--error {
		border-color: rgba(178, 59, 47, 0.55);
	}

	.field__error {
		font-size: 0.92rem;
		color: var(--color-danger);
	}
</style>
