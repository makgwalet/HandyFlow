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

## Phase 1 — app shell ✅ (branch `phase1` on top of `feat/theme-token-phase0`)

Done:
- Backend: V302 `tenant_ui_preferences` + `user_ui_preferences` (typed, CHECK-constrained columns; jsonb pinned modules; optimistic locking).
  `GET /api/v1/identity/ui-preferences`, `PUT .../me` (any authenticated user), `PUT .../tenant` (SETTINGS_MANAGE).
  Precedence: user override ?? tenant default; locked brand always wins. User rows read by (userId, tenantId). Support sessions are read-only.
  13 unit tests; verified they catch a broken brand-lock rule (mutation check).
- Frontend: `ThemeProvider` / `useTheme()` / `useThemeColors()`; optimistic, debounced, serialised saves; follows the OS in SYSTEM mode; pre-paint cache.
- Six curated brand colours with light/dark palettes; `scripts/check-brand-contrast.mjs` verifies all 36 pairings pass AA.
- Module pinning moved from localStorage to the server, with one-time migration of existing pins.

Verification limits: the sandbox cannot reach Maven Central, so backend tests were run with a local JUnit/Mockito setup against stubbed Spring/JPA APIs. Run `./mvnw test -Dtest=UiPreferencesServiceTest` and start the app once so Flyway applies V302 against the real schema.

- New shell (`src/components/shell/`) replaces the old navy top-nav `ModuleLayout`:
  - Sidebar: Home, Pinned, More modules (alphabetical), Workspace (Quotes, Billing, Settings). Full or icons-only (saved per user); off-canvas drawer below 1024px.
  - Neutral top bar (surface colour in both themes, fixing the loud brand-blue bar in dark mode): module switcher with Ctrl/⌘+K, appearance, notifications, profile menu.
  - Customizer drawer: theme, brand colour (disabled when locked), sidebar, page width, reset to organisation defaults. Organisation section for SETTINGS_MANAGE users (brand, lock, defaults).
  - Read-only support sessions show a banner; changes preview locally and are not saved.
  - Content region honours boxed (1200px) / full width.
  - Stacking: shell uses z-index 40-45, below all 229 page overlays (all >= 50).
- Navigation data moved to `src/navigation/modules.ts` (one registry + `useSubscribedModules()`).
- `PageHeader` rebuilt as a compact, token-based header with breadcrumbs, icon and actions (backwards compatible; not yet adopted by pages).
- Removed dead legacy `layout/Sidebar.tsx`, `TopBar.tsx`, `AppLayout.tsx` (no importers).

Verification: type-check identical to baseline, no lint errors in new or changed files, vite build passes. Not yet checked in a browser (needs backend + login).

Known gaps / next:
- `/dashboard` still renders outside the shell with its own header. Moving it in belongs with the hybrid landing-page redesign.
- Module pages keep their own large headers and in-page tab strips until each module's pass (Phase 2 starts with Security).
- Sidebar context mode (module sections replacing the global list) is designed but deferred to Phase 2, where Security is the first module to register sections.
- `DashboardPage` has its own module registry (descriptions, tile colours); merge with `navigation/modules.ts` during the landing-page work.
- `useThemeColors()` adoption in recharts/leaflet pages as they are touched.

## Phase 2 — pilot module: Security ✅

- Sections are routes: `/security/:section` (21 sections). Deep links, refresh and back button work. Bare `/security` and unknown ids redirect to the dashboard; old tab ids `cp-overview`/`admin-overview` are aliased.
- Sidebar context mode (`navigation/moduleSections.ts`): inside Security the global list is replaced by six groups (Overview, Operations, Workforce, Sites & assets, Services, Insights & integration). "All modules" flips back without leaving the page.
- The three-level in-page tab strip and large page header are replaced by the compact `PageHeader` with breadcrumbs. Section content keeps its surface panel, so nothing inside the sections moved.
- The empty "Admin > Overview" placeholder was dropped; Payroll moved to Workforce and Branches to Sites & assets.
- Colour migration: 212 -> 0 hex literals in `pages/security`. New `scripts/theme-codemod/jsx-colors.mjs` converts alpha suffixes (`${c}18` -> `color-mix(...)`) and lucide icon `color=` props (merging into existing `style` objects); `codemod.mjs` now follows ternaries inside template literals. Remaining helpers, component props and colour maps mapped by hand by role.
- Leaflet: the live map only uses `divIcon` (HTML in the page DOM), so tokens work there; no literal colours needed.

Verification: type-check identical to Phase 1 baseline; lint for touched folders slightly improved (4 fewer `any`); build passes; script check that all 21 sidebar sections have content. Not yet checked in a browser.

### Recipe for each module (Phase 3)

1. `node scripts/theme-codemod/jsx-colors.mjs --write src/pages/<module>`
2. `node scripts/theme-codemod/codemod.mjs --write src/pages/<module>`, then `git checkout -- scripts/theme-codemod/report.json`
3. `grep -rnE "#[0-9a-fA-F]{6}" src/pages/<module>` and map the rest by role (how each value is consumed).
4. If the module has in-page tabs: register sections in `navigation/moduleSections.ts`, route `/<module>/:section?`, swap the header for `PageHeader`.
5. Check for the double-unwrap bug (below): `grep -rnE "\.data\?\.data( \?\? (\[\]|null))?\s*(,|\)|$)" src/pages/<module>`. For each hit without a `?? r.data` fallback, confirm the endpoint's return type in the controller, then read `.data` instead. Watch for paged responses (`Page<...>`), which need `.content`.
6. Type-check against baseline, lint, build, click through in light and dark.

App-wide dry run of step 1: 67 alpha sites and 566 icon props across 224 files.

## Phase 3 — module rollout (in progress)

Correction to the original analysis: its per-module "tab counts" were file counts. Agriculture has 3 top-level sections, not 20; the other files are drill-down views. Check each module's real navigation before planning.

### Agriculture ✅
- Was unreachable: missing from the sidebar registry and the dashboard tiles (billing key `agriculture`, icon Wheat). Now in both.
- Routed sections `/agriculture/:section` (Dashboard, Farms, Species) with sidebar context mode; `PageHeader` with breadcrumbs, including the open farm's name.
- Removed the page's own full-height/1200px wrapper, which doubled the shell's padding.
- Farm drill-down stays in-page state, tied to the current history entry so any navigation returns to the list. Deep-linking a farm (`/agriculture/farms/:id`) is a possible follow-up.
- Colours: 28 -> 0 hex. `AG_ACCENT` split into `AG_ACCENT` (fills) and `AG_ACCENT_TEXT` (text); 43 uses classified by property (19 text, 24 fill).
- No double-unwrap sites. Also fixed hard-coded colours on the dashboard's Internal Audit tile.

### Fuel ✅
- Routed sections `/fuel/:section` in four groups (Overview; Stock: Tanks, Stock In, Dispatches; Logistics: Deliveries, Suppliers; Insights: Cost & Margin).
- New: sections can declare a `permission`. Cost & Margin requires `FUEL_MARGIN_READ`; the sidebar hides it and the page redirects away from it without the permission (same filter in both, via `visibleGroups` / `findSection(..., permissions)`). Mirrors, does not replace, the server check.
- Colours: 58 -> 0 hex. Fuel-type colours use the `-text` tokens (identical light values, lighter in dark mode, which also works for bars, dots and chart lines).
- The hand-drawn price chart's SVG `fill`/`stroke` attributes moved into `style` so tokens resolve; point rings use the surface colour so they still read as a gap in dark mode.


## Bug found during Phase 2 review: double-unwrapped API responses

`apiClient` (src/api/client.ts) already unwraps the `{ success, message, data }` envelope, so `res.data` is the payload. Code that reads `res.data.data` / `.data?.data` **without** a `?? res.data` fallback always gets `undefined`, and lists silently show as empty.

- Fixed: `uiPreferencesApi` (caused the "Query data cannot be undefined" console error, so preferences never loaded from the server).
- Fixed in Security (21 sites, each endpoint's return type confirmed in its controller): live map guard positions; post-order history (also removed a duplicate request), acknowledgements, site posts, site contacts; close-protection evidence, armoury logs, advance surveys, vetting history, declined principals; patrol routes, patrol rounds, site detail on the patrol page; guard screening history and gate status; on-site register, gate evidence, site-access report; rotation assignments.
- Fixed in other modules (endpoints verified, none paged):
  - Bookings (8): service and staff lists when creating a booking, available slots, staff-to-service assignments (Staff tab and booking form), availability staff list.
  - Customers (4): customer communications, follow-ups, POPIA consent, and lead stage panels. Consent previously always looked "not recorded", which could lead to duplicate consent records.
  - POS (2): current cash session lookups, which always looked like "no open session".
  - Fuel (1): dispatch approval status. Property (1): lease portal-access grants.
- Not fixed, different problem: `customers/ImportModal.tsx` posts to `/api/v1/crm/customers/import` and polls `/import/{jobId}`, but no such endpoints exist in the backend. Customer import cannot work until they are built.
- The widespread `r.data?.data ?? r.data` form is harmless (it falls back to the payload) and is left alone.
