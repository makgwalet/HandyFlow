import { apiClient, unwrap } from '../client';
import { PageResponse } from '../types';
import {
  CreateCropCycleRequest,
  CreateHarvestRecordRequest,
  CreateInputApplicationRequest,
  CreateScoutingRecordRequest,
  CropCycle,
  CropType,
  HarvestRecord,
  InputApplication,
  ScoutingRecord,
} from '../../domain-types/agriculture';

export function getCropTypes(params?: { category?: string; page?: number; size?: number }) {
  return unwrap<PageResponse<CropType>>(apiClient.get('/agriculture/crop-types', { params }));
}

export function getCropCyclesForFarm(farmId: string, params?: { status?: string; page?: number; size?: number }) {
  return unwrap<PageResponse<CropCycle>>(apiClient.get(`/agriculture/farms/${farmId}/crop-cycles`, { params }));
}

export function getCropCycle(id: string) {
  return unwrap<CropCycle>(apiClient.get(`/agriculture/crop-cycles/${id}`));
}

export function createCropCycle(farmId: string, body: CreateCropCycleRequest) {
  return unwrap<CropCycle>(apiClient.post(`/agriculture/farms/${farmId}/crop-cycles`, body));
}

// ── State-machine transitions ───────────────────────────────────────────

export function recordPlanting(
  cropCycleId: string,
  body: { plantingDate: string; seedInventoryItemId?: string; seedQuantity?: number; seedSource?: string }
) {
  return unwrap<CropCycle>(apiClient.patch(`/agriculture/crop-cycles/${cropCycleId}/record-planting`, body));
}

export function markGrowing(cropCycleId: string) {
  return unwrap<CropCycle>(apiClient.patch(`/agriculture/crop-cycles/${cropCycleId}/mark-growing`));
}

export function startHarvest(cropCycleId: string) {
  return unwrap<CropCycle>(apiClient.patch(`/agriculture/crop-cycles/${cropCycleId}/start-harvest`));
}

export function completeHarvest(cropCycleId: string) {
  return unwrap<CropCycle>(apiClient.patch(`/agriculture/crop-cycles/${cropCycleId}/complete-harvest`));
}

// ── Input applications ──────────────────────────────────────────────────

export function getInputApplications(cropCycleId: string, params?: { page?: number; size?: number }) {
  return unwrap<PageResponse<InputApplication>>(
    apiClient.get(`/agriculture/crop-cycles/${cropCycleId}/input-applications`, { params })
  );
}

export function createInputApplication(cropCycleId: string, body: CreateInputApplicationRequest) {
  return unwrap<InputApplication>(
    apiClient.post(`/agriculture/crop-cycles/${cropCycleId}/input-applications`, body)
  );
}

// ── Scouting records ─────────────────────────────────────────────────────

export function getScoutingRecords(cropCycleId: string, params?: { page?: number; size?: number }) {
  return unwrap<PageResponse<ScoutingRecord>>(
    apiClient.get(`/agriculture/crop-cycles/${cropCycleId}/scouting-records`, { params })
  );
}

export function createScoutingRecord(cropCycleId: string, body: CreateScoutingRecordRequest) {
  return unwrap<ScoutingRecord>(apiClient.post(`/agriculture/crop-cycles/${cropCycleId}/scouting-records`, body));
}

// ── Harvest records ───────────────────────────────────────────────────────

export function getHarvestRecords(cropCycleId: string, params?: { page?: number; size?: number }) {
  return unwrap<PageResponse<HarvestRecord>>(
    apiClient.get(`/agriculture/crop-cycles/${cropCycleId}/harvest-records`, { params })
  );
}

export function createHarvestRecord(cropCycleId: string, body: CreateHarvestRecordRequest) {
  return unwrap<HarvestRecord>(apiClient.post(`/agriculture/crop-cycles/${cropCycleId}/harvest-records`, body));
}
