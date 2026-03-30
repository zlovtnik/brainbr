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
		font-weight: 500;
		color: var(--text);
	}

	.field__hint,
	.field__error {
		font-size: 0.92rem;
	}

	.field__hint {
		color: var(--text-faint);
	}

	.field__error {
		color: var(--color-danger);
	}

	.field__input {
		width: 100%;
		min-height: 3rem;
		padding: 0.85rem 1rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		background: var(--bg-2);
		background-color: var(--bg-2) !important;
		background-image: none !important;
		color: var(--text);
	}

	.field__input--error {
		border-color: var(--danger-border);
		background: var(--danger-soft);
	}
</style>
