<script lang="ts">
	import InventoryEditor from '$lib/features/inventory/InventoryEditor.svelte';
	import type { PageProps } from './$types';

	let { data, form }: PageProps = $props();
</script>

<form method="POST" novalidate>
	<InventoryEditor
		cancelHref="/inventory"
		description="Create a new SKU entry and send the payload to the tenant-scoped Spring inventory API."
		errors={form?.errors}
		formError={form?.errors?._form}
		submitLabel="Create SKU"
		title="Create inventory SKU"
		values={form?.values ?? data.initialValues}
	/>
</form>
