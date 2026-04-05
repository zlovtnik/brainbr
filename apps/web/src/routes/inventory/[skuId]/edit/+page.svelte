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

<form action="?/submit" method="POST" novalidate use:handleEnhance>
	<InventoryEditor
		cancelHref={`/inventory/${encodeURIComponent(data.item.skuId)}`}
		description="Update the SKU record and keep the fiscal catalog current."
		errors={form?.errors}
		formError={form?.errors?._form}
		isEdit={true}
		{submitting}
		submitLabel="Save changes"
		title={`Edit ${data.item.skuId}`}
		values={form?.values ?? data.initialValues}
	/>
</form>
