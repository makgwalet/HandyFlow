// src/pages/earthmoving/shared/api.ts
//
// WHY a separate api.ts from the React Query hooks (see hooks.ts)? This
// layer knows nothing about React — it's plain functions that take
// arguments and return promises. That makes it independently testable, and
// it means each endpoint's HTTP verb is declared in exactly ONE place in
// the whole app. Previously, "PUT vs PATCH for status/hours" was decided
// separately, inconsistently, inside five different files' useMutation
// calls — which is exactly how it drifted out of sync with the backend.
//
// *** IMPORTANT: verbs below must match the backend controllers exactly. ***
// Status/hours/resolve/complete-log are PATCH (partial update of one field
// on an existing resource) with a matching CORS config allowing PATCH — see
// EarthAssetController.java's comment on why PUT was reverted. Deploy stays
// PUT (backend endpoint is still @PutMapping). Create is POST.

import { apiClient } from "../../../api/client"
import type { Asset, MaintenanceRecord, OperatorLog, Incident } from "./types"

export const unwrap = (r: any) => {
  const p = r.data?.data ?? r.data
  return p?.content ?? p ?? []
}

const BASE = "/api/v1/earthmoving"

// ── Assets ───────────────────────────────────────────────────────────────

export const fetchAssets = async (): Promise<Asset[]> =>
  unwrap(await apiClient.get(`${BASE}/assets?size=200`))

export const createAsset = (body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/assets`, body)

export const updateAssetStatus = (id: string, status: string, note: string) =>
  apiClient.patch(`${BASE}/assets/${id}/status`, { status, note })

export const updateAssetHours = (id: string, currentHours: number) =>
  apiClient.patch(`${BASE}/assets/${id}/hours`, { currentHours })

export const deployAsset = (assetId: string, body: Record<string, unknown>) =>
  apiClient.put(`${BASE}/assets/${assetId}/deploy`, body)

export const deleteAsset = (id: string) =>
  apiClient.delete(`${BASE}/assets/${id}`)

// ── Maintenance ──────────────────────────────────────────────────────────

export const fetchMaintenanceHistory = async (assetId: string): Promise<MaintenanceRecord[]> =>
  unwrap(await apiClient.get(`${BASE}/assets/${assetId}/maintenance?size=100`))

export const createMaintenance = (assetId: string, body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/assets/${assetId}/maintenance`, body)

// ── Operator logs ────────────────────────────────────────────────────────

export const fetchOperatorLogs = async (assetId: string): Promise<OperatorLog[]> =>
  unwrap(await apiClient.get(`${BASE}/assets/${assetId}/operator-logs?size=100`))

export const startOperatorLog = (assetId: string, body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/assets/${assetId}/operator-logs`, body)

export const completeOperatorLog = (assetId: string, logId: string, body: Record<string, unknown>) =>
  apiClient.patch(`${BASE}/assets/${assetId}/operator-logs/${logId}/complete`, body)

// ── Incidents ────────────────────────────────────────────────────────────

export const fetchIncidents = async (): Promise<Incident[]> =>
  unwrap(await apiClient.get(`${BASE}/incidents?size=200`))

export const reportIncident = (body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/incidents`, body)

export const resolveIncident = (id: string, resolutionNotes: string | null) =>
  apiClient.patch(`${BASE}/incidents/${id}/resolve`, { resolutionNotes })
