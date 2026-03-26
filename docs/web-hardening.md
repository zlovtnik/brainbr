# Web Hardening Guide

This guide captures the Phase 3 hardening baseline for `apps/web`.

## Automated gates

- Pull requests touching `apps/web` run the `Web Hardening` GitHub Actions workflow in [web-hardening.yml](../.github/workflows/web-hardening.yml).
- The workflow installs Bun and Node, then runs:
  - `bun --filter web test:unit`
  - `bun --filter web check`
  - `bun --filter web test:e2e:ci`
- Accessibility violations fail the same Playwright run that covers the critical product flows, because axe assertions are embedded in the route-level tests rather than maintained as a separate synthetic suite.

## Manual release checklist

Release is blocked until these checks pass and the findings are recorded in the release notes or PR summary.

### VoiceOver on macOS

- Auth bootstrap: invalid token message is announced, token field stays discoverable, and successful submission reaches the inventory page.
- Inventory list: landmarks, page heading, filters, pagination, and row actions are navigable and announced with useful names.
- Create and edit forms: the error summary is announced after invalid submission, each field error is associated to its control, and success or backend-failure notices are announced.

### NVDA on Windows

- Repeat the VoiceOver flow coverage on auth, inventory list, create, detail, and edit routes.
- Confirm table headers are announced in the inventory results table.
- Confirm success and error notices are announced only once and do not trap keyboard users.

### Keyboard and responsive checks

- Keyboard-only pass across auth bootstrap, list filtering, pagination, create, detail navigation, and edit save/error handling.
- Reduced-motion spot check with system reduced-motion enabled.
- Narrow viewport sanity pass at 320px wide for auth, inventory list, create, and edit flows.

## Performance audit baseline

Baseline captured on March 26, 2026 with:

```sh
API_BASE_URL=http://127.0.0.1:5050 APP_SESSION_SECRET=test-session-secret-with-extra-entropy-1234567890 bun --filter web build
```

Observed production bundle sizes:

- Largest client chunk: `31.89 kB` raw, `12.41 kB` gzip
- Second-largest client chunk: `24.53 kB` raw, `9.67 kB` gzip
- Main client app entry: `6.79 kB` raw, `2.99 kB` gzip
- Inventory editor CSS: `1.53 kB` raw, `0.55 kB` gzip
- Inline notice CSS: `1.84 kB` raw, `0.64 kB` gzip

Implementation observations:

- Inventory routes remain server-rendered and URL-driven, so list filtering and pagination do not add client-side data-fetch loops.
- The create and edit routes hydrate a small field surface with no global client store, which keeps form interactivity cheap.
- The hardening changes add focus management and error-summary behavior without introducing new route-level client state or background polling.

Low-risk follow-up options if production telemetry shows regressions:

- Split any oversized client chunk only after route-level analysis shows an actual navigation or hydration cost.
- Keep API latency as the first suspect for inventory-list slowness, because the current frontend does not perform duplicate client fetches on top of the server load.
