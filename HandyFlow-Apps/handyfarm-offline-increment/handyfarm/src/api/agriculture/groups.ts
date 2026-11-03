import { apiClient, unwrap, writeOrQueue, WriteResult } from '../client';
import { PageResponse } from '../types';
import {
  AnimalGroup,
  CreateGroupRequest,
  CreateHealthEventRequest,
  CreateMortalityRecordRequest,
  HealthEvent,
  MortalityRecord,
  RecordWeightRequest,
  WeightRecord,
} from '../../domain-types/agriculture';

export function getGroupsForFarm(farmId: string, params?: { status?: string; page?: number; size?: number }) {
  return unwrap<PageResponse<AnimalGroup>>(apiClient.get(`/agriculture/farms/${farmId}/groups`, { params }));
}

export function getGroup(id: string) {
  return unwrap<AnimalGroup>(apiClient.get(`/agriculture/groups/${id}`));
}

export function createGroup(farmId: string, body: CreateGroupRequest) {
  return unwrap<AnimalGroup>(apiClient.post(`/agriculture/farms/${farmId}/groups`, body));
}

export function getGroupWeightHistory(groupId: string, params?: { page?: number; size?: number }) {
  return unwrap<PageResponse<WeightRecord>>(apiClient.get(`/agriculture/groups/${groupId}/weight-records`, { params }));
}

export function recordGroupWeight(groupId: string, body: RecordWeightRequest): Promise<WriteResult<WeightRecord>> {
  return writeOrQueue<WeightRecord>(
    { method: 'POST', url: `/agriculture/groups/${groupId}/weight-records`, data: body },
    `Weight — group ${groupId}`
  );
}

export function createGroupHealthEvent(groupId: string, body: Omit<CreateHealthEventRequest, 'animalId' | 'groupId'>) {
  return unwrap<HealthEvent>(apiClient.post(`/agriculture/groups/${groupId}/health-events`, { ...body, groupId }));
}

export function createGroupMortalityRecord(
  groupId: string,
  body: Omit<CreateMortalityRecordRequest, 'animalId' | 'groupId'>
) {
  return unwrap<MortalityRecord>(
    apiClient.post(`/agriculture/groups/${groupId}/mortality-records`, { ...body, groupId })
  );
}
