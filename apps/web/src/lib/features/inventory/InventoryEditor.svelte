<script lang="ts">
	import Button from '$lib/components/Button.svelte';
	import Card from '$lib/components/Card.svelte';
	import InlineNotice from '$lib/components/InlineNotice.svelte';
	import Input from '$lib/components/Input.svelte';
	import type { InventoryFormErrors, InventoryFormValues } from '$lib/features/inventory/types';

	interface Props {
		title: string;
		description: string;
		submitLabel: string;
		values: InventoryFormValues;
		errors?: InventoryFormErrors;
		formError?: string;
		isEdit?: boolean;
		cancelHref: string;
	}

	let {
		title,
		description,
		submitLabel,
		values,
		errors = {},
		formError,
		isEdit = false,
		cancelHref
	}: Props = $props();
</script>

<Card>
	{#snippet header()}
		<div class="stack">
			<h1>{title}</h1>
			<p class="lede">{description}</p>
		</div>
	{/snippet}

	{#if formError}
		<InlineNotice message={formError} title="Unable to save inventory" variant="error" />
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
		<Input error={errors.originState} id="originState" label="Origin state" name="originState" required value={values.originState} />
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
			<Input error={errors.legacyTaxes?.icms} id="legacyTax-icms" label="ICMS" name="legacyTax.icms" value={values.legacyTaxes?.icms ?? ''} />
			<Input error={errors.legacyTaxes?.pis} id="legacyTax-pis" label="PIS" name="legacyTax.pis" value={values.legacyTaxes?.pis ?? ''} />
			<Input error={errors.legacyTaxes?.cofins} id="legacyTax-cofins" label="COFINS" name="legacyTax.cofins" value={values.legacyTaxes?.cofins ?? ''} />
			<Input error={errors.legacyTaxes?.iss} id="legacyTax-iss" label="ISS" name="legacyTax.iss" value={values.legacyTaxes?.iss ?? ''} />
		</div>
	</section>

	<div class="cluster">
		<Button type="submit">
			{#snippet children()}{submitLabel}{/snippet}
		</Button>
		<a class="editor-link" href={cancelHref}>Cancel</a>
	</div>
</Card>

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

	.form-grid__full {
		grid-column: 1 / -1;
	}

	.editor-link {
		display: inline-flex;
		align-items: center;
		justify-content: center;
		min-height: 2.9rem;
		padding: 0.8rem 1.15rem;
		border-radius: 999px;
		border: 1px solid var(--color-border-strong);
		text-decoration: none;
		font-weight: 700;
	}
</style>
