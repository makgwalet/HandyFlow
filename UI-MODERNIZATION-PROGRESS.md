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

**Status (be careful with earlier optimism):** every staff page inside the shell is converted. 230 hard-coded colours remain in `src/pages`, all outside the shell: the auth pages 114 (login, register, password reset), client portals about 96 (accountant-portal 24, auditor-portal 19, careers 14, collections-agency-portal 11, warehousing-portal 9, property-portal 7, training-provider-portal 6, small ones 6), and about 20 that are intentional and must stay literal (Tasks board and Bookings service colour palettes, Contracting signature-pad canvas, Marketing's sample email HTML). `styles/brand.ts` holds the brand palette definitions and correctly stays hex. Re-measure rather than trusting counts written here.


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
