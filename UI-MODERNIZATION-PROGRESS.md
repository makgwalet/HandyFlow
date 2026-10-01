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

**Status (be careful with earlier optimism):** every staff page and every client portal is converted. 148 hard-coded colours remain in `src/pages`: the auth pages 114 (login, register, password reset), and 34 that are intentional and must stay literal: Careers' own editorial palette 14 (see below), the Tasks board and Bookings service colour palettes 17 (saved data), the Contracting signature-pad canvas 2, and Marketing's sample email HTML 1. `styles/brand.ts` holds the brand palette definitions and correctly stays hex. Re-measure rather than trusting counts written here.


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

### Fleet ✅
- Routed sections `/fleet/:section`: Overview (Dashboard); Fleet (Vehicles, Drivers); Operations (Logbook, Service History, Fuel Log); Compliance.
- Colours: 70 -> 0 hex. Three config-fed icons moved to `style`, which unblocked the codemod for three files.
- Compliance alert groups compare their `color` prop to pick a border; the props and the comparisons were converted to the same token strings together so the check still matches.
- No double-unwrap sites.

### Shared: `SectionedModulePage` (components/shell)
- One component now handles routed sections for Fuel, Fleet and Accounting: redirect for unknown/forbidden sections, breadcrumbs header, remount-per-section panel. New modules should use it (Security keeps its own because of its legacy id aliases; Agriculture because of its farm drill-down).

### Codemod: named colour `white`
- `codemod.mjs` now treats `white` exactly like `#FFFFFF` (text -> on-solid, background/border -> surface). 343 named colours existed app-wide; the four modules migrated earlier had none left except two of my own gradient fades, now fading toward the surface colour.
- New tokens for positive/negative figures on coloured cards: `--hf-success-on-solid` / `--hf-danger-on-solid` (card stays dark in both themes) and `--hf-success-on-inverse` / `--hf-danger-on-inverse` (card flips with the theme).

### Accounting ✅
- Routed sections `/accounting/:section`: Overview (Dashboard); Books (Chart of Accounts, Journal Entries, Bank Accounts); Reporting (Reports, AR / AP Aging); Tax (VAT Returns).
- Colours: 25 hex + 57 named -> 0. Dashboard SVG chart attributes moved to `style`.
- No double-unwrap sites. Removed an unused import (fixes a pre-existing type error).

### Accountant ✅
- Routed sections `/accountant/:section`: Overview (Dashboard); Clients (Clients, Compliance); Work & billing (Time, Billing); Records (Journals, Workpapers).
- `SectionedModulePage` gained `action`, `subtitle`, `banner` and `children` slots, so the page keeps its practice-settings button, firm-name subtitle, "set up your practice" warning, KPI strip and profile modal.
- Client filter (opening Time or Billing for one client from a client's workspace) moved from in-memory state to the URL: `/accountant/time?client=<id>`. It now survives refresh and can be shared; clicking the sidebar clears it, as clicking a tab did before.
- Colours: 36 -> 0. Deadline-type colours map to `-text` tokens (they feed both text and `color-mix` tints). An overdue-row tint that was `#FFF8F8` is now `color-mix(danger-soft 50%, surface)`, which is the same colour in light mode.
- No double-unwrap sites.

### Bug found: colours that are DATA must stay literals (regression from Phase 0)
The Phase 0 codemod converted the default colour of a new bookable service (`EMPTY_FORM.color`, and the edit fallback) to `var(--hf-accent-text)`. That value is saved to `booking_services.color VARCHAR(7)`, so creating a service without touching the swatches (or editing one with no colour) would fail with a value-too-long error. Fixed; the swatch palette and default are literals with a comment saying why.
- Prevention: `scripts/theme-codemod/data-context.mjs` classifies a colour as data when it is in form/initial state, an API payload (`apiClient.post`, `mutate`), persisted (`localStorage`, `JSON.stringify`), fed to a state setter, or a data-named constant (`EMPTY_FORM`, `DEFAULT_*`). Both codemods now skip those and report them.
- Detection: `npm run audit:data-colors` finds any `var(--hf-*)` in those positions (exit 1 if any). Run it in CI. Suppress a reviewed false positive with `// data-color-ok: <why>`.
- Whole-app result: the only conversion in a data position was this one. The guard would also have stopped `TasksPage` (board colour form default, `tasks.color VARCHAR(20)`) from being broken when Tasks is migrated.
- Checked the other colour-bearing backend fields: desk categories (display maps only), fleet `colour` (free-text paint colour, different property name), email branding (no frontend).

### Training Provider, Legal & Compliance, Earthmoving ✅
- Routed sections on `SectionedModulePage`. Training Provider: Overview; Clients & courses; Delivery (Sessions, Certificates); Setup (Academy Profile). Legal & Compliance: Overview; Compliance (Obligations, POPIA Register, DSAR Requests, Calendar); Legal matters (Litigation). Earthmoving: Overview; Fleet & sites; Operations; Safety.
- Training Provider: removed its own full-height/1200px wrapper (double padding in the shell). Its client portal (`/training-provider/portal/*`) is routed separately outside the shell and is unaffected.
- Colours: 42 + 60 + 25 -> 0. `TRAINPROV_ACCENT` split into fill and text tokens (45 uses classified: 21 text, 24 fill).
- New tokens: `--hf-warning-solid-strong` (amber-700 fill), `--hf-neutral-solid` (grey button fill), `--hf-{info,violet,accent}-dot` (mid-tone status dots, same in both themes). Map additions: `#64748B` and `#CBD5E1` now convert as fills.
- No double-unwrap sites.

### Tasks and Recruiter (colours only) ✅
- Neither is a tabbed module: each is a single large page (1,430 and 1,707 lines) whose "tabs" live inside a task or candidate detail panel. No routed sections apply, so only colours were migrated (48 + 21 -> 0). Their custom headers carry page-specific actions and were left as is.
- Tasks: the board colour form default and its swatch palette are DATA (`tasks.color VARCHAR(20)`) and stay `#RRGGBB` literals, with a comment. The data-colour guard skipped that conversion automatically. Stored board/column colours are only ever *displayed* with token fallbacks.
- Small intentional look changes: progress-bar and time colours use the nearest theme green/red (`#10B981` -> success, `#EF4444` -> danger); the Recruiter Compare button's disabled fill (dark slate `#334155`) is now a light grey surface in light mode.

### Supply Chain and Business Compliance & Tender ✅
- Routed sections on `SectionedModulePage`. Supply Chain (`/supply-chain/:section`, sidebar key `supply_chain`): Overview; Purchasing (Suppliers, Purchase Orders, Supplier Invoices); Stock (Inventory). Compliance & Tender (`/compliancetender/:section`): Overview; Compliance (Registrations, Documents, Deadlines); Tenders. Tender detail pages (`/compliancetender/tenders/:id`) are unchanged; their back button (`navigate(-1)`) now returns to the Tenders section instead of the dashboard.
- Colours: 215 + 330 -> 0.
- recharts (Supply Chain low-stock chart): SVG colours read through `useThemeColors()`; the tooltip box now uses theme colours (it had a hard-coded white background). A file that already does this can opt out of the codemod's recharts check with the comment `theme-codemod: charts-use-useThemeColors`.
- New token `--hf-sky-solid-strong` (sky-700 fill); `#0369A1` and `#F0F7FF` now convert. Per-file `ACCENT` constants split into fill and text tokens by property.
- Props passed to local components (`ActionBtn`, `KpiCard`, `Metric`) were converted only after checking each component uses them inside `style` (never as SVG attributes).
- Found, not changed: `scm_shared.tsx` is an unused near-duplicate of `scm.shared.tsx` (the one every tab imports). Only the unused copy supports `ModalFooter`'s `accent` prop, so the amber/red confirm buttons callers ask for have never rendered (existing behaviour). Decide whether to adopt the copy or delete it.

### Events, Contracting, AP, Property (routed) and Creative, Invoicing (colours only) ✅
- Routed sections: Events `/events/:section` (Events; Selected event: Guests, Vendors, Analytics), Contracting `/contracts/:section` (opens on Contracts; Dashboard; Contracts, Templates), AP `/ap/:section` (Payables: Bills, EFT Batches, Recurring; Suppliers & reporting), Property `/property/:section` (Overview; Portfolio; Operations).
- Events: the picked event is shared by Guests, Vendors and Analytics. It stays in page state (the page remains mounted across sections) and is shown under the title instead of on the old tab chips.
- Property: the dashboard hand-off (leases pre-filtered, payments for one lease) moved from state to the URL: `/property/leases?filter=EXPIRING_SOON`, `/property/payments?lease=<id>`.
- Contracting keeps the `unwrap`/`fmtR` exports its tabs import from `ContractingPage`. AP keeps its summary strip and passes a summary-refresh callback to its tabs. KPI strips ride in the `banner` slot.
- Creative (single 1,345-line page whose tabs are inside a detail panel) and Invoicing (its Quotes/Invoices/Recurring "tabs" are already three separate top-level routes `/quotes`, `/invoices`, `/recurring`, so there is no single base path for sidebar sections) are colours only.
- Colours: ~430 converted in the four routed modules plus ~440 across Creative and Invoicing; all six at zero except two canvas literals.
- Canvas exception: signature pads (`ctx.strokeStyle = '#1B3A6B'` in Contracting's `ContractsTab` and `SigningPage`) stay literal (canvas cannot use CSS variables, and the drawn signature is saved as an image, so it must not follow the theme). Commented in place.
- Codemod: values in a map named `*_COLOR(S)`/`*_PALETTE` and their `MAP[key] ?? '#hex'` fallbacks are now converted as text colours. New tokens `--hf-pink-text`, `--hf-sky-dot`, `--hf-on-brand-muted` (pale text on the dark brand header); map additions `#6366F1`, `#BE185D`, `#DB2777`, `#38BDF8`, `#93C5FD`.
- Pending-state button fills (`create.isPending ? '#C4B5FD' : ...`) now tint the base colour toward the surface with `color-mix`, so they adapt to the theme.
- SVG check: no migrated module feeds a token into an SVG attribute (only the not-yet-migrated `projects/tabs/GanttTab.tsx` does: convert its `fill={...}` to `style` when Projects is done).
- No double-unwrap sites.

### Clinic (routed), Compliance Services and Projects (colours only) ✅
- **Clinic** `/clinic/:section`, the same nine sections as the old tabs, in four groups: Overview; Patients (Patients, Consultations, Recalls, Waitlist); Scheduling; Practice & finance (Practitioners, Claims, Billing).
- **The patient file** (which replaces the whole page when a patient is opened) is carried in the history entry's router state on `/clinic/patients`: `{ openPatient, sessionAppointment }`. Effects: the browser Back button closes the file; a refresh keeps it open; clicking any sidebar section closes it (a plain link carries no state); header shows the patient's name with a "Back to Patients" action and a Clinic > Patients > name breadcrumb (new `detail` prop on `SectionedModulePage`).
- **Starting a session from the Schedule is one-shot.** It opens the patient's file with the appointment; the file consumes it on mount and asks the page to clear it from the history entry (`replace`), so a refresh cannot restart the session. The file's state is only honoured on the Patients section.
- Verified with a scratch vitest suite (11 behavioural cases: redirects, open/close, sidebar close, one-shot session, refresh, switching patients, state on the wrong section) plus two deliberate mutations, each caught by a test (one needed a stronger assertion first). **Not committed:** the project has no test runner; say the word to add vitest and keep the suite.
- **Six dead dashboard buttons fixed.** The Clinic dashboard's "Today's appointments" and two other KPI cards, its two "view" buttons and "Book appointment" navigated to an `appointments` tab that has never existed, so each opened an empty panel. They now open Schedule. A scan of all 18 migrated modules found no other invalid navigation targets. (`AppointmentsTab.tsx` is an unused older leftover.)
- **Compliance Services** is a client list whose detail routes (`/complianceservices/clients/:clientId`) have their own four tabs: list-plus-detail, no module-level sections. Colours only (339 -> 0, all one `ACCENT` constant per file).
- **Projects** is a two-tab shell (Dashboard / Projects) with the project detail shown in-page, plus a second, older routed `/projects/:id` page. Two tabs need no sidebar sections, and `/projects/:section` would collide with `/projects/:id`. Colours only.
- **Gantt chart SVG:** fills and strokes moved into `style` so tokens resolve. The scan for SVG colour attributes fed by expressions now finds only the theme-helper case in Supply Chain.
- **New tool `scripts/theme-codemod/split-constants.mjs`** converts top-level `const NAME = "#hex"` constants and splits them by how each usage consumes the colour (text usages get a `NAME_TEXT` variant; fills/borders keep the base). It refuses to touch a constant used in an SVG attribute, a canvas call or a data position, and reports it. It converted 81 constants in 18 files here. (It first emitted `_TEXT` declarations without a terminating `;`, which broke same-line statements; fixed in the tool and repaired in the files before anything was committed.)
- New tokens: `--hf-primary-deep` (header gradient end), `--hf-{danger,warning,accent}-on-brand` (pale badge text on the dark navy patient header; identical in both themes because that header is dark in both).
- Colours: 284 + 339 + 179 -> 0. Two icon components that forwarded a colour prop to an icon's `color` attribute were moved to `style`.

### Mid-size modules: Debt Collection (routed), Quotes, Customers, Billing, Expenses, POS, Settings (colours only) ✅
- **Debt Collection** `/debtcollection/:section`: Dashboard; Cases.
- The rest get colours only, each for a specific reason: Customers, Billing and Expenses are single pages (Customers has an active/deleted view toggle); POS keeps ~20 pieces of till state (cart, cash session, a dozen modals) and all four tab bodies inline in one 1,877-line component, so routing it would mean restructuring the render for no navigation gain; Settings, Billing and Quotes are workspace pages, not subscribed modules, so sidebar context mode does not apply to them; Quotes' routes are `/quotes` (rendered by `InvoicingPage`), `/quotes/new`, `/quotes/:id`.
- **`quotes/QuotesPage.tsx` (508 lines) is not imported anywhere** (dead code); converted mechanically, safe to delete.
- **Stylesheets were a blind spot.** The TypeScript codemods only read `.ts/.tsx`. Only one stylesheet in the app had hard-coded colours, `customers/CustomersPage.module.css` (172 usages, including a private `--navy/--red/--green/...` palette used for text, fills and borders alike). New tool `scripts/theme-codemod/css-colors.mjs` resolves each palette variable per usage by the property's role (text / fill / border), removes the definitions once unreferenced, and reports anything it cannot map. Side benefit: those variables were defined only on `.page`, so `ImportModal` and `ExportButton` (rendered elsewhere) never resolved them.
- New tokens `--hf-danger-hover` (#b91c1c) and `--hf-orange-solid-strong` (#9a3412); map additions for `#132C52`, `#B91C1C`, `#EA580C` and `#94A3B8` as fills/borders. `split-constants.mjs` now knows `#9A3412`.
- **Shell layout regressions found with a `100vh` scan** (pages sized for the old 60px top bar): `TrainingPage` and `WarehousingPage` forced `minHeight: 100vh` and their own background, so they always scrolled by an extra top-bar height; `BookingAgencyPage`, `PayrollBureauPage` and `RecruitmentAgencyPage` used `calc(100vh - 60px)`. Fixed: the first two dropped the forced height/background; the three fixed heights are now `calc(100vh - var(--hf-topbar-h) - 64px)` (top bar plus the content area's 24px/40px padding). Login, portal and public pages are outside the shell and correctly keep `100vh`.
- Small intentional look changes: "pending" button fills that were a hard-coded pale colour are now a tint of the button's own colour (`color-mix`). One was a copy-paste oddity: Quote detail's pending *red* button used a pale *navy*; it is now pale red.
- Customer import has no backend: `ImportModal` calls `/api/v1/crm/customers/import`, and no controller under `crm` maps an import route (re-checked). The two `res.data?.data` reads there are moot until one exists.
- Colours: ~200 in TSX plus 172 in the stylesheet; all seven modules at zero. POS `STATUS_COLORS` `[fg, bg]` pairs were mapped by position.

### Next tier: Marketing, Training, Warehousing, Collections Agency (routed), Catalogue (colours only) ✅
- **Routed sections:** Marketing `/marketing/:section` (Campaigns, Templates; Contacts; Analytics; the KPI strip rides in the banner slot and its summary still feeds Analytics), Training & L&D `/training/:section`, Warehousing `/warehousing/:section`, Collections Agency `/collections-agency/:section`. Catalogue is a single page: colours only.
- **Portal routes verified.** Warehousing, Collections Agency, Training Provider and Property each have a client portal at `/<module>/portal/*` next to the staff `/<module>/:section?` route. A scratch router test (8 cases) confirmed the static `portal` segment outranks the `:section` parameter, that portal pages never render inside the staff shell, and that staff sections still resolve.
- **Marketing's sample email HTML is deliberately a literal hex** (`EXAMPLE_EMAIL_HTML` in `TemplatesTab.tsx`): email clients cannot resolve CSS variables, so email markup must never use theme tokens.
- **New tool `scripts/theme-codemod/split-shared-constant.mjs`** for a colour constant defined in one `constants.ts` and imported across a module (the case `split-constants.mjs` does not cover). Define `NAME` (fills/borders) and `NAME_TEXT` (text) in the constants file, then run it on the module folder: text usages become `NAME_TEXT`, and the import is rewritten (adds `NAME_TEXT`, drops `NAME` when nothing uses it). Used for `TRAINING_ACCENT`, `CA_ACCENT`, `WHSE_ACCENT`. The shared role logic moved to `roles.mjs`, which `split-constants.mjs` now also uses (verified on a scratch file covering text, fill, border, SVG and data usages).
- New tokens `--hf-violet-solid-strong` and `--hf-accent-solid-strong`; map additions for `#5B21B6`, `#0F766E`, `#15803D` fills.
- **Shared shell components fixed.** `NotificationDrawer` and `SessionExpiryModal` still had `background: 'white'` (the named-colour support came after Phase 0 and had only been run on page folders), so in dark mode the notification panel and the session-expiry dialog would have been glaring white boxes with light text. Also `ModuleSwitcher`'s icon colours. `src/components` is now at zero.
- One component (`StatCard` in the Collections Agency dashboard) forwarded its `tone` prop to an icon's `color` attribute; moved to `style`.
- Colours: 197 in the five modules -> 0 (plus 10 in shared components). Type errors 282 -> 262 (Marketing's 20 unused imports went with the rewrite).

### Internal Audit (routed), Desk, Dashboard, Invoices and the agency-variant pages (colours only) ✅
- **Internal Audit** `/internal-audit/:section`: Audit Universe, Annual Plans, Engagements. Only the page's top-level component was replaced (state, header, tab strip); the ~1,300 lines of section logic are untouched, and the shared data (universe, plans, engagements, users) still loads once at page level for all three sections.
- **Desk** (its `thread`/`notes`/`details` tabs are inside a ticket panel), **Dashboard** (the landing page, outside the shell, to be redone with the hybrid design) and **Invoices** are colours only. `invoices/InvoicesPage.tsx` is not imported anywhere (dead code; the `/invoices` route renders `InvoicingPage`); converted mechanically, safe to delete.
- Also finished the small pages not in the sidebar: Control Exceptions and the Booking / Payroll / Recruitment agency pages.
- **More stacked page padding removed** (same class as the earlier `100vh` fixes): Internal Audit wrapped itself in `padding: 24px 28px`; Control Exceptions had `24px 32px`; both tender detail pages had `24px 20px`; `security/PostOrdersTab` padded inside the Security panel, which already has its own 24px. Centred `maxWidth: 960` forms are intentional and left alone.
- **New guard `npm run check:navigation`** (`scripts/check-navigation.mjs`, exit 1 on any problem; add it to CI). For every section config it checks: exactly one page imports it and renders content for every section id and nothing else; the default section exists; the module key is in the sidebar registry (otherwise the sidebar never switches to the module's sections); `App.tsx` routes `<basePath>/:section?`; every `onNavigate("x")` / `goTo("x")` / `{ tab: "x" }` in the module's folder is a real section (two dashboards once navigated to a tab that did not exist, so those buttons opened an empty panel). Verified by reintroducing both bug classes: each is caught. Currently: 24 section configs OK.
- Colours: 33 in the four modules, 6 in Control Exceptions and 18 in the agency pages -> 0. Variable-declared colours (`const sevColor = cond ? "#..." : ...`) were mapped by the variable name's role (`...Color` text, `...Bg` fill), which the codemod cannot infer.

### Client portals ✅
- **Ten portal folders converted** (accountant, auditor, recruitment-agency, booking-agency, payroll-bureau, collections-agency, warehousing, property, training-provider, plus careers handled separately): ~93 colours -> 0.
- **`accountant-portal/portal-theme.ts` is a shared palette used by four portals** (accountant, recruitment-agency, booking-agency, payroll-bureau; ~16 pages). Its `color` object now holds theme tokens, so converting it once converted all four. Colours used for both text and fills come in two variants (`color.navy` for fills and borders, `color.navyText` for text; likewise teal, amber, red, green, blue); `statusTone` uses the text variants.
- **New tool `scripts/theme-codemod/split-object-members.mjs`** splits a palette object accessed as `color.navy` by usage role (the object-member counterpart of `split-shared-constant.mjs`). It rewrote 24 text usages in 14 files and reports any key used in an SVG attribute, canvas call or data position. Every usage of the mixed-role keys was reviewed by hand first (ternaries resolve to their owning property; `.style.color =` is text; `.style.background =` and `.style.borderColor =` are fills; gradients are fills). `split-constants.mjs` now also knows `#5B21B6`, `#0F766E`, `#B45309`.
- **Portals and the theme:** `ThemeProvider` only fetches preferences when a *staff* token exists (`enabled: !!token`); portal users authenticate separately, so they trigger no request (and no 401 redirect) and get the default light theme. A staff user with a saved dark theme who opens a portal in the same browser sees it dark.
- **Careers has its own deliberate design** (warm paper and ink palette, forest-green accent) and is left as a fixed literal palette. That exposed a real bug: it mixed that palette with app tokens (`var(--hf-surface)` for cards and inputs, from the Phase 0 conversion of white), so with a dark theme saved the cards and inputs turned dark while the text stayed near-black. Fix: each Careers page root carries `data-theme="light"`, which re-declares every token with its light value for the whole subtree (and sets `color-scheme: light` for native controls). This is a general tool for any page that must stay light regardless of the viewer's theme. Verified by reading the token file's scoping (`:root, [data-theme='light']`), not in a browser.
- The login-page background gradients used a hard-coded light slate in eight places; now `--hf-surface-sunken`.
- Portal pages use a separate API client (`portal.client.ts`, raw axios) that does not unwrap the response envelope, so the double-unwrap check used elsewhere does not apply to them and was not run on them.

### Navigation gaps found and fixed
- 7 modules had dashboard tiles but were missing from the sidebar/Ctrl+K registry (carried over from the old top-nav): collections agency, warehousing, legal & compliance, business compliance & tender, compliance services, debt collection, projects. Added.
- 2 active catalogue modules (R249 `training`, R449 `trainingprovider`) were on neither the dashboard nor the sidebar, so subscribers could only reach them by typing the URL. Added to both.
- Five staff routes still have no sidebar or dashboard entry: `/booking-agency`, `/payroll-bureau`, `/recruitment-agency` (agency-variant pages; not checked against `module_catalogue`), and `/control-exceptions`, `/recurring` (probably sub-pages of Internal Audit and Invoicing). Decide whether each should be a module entry.
- The dashboard tiles and the sidebar registry are two separate lists that had drifted apart; merge them into one source when the landing page is redone.

### HR ✅
- Routed sections `/hr/:section`; opens on Employees as before (`/hr` redirects to `/hr/employees`). Groups: Overview (Dashboard); People (Employees, Leave, Disciplinary); Payroll & compliance (Payroll, Compliance).
- Fixed a dead button: the dashboard's "Download EMP201" quick action navigated to a `sars` tab that no longer exists, so it opened an empty panel. It now opens Compliance, where EMP201 lives. (`SarsTab.tsx` is an unused older duplicate of `ComplianceTab`; left in place.)
- Colours: 36 -> 0 (department chart palette moved to `-text` tokens).

### Bookings ✅
- Routed sections `/bookings/:section`: Overview (Dashboard); Schedule (Calendar, Bookings); Setup (Services, Staff, Availability).
- The pending-count pill that sat on the Bookings tab is now a live badge on that sidebar item (`navigation/sectionBadges.ts`, same `bookings-pending-count` query key, so the invalidation after confirming a booking still refreshes it). Fetched only while inside Bookings. In icons-only mode any badge (this one and Security's LIVE) collapses to a dot on the icon.
- Colours: 52 -> 0, except the service colour swatches, which are data (see above).


## Regression found in the browser: null payloads leaked the response envelope

The first browser test (`/security/guard-screening`) went blank with "Objects are not valid as a React child (found: object with keys {success, message, timestamp})".

- **Cause:** `ApiResponse<T>` is serialised with `@JsonInclude(NON_NULL)`, so when a payload is null the `data` key is omitted. `apiClient`'s response interceptor only unwrapped when a `data` key existed, so a null payload returned the whole `{ success, message, timestamp }` envelope. The screening "gate warning" is a nullable string, and the page rendered the envelope.
- **How I caused it:** the earlier double-unwrap fix changed `res.data?.data` to `res.data`. That is right when a payload exists but wrong when it does not (the old code got a harmless `undefined` from the envelope). I checked each endpoint's return type for paging, but not whether it could return nothing.
- **Fix, in the client so it is correct everywhere:** a body with a boolean `success` and a string `timestamp` and no `data` key is now unwrapped to `null`. Any body with a `data` key unwraps as before; anything else is left alone. It also fixes a latent crash: the old `'data' in body` check threw a TypeError on any plain-text response.
- **Verified** with a 10-case scratch test (payload list/object/explicit null/omitted, message omitted, non-envelope, a body with only `success` and `message`, string, array, empty); against the original client 3 of them fail (the regression, its variant, and the string body). Not committed (no test runner in the project).
- **Residual risk (be aware):** a component that reads a property straight off a query result that can be null, or whose destructuring default (`= {}`) is skipped because `null` is not `undefined`, would now hit a TypeError where it used to get a leaked envelope. Searches for direct property reads on API results found none, but a browser pass is the real check.

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

## Tasks module review and rebuild

Backend (`platform`, tasks module):
- **V304** adds `task_columns.category` (TODO / IN_PROGRESS / IN_REVIEW / BLOCKED / DONE), allows status `BLOCKED`,
  and heals tasks whose status had drifted from their column. A task's status is now the category of its column,
  not a guess from the column's name. Backfill uses the old name heuristics; correct any column in Board settings.
- **V305** (optional) gives `EMPLOYEE` roles `TASKS_READ` + `TASKS_MANAGE`, as Clinic and Projects do. Do not apply it
  if Tasks should stay admin-only.
- A column id from the client must belong to the board and tenant (was a bare `findById`: cross-tenant write/delete).
- Moving a task honours its position; fields can be cleared (`clearAssignee`, `clearDueDate`, ...); request validation
  returns 400 instead of 500; priority sorts by rank; archived boards are excluded from counts and alert sweeps;
  deleting a column no longer fails when soft-deleted tasks still point at it; a new tenant gets a default board on
  first load; the notification sweeps no longer run in one transaction; business-day (Africa/Johannesburg) dates.
- Verified: migrations and every new SQL statement against a real PostgreSQL 16 (36 checks). **Java not compiled**
  (no Maven Central in the build sandbox): syntax, signature and enum-vs-CHECK checks only. Run
  `mvn test -Dtest='TasksServiceTest,TaskTest'`.

Frontend (`src/pages/tasks`): the 1,432-line `TasksPage.tsx` is split into types / constants / logic / api hooks /
components. New Modernize-style board (tinted lists, cards with avatar + priority pill + footer counts), drag and drop
with dnd-kit (mouse, touch long-press, keyboard) plus a "Move to" menu, optimistic moves with rollback, URL state
(`?board=&task=&view=` + filters), permission gating with `usePermission`, toasts instead of swallowed errors, board
settings (lists, stages, colours, archive), truncation warning, task modal can edit priority / due date / estimate and
unassign. Fixed: a task due today showed as overdue from 02:00 SAST (UTC parse of date-only strings).
- Needs `npm install` (new dependencies `@dnd-kit/*`).
- Verified with throwaway vitest tests (33, in `tasks-scratch-tests/`, not committed: vitest is not a project dependency),
  tsc/eslint clean for the Tasks files, production bundle builds. **Not verified in a browser**; real drag gestures are
  untested (jsdom cannot drive them).
- Not done: real server paging, tags, comment edit/delete, activity log, recurring tasks, multi-instance scheduler lock,
  optimistic-lock conflict handling.

## The five unregistered routes
`/booking-agency`, `/payroll-bureau`, `/recruitment-agency`, `/control-exceptions`, `/recurring` were routed in
`App.tsx` but owned by no navigation entry.
- `bookingagency`, `payrollbureau`, `recruitmentagency` added to the sidebar registry (`navigation/modules.ts`) and
  the dashboard registry. Keys match what the backend enforces (`requireModule(...)`). Both registries silently drop a
  subscribed module they have no entry for, so these were invisible to subscribed tenants.
- `/recurring` is not a module: it is the Recurring tab of Invoicing. Invoicing now declares it as an `alias`, and the
  sidebar highlights by alias (before, nothing was highlighted there).
- `/control-exceptions` is a cross-module board open to any signed-in user, so it is a Workspace link, not a module.
- `check:navigation` now also fails when the two registries list different module keys, or when an `App.tsx` route
  base is owned by nothing and is not in `NON_MODULE_ROUTES` (auth, public, portal, profile, home). Run against the
  old registries it reports exactly these five routes.
- Fixed in passing: the dashboard's profile button called `setNotifOpen`, which does not exist (ReferenceError on click).

## Sidebar context mode for the last nine modules
Recruiter, Creative, Catalogue, Compliance Services, Desk Support, Expenses, POS & Stock, Projects and Tasks now use
the same sidebar as the other modules: "All modules", the module name, then its sections (`*_SECTIONS` in
`navigation/moduleSections.ts`, 33 configs in all). Each page renders through `SectionedModulePage`, and its route is
`/<module>/:section?`.
- Recruiter: Job Postings / Pipeline / Applications. POS: POS Terminal, Transactions / Stock, Purchase Orders.
  Projects: Dashboard / Projects. Tasks: Boards / My tasks. Creative, Catalogue, Compliance Services, Desk and Expenses
  have one section each, under "Overview".
- The in-page header and tab bar are gone (the shared header supplies the title, breadcrumbs and actions); the section
  is chosen by the URL, not by `useState`.
- URLs gain a section (`/tasks` -> `/tasks/boards`); old URLs redirect and **the redirect now keeps the query string**
  (`SectionedModulePage`), so `/tasks?board=&task=` deep links still work.
- Projects: the open project is `?project=<id>`; the old orphan `/projects/:id` route is removed and a legacy
  `/projects/<uuid>` link redirects there. `ProjectDetailPage.tsx` and `ProjectListPage.tsx` are now unreferenced.
- Fixed: `NotificationDrawer` read the route base from the whole URL, so `/tasks?board=...` (and every actionUrl with a
  query) fell back to the dashboard. The logic is now `navigation/actionUrl.ts`, reads the path only, keeps the query
  for Tasks, and derives its allow-list from the navigation registry instead of a hand-kept copy.
- Verified: 124 throwaway vitest tests (not committed; vitest is not a project dependency); `check:navigation` passes
  (33 configs); no new type or lint errors against the clean baseline (type errors 247, was 249). Not verified in a browser.

## Agriculture: crops, seasons and cost reports (frontend only)

**What shipped.** Crop cycles, seasons, crop types and cost reports, using 49 backend endpoints that previously had no UI. No Java was changed.
Sidebar gained **Crops** (Crop cycles, Seasons, Crop types) and **Insights** (Cost reports). The same components are also tabs inside a farm.

**Farm scope.** Crop cycles, seasons and costs are farm-scoped on the server, so those sections use a farm picker (`?farm=<id>`, default first active farm).
An "all farms" view is deliberately not built: it would mean looping over every farm, and needs a tenant-wide endpoint.

**Costs only.** Agriculture records no revenue, labour cost, equipment cost or weather, so there is no margin or profit anywhere. Decision: revisit later.
Crop cost = seed + recorded inputs; labour hours are shown but not costed.

**Rules the backend does not enforce, so the UI does:**
- fail/abandon/delete are accepted by the server in any status; the UI only offers them on live cycles
- input cost is stored as sent and reports read it, so the form prefills quantity x stock unit cost
- stock over-issue is caught before the 409
- yield per hectare is a plain sum labelled with the crop type's default unit, so mixed harvest units are warned about
- season activate/close have no guard, so activating a second season asks first
- crop category, input type, observation type, unit and grade are free text on the server; the UI offers a fixed vocabulary

**Verification.** On a fresh clone of origin with the nav, sidebar and agriculture patches applied: `npm ci`, `vite build`, `check:navigation` (33 configs), `audit:data-colors` (0),
tsc 247 errors (baseline 249), 203 scratch tests pass, 7 mutation checks all caught. **Not verified in a browser.**

**Still needs backend work:** tenant-wide farm dashboard, trends, richer attention severities (only OVERDUE / DUE_TODAY / MEDIUM exist), harvest unit normalisation, server-side guards on lifecycle and seasons.
**Not built:** scouting edit (PUT exists, unused), per-record input/harvest edit and delete (no endpoints).

## Agriculture phases 1 and 2: backend hardening and the tenant-wide dashboard

**Phase 1: server-side guards (previously enforced only by the web UI)**
- Crop cycle: mark-failed only from PLANTED/GROWING/HARVESTING; abandon not from a finished cycle; expected harvest not before planting.
- Inputs and scouting refused on FAILED/ABANDONED cycles; harvests also refused before planting.
- Season: activate not when already active, close only from ACTIVE, update re-checks the dates, delete refused while crop cycles use it.
  Deliberately NOT enforced: one active season per farm (farms run overlapping seasons; the UI warns instead).
- Crop cycle create now checks its references exist in the tenant and belong to the same farm (area, season, enterprise, crop type
  active, season not closed); seed/inventory stock must belong to the cycle's farm. Season create checks the farm exists.
- Harvest units: kg, g, t, lb convert; anything else must match the crop type's unit; incompatible units are rejected (400). Yield is
  summed in the crop's unit; units that cannot convert (older data) are excluded and counted in `unconvertedYieldUnits`.
- Richer attention severities: CRITICAL (out of stock, open HIGH scouting), OVERDUE, DUE_TODAY, UPCOMING (next 7 days), MEDIUM.
  New item types HARVEST_DUE and SCOUTING_HIGH_SEVERITY; every item carries farmId/farmName.
- Bug fixed: `countActiveForFarm` for animals and groups counted sold/deceased/culled animals and closed groups as "active".

**Phase 2: `GET /api/v1/agriculture/dashboard`** (AGRICULTURE_READ): totals, farm types, farm locations, crops in production,
livestock by species and the ranked attention list for all ACTIVE farms, from grouped queries (not one request per farm).
Attention is computed per farm on the server (a few queries per farm); fine for a handful of farms, would need joins to scale.

**Frontend:** new dashboard (KPIs, attention list, farm-type donut, livestock and crop bars, farm map, farm table); harvest tab
converts and blocks units like the server; season delete explains why it is blocked. Fixed: crop cycle Edit erased notes
(PUT replaces variety, name, expected harvest and notes).

**Verification.** Java cannot be built here (no Maven). Instead: the real Agriculture sources (150 files) were compiled against stubbed
libraries with Lombok emulated, 0 errors, and the harness catches planted mistakes; the dependency-free rules and aggregator and the
entity tests were RUN (JUnit stand-in), 57 tests; Mockito service tests were type-checked, not run. Frontend: 231 scratch tests,
tsc 247 (baseline 249), guards pass.
**Still to do on your side:** `mvn test`, and the `HandyFlowApplicationTests` context load (Spring validates every new @Query at startup).
Not browser-tested.

**Next:** trends and a production chart (phase 3); then revenue, labour and equipment cost, suppliers, report export, weather, NDVI.

## Agriculture phase 3: trends and the production chart

**`GET /api/v1/agriculture/trends?farmId=&months=`** (AGRICULTURE_READ; `farmId` omitted = every farm; `months` 1 to 24, default 12).
Computed from dated records only, with the cost reports' own definitions so monthly totals agree with them:
- costs by month: seed (stock issued to a crop cycle), inputs, feed, health (any status), animal purchases
- harvest by month in tonnes (kg, g, t, lb convert; a unit that is not a mass is excluded and counted), and per crop in the crop's own unit
- livestock EVENTS by month: births (a birth with no head count counts as 1), deaths (head) and the recorded value of losses
- last 30 days against the 30 before (like-for-like, not a part month against a full one): total/crop/livestock cost, harvest tonnes,
  births, deaths, high-severity scouting; percentage is null when the earlier period was zero

**What it cannot say, by design:** herd size over time (an animal's status changes but not when, so there is no head-count history:
births and deaths are shown instead) and anything about revenue, labour or equipment cost (no data). The response carries these as
`limitations` and the page prints them.

**Frontend:** Insights > Trends (All farms or one, 6/12/24 months): comparison cards with good/bad colouring (cost and deaths rising is
bad; harvest and births rising is good), harvested-tonnes, stacked-cost and births-vs-deaths charts, harvest by crop. The dashboard has a
last-30-days strip that disappears quietly if trends cannot be loaded. The current month is marked `*` (partial).

**Known limits:** each request loads an id-to-farm lookup for all animals, groups and crop cycles of the tenant (fine for thousands, a
join would scale better); records whose animal, group or cycle was deleted are left out (as in the cost reports).

**Verification:** same method as phases 1 and 2. Java is not built here; the real sources compile against stubs with 0 errors, the pure
rules and aggregator tests RUN (57, of which 17 are the trends aggregator) plus 36 entity tests, the Mockito service tests are type-checked only, and the new `@Query` methods and the
controller test have never run. **Run `mvn test` and the application context-load test.** Frontend: 252 scratch tests, tsc 246 (baseline
249), guards pass, not browser-tested.

**Next (needs decisions first):** revenue and gross margin, labour cost, equipment cost; then suppliers, report export, weather, NDVI.

## Agriculture W1: cost allocation ledger (ADR-001)

Defaults in ADR-001 section 4 were accepted, and W1 is built. **W2 to W9 are not started.**

- **Table `ag_cost_entries` (V306)**, append-only: a cost is one or more rows, one per target (crop cycle, group, animal, enterprise), sharing an
  `allocation_group_id`. A mistake is REVERSED: the original is flagged and a negative REVERSAL row, dated like the original, nets it out. Net cost is a plain SUM.
  Columns for the snapshot (`quantity`, `unit`, `rate`, `amount`) and a `source_type`/`source_ref` are ready for HR, Fleet and Fuel (W3, W4).
- **Only the NEW categories** (LABOUR, EQUIPMENT, FUEL, OTHER_DIRECT) live here. Feed, health, inputs, seed and animal purchases stay in their own tables, so
  nothing is counted twice. Only OTHER_DIRECT can be typed in; the others will be costed from their owning modules.
- **Exact splitting:** a cost split by percentage is apportioned by largest remainder in whole cents, so the parts always add up to the cost to the cent. The
  server (`AgCostAllocation`) and the page preview (`agLedger.logic.ts`) use the same algorithm.
- **Integrity:** every target must exist in the tenant AND belong to the farm; percentages must total 100; no future dates; one bad target stops the whole cost.
- **Permission `AGRICULTURE_FINANCE`** (seeded to ADMIN; new tenants get it because their ADMIN role is given every permission). Every ledger endpoint needs it:
  READ, MANAGE and ADMIN are not enough, because rates behind labour cost are derived from salaries. The page makes no request for a user without it.
- **API:** `POST/GET /farms/{id}/cost-entries`, `GET /farms/{id}/cost-entries/totals`, `POST /cost-entries/groups/{id}/reverse`.
- **UI:** Insights > Cost ledger: net cost by category, costs with their splits, filter by target, record a cost (live exact preview, "Split evenly"), reverse.
- **Not yet:** cost reports, trends and the dashboard do not include ledger costs; they will be combined in W5 in one place.

**Verification:** same method as before. Pure and entity tests RUN (allocation 12 tests, cost entry 7), Mockito service test type-checked only, controller test and the
new `@Query` methods and the V306 migration never run. **Run `mvn test`, the context-load test, and apply V306 to a database.** Frontend: 276 scratch tests,
tsc 246 (baseline 249), guards pass, not browser-tested.

**Process note:** the scratch tests were accidentally committed in two earlier patches (they import vitest, which is not a project dependency). A corrective commit
untracks them and `.gitignore` now excludes `src/__scratch__/` and `vitest.scratch.config.ts`.

## Agriculture W2: sales and revenue linked to production (ADR-001)

W2 is built. **W3 to W9 are not started.**

- **Invoicing facade (read-only):** `findSaleLine`, `findSaleLines`, `searchSaleLines` return invoice lines with status, customer name, ex-VAT line total, the invoice's
  ex-VAT subtotal and the credit notes against it. Nothing lets another module create or change a sale. New `InvoiceRepository`/`CreditNoteRepository` queries back it.
- **Table `ag_sales_allocations` (V307):** which part of an invoice line belongs to which target. It stores NO money. Allocations are removed (status REMOVED), not deleted.
- **Revenue rules** (`AgRevenueRules`): issued onwards only (ISSUED, PARTIALLY_PAID, PAID, OVERPAID, OVERDUE; never DRAFT or CANCELLED); ex-VAT; credit notes netted in
  proportion across the invoice's lines (they belong to the invoice, not a line); a line's revenue is shared across all its allocations by quantity with an exact
  largest-remainder split, and can never pay out more than the line earned. The tests caught a real over-payout bug here (a hair over the line paid R500.02 on R500.00).
- **Integrity:** the target must exist and belong to the farm (one shared check with the cost ledger, `AgTargetOwnership`); only issued invoices can be allocated; a line cannot be
  over-allocated, counting every farm's allocations; no future sale date; a single animal is one head.
- **Access:** every endpoint needs AGRICULTURE_FINANCE; those that read or return invoice data also need INVOICE_READ and an Invoicing subscription.
- **Not done on purpose:** it does not mark animals sold or reduce a group's count (there is still no real livestock sale event; the allocation records when and how many).
  Revenue is not in cost reports, trends or the dashboard yet (W5). Currency is assumed to be ZAR (it is returned but not converted). The invoice picker filters text in memory
  over at most 200 invoices.
- **Module boundary:** Agriculture's `allowedDependencies` now include `invoicing` (Debt Collection already does the same). `ArchitectureVerificationTest` has not been run.

**Verification:** same method as before. Pure and entity tests RUN (revenue rules 12, sales allocation 6), Mockito tests type-checked only (sales service, Invoicing facade),
controller test, the new `@Query` methods and V307 never run. **Run `mvn test` (including `ArchitectureVerificationTest`), the context-load test, and apply V307.**
Frontend: 301 scratch tests, tsc 246 (baseline 249), guards pass, not browser-tested.
