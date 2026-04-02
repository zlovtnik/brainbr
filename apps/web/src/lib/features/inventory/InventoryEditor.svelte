<script lang="ts">
	import { tick } from 'svelte';
	import Button from '$lib/components/Button.svelte';
	import Card from '$lib/components/Card.svelte';
	import InlineNotice from '$lib/components/InlineNotice.svelte';
	import Input from '$lib/components/Input.svelte';
	import Spinner from '$lib/components/Spinner.svelte';
	import type { InventoryFormErrors, InventoryFormValues } from '$lib/features/inventory/types';

	interface FieldErrorLink {
		id: string;
		label: string;
		message: string;
	}

	function collectFieldErrors(errors: InventoryFormErrors): FieldErrorLink[] {
		return [
			errors.skuId ? { id: 'skuId', label: 'SKU ID', message: errors.skuId } : null,
			errors.ncmCode ? { id: 'ncmCode', label: 'NCM code', message: errors.ncmCode } : null,
			errors.description
				? { id: 'description', label: 'Description', message: errors.description }
				: null,
			errors.originState
				? { id: 'originState', label: 'Origin state', message: errors.originState }
				: null,
			errors.destinationState
				? { id: 'destinationState', label: 'Destination state', message: errors.destinationState }
				: null,
			errors.legacyTaxes?.icms
				? { id: 'legacyTax-icms', label: 'ICMS', message: errors.legacyTaxes.icms }
				: null,
			errors.legacyTaxes?.pis
				? { id: 'legacyTax-pis', label: 'PIS', message: errors.legacyTaxes.pis }
				: null,
			errors.legacyTaxes?.cofins
				? { id: 'legacyTax-cofins', label: 'COFINS', message: errors.legacyTaxes.cofins }
				: null,
			errors.legacyTaxes?.iss
				? { id: 'legacyTax-iss', label: 'ISS', message: errors.legacyTaxes.iss }
				: null
		].filter((entry): entry is FieldErrorLink => Boolean(entry));
	}

	interface Props {
		title: string;
		description: string;
		submitLabel: string;
		values: InventoryFormValues;
		errors?: InventoryFormErrors;
		formError?: string;
		isEdit?: boolean;
		cancelHref: string;
		submitting?: boolean;
	}

	let {
		title,
		description,
		submitLabel,
		values,
		errors = {},
		formError,
		isEdit = false,
		cancelHref,
		submitting = false
	}: Props = $props();

	let fieldErrors = $derived(collectFieldErrors(errors));
	let errorSummary = $state<HTMLElement | undefined>();
	let editorRoot = $state<HTMLElement | undefined>();
	let hasFocusedErrors = $state(false);

	function handleSubmitStart() {
		hasFocusedErrors = false;
	}

	$effect(() => {
		const form = editorRoot?.closest('form');
		if (!form) {
			return;
		}

		form.addEventListener('submit', handleSubmitStart);

		return () => {
			form.removeEventListener('submit', handleSubmitStart);
		};
	});

	$effect(() => {
		if (fieldErrors.length === 0) {
			hasFocusedErrors = false;
			return;
		}

		if (hasFocusedErrors) {
			return;
		}

		void tick().then(() => {
			errorSummary?.focus();
			hasFocusedErrors = true;
		});
	});
</script>

<div bind:this={editorRoot}>
	<Card>
		{#snippet header()}
			<div class="stack">
				<h1>{title}</h1>
				<p class="lede">{description}</p>
			</div>
		{/snippet}

		{#if formError}
			<InlineNotice
				autofocus={fieldErrors.length === 0}
				message={formError}
				title="Unable to save inventory"
				variant="error"
			/>
		{/if}

		{#if fieldErrors.length > 0}
			<section
				bind:this={errorSummary}
				aria-labelledby="inventory-form-errors-title"
				class="error-summary"
				id="inventory-form-errors"
				tabindex="-1"
			>
				<h2 id="inventory-form-errors-title">Fix the following fields before continuing.</h2>
				<ul>
					{#each fieldErrors as error}
						<li>
							<a href={`#${error.id}`}>{error.label}: {error.message}</a>
						</li>
					{/each}
				</ul>
			</section>
		{/if}

		<div class="form-grid">
			<Input
				error={errors.skuId}
				hint="Immutable inventory identifier."
				id="skuId"
				label="SKU ID"
				name="skuId"
				readonly={isEdit}
				required
				value={values.skuId}
			/>
			<Input
				error={errors.ncmCode}
				hint="Eight-digit NCM code."
				id="ncmCode"
				label="NCM code"
				name="ncmCode"
				required
				value={values.ncmCode}
			/>
			<div class="form-grid__full">
				<Input
					error={errors.description}
					id="description"
					label="Description"
					name="description"
					required
					value={values.description}
				/>
			</div>
			<Input
				error={errors.originState}
				id="originState"
				label="Origin state"
				name="originState"
				required
				value={values.originState}
			/>
			<Input
				error={errors.destinationState}
				id="destinationState"
				label="Destination state"
				name="destinationState"
				required
				value={values.destinationState}
			/>
		</div>

		<section class="stack">
			<h2>Legacy taxes</h2>
			<div class="form-grid">
				<Input
					error={errors.legacyTaxes?.icms}
					id="legacyTax-icms"
					label="ICMS"
					name="legacyTax.icms"
					value={values.legacyTaxes?.icms ?? ''}
				/>
				<Input
					error={errors.legacyTaxes?.pis}
					id="legacyTax-pis"
					label="PIS"
					name="legacyTax.pis"
					value={values.legacyTaxes?.pis ?? ''}
				/>
				<Input
					error={errors.legacyTaxes?.cofins}
					id="legacyTax-cofins"
					label="COFINS"
					name="legacyTax.cofins"
					value={values.legacyTaxes?.cofins ?? ''}
				/>
				<Input
					error={errors.legacyTaxes?.iss}
					id="legacyTax-iss"
					label="ISS"
					name="legacyTax.iss"
					value={values.legacyTaxes?.iss ?? ''}
				/>
			</div>
		</section>

		<div class="cluster">
			<Button disabled={submitting} type="submit">
				{#snippet children()}
					{#if submitting}
						<Spinner label="Saving inventory" />
						Saving…
					{:else}
						{submitLabel}
					{/if}
				{/snippet}
			</Button>
			{#if submitting}
				<span class="editor-link editor-link--disabled">Cancel</span>
			{:else}
				<a class="editor-link" href={cancelHref}>Cancel</a>
			{/if}
		</div>
	</Card>
</div>

<style>
	h1,
	h2 {
		margin: 0;
	}

	.form-grid {
		display: grid;
		grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
		gap: var(--space-4);
	}

	.error-summary {
		display: grid;
		gap: var(--space-3);
		padding: var(--space-4);
		border-radius: var(--radius-md);
		border: 1px solid var(--danger-border);
		background: var(--danger-soft);
	}

	.error-summary h2,
	.error-summary ul {
		margin: 0;
	}

	.error-summary ul {
		padding-left: 1.2rem;
	}

	.error-summary a {
		font-weight: 700;
		color: var(--color-danger);
		text-underline-offset: 0.16em;
	}

	.form-grid__full {
		grid-column: 1 / -1;
	}

	.editor-link {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-height: 2.75rem;
		padding: 0.8rem 1.15rem;
		border-radius: var(--radius-sm);
		border: 1px solid var(--border);
		text-decoration: none;
		font-weight: 500;
		color: var(--text-muted);
		background: var(--bg-2);
	}

	.editor-link--disabled {
		user-select: none;
		-webkit-user-select: none;
		-moz-user-select: none;
		opacity: 0.5;
		cursor: not-allowed;
	}
</style>
