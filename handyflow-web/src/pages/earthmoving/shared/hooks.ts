// src/pages/earthmoving/shared/hooks.ts
import { useQuery, useMutation, useQueryClient } from "@tanstack/react-query"
import * as api from "./api"
import type { Asset, MaintenanceRecord, OperatorLog, Incident, WorkOrder, MaintenancePlan, StatusEvent, EarthAlert, AlertRule } from "./types"

// Central query keys — every tab imports these instead of typing the
// literal string, so a rename is a one-line change instead of a grep.
export const QK = {
  assets: ["em-assets"] as const,
  maintenance: (assetId: string) => ["em-maintenance", assetId] as const,
  operatorLogs: (assetId: string) => ["em-oplogs", assetId] as const,
  incidents: ["em-incidents"] as const,
  workOrders: ["em-work-orders"] as const,
  plans: ["em-plans"] as const,
  alerts: ["em-alerts"] as const,
  alertRules: ["em-alert-rules"] as const,
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

export function useUpdateAsset(onSuccess?: (asset: Asset) => void) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: Record<string, unknown> }) => api.updateAsset(id, body),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: QK.assets })
      onSuccess?.(res.data?.data ?? res.data)
    },
  })
}

export function useUpdateAssetStatus(onSuccess?: (asset: Asset) => void) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, status, note, overrideReason }: { id: string; status: string; note: string; overrideReason?: string }) =>
      api.updateAssetStatus(id, status, note, overrideReason),
    onSuccess: (res) => {
      qc.invalidateQueries({ queryKey: QK.assets })
      qc.invalidateQueries({ queryKey: ["em-status-history"] })
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
    mutationFn: (body: Record<string, unknown> & { assetId: string }) => api.deployAsset(body.assetId, body),
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

// ── Work orders and plans ────────────────────────────────────────────────

export function useWorkOrders() {
  return useQuery<WorkOrder[]>({ queryKey: QK.workOrders, queryFn: api.fetchWorkOrders, staleTime: 10_000 })
}

export function useAssignableUsers() {
  return useQuery({ queryKey: ["em-assignable-users"], queryFn: api.fetchAssignableUsers, staleTime: 60_000 })
}

/** Any change to a work order can change a machine's release state, a plan's state and the maintenance history. */
function useRefreshAfterWorkOrder() {
  const qc = useQueryClient()
  return () => {
    qc.invalidateQueries({ queryKey: QK.workOrders })
    qc.invalidateQueries({ queryKey: QK.plans })
    qc.invalidateQueries({ queryKey: QK.assets })
    qc.invalidateQueries({ queryKey: ["em-maintenance"] })
    qc.invalidateQueries({ queryKey: QK.incidents })
  }
}

export function useCreateWorkOrder(onSuccess?: () => void) {
  const refresh = useRefreshAfterWorkOrder()
  return useMutation({ mutationFn: api.createWorkOrder, onSuccess: () => { refresh(); onSuccess?.() } })
}

export function useWorkOrderAction(onSuccess?: () => void) {
  const refresh = useRefreshAfterWorkOrder()
  return useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: string; body?: Record<string, unknown> }) =>
      api.workOrderAction(id, action, body),
    onSuccess: () => { refresh(); onSuccess?.() },
  })
}

export function useWorkOrderLine(onSuccess?: () => void) {
  const refresh = useRefreshAfterWorkOrder()
  return useMutation({
    mutationFn: ({ id, body, remove }: { id: string; body?: Record<string, unknown>; remove?: string }) =>
      remove ? api.removeWorkOrderLine(id, remove) : api.addWorkOrderLine(id, body ?? {}),
    onSuccess: () => { refresh(); onSuccess?.() },
  })
}

export function usePlans() {
  return useQuery<MaintenancePlan[]>({ queryKey: QK.plans, queryFn: api.fetchPlans, staleTime: 10_000 })
}

export function useSavePlan(onSuccess?: () => void) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id?: string; body: Record<string, unknown> }) => id ? api.updatePlan(id, body) : api.createPlan(body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: QK.plans }); onSuccess?.() },
  })
}

export function useScanPlans(onSuccess?: (r: { checked: number; workOrdersRaised: number; raised: string[] }) => void) {
  const refresh = useRefreshAfterWorkOrder()
  return useMutation({ mutationFn: api.scanPlans, onSuccess: (r) => { refresh(); onSuccess?.(r) } })
}

export function useStatusHistory(assetId: string) {
  return useQuery<StatusEvent[]>({ queryKey: ["em-status-history", assetId], queryFn: () => api.fetchStatusHistory(assetId), enabled: !!assetId })
}

// ── Alerts ───────────────────────────────────────────────────────────────

export function useAlerts(openOnly: boolean) {
  return useQuery<EarthAlert[]>({ queryKey: [...QK.alerts, openOnly], queryFn: () => api.fetchAlerts(openOnly), staleTime: 10_000, refetchInterval: 60_000 })
}

export function useAlertAction(onSuccess?: () => void) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, action, body }: { id: string; action: "acknowledge" | "resolve"; body?: Record<string, unknown> }) =>
      api.alertAction(id, action, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: QK.alerts }); onSuccess?.() },
  })
}

export function useAlertRules(enabled: boolean) {
  return useQuery<AlertRule[]>({ queryKey: QK.alertRules, queryFn: api.fetchAlertRules, enabled })
}

export function useSaveAlertRule(onSuccess?: () => void) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ type, body }: { type: string; body: Record<string, unknown> }) => api.saveAlertRule(type, body),
    onSuccess: () => { qc.invalidateQueries({ queryKey: QK.alertRules }); onSuccess?.() },
  })
}
