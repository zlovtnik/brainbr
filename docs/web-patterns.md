# Web Patterns

This document records the reusable implementation patterns established in `apps/web` during the Svelte 5 rollout.

## Primitive component contracts

- `Button.svelte`: snippet-based children, explicit `variant`, optional `href`, and minimum target sizing suitable for keyboard and touch interactions.
- `Input.svelte`: label-first contract with persistent hint and error wiring through `aria-describedby` and `aria-errormessage`.
- `Select.svelte`: mirrors the input error contract so form controls expose consistent accessibility behavior.
- `InlineNotice.svelte`: shared success/info/error surface with variant-specific live-region behavior and optional programmatic focus when a server response needs immediate attention.
- `TableShell.svelte`: caption-first table wrapper that keeps data tables semantic while delegating row content to feature-level components.

## Route and data-loading patterns

- Keep secrets and bearer-token forwarding on the server using SvelteKit `+page.server.ts` load functions and actions.
- Treat URL search params as the source of truth for inventory filters, sorting, pagination, and empty-state reproduction.
- Keep API transport mapping in feature helpers so routes pass view models to Svelte components instead of raw backend contracts.
- Use server actions for create and edit flows so validation errors and backend failures return directly to the same page with preserved values.

## Testing patterns

- Use Vitest for parser, API-client, session, and component-level coverage where DOM rendering or transport mapping can be verified in isolation.
- Keep Playwright coverage aligned to user-visible flows instead of testing every component variant independently.
- Share auth bootstrap, mock reset, main-focus assertions, and axe setup through `apps/web/e2e/helpers.js` to keep route-level specs short and readable.
- Run axe assertions inside the critical-flow tests so accessibility regressions fail next to the workflow they break.

## Accessibility patterns

- Move focus to `#main-content` after client-side route transitions so keyboard and screen-reader users land on the new page context.
- Use a linked error summary for form-action validation failures, with field-specific anchors that point back to the offending controls.
- Announce backend failures and success states through `InlineNotice` instead of custom ad hoc live regions in each route.
- Preserve semantic landmarks, skip-link navigation, and captioned data tables as the default layout pattern across the app.
