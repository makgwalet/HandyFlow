# UI modernization progress

Reference: Modernize admin template screenshots (sidebar shell, customizer, table/badge styling).
Decisions agreed in chat:

- Two-level context-switching sidebar (modules list at home; grouped module sections inside a module), with full/mini modes.
- In-page tab state becomes nested routes (`/security/guards`), with redirects from old flat routes.
- Customizer scope: light/dark, curated brand colours, sidebar full/mini, boxed/full container. No RTL, no horizontal layout for now.
- Preferences: tenant owns brand colour (optionally locked); user owns appearance. Stored server-side as versioned JSON; localStorage is only a pre-paint cache.
- Compact page header (title, breadcrumb, primary action) instead of Modernize's large illustrated banner.
- Landing page: hybrid (module launcher + attention strip from existing data). No placeholder charts.

## Phase 0 — design tokens ✅ (branch `feat/theme-tokens-phase0`)

Details and numbers: `handyflow-web/docs/THEME-TOKENS.md`.

- Added `src/styles/tokens.css` (semantic tokens, light + dark).
- Tailwind colours now point at tokens.
- Pre-paint theme bootstrap in `index.html` (`?theme=dark` for testing).
- Codemod converted 14,454 of 17,667 hex colours; 3,188 left with documented reasons.
- Type-check, lint: identical to baseline. Vite build passes.

Findings recorded:
- `npm run build` fails on `main` before any change (287 TS errors, mostly unused imports). Worth a separate cleanup commit.
- `components/layout/Sidebar.tsx`, `TopBar.tsx`, `AppLayout.tsx` are not imported anywhere (legacy). Candidates for removal once the new shell lands.

## Phase 1 — app shell (next)

- Preferences API: `tenant_ui_preferences`, `user_ui_preferences`, `GET/PUT /api/v1/.../ui-preferences`, with tenant-isolation tests.
- `ThemeProvider` + `useThemeColors()` (computed token values for recharts/leaflet).
- New shell: sidebar (full/mini, context switching), top bar, compact `PageHeader`, customizer drawer.
- Sweep of the ~680 icon/JSX colour attributes as components get touched.

## Phase 2 — pilot module: Security (not started)

- Group the 23 sections; nested routes; finish token migration of the 9 unsafe files.

## Phase 3 — module rollout (not started)
