# HandyFlow Agriculture — Mobile App Overview

This document grounds the code in `src/` against the full product/UX brief
supplied for this project (livestock, poultry, crops, offline-first, role-based
experiences, deep HandyFlow ecosystem integration). It is intentionally short —
see the source brief for the full 40-point spec. Fuller docs (domain model,
IA, API map, offline architecture, etc.) should be split out as the app grows
past this first increment.

## Update — Livestock is now wired to the real backend

`10-API-MAP.md` and `11-API-GAPS.md` now exist. The Java Agriculture
module was inspected directly (controllers/DTOs/services under
`platform/.../agriculture/`), confirming Increment 1 (Farm Foundation +
Livestock) and Increment 2 (Crops) are real and callable. Poultry,
Aquaculture, and Farm Map are confirmed **not** built on the backend yet —
not a mobile-app gap, a backend-scope decision (`package-info.java`
explicitly sequences them for later).

Built against that real API, no mocking:
- `src/api/` + `src/domain-types/agriculture.ts` — axios client and typed
  service functions for Farms, Animals, Groups, matching the backend DTOs
  field-for-field.
- `src/hooks/agriculture/` — TanStack Query hooks (`useFarms`,
  `useAnimalsForFarm`, `useGroupsForFarm`, `useAnimal`, `useGroup`,
  `useAnimalWeightHistory`, `useRecordWeight`, `useRecordHealthEvent`,
  `useRecordMortality`).
- `src/screens/livestock/` — Farm list → Animal/Group list → Animal/Group
  profile (real weight history) → Record Weight / Record Treatment /
  Record Mortality, each a React Hook Form + Zod form validated to match
  the backend's own `@NotNull`/`@NotBlank` rules, posting real mutations.
- Wired into a nested stack inside the `Production` tab
  (`src/navigation/ProductionStackNavigator.tsx`).
- Home screen's Quick Capture: Weight/Treatment/Mortality now navigate into
  that real flow; Eggs/Harvest/Photo show an explicit "not available yet"
  reason instead of doing nothing or pretending to work. Poultry stats
  (eggs/birds) were removed from the Home stat row rather than left in as
  decoration, since there's no backend behind them.

This proves out 3 of the "10 critical journeys": *Quick Record → animal
weight*, *Quick Record → treatment*, and *Livestock → animal → history*.

## Update — Crops feature added

Same pattern as Livestock, against fully-confirmed backend DTOs
(`AgCropCycleController`, `AgInputApplicationService`,
`AgScoutingRecordService`, `AgHarvestRecordService`):

- `src/api/agriculture/cropCycles.ts` + the crop-cycle types in
  `src/domain-types/agriculture.ts`.
- `src/hooks/agriculture/useCrops.ts` — crop cycles, input applications,
  scouting records, harvest records.
- `src/screens/crops/` — Crop Cycle list → Crop Cycle profile (input/
  scouting/harvest history, an open-scouting-findings banner) → Record
  Input Application / Record Scouting / Record Harvest.
- `src/screens/livestock/FarmHubScreen.tsx` — picking a farm now offers
  Livestock or Crops, rather than jumping straight to Animals.
- Home screen's "Harvest" quick-capture action is now real.

This proves out 2 more of the "10 critical journeys": *Crops → field →
scout → photo/GPS* (photo/GPS itself still stubbed — see API GAP 5-adjacent
note in the Crops scouting screen) and *Crops → field → harvest*.

Known scope gap from this pass, documented as API GAP 12: the app never
calls the crop cycle's own status transitions (`record-planting`,
`mark-growing`, `start-harvest`, `complete-harvest`) — those endpoints
exist but nothing in the UI uses them yet.

## Update — Offline sync queue + crop status transitions

**GAP 12 closed.** `CropCycleProfileScreen` now shows the right next
action for the cycle's current status (`Record Planting` for `PLANNED`,
then `Mark Growing` → `Start Harvest` → `Complete Harvest`), each backed by
a real mutation (`src/hooks/agriculture/useCrops.ts`).

**Offline architecture v1 landed** — see
`docs/agriculture/12-OFFLINE-ARCHITECTURE.md` for the full design. Short
version: a SQLite-backed pending-mutations queue
(`src/offline/db.ts`, `src/offline/queue.ts`), a `writeOrQueue` helper in
`src/api/client.ts` that only queues genuine network failures (not
validation errors), and a `useOfflineSync` hook wired into the Home
screen's `SyncStatusBar`, which now shows a real pending count and can
trigger a manual sync. Wired into exactly one flow so far — Record Weight
— as the proof of concept; every other capture screen still fails loudly
offline. That gap is intentional and documented, not accidental.

This proves out the 10th and last of the brief's "10 critical journeys":
*Offline record → sync* (for the one flow it's wired into).

## What exists right now

- **Design system** (`src/design-system/`): semantic light/dark tokens
  (background, surface, textPrimary/Secondary, success/warning/danger/info,
  livestock/poultry/crop/production accents), spacing, radius, type scale.
- **Navigation shell** (`src/navigation/RootNavigator.tsx`): five-tab IA —
  Home, Farm Map, Production, Tasks, More — matching the "farmer thinks in
  jobs, not modules" decision in the brief.
- **Home / Today screen** (`src/screens/HomeScreen.tsx`): the command-centre
  pattern from the brief — greeting, today's stats, an Attention list
  (severity-coded, never colour-only), and a Quick Capture grid of large
  one-handed tap targets. A persistent `SyncStatusBar` is always visible so a
  worker never has to wonder whether a record was saved.
- The Home screen's greeting/stats/attention list still come from
  `src/data/placeholderHomeData.ts`, clearly marked as placeholder — not a
  mock API. Quick Capture actions with a real backend now navigate into the
  Livestock flow instead of using placeholder data (see update above).

## What is deliberately NOT built yet

- Offline support beyond Record Weight — see
  `docs/agriculture/12-OFFLINE-ARCHITECTURE.md`'s "what this deliberately
  does not cover" section for the full list (idempotency, conflict
  resolution, attachments, background sync, auth-expiry handling, and
  every capture screen besides Record Weight still failing loudly offline).
- `Farm Map` and `Tasks` remain stub screens. Farm Map has no geometry data
  on the backend to show (API GAP 3); Tasks has no backend facade at all
  (API GAP 6) and needs a product decision on what it should show instead
  (see that gap's proposed relabel to "Due").
- A real Home/Today summary. There's no aggregate endpoint (API GAP 4) and
  no attention/alerts-read endpoint (API GAP 5); both have documented,
  honest workarounds that haven't been built yet.
- Scan-first flows (QR/barcode/RFID/NFC), camera capture flows, GPS-tagged
  scouting.
- Onboarding / farm-type configuration wizard.
- Role-based home variants (worker/manager/owner/vet/agronomist/finance).
- Tablet split-view and desktop layouts.
- Offline sync engine (local DB, sync queue, retries, conflict resolution) —
  `expo-sqlite` is already a dependency but unused so far; every mutation
  today requires a live connection and will simply error offline.

## Suggested next increments (in order)

1. **Extend `writeOrQueue` to the remaining capture screens** — Treatment,
   Mortality, Input Application, Scouting, Harvest, Record Planting all
   still fail loudly offline. Mechanical per screen (see
   `docs/agriculture/12-OFFLINE-ARCHITECTURE.md`), but real farm-data-loss
   risk until done.
2. **Connectivity-triggered auto-sync** — add a `NetInfo`-style listener so
   the queue drains automatically when the device comes back online,
   instead of only on the next successful write or a manual tap.
3. **Attention list workaround (API GAP 5)** — replace the Home screen's
   placeholder attention items with a real client-side composition: fetch
   each visible farm's health events, scouting follow-ups, and inventory
   items, filter for due/low-stock. Flag to the backend team that a
   dedicated endpoint would be far cheaper.
4. **Confirm the assumptions in API GAPS 9–11** (exact `ApiResponse` shape,
   auth/tenant mechanism, the two inferred harvest-transition paths)
   against a real response or the OpenAPI spec — cheap to do now, expensive
   to discover wrong later. This matters more now that offline replay
   depends on those exact paths being right.
5. **Farm Map tab** — blocked on backend geometry data (API GAP 3); raise
   with the backend team before attempting a client-side placeholder.

## Design principle to preserve

> "Capture what happens on the farm, connect it to the business, and turn it
> into better decisions."

Every new screen should be judged against: does this help someone record
something in one interaction, or see what needs attention right now?
