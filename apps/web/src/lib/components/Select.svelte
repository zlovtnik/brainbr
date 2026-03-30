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
		font-weight: 500;
		color: var(--text);
	}

	.field__select {
		appearance: none;
		width: 100%;
		min-height: 3.2rem;
		padding: 0.95rem 2.75rem 0.95rem 1rem;
		border: 1px solid var(--border);
		border-radius: var(--radius-sm);
		background-color: var(--bg-2) !important;
		color: var(--text);
		background-image:
			linear-gradient(45deg, transparent 50%, var(--text-faint) 50%),
			linear-gradient(135deg, var(--text-faint) 50%, transparent 50%);
		background-position:
			calc(100% - 1.15rem) calc(50% - 0.12rem),
			calc(100% - 0.8rem) calc(50% - 0.12rem);
		background-repeat: no-repeat;
		background-size: 0.42rem 0.42rem;
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
