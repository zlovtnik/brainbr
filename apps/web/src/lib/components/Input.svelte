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
		step?: string;
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
		readonly = false,
		step
	}: Props = $props();

	let errorId = $derived(error ? `${id}-error` : undefined);
	let describedBy = $derived(
		[hint ? `${id}-hint` : null, errorId].filter(Boolean).join(' ') || undefined
	);
</script>

<div class="field">
	<label class="field__label" for={id}>{label}</label>
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
			{step}
			aria-invalid={Boolean(error)}
		aria-describedby={describedBy}
		aria-errormessage={errorId}
	/>
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

	.field__hint,
	.field__error {
		font-size: 0.78rem;
	}

	.field__hint {
		color: var(--text-faint);
	}

	.field__error {
		color: var(--color-danger);
	}

	.field__input {
		width: 100%;
		min-height: 2rem;
		padding: 0.35rem 0.65rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		background: var(--bg-2);
		background-color: var(--bg-2) !important;
		background-image: none !important;
		font-size: 0.86rem;
		color: var(--text);
	}

	.field__input--error {
		border-color: var(--danger-border);
		background: var(--danger-soft);
	}
</style>
