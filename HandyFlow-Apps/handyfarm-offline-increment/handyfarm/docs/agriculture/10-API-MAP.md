# HandyFlow Agriculture — API Map

Source of truth: Java classes under
`platform/src/main/java/za/co/handyflow/platform/agriculture/` inspected in
project knowledge (controllers, DTOs, services, `package-info.java`). This
lists only endpoints whose `@GetMapping`/`@PostMapping`/... signature and
DTO were actually seen. Nothing here is invented — see
`11-API-GAPS.md` for what is required but not confirmed to exist.

**Base path:** `/api/v1/agriculture`
**Auth:** every endpoint is `@PreAuthorize("hasAuthority('AGRICULTURE_READ')")`
or `'AGRICULTURE_MANAGE'` (delete endpoints use `'AGRICULTURE_ADMIN'`), plus
`featureGuard.requireModule("agriculture")`. Tenant is resolved server-side
from `TenantContext.getTenantIdAsObject()` — **not confirmed** whether that
comes from the JWT alone or an additional header; the mobile client assumes
"send the existing HandyFlow bearer token, nothing Agriculture-specific."
**Envelope:** every response body is `ApiResponse<T>` and every list is a
Spring Data `Page<T>`. The exact JSON field names of `ApiResponse` (e.g.
`success`/`message`/`data`) were not directly inspected — `src/api/types.ts`
documents this as an assumption to confirm against a real response or the
OpenAPI/Swagger doc before shipping.

**Status:** Increment 1 (Farm Foundation + Livestock) and Increment 2
(Crops) per `package-info.java`. Poultry, Aquaculture, Farm Map/GIS,
weather/satellite/IoT and AI insights are explicitly out of scope so far —
see API gaps doc.

## Reference data (tenant-scoped, not farm-scoped)

| Method | Path | Notes |
|---|---|---|
| GET | `/species` | `?category=` filter, paginated |
| GET | `/species/{id}` | |
| GET | `/crop-types` | `?category=` filter, paginated |
| GET | `/crop-types/{id}` | |

`AgSpecies`/`AgCropType` are tenant catalogues (shared across all the
tenant's farms). Breed and crop variety are plain string fields, not
separate catalogues.

## Farms

| Method | Path | Notes |
|---|---|---|
| GET | `/farms` | `?status=`, paginated |
| GET | `/farms/{id}` | |
| POST | `/farms` | `CreateFarmRequest` |
| PUT | `/farms/{id}` | `UpdateFarmRequest` |
| (assign manager endpoint exists — exact verb/path not captured; `AssignManagerRequest` DTO confirmed) | | validates employee via `HrFacade` |

`FarmResponse`: `id, name, farmType, registrationNumber, province, region, gpsLatitude, gpsLongitude, totalHectares, managerId, managerName, status, notes, createdAt, updatedAt`

## Production areas (camp/field/paddock/house/pen/pond/orchard — one entity, typed by `areaType`)

| Method | Path | Notes |
|---|---|---|
| GET | `/farms/{farmId}/production-areas` | `?status=`, paginated |
| POST | `/farms/{farmId}/production-areas` | `CreateProductionAreaRequest` |
| PATCH | `/production-areas/{id}/status` (exact path not fully captured) | `ChangeAreaStatusRequest` |

`areaType` is one of `CAMP \| FIELD \| PADDOCK \| HOUSE \| PEN \| POND \| ORCHARD \| OTHER` — this is deliberately the same entity Poultry/Aquaculture will reuse when they arrive.

## Animals (individually tracked)

Base: `/api/v1/agriculture` (controller: `AgAnimalController`)

| Method | Path | Notes |
|---|---|---|
| GET | `/farms/{farmId}/animals` | `?status=`, paginated |
| POST | `/farms/{farmId}/animals` | `CreateAnimalRequest` |
| GET | `/animals/{id}` | |
| PUT | `/animals/{id}` | `UpdateAnimalRequest` (signature not fully captured) |
| PATCH | `/animals/{id}/move` | `MoveAnimalRequest` |
| PATCH | `/animals/{id}/status` | `ChangeAnimalStatusRequest` |
| DELETE | `/animals/{id}` | `AGRICULTURE_ADMIN` |
| GET | `/animals/{id}/weight-records` | paginated |
| POST | `/animals/{id}/weight-records` | `RecordWeightRequest` — also updates `AgAnimal.currentWeightKg` |
| GET | `/animals/{id}/health-events` | paginated |
| POST | `/animals/{id}/health-events` | `CreateHealthEventRequest` |
| GET | `/health-events/{eventId}` | shared with groups |
| PATCH | `/health-events/{eventId}/complete` (name inferred from `markCompleted`) | |
| GET | `/animals/{id}/mortality-records` | paginated |
| POST | `/animals/{id}/mortality-records` | `CreateMortalityRecordRequest` — sets animal status to `DECEASED` |
| GET | `/animals/{id}/feed-records` | paginated |
| POST | `/animals/{id}/feed-records` | `CreateFeedRecordRequest` |
| breeding sub-resource (create/list + `confirm-pregnant`/`not-pregnant`/`record-birth` transitions) | `/animals/{id}/breeding-records`, `/breeding-records/{id}/...` | seen on `AgGroupController`; identical endpoints exist on `AgAnimalController` |
| evidence photos | generic passthrough to `EvidenceFacade` | exact path not fully captured |

### DTOs actually seen

```
CreateAnimalRequest(farmId*, productionAreaId, enterpriseId, speciesId*, tagNumber*,
  name, breed, sex*, dateOfBirth, estimatedAge, sireId, damId,
  acquisitionType*, acquisitionDate*, acquisitionCost)

AnimalResponse(id, farmId, productionAreaId, enterpriseId, speciesId, tagNumber,
  name, breed, sex, dateOfBirth, estimatedAge, sireId, damId, acquisitionType,
  acquisitionDate, acquisitionCost, currentWeightKg, status, notes, createdAt, updatedAt)

RecordWeightRequest(recordedDate*, weightKg*, sampleSize, recordedBy, notes)
  — shared by animals AND groups; sampleSize is ignored for an animal.

WeightRecordResponse(id, animalId, groupId, recordedDate, weightKg, sampleSize,
  recordedBy, recordedByName, notes, createdAt)

CreateHealthEventRequest(animalId, groupId, eventType*, eventDate*, description*,
  productUsed, dosage, administeredBy, veterinarian, cost, withdrawalPeriodDays,
  nextDueDate, status, notes)
  — exactly one of animalId/groupId required. eventType one of
  VACCINATION | TREATMENT | OBSERVATION | ILLNESS | INJURY | DEWORMING.

HealthEventResponse(id, animalId, groupId, eventType, eventDate, description,
  productUsed, dosage, administeredBy, administeredByName, veterinarian, cost,
  withdrawalPeriodDays, nextDueDate, reminderAcknowledged, status, notes,
  createdAt, updatedAt)

CreateMortalityRecordRequest(animalId, groupId, mortalityDate*, countLost*,
  causeCategory*, causeDetail, estimatedValueLoss, reportedBy, notes)

MortalityRecordResponse(id, animalId, groupId, mortalityDate, countLost,
  causeCategory, causeDetail, estimatedValueLoss, reportedBy, reportedByName,
  notes, createdAt)
```
(* = `@NotNull`/`@NotBlank` on the backend — mirrored in the Zod schemas.)

## Groups (batch/flock/herd tracked)

Base: `/api/v1/agriculture` (controller: `AgGroupController`) — structurally
mirrors `AgAnimalController` exactly, using the *same* `RecordWeightRequest`,
`CreateHealthEventRequest`, `CreateMortalityRecordRequest`,
`CreateFeedRecordRequest` DTOs with `groupId` populated instead of `animalId`.

| Method | Path | Notes |
|---|---|---|
| GET | `/farms/{farmId}/groups` | `?status=`, paginated |
| POST | `/farms/{farmId}/groups` | `CreateGroupRequest` |
| GET | `/groups/{id}` | |
| PATCH | `/groups/{id}/move` | `MoveGroupRequest` |
| PATCH | `/groups/{id}/reduce-count` | `AdjustGroupCountRequest` — loss/sale not routed through mortality |
| PATCH | `/groups/{id}/increase-count` | `AdjustGroupCountRequest` |
| PATCH | `/groups/{id}/close` | auto-closes at zero anyway |
| PATCH | `/groups/{id}/reopen` | |
| DELETE | `/groups/{id}` | `AGRICULTURE_ADMIN` |
| GET/POST | `/groups/{id}/weight-records` | updates `averageWeightKg` |
| GET/POST | `/groups/{id}/health-events` | |
| GET/POST | `/groups/{id}/mortality-records` | reduces `currentCount` |
| GET/POST | `/groups/{id}/feed-records` | |
| GET/POST | `/groups/{id}/breeding-records` | |

`CreateGroupRequest(farmId*, productionAreaId, enterpriseId, speciesId*, batchNumber*, breed, initialCount*, originDate*, acquisitionType*)`

`GroupResponse(id, farmId, productionAreaId, enterpriseId, speciesId, batchNumber, breed, initialCount, currentCount, averageWeightKg, originDate, acquisitionType, status, notes, createdAt, updatedAt)`

## Crop cycles (Increment 2)

Base: `/api/v1/agriculture` (controller: `AgCropCycleController`) — fully confirmed this pass.

| Method | Path | Notes |
|---|---|---|
| GET | `/farms/{farmId}/crop-cycles` | `?status=`, paginated |
| GET | `/seasons/{seasonId}/crop-cycles` | paginated |
| POST | `/farms/{farmId}/crop-cycles` | `CreateCropCycleRequest` — starts as `PLANNED`, or `PLANTED` if `plantingDate` given at creation (auto-issues seed if `seedInventoryItemId` set) |
| GET | `/crop-cycles/{id}` | |
| PUT | `/crop-cycles/{id}` | `UpdateCropCycleRequest` |
| DELETE | `/crop-cycles/{id}` | `AGRICULTURE_ADMIN` |
| PATCH | `/crop-cycles/{id}/record-planting` | `RecordPlantingRequest` — `PLANNED → PLANTED` |
| PATCH | `/crop-cycles/{id}/mark-growing` | `PLANTED → GROWING` |
| PATCH | `/crop-cycles/{id}/start-harvest` | `GROWING\|PLANTED → HARVESTING` — **path inferred** from the `record-planting`/`mark-growing` naming convention; not directly captured, confirm before relying on it |
| PATCH | `/crop-cycles/{id}/complete-harvest` | `HARVESTING → HARVESTED` — **path inferred**, same caveat |
| PATCH | `/crop-cycles/{id}/fail` | `CropCycleReasonRequest` |
| PATCH | `/crop-cycles/{id}/abandon` | `CropCycleReasonRequest` |
| GET/POST | `/crop-cycles/{id}/input-applications` | `CreateInputApplicationRequest` — fertiliser/pesticide/herbicide/fungicide/irrigation, one entity for all (irrigation is just another `inputType`) |
| GET/POST | `/crop-cycles/{id}/scouting-records` | `CreateScoutingRecordRequest` |
| GET | `/scouting-records/{id}` | |
| PUT | `/scouting-records/{id}` | `UpdateScoutingRecordRequest` |
| PATCH | `/scouting-records/{id}/resolve` | `OPEN → RESOLVED` |
| PATCH | `/scouting-records/{id}/reopen` | |
| PATCH | `/scouting-records/{id}/acknowledge-follow-up` | acknowledges without resolving |
| GET/POST | `/crop-cycles/{id}/harvest-records` | `CreateHarvestRecordRequest` — multiple per cycle expected (multi-pick crops); **does not** transition the cycle's own status, caller must also call `start-harvest`/`complete-harvest` |

`AgCropCycle.status`: `PLANNED \| PLANTED \| GROWING \| HARVESTING \| HARVESTED \| FAILED \| ABANDONED`.

### DTOs actually seen

```
CreateCropCycleRequest(farmId*, productionAreaId*, enterpriseId, seasonId, cropTypeId*,
  variety, cycleName, areaPlantedHectares*, plantingDate, expectedHarvestDate,
  seedInventoryItemId, seedQuantity, seedSource, notes)

CropCycleResponse(id, farmId, productionAreaId, enterpriseId, seasonId, cropTypeId,
  variety, cycleName, areaPlantedHectares, plantingDate, expectedHarvestDate,
  seedInventoryItemId, seedQuantity, seedSource, status, notes, createdAt, updatedAt)

CreateInputApplicationRequest(applicationDate*, inputType*, inventoryItemId, productUsed,
  quantityApplied*, unitOfMeasure*, applicationMethod, appliedBy, laborHours, cost,
  weatherConditions, notes)
  — inputType: FERTILISER | PESTICIDE | HERBICIDE | FUNGICIDE | IRRIGATION | OTHER

CreateScoutingRecordRequest(scoutingDate*, observationType*, severity, description*,
  recommendedAction, scoutedBy, followUpDate, notes)
  — observationType: PEST | DISEASE | WEED | NUTRIENT_DEFICIENCY | WEATHER_DAMAGE | GENERAL
  — severity: LOW | MEDIUM | HIGH (defaults LOW server-side if blank)

CreateHarvestRecordRequest(harvestDate*, quantityHarvested*, unitOfMeasure*, qualityGrade,
  moistureContent, storageLocation, harvestedBy, laborHours, notes)
```
(* = `@NotNull`/`@NotBlank` on the backend — mirrored in the Zod schemas.)

Also confirmed: `AgSeasonController`/`AgSeasonService` (farm-scoped season CRUD + activate/close, mirrors `AgProductionAreaService`).

## Inventory (feed/seed/fertiliser/chemicals — Agriculture owns this; no Supply Chain reuse, see gaps doc)

Base: `/api/v1/agriculture` (controller: `AgInventoryItemController`)

| Method | Path | Notes |
|---|---|---|
| POST | `/farms/{farmId}/inventory-items` (inferred path) | `CreateInventoryItemRequest` |
| GET/PUT | `/inventory-items/{id}` | |
| POST | `/inventory-items/{id}/receive` | increases quantity |
| POST | `/inventory-items/{id}/issue` | decreases quantity, fails if insufficient |
| POST | `/inventory-items/{id}/adjust` | sets quantity directly |

## Cost reporting

Base: `/api/v1/agriculture` (controller: `AgCostReportingController`)

| Method | Path | Returns |
|---|---|---|
| GET | `/animals/{id}/cost-summary` (inferred) | `AnimalCostSummaryResponse` |
| GET | `/farms/{farmId}/animals/cost-summary` (inferred) | `List<AnimalCostSummaryResponse>` |
| GET | `/groups/{id}/cost-summary` | `GroupCostSummaryResponse` (health+feed cost; **excludes acquisition cost** — `AgGroup` has no acquisition-cost field) |
| GET | `/farms/{farmId}/groups/cost-summary` | `List<GroupCostSummaryResponse>`, sorted most expensive first |
| GET | `/crop-cycles/{id}/cost-summary` | `CropCycleCostSummaryResponse` (seed+input cost, cost/ha, yield/ha; **labour reported as hours, not currency** — no stored labour rate anywhere) |
| GET | `/farms/{farmId}/crop-cycles/cost-summary` | `List<CropCycleCostSummaryResponse>`, sorted most expensive per hectare |

None of these post to Accounting/GL — they are reporting numbers only, by
deliberate design (see `package-info.java`).

## What this module explicitly does NOT depend on

Per `package-info.java`'s `allowedDependencies`: only `shared`, `billing`
(FeatureGuard), `hr` (HrFacade — employee references on farm managers,
`recordedBy`/`administeredBy`/`reportedBy`), `evidence` (photo attachments),
`notifications` (the module's own daily due-date sweeps). **No dependency on
`supplychain`, `accounting`, `invoicing`, `crm`, `fleet`, `tasks`.** See
`11-API-GAPS.md` for what that means for the mobile app.
