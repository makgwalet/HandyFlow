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

export function unwrap<T>(r: { data?: { data?: unknown } & Record<string, unknown> }): T {
  const p = (r.data?.data ?? r.data) as { content?: unknown } | undefined
  return (p?.content ?? p ?? []) as T
}

const BASE = "/api/v1/earthmoving"

// ── Assets ───────────────────────────────────────────────────────────────

// Reads every page. Before patch E1 the lists loaded only the first 200 rows and silently cut off a larger fleet.
async function fetchAllPages<T>(url: string, pageSize = 200, maxPages = 25): Promise<T[]> {
  const out: T[] = []
  for (let page = 0; page < maxPages; page++) {
    const r = await apiClient.get(`${url}${url.includes("?") ? "&" : "?"}size=${pageSize}&page=${page}`)
    const p = r.data?.data ?? r.data
    const content: T[] = p?.content ?? (Array.isArray(p) ? p : [])
    out.push(...content)
    if (p?.last !== false || content.length === 0) break
  }
  return out
}

export const fetchAssets = (): Promise<Asset[]> => fetchAllPages<Asset>(`${BASE}/assets`)

export const updateAsset = (id: string, body: Record<string, unknown>) =>
  apiClient.put(`${BASE}/assets/${id}`, body)

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

export const fetchIncidents = (): Promise<Incident[]> => fetchAllPages<Incident>(`${BASE}/incidents`)

export const reportIncident = (body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/incidents`, body)

export const resolveIncident = (id: string, resolutionNotes: string | null) =>
  apiClient.patch(`${BASE}/incidents/${id}/resolve`, { resolutionNotes })
