// src/pages/earthmoving/shared/hooks.ts
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import * as api from "./api"
import type { Asset, MaintenanceRecord, OperatorLog, Incident } from "./types"

// Central query keys — every tab imports these instead of typing the
// literal string, so a rename is a one-line change instead of a grep.
export const QK = {
  assets: ["em-assets"] as const,
  maintenance: (assetId: string) => ["em-maintenance", assetId] as const,
  operatorLogs: (assetId: string) => ["em-oplogs", assetId] as const,
  incidents: ["em-incidents"] as const,
}

// ── Assets ───────────────────────────────────────────────────────────────

export function useAssets() {
  return useQuery<Asset[]>({
    queryKey: QK.assets,
    queryFn: api.fetchAssets,
    // Every tab mounts this same query. Without a staleTime, switching tabs
    // re-fetches the whole fleet every time even though it was fetched 2
    // seconds ago on the previous tab — a 15s staleTime means normal
    // tab-switching feels instant while status changes still show up
    // quickly after a mutation invalidates the cache.
    staleTime: 15_000,
  })
}

export function useCreateAsset(onSuccess?: (asset: Asset) => void) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: api.createAsset,
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: QK.assets })
      onSuccess?.(res.data?.data ?? res.data)
    },
  })
}

export function useUpdateAssetStatus(onSuccess?: (asset: Asset) => void) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status, note }: { id: string; status: string; note: string }) =>
      api.updateAssetStatus(id, status, note),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: QK.assets })
      onSuccess?.(res.data?.data ?? res.data)
    },
  })
}

export function useUpdateAssetHours(onSuccess?: () => void) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, hours }: { id: string; hours: number }) => api.updateAssetHours(id, hours),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: QK.assets })
      onSuccess?.()
    },
  })
}

export function useDeployAsset(onSuccess?: () => void) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: any) => api.deployAsset(body.assetId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: QK.assets })
      onSuccess?.()
    },
  })
}

export function useReturnToYard() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => api.updateAssetStatus(id, "AVAILABLE", "Returned from deployment"),
    onSuccess: () => qc.invalidateQueries({ queryKey: QK.assets }),
  })
}

// ── Maintenance ──────────────────────────────────────────────────────────

export function useMaintenanceHistory(assetId: string) {
  return useQuery<MaintenanceRecord[]>({
    queryKey: QK.maintenance(assetId),
    queryFn: () => api.fetchMaintenanceHistory(assetId),
    enabled: !!assetId,
  })
}

export function useCreateMaintenance(assetId: string, onSuccess?: () => void) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => api.createMaintenance(assetId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: QK.maintenance(assetId) })
      qc.invalidateQueries({ queryKey: QK.assets })
      onSuccess?.()
    },
  })
}

// ── Operator logs ────────────────────────────────────────────────────────

export function useOperatorLogs(assetId: string) {
  return useQuery<OperatorLog[]>({
    queryKey: QK.operatorLogs(assetId),
    queryFn: () => api.fetchOperatorLogs(assetId),
    enabled: !!assetId,
  })
}

export function useStartOperatorLog(assetId: string, onSuccess?: () => void) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: Record<string, unknown>) => api.startOperatorLog(assetId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: QK.operatorLogs(assetId) })
      onSuccess?.()
    },
  })
}

export function useCompleteOperatorLog(assetId: string, onSuccess?: () => void) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ logId, body }: { logId: string; body: Record<string, unknown> }) =>
      api.completeOperatorLog(assetId, logId, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: QK.operatorLogs(assetId) })
      onSuccess?.()
    },
  })
}

// ── Incidents ────────────────────────────────────────────────────────────

export function useIncidents() {
  return useQuery<Incident[]>({
    queryKey: QK.incidents,
    queryFn: api.fetchIncidents,
    staleTime: 10_000,
  })
}

export function useReportIncident(onSuccess?: () => void) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: api.reportIncident,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: QK.incidents })
      // Reporting a BREAKDOWN/ACCIDENT incident may have changed the
      // asset's status on the backend (see IncidentService.maybeAutoBreakdown)
      // — refresh the fleet list too so status badges stay accurate.
      qc.invalidateQueries({ queryKey: QK.assets })
      onSuccess?.()
    },
  })
}

export function useResolveIncident() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, resolutionNotes }: { id: string; resolutionNotes: string | null }) =>
      api.resolveIncident(id, resolutionNotes),
    onSuccess: () => qc.invalidateQueries({ queryKey: QK.incidents }),
  })
}
