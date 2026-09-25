// src/api/uiPreferences.api.ts
import { apiClient } from './client'
import type {
  UiPreferences, UpdateTenantUiPreferencesRequest, UpdateUserUiPreferencesRequest,
} from '../types/uiPreferences.types'

interface Envelope<T> { success: boolean; message: string; data: T }

const BASE = '/api/v1/identity/ui-preferences'

export const uiPreferencesApi = {
  get: async (): Promise<UiPreferences> => {
    const res = await apiClient.get<Envelope<UiPreferences>>(BASE)
    return res.data.data
  },

  /** Full replacement of the current user's overrides. */
  updateMine: async (req: UpdateUserUiPreferencesRequest): Promise<UiPreferences> => {
    const res = await apiClient.put<Envelope<UiPreferences>>(`${BASE}/me`, req)
    return res.data.data
  },

  /** Requires SETTINGS_MANAGE. */
  updateTenant: async (req: UpdateTenantUiPreferencesRequest): Promise<UiPreferences> => {
    const res = await apiClient.put<Envelope<UiPreferences>>(`${BASE}/tenant`, req)
    return res.data.data
  },
}
