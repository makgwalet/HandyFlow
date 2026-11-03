import { apiClient, unwrap, writeOrQueue, WriteResult } from '../client';
import { PageResponse } from '../types';
import {
  Animal,
  CreateAnimalRequest,
  CreateHealthEventRequest,
  CreateMortalityRecordRequest,
  HealthEvent,
  MortalityRecord,
  RecordWeightRequest,
  WeightRecord,
} from '../../domain-types/agriculture';

export function getAnimalsForFarm(farmId: string, params?: { status?: string; page?: number; size?: number }) {
  return unwrap<PageResponse<Animal>>(apiClient.get(`/agriculture/farms/${farmId}/animals`, { params }));
}

export function getAnimal(id: string) {
  return unwrap<Animal>(apiClient.get(`/agriculture/animals/${id}`));
}

export function createAnimal(farmId: string, body: CreateAnimalRequest) {
  return unwrap<Animal>(apiClient.post(`/agriculture/farms/${farmId}/animals`, body));
}

export function getAnimalWeightHistory(animalId: string, params?: { page?: number; size?: number }) {
  return unwrap<PageResponse<WeightRecord>>(
    apiClient.get(`/agriculture/animals/${animalId}/weight-records`, { params })
  );
}

export function recordAnimalWeight(animalId: string, body: RecordWeightRequest): Promise<WriteResult<WeightRecord>> {
  return writeOrQueue<WeightRecord>(
    { method: 'POST', url: `/agriculture/animals/${animalId}/weight-records`, data: body },
    `Weight — animal ${animalId}`
  );
}

export function getAnimalHealthEvents(animalId: string, params?: { page?: number; size?: number }) {
  return unwrap<PageResponse<HealthEvent>>(
    apiClient.get(`/agriculture/animals/${animalId}/health-events`, { params })
  );
}

export function createAnimalHealthEvent(animalId: string, body: Omit<CreateHealthEventRequest, 'animalId' | 'groupId'>) {
  return unwrap<HealthEvent>(
    apiClient.post(`/agriculture/animals/${animalId}/health-events`, { ...body, animalId })
  );
}

export function getAnimalMortalityRecords(animalId: string, params?: { page?: number; size?: number }) {
  return unwrap<PageResponse<MortalityRecord>>(
    apiClient.get(`/agriculture/animals/${animalId}/mortality-records`, { params })
  );
}

export function createAnimalMortalityRecord(
  animalId: string,
  body: Omit<CreateMortalityRecordRequest, 'animalId' | 'groupId'>
) {
  return unwrap<MortalityRecord>(
    apiClient.post(`/agriculture/animals/${animalId}/mortality-records`, { ...body, animalId })
  );
}
