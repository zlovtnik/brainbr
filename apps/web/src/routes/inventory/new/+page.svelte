<script lang="ts">
	import { enhance } from '$app/forms';
	import InventoryEditor from '$lib/features/inventory/InventoryEditor.svelte';
	import type { PageProps } from './$types';

	let { data, form }: PageProps = $props();
	let submitting = $state(false);

	function handleEnhance(formElement: HTMLFormElement) {
		return enhance(formElement, () => {
			submitting = true;

			return async ({ update }) => {
				try {
					await update();
				} finally {
					submitting = false;
				}
			};
		});
	}
</script>

<form action="?/default" method="POST" novalidate use:handleEnhance>
	<InventoryEditor
		cancelHref="/inventory"
		description="Create a new SKU and add it to the fiscal catalog."
		errors={form?.errors}
		formError={form?.errors?._form}
		submitting={submitting}
		submitLabel="Create SKU"
		title="Create inventory SKU"
		values={form?.values ?? data.initialValues}
	/>
</form>
