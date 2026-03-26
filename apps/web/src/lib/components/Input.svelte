<script lang="ts">
	interface Props {
		id: string;
		name: string;
		label: string;
		value?: string;
		type?: string;
		error?: string;
		hint?: string;
		required?: boolean;
		placeholder?: string;
		readonly?: boolean;
	}

	let {
		id,
		name,
		label,
		value = $bindable(''),
		type = 'text',
		error,
		hint,
		required = false,
		placeholder,
		readonly = false
	}: Props = $props();

	let labelId = $derived(`${id}-label`);
	let errorId = $derived(error ? `${id}-error` : undefined);
	let describedBy = $derived(
		[hint ? `${id}-hint` : null, errorId].filter(Boolean).join(' ') || undefined
	);
</script>

<label class="field">
	<span class="field__label" id={labelId}>{label}</span>
	{#if hint}
		<span class="field__hint" id={`${id}-hint`}>{hint}</span>
	{/if}
	<input
		class:field__input--error={Boolean(error)}
		class="field__input"
		{id}
		{name}
		{type}
		bind:value
		{placeholder}
		{readonly}
		{required}
		aria-labelledby={labelId}
		aria-invalid={Boolean(error)}
		aria-describedby={describedBy}
		aria-errormessage={errorId}
	/>
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

	.field__hint,
	.field__error {
		font-size: 0.92rem;
	}

	.field__hint {
		color: var(--color-ink-muted);
	}

	.field__error {
		color: var(--color-danger);
	}

	.field__input {
		width: 100%;
		min-height: 3rem;
		padding: 0.85rem 1rem;
		border: 1px solid var(--color-border);
		border-radius: var(--radius-md);
		background: rgba(255, 255, 255, 0.84);
		color: var(--color-ink);
	}

	.field__input--error {
		border-color: rgba(178, 59, 47, 0.55);
		background: rgba(255, 245, 242, 0.9);
	}
</style>
