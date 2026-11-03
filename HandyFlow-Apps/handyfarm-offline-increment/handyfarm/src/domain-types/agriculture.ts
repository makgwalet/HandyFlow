/**
 * Every type here mirrors a Java record actually inspected in
 * `platform/.../agriculture/dto/`. Field names and nullability match the
 * backend 1:1 — see docs/agriculture/10-API-MAP.md for the source mapping.
 * UUID -> string, BigDecimal -> number, LocalDate -> string (ISO date),
 * Instant -> string (ISO datetime).
 */

export type EntityStatus = 'ACTIVE' | 'INACTIVE' | string;

// ── Farms ────────────────────────────────────────────────────────────────

export interface Farm {
  id: string;
  name: string;
  farmType: string;
  registrationNumber: string | null;
  province: string | null;
  region: string | null;
  gpsLatitude: number | null;
  gpsLongitude: number | null;
  totalHectares: number | null;
  managerId: string | null;
  managerName: string | null;
  status: string;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateFarmRequest {
  name: string;
  farmType: string;
  registrationNumber?: string;
  province?: string;
  region?: string;
  totalHectares?: number;
}

// ── Reference data ───────────────────────────────────────────────────────

export interface Species {
  id: string;
  name: string;
  category: string;
  status: string;
}

// ── Animals (individually tracked) ──────────────────────────────────────

export type AnimalSex = 'MALE' | 'FEMALE';
export type AcquisitionType = 'BORN_ON_FARM' | 'PURCHASED' | 'TRANSFERRED' | string;

export interface Animal {
  id: string;
  farmId: string;
  productionAreaId: string | null;
  enterpriseId: string | null;
  speciesId: string;
  tagNumber: string;
  name: string | null;
  breed: string | null;
  sex: AnimalSex;
  dateOfBirth: string | null;
  estimatedAge: boolean;
  sireId: string | null;
  damId: string | null;
  acquisitionType: AcquisitionType;
  acquisitionDate: string;
  acquisitionCost: number | null;
  currentWeightKg: number | null;
  status: string; // e.g. ACTIVE | DECEASED | SOLD
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateAnimalRequest {
  farmId: string;
  productionAreaId?: string;
  enterpriseId?: string;
  speciesId: string;
  tagNumber: string;
  name?: string;
  breed?: string;
  sex: AnimalSex;
  dateOfBirth?: string;
  estimatedAge: boolean;
  sireId?: string;
  damId?: string;
  acquisitionType: AcquisitionType;
  acquisitionDate: string;
  acquisitionCost?: number;
}

// ── Groups (batch/flock/herd tracked) ───────────────────────────────────

export interface AnimalGroup {
  id: string;
  farmId: string;
  productionAreaId: string | null;
  enterpriseId: string | null;
  speciesId: string;
  batchNumber: string;
  breed: string | null;
  initialCount: number;
  currentCount: number;
  averageWeightKg: number | null;
  originDate: string;
  acquisitionType: AcquisitionType;
  status: string;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateGroupRequest {
  farmId: string;
  productionAreaId?: string;
  enterpriseId?: string;
  speciesId: string;
  batchNumber: string;
  breed?: string;
  initialCount: number;
  originDate: string;
  acquisitionType: AcquisitionType;
}

// ── Weight (shared shape for animal or group) ───────────────────────────

export interface RecordWeightRequest {
  recordedDate: string;
  weightKg: number;
  sampleSize?: number; // meaningful for a group sample; ignored for an animal
  recordedBy?: string;
  notes?: string;
}

export interface WeightRecord {
  id: string;
  animalId: string | null;
  groupId: string | null;
  recordedDate: string;
  weightKg: number;
  sampleSize: number | null;
  recordedBy: string | null;
  recordedByName: string | null;
  notes: string | null;
  createdAt: string;
}

// ── Health events (vaccination/treatment/observation/illness/injury/deworming) ─

export type HealthEventType = 'VACCINATION' | 'TREATMENT' | 'OBSERVATION' | 'ILLNESS' | 'INJURY' | 'DEWORMING';

export interface CreateHealthEventRequest {
  animalId?: string;
  groupId?: string; // exactly one of animalId/groupId required
  eventType: HealthEventType;
  eventDate: string;
  description: string;
  productUsed?: string;
  dosage?: string;
  administeredBy?: string;
  veterinarian?: string;
  cost?: number;
  withdrawalPeriodDays?: number;
  nextDueDate?: string;
  status?: string;
  notes?: string;
}

export interface HealthEvent {
  id: string;
  animalId: string | null;
  groupId: string | null;
  eventType: HealthEventType;
  eventDate: string;
  description: string;
  productUsed: string | null;
  dosage: string | null;
  administeredBy: string | null;
  administeredByName: string | null;
  veterinarian: string | null;
  cost: number | null;
  withdrawalPeriodDays: number | null;
  nextDueDate: string | null;
  reminderAcknowledged: boolean;
  status: string; // COMPLETED | SCHEDULED
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

// ── Mortality ────────────────────────────────────────────────────────────

export interface CreateMortalityRecordRequest {
  animalId?: string;
  groupId?: string;
  mortalityDate: string;
  countLost: number;
  causeCategory: string;
  causeDetail?: string;
  estimatedValueLoss?: number;
  reportedBy?: string;
  notes?: string;
}

export interface MortalityRecord {
  id: string;
  animalId: string | null;
  groupId: string | null;
  mortalityDate: string;
  countLost: number;
  causeCategory: string;
  causeDetail: string | null;
  estimatedValueLoss: number | null;
  reportedBy: string | null;
  reportedByName: string | null;
  notes: string | null;
  createdAt: string;
}

// ── Crop types (reference data) ─────────────────────────────────────────

export interface CropType {
  id: string;
  name: string;
  category: string; // FIELD_CROP | HORTICULTURE | ORCHARD | OTHER
  typicalGrowingDays: number | null;
  defaultUnitOfMeasure: string;
  status: string;
}

// ── Crop cycles (a single planting instance) ────────────────────────────

export type CropCycleStatus = 'PLANNED' | 'PLANTED' | 'GROWING' | 'HARVESTING' | 'HARVESTED' | 'FAILED' | 'ABANDONED';

export interface CropCycle {
  id: string;
  farmId: string;
  productionAreaId: string;
  enterpriseId: string | null;
  seasonId: string | null;
  cropTypeId: string;
  variety: string | null;
  cycleName: string | null;
  areaPlantedHectares: number;
  plantingDate: string | null;
  expectedHarvestDate: string | null;
  seedInventoryItemId: string | null;
  seedQuantity: number | null;
  seedSource: string | null;
  status: CropCycleStatus;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCropCycleRequest {
  farmId: string;
  productionAreaId: string;
  enterpriseId?: string;
  seasonId?: string;
  cropTypeId: string;
  variety?: string;
  cycleName?: string;
  areaPlantedHectares: number;
  plantingDate?: string;
  expectedHarvestDate?: string;
  seedInventoryItemId?: string;
  seedQuantity?: number;
  seedSource?: string;
  notes?: string;
}

// ── Input applications (fertiliser/pesticide/herbicide/fungicide/irrigation) ─

export type InputType = 'FERTILISER' | 'PESTICIDE' | 'HERBICIDE' | 'FUNGICIDE' | 'IRRIGATION' | 'OTHER';

export interface CreateInputApplicationRequest {
  applicationDate: string;
  inputType: InputType;
  inventoryItemId?: string;
  productUsed?: string;
  quantityApplied: number;
  unitOfMeasure: string;
  applicationMethod?: string;
  appliedBy?: string;
  laborHours?: number;
  cost?: number;
  weatherConditions?: string;
  notes?: string;
}

export interface InputApplication {
  id: string;
  cropCycleId: string;
  applicationDate: string;
  inputType: InputType;
  inventoryItemId: string | null;
  productUsed: string | null;
  quantityApplied: number;
  unitOfMeasure: string;
  applicationMethod: string | null;
  appliedBy: string | null;
  appliedByName: string | null;
  laborHours: number | null;
  cost: number | null;
  weatherConditions: string | null;
  notes: string | null;
  createdAt: string;
}

// ── Scouting records (pest/disease/weed/nutrient-deficiency observation) ─

export type ObservationType = 'PEST' | 'DISEASE' | 'WEED' | 'NUTRIENT_DEFICIENCY' | 'WEATHER_DAMAGE' | 'GENERAL';
export type ScoutingSeverity = 'LOW' | 'MEDIUM' | 'HIGH';

export interface CreateScoutingRecordRequest {
  scoutingDate: string;
  observationType: ObservationType;
  severity?: ScoutingSeverity;
  description: string;
  recommendedAction?: string;
  scoutedBy?: string;
  followUpDate?: string;
  notes?: string;
}

export interface ScoutingRecord {
  id: string;
  cropCycleId: string;
  scoutingDate: string;
  observationType: ObservationType;
  severity: ScoutingSeverity;
  description: string;
  recommendedAction: string | null;
  scoutedBy: string | null;
  scoutedByName: string | null;
  followUpDate: string | null;
  followUpAcknowledged: boolean;
  status: 'OPEN' | 'RESOLVED';
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

// ── Harvest records ──────────────────────────────────────────────────────

export interface CreateHarvestRecordRequest {
  harvestDate: string;
  quantityHarvested: number;
  unitOfMeasure: string;
  qualityGrade?: string;
  moistureContent?: number;
  storageLocation?: string;
  harvestedBy?: string;
  laborHours?: number;
  notes?: string;
}

export interface HarvestRecord {
  id: string;
  cropCycleId: string;
  harvestDate: string;
  quantityHarvested: number;
  unitOfMeasure: string;
  qualityGrade: string | null;
  moistureContent: number | null;
  storageLocation: string | null;
  harvestedBy: string | null;
  harvestedByName: string | null;
  laborHours: number | null;
  notes: string | null;
  createdAt: string;
}
