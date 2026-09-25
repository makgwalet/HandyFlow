// src/api/uiPreferences.api.ts
import { apiClient } from './client'
import type {
  UiPreferences, UpdateTenantUiPreferencesRequest, UpdateUserUiPreferencesRequest,
} from '../types/uiPreferences.types'

// Note: apiClient already unwraps the { success, message, data } envelope
// (see the response interceptor in client.ts), so res.data IS the payload.

const BASE = '/api/v1/identity/ui-preferences'

export const uiPreferencesApi = {
  get: async (): Promise<UiPreferences> => {
    const res = await apiClient.get<UiPreferences>(BASE)
    return res.data
  },

  /** Full replacement of the current user's overrides. */
  updateMine: async (req: UpdateUserUiPreferencesRequest): Promise<UiPreferences> => {
    const res = await apiClient.put<UiPreferences>(`${BASE}/me`, req)
    return res.data
  },

  /** Requires SETTINGS_MANAGE. */
  updateTenant: async (req: UpdateTenantUiPreferencesRequest): Promise<UiPreferences> => {
    const res = await apiClient.put<UiPreferences>(`${BASE}/tenant`, req)
    return res.data
  },
}
