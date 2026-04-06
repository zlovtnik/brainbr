# SvelteKit Navigation System — Design Rules

Decisions and patterns established for the BrainBR sidebar nav and component system.

## Component sizing system

This is a dense ops console, not a marketing page. Every interactive element follows one compact scale:

| Element | min-height | padding | font-size |
|---|---|---|---|
| Button | `2rem` | `0.3rem 0.75rem` | `0.86rem` |
| Input | `2rem` | `0.35rem 0.65rem` | `0.86rem` |
| Select | `2rem` | `0.35rem 0.65rem 0.35rem 0.65rem` | `0.86rem` |
| Status chip | `2rem` | `0.28rem 0.6rem` | `0.75rem mono` |
| Topbar action | `2rem` | `0.3rem 0.75rem` | `0.82rem` |

**Never use `min-height: 2.75rem` or `3rem`** on form controls or buttons — it breaks the density contract.

## Typography scale

| Role | Size | Font | Notes |
|---|---|---|---|
| Page/workspace title | `clamp(1.1rem, 2vw, 1.4rem)` | display | Not a landing page headline |
| Body / description | `0.84rem` | sans | `color: --text-muted` |
| Label (field) | `0.8rem` | mono | `color: --text-muted`, no bold |
| Hint / error | `0.78rem` | sans | |
| Stat value | `clamp(1.1rem, 2vw, 1.4rem)` | mono | Data reads better in mono |
| Stat label | `0.72rem` | mono uppercase | `color: --text-faint` |
| Eyebrow / section header | `0.68–0.72rem` | mono uppercase | `color: --text-faint` |

**Never use `clamp(2.2rem, 4vw, 3rem)` or `clamp(1.9rem, 4vw, 2.8rem)`** — those are marketing scales.

## Field label style

Labels are mono, small, muted — not bold, not full `--text` color. They are metadata, not headings.

```css
font-size: 0.8rem;
font-weight: 400;
font-family: var(--font-mono);
letter-spacing: 0.04em;
color: var(--text-muted);
```

## Button variants

- `primary` — accent tint background, accent border
- `secondary` — `--bg-2`, `--text`
- `ghost` — transparent, `--text-muted`
- All share the same size — no size variants
- `font-weight: 400` — not bold

## Component structure

```
+layout.svelte
  └── NavGroup.svelte   (recursive, handles one tree node)
```

`NavGroup` is self-contained: it owns open/close state, active detection, badge propagation, and keyboard handling. Do not split these concerns across parent and child.

## Data model

Nav is driven by `navTree: NavItem[]` exported from `capabilities.ts`, not derived from `capabilityList`. Keep them separate — `capabilityList` is the API/capability registry, `navTree` is purely a UI concern.

```ts
interface NavItem {
  id: string
  label: string
  icon?: string
  path?: string
  status?: 'live' | 'available' | 'partial' | 'locked'
  children?: NavItem[]
  defaultOpen?: boolean
}
```

To add sub-routes, add children to `navTree` only — no component changes needed.

## Visual hierarchy (contrast ladder)

Three levels, strictly enforced:

| Level | Element | Color | Style |
|---|---|---|---|
| 1 | Section header (trigger) | `--text-faint` | uppercase mono, `0.72rem` |
| 2 | Child link (inactive) | `--text-muted` | normal weight, `0.86rem` |
| 3 | Child link (active) | `--accent-vivid` | left border `--accent`, `background: --accent-soft` |

Never use `--text` or full opacity on inactive items — it collapses the hierarchy.

## Density

- Trigger padding: `0.28rem 0.75rem` (tight, not airy)
- Child link padding: `0.3rem 0.75rem 0.3rem 1.85rem` (indent creates hierarchy, no tree border line needed)
- No `border-left` tree line — indent alone is sufficient

## Badge rules

- Badge is **hidden when the group is open** — it's noise when children are visible
- Badge is **visible when collapsed** — signals status at a glance
- Badge value = `worstStatus()` of all children (bubbles up: `live > available > partial > locked`)
- Use CSS class `nav-group--collapsed` on the `<li>` to toggle badge visibility

## Active state

- Auto-expand parent when any child route is active (via `$effect`)
- Active leaf: `--accent-vivid` text + `2px solid --accent` left border + `--accent-soft` background
- Parent with active child: `--text-muted` color, no background — subtle, not highlighted
- Use `aria-current="page"` on the active leaf `<li>`

## Hover

Color shift only (`--text-muted` → `--text`). No background flash on hover — it competes with the active state.

## Keyboard / ARIA

- `<ul role="tree">` wraps all groups
- `<li role="treeitem" aria-expanded>` per group
- `<ul role="group">` for children
- `ArrowRight` / `Enter` → expand, `ArrowLeft` → collapse
- `aria-current="page"` on active leaf

## Mobile

At `≤1024px` the sidebar collapses to a horizontal scroll row. The `cap-nav` flex-direction flips to `row`. NavGroup triggers become pill-shaped cards. No special mobile logic needed in the component itself.

## Anti-patterns

- ❌ Don't show badges on every row when expanded — it's visual noise
- ❌ Don't use `--text` color on inactive items — kills contrast hierarchy
- ❌ Don't use a `border-left` tree line alongside indent — redundant
- ❌ Don't mix `capabilityList` and `navTree` — they serve different purposes
- ❌ Don't exceed 3 levels of nesting
