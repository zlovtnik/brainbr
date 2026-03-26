<script lang="ts">
	import { page } from '$app/state';
</script>

<section class="stack">
	<p class="eyebrow">Request failed</p>
	<h1 class="headline">{page.status}</h1>
	<p class="lede">{page.error?.message ?? 'An unexpected error interrupted the workflow.'}</p>
	<a href="/inventory">Return to inventory</a>
</section>
