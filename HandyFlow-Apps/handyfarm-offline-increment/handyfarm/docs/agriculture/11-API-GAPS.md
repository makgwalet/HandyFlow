# HandyFlow Agriculture — API Gaps

Per the project's hard rule: if the backend doesn't have it, the app must
not quietly mock it. Each gap below states what's missing, why the mobile
app wants it, and the workaround (if any) used in the meantime.

## GAP 1 — Poultry is not implemented at all

**Missing:** every poultry concept — Houses, Layer flocks, Broiler batches,
Rearing, Hatchery, egg collection, FCR/lay-rate performance.
**Why the app wants it:** the product brief's Home screen mock, Poultry tab,
and 6 of the "10 critical journeys" assume poultry exists.
**Status:** confirmed out of scope for Increment 1/2 by
`package-info.java`: *"Poultry (including hatchery), Aquaculture, Farm
Map/GIS, weather, satellite/IoT integrations, AI anomaly detection... are
all deliberately out of scope for this module as it stands — sequenced,
not rejected."*
**Workaround in this build:** none. The `Production` tab currently only
exposes Livestock (Animals/Groups), which the backend actually supports.
The old placeholder Home screen's egg/bird stats have been removed rather
than left in as decoration — see `00-OVERVIEW.md`.

## GAP 2 — Aquaculture is not implemented at all

Same status as Gap 1 (species/ponds/tanks/cages/batches/stocking/biomass/
water quality/FCR/harvest). Not started.

## GAP 3 — Farm Map / GIS is not implemented

**Missing:** any endpoint returning farm geometry, field boundaries, or a
spatial layout of production areas. `AgFarm` has `gpsLatitude`/
`gpsLongitude` (a single point) and `AgProductionArea` has
`sizeHectares`, but no boundary/polygon data, no water/infrastructure/
equipment layer.
**Why the app wants it:** the brief treats Farm Map as a first-class,
tap-a-field/tap-a-herd spatial operating layer.
**Workaround:** the `Farm Map` tab remains a stub screen. Building a real
one now would mean inventing geometry data the backend doesn't store.

## GAP 4 — No aggregate "Home / Today" summary endpoint

**Missing:** a single endpoint like `GET /api/v1/agriculture/farms/{id}/today-summary`
returning today's counts, attention items, and pending actions in one call.
**Why the app wants it:** the brief's core UX decision is a command-centre
Home screen — "6,284 eggs / 8,420 birds / 342 cattle / 284 ha" in one glance.
**Workaround:** none implemented yet in this increment. The honest options,
in order of preference, are:
1. Request this endpoint from the backend team (cheapest for both sides —
   one round trip instead of N).
2. Compose it client-side from `GET /farms/{id}/animals`,
   `/farms/{id}/groups`, `/farms/{id}/crop-cycles`, and cost-summary calls,
   accepting N requests and no server-side "what changed today" filtering.
Given `/farms/{id}/animals` and `/groups` return full paginated lists with
no `updatedToday`/`createdToday` filter, option 2 can show *current state*
(e.g. "342 active animals") but cannot honestly show "what happened today"
without also fetching weight/health/mortality/feed record pages and
filtering by date client-side — expensive and still incomplete once a farm
has thousands of animals. Recommend option 1.

## GAP 5 — No "attention" / alerts read endpoint

**Missing:** a `GET` endpoint returning the current tenant's outstanding
health-event reminders (`nextDueDate` due) and low-stock inventory items in
one list, ranked by severity.
**What exists instead:** `AgNotificationScheduler` (health events) and
`AgCropNotificationScheduler` (scouting follow-ups) run daily sweeps that
*push* notifications via the platform's `notifications` module — but no
`AgAnimalController`/`AgGroupController`/`AgInventoryItemController`
endpoint was found that lets a client *pull* "what's currently overdue" on
demand (as opposed to receiving a push once).
**Workaround available today:** the Attention list can be computed
honestly, without inventing data, by combining:
- `GET /animals/{id}/health-events` / `/groups/{id}/health-events` and
  filtering client-side for `nextDueDate <= today && !reminderAcknowledged`
- `GET /inventory-items` and filtering for `currentQuantity <= reorderLevel`
This is real data via real endpoints, just assembled on-device rather than
server-side — noted as a near-term backend improvement (a dedicated
`GET /farms/{id}/attention` would be far cheaper for a farm with many
animals/groups).

## GAP 6 — No Tasks module integration

**Confirmed by `package-info.java`:** *"NO tasks REUSE: tasks exposes no
public facade either (no TasksFacade exists anywhere in the codebase)."*
Agriculture generates its own actionable records instead (health event
`nextDueDate`, scouting `followUpDate`) surfaced via the notification
sweep. **Consequence for the app:** the "Tasks" tab in the mobile IA cannot
be backed by a generic cross-module task list yet. It should either be
removed from the nav for this increment, or repointed to show the same
health-event/scouting due-date items as Gap 5's workaround, relabelled
"Due" rather than "Tasks". Left as a stub screen for now rather than
building either without a decision from the product owner.

## GAP 7 — No Supply Chain integration

**Confirmed by `package-info.java`:** *"NO supplychain REUSE: supplychain
exposes no public facade (ScmService is internal-only)."* Agriculture owns
its own `AgInventoryItem`/`AgStockMovement`, scoped per farm. This is not a
gap to fix — it's a documented, deliberate architecture decision — but it
means "Purchase feed → Supply Chain PO" from the product brief's §19 is not
buildable as written; only the Agriculture-owned inventory receive/issue/
adjust endpoints exist.

## GAP 8 — No harvest → sale/invoice linkage

**Confirmed by `package-info.java`:** deliberately out of scope — a farm
selling produce is a normal `invoicing`/`crm` commercial event, not
duplicated here. `AgHarvestRecord` records yield/quality only. Any
"Harvest → Sale" screen must hand off to Invoicing/CRM once those modules'
public facades are confirmed, not create an `AgSale` entity.

## GAP 9 — Exact `ApiResponse<T>` JSON shape unconfirmed

Every controller method returns `ApiResponse.success(data)`,
`ApiResponse.success(message, data)`, or `ApiResponse.error(message)`, but
the actual `ApiResponse` class body (field names, whether `success` is a
boolean, whether errors carry a code) was not directly inspected. `src/api/types.ts`
encodes the most likely shape and flags it as an assumption. **Action:**
confirm against a real response payload or the generated OpenAPI/Swagger
spec before this ships past local dev.

## GAP 10 — Auth/tenant mechanism unconfirmed from the client's side

`TenantContext.getTenantIdAsObject()` resolves server-side, but nothing in
the inspected Agriculture code shows whether tenant selection is encoded in
the JWT, a header, or a session. `src/api/client.ts` assumes "attach the
existing HandyFlow bearer token; nothing Agriculture-specific to add" and
flags this as an assumption pending confirmation from whatever module owns
login/session (not inspected — outside Agriculture's own module boundary).

## GAP 11 — A couple of exact paths still inferred

Resolved this pass: crop-cycle creation, scouting-record transitions, and
manager assignment's DTO are now confirmed (see `10-API-MAP.md`). Still
inferred, not directly captured in source: the exact paths for
`start-harvest` and `complete-harvest` on `AgCropCycleController` (inferred
from the `record-planting`/`mark-growing` naming convention) and
`AgProductionAreaController`'s status-change path. **Action before
wiring further:** grep the actual controller files directly (or pull the
Swagger spec) rather than trusting the inferred path. The mobile app's
`src/api/agriculture/cropCycles.ts` calls these two inferred paths for the
harvest-transition buttons that don't exist yet in the UI — no screen
currently calls `startHarvest`/`completeHarvest`, so this is a latent risk,
not a live bug.

## GAP 12 — Crops feature has no cycle status transitions in the UI (CLOSED)

~~The mobile app (as of the previous increment) let a user record input
applications, scouting observations, and harvest yields against a crop
cycle, but never called `record-planting`, `mark-growing`,
`start-harvest`, or `complete-harvest`.~~ **Closed:** `CropCycleProfileScreen`
now surfaces the correct next transition for the cycle's current status.
Still relies on the two inferred paths from GAP 11 (`start-harvest`,
`complete-harvest`) — if those paths are wrong, this is where it'll show up.

## GAP 13 — Offline queue only covers Record Weight

`src/offline/` (see `docs/agriculture/12-OFFLINE-ARCHITECTURE.md`) is a
real, working sync queue, but only `RecordWeightScreen` uses it via
`writeOrQueue`. Treatment, Mortality, Input Application, Scouting,
Harvest, and Record Planting all still call the plain `apiClient`/`unwrap`
path directly and will throw a normal error — losing the person's typed
input — if they're offline when they hit Save. This is a real farm-data
risk, not just an incomplete feature list, until each screen is migrated
to the same pattern.
