import { apiClient, unwrap } from '../client';
import { PageResponse } from '../types';
import { CreateFarmRequest, Farm, Species } from '../../domain-types/agriculture';

export function getFarms(params?: { status?: string; page?: number; size?: number }) {
  return unwrap<PageResponse<Farm>>(apiClient.get('/agriculture/farms', { params }));
}

export function getFarm(id: string) {
  return unwrap<Farm>(apiClient.get(`/agriculture/farms/${id}`));
}

export function createFarm(body: CreateFarmRequest) {
  return unwrap<Farm>(apiClient.post('/agriculture/farms', body));
}

export function getSpecies(params?: { category?: string; page?: number; size?: number }) {
  return unwrap<PageResponse<Species>>(apiClient.get('/agriculture/species', { params }));
}
