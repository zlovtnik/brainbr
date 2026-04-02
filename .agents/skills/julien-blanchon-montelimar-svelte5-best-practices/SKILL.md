---
name: svelte5-best-practices
description: "Master Svelte 5 runes ($state, $derived, $effect, $props, $bindable), snippets, event handling, and SvelteKit patterns. Use when writing Svelte components, fixing reactivity issues, migrating from Svelte 4/stores, converting slots to snippets, implementing SSR, optimizing performance, or debugging component behavior. Supports TypeScript, form actions, data loading, generic components, and component testing."
license: MIT
metadata:
  author: ejirocodes
  version: '1.0.0'
---

# Svelte 5 Best Practices

## When to Use This Skill

| Scenario | Action |
|----------|--------|
| **Writing a new component** | Use [runes.md](./references/runes.md) for `$state`, `$derived`, `$props` patterns |
| **Migrating from Svelte 4** | Follow [migration.md](./references/migration.md) to upgrade stores, events, slots |
| **Debugging reactivity issues** | Check [Common Mistakes](#common-mistakes) and [performance.md](./references/performance.md) |
| **Replacing slots with modern patterns** | Use [snippets.md](./references/snippets.md) and snippet examples below |
| **Setting up SvelteKit app** | Review [sveltekit.md](./references/sveltekit.md) for load functions and form actions |
| **Fixing event handling** | Convert `on:click` → `onclick` handler using [events.md](./references/events.md) |
| **Building type-safe components** | Use [typescript.md](./references/typescript.md) for props typing and generics |

## Example Prompts

- "Help me convert this Svelte 4 component with stores to Svelte 5 runes"
- "Review my component for Svelte 5 best practices and common mistakes"
- "I need to refactor slots to snippets in my layout component"
- "How do I handle two-way binding with $bindable()?"
- "Is my SvelteKit load function following best practices?"
- "Optimize this component— it's re-rendering too much"

## Quick Reference

| Topic | When to Use | Reference |
|-------|-------------|-----------|
| **Runes** | $state, $derived, $effect, $props, $bindable, $inspect | [runes.md](references/runes.md) |
| **Snippets** | Replacing slots, {#snippet}, {@render} | [snippets.md](references/snippets.md) |
| **Events** | onclick handlers, callback props, context API | [events.md](references/events.md) |
| **TypeScript** | Props typing, generic components | [typescript.md](references/typescript.md) |
| **Migration** | Svelte 4 to 5, stores to runes | [migration.md](references/migration.md) |
| **SvelteKit** | Load functions, form actions, SSR, page typing | [sveltekit.md](references/sveltekit.md) |
| **Performance** | Universal reactivity, avoiding over-reactivity, streaming | [performance.md](references/performance.md) |

## Essential Patterns

### Reactive State

```svelte
<script>
  let count = $state(0);           // Reactive state
  let doubled = $derived(count * 2); // Computed value
</script>
```

### Component Props

```svelte
<script>
  let { name, count = 0 } = $props();
  let { value = $bindable() } = $props(); // Two-way binding
</script>
```

### Snippets (replacing slots)

```svelte
<script>
  let { children, header } = $props();
</script>

{@render header?.()}
{@render children()}
```

### Event Handlers

```svelte
<!-- Svelte 5: use onclick, not on:click -->
<button onclick={() => count++}>Click</button>
```

### Callback Props (replacing createEventDispatcher)

<script>
  let { onclick, data } = $props();
</script>

<button onclick={() => onclick?.(data)}>Click</button>

## Common Mistakes

1. **Using `let` without `$state`** - Variables are not reactive without `$state()`
2. **Using `$effect` for derived values** - Use `$derived` instead
3. **Using `on:click` syntax** - Use `onclick` in Svelte 5
4. **Using `createEventDispatcher`** - Use callback props instead
5. **Using `<slot>`** - Use snippets with `{@render}`
6. **Forgetting `$bindable()`** - Required for `bind:` to work
7. **Setting module-level state in SSR** - Causes cross-request leaks
8. **Sequential awaits in load functions** - Use `Promise.all` for parallel requests

## Summary & Next Steps

This skill provides:
- **Quick Reference**: Jump to specific topics (runes, snippets, events, etc.)
- **Pattern Examples**: Copy-paste starting points for common scenarios
- **Migration Guide**: Step-by-step upgrading from Svelte 4 and store-based patterns
- **Pitfall Prevention**: Reference common mistakes to avoid

### To Get the Most from This Skill

1. **First-time in Svelte 5?** Start with [runes.md](./references/runes.md) and understand `$state`, `$derived`, `$props`
2. **Migrating a project?** Read [migration.md](./references/migration.md) before refactoring
3. **Hitting a bug?** Check [Common Mistakes](#common-mistakes) — you'll likely find the answer
4. **Need performance help?** Review [performance.md](./references/performance.md) for reactivity patterns
5. **SvelteKit specific?** Use [sveltekit.md](./references/sveltekit.md) for layouts, load functions, and forms

### Related Topics
- **Testing**: Check component testing patterns in references
- **TypeScript**: See [typescript.md](./references/typescript.md) for props typing
- **Forms**: Review SvelteKit [form actions](./references/sveltekit.md) and validation
