<script lang="ts">
	import Select from '$lib/components/Select.svelte';

	const options = [
		{ value: 'updated_at', label: 'Updated time' },
		{ value: 'sku_id', label: 'SKU ID' }
	];

	let value = $state('updated_at');
</script>

<Select bind:value id="sortBy" label="Sort field" name="sortBy" {options} />
<p>Current: {value}</p>
<button type="button" onclick={() => (value = 'sku_id')}>Switch</button>
