// src/pages/agriculture/agCrops.types.ts
//
// Response shapes for crop cycles, seasons, crop types and cost reporting. Field-for-field from the backend
// records in za.co.handyflow.platform.agriculture.dto (BigDecimal arrives as a JSON number).
export interface Page<T> { content: T[]; totalElements: number }

/** The real state machine (AgCropCycle): there is no "COMPLETED"; a finished cycle is HARVESTED. */
export type CycleStatus = "PLANNED" | "PLANTED" | "GROWING" | "HARVESTING" | "HARVESTED" | "FAILED" | "ABANDONED"
export type SeasonStatus = "PLANNING" | "ACTIVE" | "CLOSED"

export interface CropType {
  id: string; name: string; category: string; typicalGrowingDays: number | null
  defaultUnitOfMeasure: string; status: "ACTIVE" | "INACTIVE"; createdAt: string; updatedAt: string
}

export interface Season {
  id: string; farmId: string; name: string; startDate: string; endDate: string | null
  status: SeasonStatus; notes: string | null; createdAt: string; updatedAt: string
}

export interface CropCycle {
  id: string; farmId: string; productionAreaId: string; enterpriseId: string | null; seasonId: string | null
  cropTypeId: string; variety: string | null; cycleName: string | null; areaPlantedHectares: number
  plantingDate: string | null; expectedHarvestDate: string | null
  seedInventoryItemId: string | null; seedQuantity: number | null; seedSource: string | null
  status: CycleStatus; notes: string | null; createdAt: string; updatedAt: string
}

export interface InputApplication {
  id: string; cropCycleId: string; applicationDate: string; inputType: string; inventoryItemId: string | null
  productUsed: string | null; quantityApplied: number; unitOfMeasure: string; applicationMethod: string | null
  appliedBy: string | null; appliedByName: string | null; laborHours: number | null; cost: number | null
  weatherConditions: string | null; notes: string | null; createdAt: string
}

export interface ScoutingRecord {
  id: string; cropCycleId: string; scoutingDate: string; observationType: string; severity: "LOW" | "MEDIUM" | "HIGH"
  description: string; recommendedAction: string | null; scoutedBy: string | null; scoutedByName: string | null
  followUpDate: string | null; followUpAcknowledged: boolean; status: "OPEN" | "RESOLVED"
  notes: string | null; createdAt: string; updatedAt: string
}

export interface HarvestRecord {
  id: string; cropCycleId: string; harvestDate: string; quantityHarvested: number; unitOfMeasure: string
  qualityGrade: string | null; moistureContent: number | null; storageLocation: string | null
  harvestedBy: string | null; harvestedByName: string | null; laborHours: number | null; notes: string | null; createdAt: string
}

// -- Cost reporting (costs only: there is no revenue, labour-cost or equipment-cost data in Agriculture) -----
export interface CropCycleCost {
  cropCycleId: string; cycleName: string | null; farmId: string; cropTypeId: string
  areaPlantedHectares: number | null; totalSeedCost: number; totalInputCost: number; totalCost: number
  costPerHectare: number | null; totalLaborHours: number | null
  totalYieldHarvested: number; yieldUnitOfMeasure: string | null; yieldPerHectare: number | null
  /** Harvest units that could not be converted to the crop's unit and are NOT in the yield (absent on older servers). */
  unconvertedYieldUnits?: number
}
export interface AnimalCost {
  animalId: string; tagNumber: string; farmId: string; breedingStock: boolean; acquisitionCost: number; totalHealthCost: number
  totalFeedCost: number; totalCost: number; currentWeightKg: number | null; costPerKgLiveweight: number | null
}
export interface GroupCost {
  groupId: string; batchNumber: string; farmId: string; totalHealthCost: number; totalFeedCost: number
  totalCost: number; currentCount: number; costPerHead: number | null; averageWeightKg: number | null; costPerKgLiveweight: number | null
}
