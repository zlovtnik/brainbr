# Interactive Svelte 5 Project Guide

## Purpose

This document defines the initial development baseline for an interactive Svelte 5 frontend that can evolve alongside the current backend services in this repository.

## Project objective

- Build a responsive, interactive web client with Svelte 5 and SvelteKit.
- Keep frontend architecture simple enough for rapid iteration but strict enough to avoid rework.
- Integrate with the existing API platform without leaking secrets or duplicating business rules in the browser.

## Recommended baseline

- Framework: SvelteKit with Svelte 5 runes enabled.
- Language: TypeScript everywhere.
- Build/runtime: Vite + Node.js LTS.
- Styling: component-scoped CSS plus shared design tokens in `$lib/styles`.
- Testing:
  - `vitest` for component and utility tests.
  - `@testing-library/svelte` for UI behavior.
  - `playwright` for end-to-end flows.
  - `@axe-core/playwright` for automated accessibility assertions in critical flows.
- Quality:
  - ESLint
  - Prettier
  - strict TypeScript config

## Repository placement

Recommended new frontend workspace:

```text
apps/
  web/
    src/
      lib/
      routes/
      app.html
    static/
    package.json
```

Rationale:

- Keeps the backend root stable.
- Allows frontend-specific tooling without polluting the existing Gradle application.
- Makes future monorepo automation straightforward.

## Application shape

Initial application layers:

- `src/routes`: route entry points, page layouts, and SvelteKit load/actions.
- `src/lib/components`: reusable UI building blocks.
- `src/lib/features`: feature-specific components, state, and API adapters.
- `src/lib/server`: server-only utilities such as authenticated API clients and secret-backed integrations.
- `src/lib/types`: shared frontend types and API response contracts.
- `src/lib/styles`: design tokens, global CSS, and interaction primitives.

## Frontend engineering defaults

### Svelte 5 reactivity

- Use `$state()` for local reactive state.
- Use `$derived()` for computed values.
- Use `$effect()` only for side effects such as subscriptions, timers, or browser APIs.
- Do not use `$effect()` to assign derived state.

Example:

```svelte
<script lang="ts">
  let filters = $state({ query: '', onlyActive: false });
  let itemCount = $state(0);
  let summary = $derived(`${filters.query}:${itemCount}`);
</script>
```

### Component contracts

- Use `$props()` instead of `export let`.
- Use callback props instead of `createEventDispatcher`.
- Prefer small components with explicit props over large shared stateful widgets.

Example:

```svelte
<script lang="ts">
  interface Props {
    selected: boolean;
    ontoggle?: (next: boolean) => void;
  }

  let { selected, ontoggle }: Props = $props();
</script>

<button onclick={() => ontoggle?.(!selected)}>
  {selected ? 'On' : 'Off'}
</button>
```

### Composition

- Use snippets and `{@render}` instead of slots.
- Treat snippets as the default composition model for layout regions and list item templates.

### Event handling

- Use Svelte 5 event attributes such as `onclick`, `oninput`, and `onsubmit`.
- Do not use `on:click` syntax in new code.
- Inline handlers are acceptable for simple state updates.
- Wrap logic manually for `preventDefault()` or `stopPropagation()` when needed.

## SvelteKit rules

- Use `+page.server.ts` for secrets, authenticated server fetches, and backend-only logic.
- Use universal `load` only for public data and browser-safe behavior.
- Keep mutable state request-scoped; never create module-level mutable state that can leak across SSR requests.
- Use generated `./$types` in `+page.svelte` and `+layout.svelte`.
- Use `fail()` for validation errors in form actions and throw errors only for unexpected failures.

## Interactive UI baseline

The first release should emphasize fast feedback and clear state transitions.

Core interaction patterns:

- searchable list/detail views
- progressive filtering and sorting
- inline validation for forms
- optimistic UI only where backend rollback is well-defined
- loading, empty, success, and error states for every async surface
- keyboard-accessible controls and focus management for dialogs, menus, and tabs
- semantic landmarks, skip links, and screen-reader announcements for navigation and async updates

## State strategy

- Keep page-local state inside the page or feature component with runes.
- Lift state only when multiple siblings need to coordinate.
- Use URL query parameters for shareable filters, sorting, pagination, and view mode.
- Use server data as the source of truth for persisted entities.
- Avoid introducing a global client store until repeated cross-route coordination is proven necessary.

## Backend integration plan

- Frontend should consume the existing API surface from this repository rather than reimplement domain rules.
- Centralize HTTP access in `src/lib/features/*/api.ts` or `src/lib/server/api.ts`.
- Keep auth/session handling on the server side whenever credentials or tenant context are involved.
- Normalize API errors into predictable UI states.

## Suggested first routes

- `/`
  - product overview and navigation entry point
- `/inventory`
  - searchable table with filters and empty/loading/error states
- `/inventory/[skuId]`
  - detail view with audit and explainability panels
- `/audit`
  - queue or audit history view

## Component design rules

- Build primitive components first: button, input, select, dialog, card, table shell, badge, toast.
- Feature components may compose primitives but should not redefine foundational styles.
- Keep one responsibility per component.
- Type snippets and callback props explicitly in TypeScript when they cross feature boundaries.

## Performance defaults

- Prefer server rendering for first paint and SEO-safe routes.
- Parallelize independent data fetching in load functions.
- Defer expensive client-only interactions until needed.
- Avoid broad reactive dependencies that force unnecessary rerenders.
- Virtualize large lists only after measuring actual UI cost.

## Accessibility baseline

- Target WCAG 2.2 Level AA by default for all shipped flows.
- All interactive controls must be keyboard reachable.
- Visible focus states are required.
- Use native HTML elements first and add ARIA only when semantics are not otherwise available.
- Include a skip link, a single page-level `h1`, and semantic landmarks such as `header`, `nav`, `main`, and `footer`.
- Dialogs must trap focus and restore it on close.
- Form inputs must have persistent labels, programmatic descriptions, and error messages associated through `for`/`id` and `aria-describedby`.
- Do not rely on color alone to communicate status; pair color with text or iconography.
- Minimum target size must meet WCAG 2.2 AA 24x24 CSS px, with 44x44 preferred for touch-first controls.
- Text contrast must meet 4.5:1 for normal text and 3:1 for large text, controls, and focus indicators.
- Motion should be subtle and respect reduced-motion preferences.
- Dynamic updates such as toasts, validation summaries, and async result counts must be announced with appropriate live regions.
- Zoom, text resizing, and narrow mobile viewports must not hide content or break task completion.

## Delivery phases

### Phase 1 - Frontend foundation

- Create `apps/web` with SvelteKit, TypeScript, linting, formatting, and test setup.
- Establish global styles, tokens, layout shell, and primitive components.
- Wire a typed API client and environment configuration.

### Phase 2 - Core interactive flows

- Implement list/detail pages.
- Add filters, loading states, error handling, and empty states.
- Introduce forms and server actions where they reduce client complexity.

### Phase 3 - Hardening

- Add Playwright coverage for the highest-value flows.
- Audit accessibility and performance bottlenecks.
- Run automated accessibility checks in CI and manual passes with VoiceOver plus one additional screen reader before release.
- Document reusable component and feature patterns discovered during implementation.
- Reference implementation notes:
  - `docs/web-hardening.md`
  - `docs/web-patterns.md`

## Definition of done for the initial frontend

- Frontend workspace exists and runs locally.
- At least one real backend-backed route is functional.
- UI states cover loading, empty, error, and success cases.
- New Svelte code follows runes, snippet, and callback-prop conventions.
- Accessibility checks cover keyboard navigation, focus management, color contrast, and screen-reader naming for the first shipped flow.
- Typed page props and typed API contracts are in place.
- Component, route, and end-to-end smoke tests cover the first user flow.
