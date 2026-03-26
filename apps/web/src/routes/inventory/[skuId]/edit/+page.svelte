<script lang="ts">
	import InventoryEditor from '$lib/features/inventory/InventoryEditor.svelte';
	import type { PageProps } from './$types';

	let { data, form }: PageProps = $props();
</script>

<form method="POST">
	<InventoryEditor
		cancelHref={`/inventory/${encodeURIComponent(data.item.skuId)}`}
		description="Update the persisted SKU metadata while keeping bearer auth and request forwarding on the server."
		errors={form?.errors}
		formError={form?.errors?._form}
		isEdit={true}
		submitLabel="Save changes"
		title={`Edit ${data.item.skuId}`}
		values={form?.values ?? data.initialValues}
	/>
</form>
