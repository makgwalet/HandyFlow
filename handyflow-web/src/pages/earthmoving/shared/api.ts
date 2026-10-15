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
import type { Asset, MaintenanceRecord, OperatorLog, Incident, WorkOrder, MaintenancePlan, PlanScan, StatusEvent, EarthAlert, AlertRule, Site, Reconciliation, LinkResult, PickOption, MeterReading, Inspection, LatestInspection, HireCharge, DeploymentOption, DraftInvoiceResult } from "./types"

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

export const updateAssetStatus = (id: string, status: string, note: string, overrideReason?: string) =>
  apiClient.patch(`${BASE}/assets/${id}/status`, { status, note, overrideReason: overrideReason || null })

export const fetchStatusHistory = (id: string): Promise<StatusEvent[]> =>
  fetchAllPages<StatusEvent>(`${BASE}/assets/${id}/status-history`)

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

// ── Work orders and maintenance plans ───────────────────────────────────

export const fetchWorkOrders = (): Promise<WorkOrder[]> => fetchAllPages<WorkOrder>(`${BASE}/work-orders`)

export const createWorkOrder = (body: Record<string, unknown>) => apiClient.post(`${BASE}/work-orders`, body)

// action: assign | start | awaiting-parts | resume | cancel | blocks-release | complete
export const workOrderAction = (id: string, action: string, body?: Record<string, unknown>) =>
  apiClient.patch(`${BASE}/work-orders/${id}/${action}`, body ?? {})

export const addWorkOrderLine = (id: string, body: Record<string, unknown>) =>
  apiClient.post(`${BASE}/work-orders/${id}/lines`, body)

export const removeWorkOrderLine = (id: string, lineId: string) =>
  apiClient.delete(`${BASE}/work-orders/${id}/lines/${lineId}`)

export const fetchPlans = async (): Promise<MaintenancePlan[]> => unwrap<MaintenancePlan[]>(await apiClient.get(`${BASE}/maintenance-plans`))

export const createPlan = (body: Record<string, unknown>) => apiClient.post(`${BASE}/maintenance-plans`, body)

export const updatePlan = (id: string, body: Record<string, unknown>) => apiClient.put(`${BASE}/maintenance-plans/${id}`, body)

export const scanPlans = async (): Promise<PlanScan> => {
  const r = await apiClient.post(`${BASE}/maintenance-plans/scan`)
  return (r.data?.data ?? r.data) as PlanScan
}

export const fetchAssignableUsers = async (): Promise<{ id: string; name: string }[]> => {
  const r = await apiClient.get("/api/v1/tasks/assignable-users")
  const d = r.data?.data ?? r.data
  return (Array.isArray(d) ? d : d?.content ?? []) as { id: string; name: string }[]
}

// ── Alerts ──────────────────────────────────────────────────────────────

export const fetchAlerts = (openOnly: boolean): Promise<EarthAlert[]> =>
  fetchAllPages<EarthAlert>(`${BASE}/alerts?openOnly=${openOnly}`)

export const alertAction = (id: string, action: "acknowledge" | "resolve", body?: Record<string, unknown>) =>
  apiClient.patch(`${BASE}/alerts/${id}/${action}`, body ?? {})

export const fetchAlertRules = async (): Promise<AlertRule[]> => unwrap<AlertRule[]>(await apiClient.get(`${BASE}/alerts/rules`))

export const saveAlertRule = (type: string, body: Record<string, unknown>) => apiClient.put(`${BASE}/alerts/rules/${type}`, body)

// ── Sites and links ──────────────────────────────────────────────────────

export const fetchSites = async (includeInactive: boolean): Promise<Site[]> =>
  unwrap<Site[]>(await apiClient.get(`${BASE}/sites?includeInactive=${includeInactive}`))

export const saveSite = (id: string | null, body: Record<string, unknown>) =>
  id ? apiClient.put(`${BASE}/sites/${id}`, body) : apiClient.post(`${BASE}/sites`, body)

export const deactivateSite = (id: string) => apiClient.patch(`${BASE}/sites/${id}/deactivate`, {})

export const fetchReconciliation = async (): Promise<Reconciliation> => {
  const r = await apiClient.get(`${BASE}/sites/reconciliation`)
  return (r.data?.data ?? r.data) as Reconciliation
}

export const applyVerifiedLinks = async (): Promise<LinkResult> => {
  const r = await apiClient.post(`${BASE}/sites/reconciliation/apply-verified`, {})
  return (r.data?.data ?? r.data) as LinkResult
}

export const resolveLinks = async (body: Record<string, unknown>): Promise<LinkResult> => {
  const r = await apiClient.post(`${BASE}/sites/reconciliation/resolve`, body)
  return (r.data?.data ?? r.data) as LinkResult
}

/** Customers and projects for the pickers. Names only; the server checks every id again when it is saved. */
export const fetchCustomerOptions = async (): Promise<PickOption[]> =>
  (await fetchAllPages<{ id: string; name: string }>("/api/v1/crm/customers", 100, 10)).map(c => ({ id: c.id, name: c.name }))

export const fetchProjectOptions = async (): Promise<PickOption[]> =>
  (await fetchAllPages<{ id: string; name: string }>("/api/v1/projects", 100, 10)).map(p => ({ id: p.id, name: p.name }))

// ── Meters ───────────────────────────────────────────────────────────────

export const fetchMeterReadings = async (assetId: string): Promise<MeterReading[]> =>
  unwrap<MeterReading[]>(await apiClient.get(`${BASE}/assets/${assetId}/meter/readings`))

export const correctMeterReading = (assetId: string, body: { reading: number; reason: string }) =>
  apiClient.post(`${BASE}/assets/${assetId}/meter/correction`, body)

export const changeMeter = (assetId: string, body: Record<string, unknown>) =>
  apiClient.put(`${BASE}/assets/${assetId}/meter`, body)

// ── Inspections ──────────────────────────────────────────────────────────

export const fetchChecklist = async (): Promise<string[]> => unwrap<string[]>(await apiClient.get(`${BASE}/inspections/checklist`))

export const fetchInspections = async (assetId: string): Promise<Inspection[]> =>
  unwrap<Inspection[]>(await apiClient.get(`${BASE}/inspections${assetId ? `?assetId=${assetId}` : ""}`))

export const fetchLatestInspections = async (): Promise<LatestInspection[]> => {
  const r = unwrap<unknown>(await apiClient.get(`${BASE}/inspections/latest`))
  return Array.isArray(r) ? (r as LatestInspection[]) : []
}

export const createInspection = async (body: Record<string, unknown>): Promise<Inspection> =>
  unwrap<Inspection>(await apiClient.post(`${BASE}/inspections`, body))

// ── Hire charges ─────────────────────────────────────────────────────────

export const fetchHireCharges = async (status: string): Promise<HireCharge[]> =>
  unwrap<HireCharge[]>(await apiClient.get(`${BASE}/hire-charges${status ? `?status=${status}` : ""}`))

export const fetchAssetDeployments = (assetId: string): Promise<DeploymentOption[]> =>
  fetchAllPages<DeploymentOption>(`${BASE}/assets/${assetId}/deployments`, 50, 10)

export const previewHireCharge = async (body: Record<string, unknown>): Promise<HireCharge> =>
  unwrap<HireCharge>(await apiClient.post(`${BASE}/hire-charges/preview`, body))

export const createHireCharge = async (body: Record<string, unknown>): Promise<HireCharge> =>
  unwrap<HireCharge>(await apiClient.post(`${BASE}/hire-charges`, body))

export const voidHireCharge = (id: string, reason: string) => apiClient.patch(`${BASE}/hire-charges/${id}/void`, { reason })

export const reopenHireCharge = (id: string, reason: string) => apiClient.patch(`${BASE}/hire-charges/${id}/reopen`, { reason })

export const draftInvoiceFromCharges = async (body: Record<string, unknown>): Promise<DraftInvoiceResult> =>
  unwrap<DraftInvoiceResult>(await apiClient.post(`${BASE}/hire-charges/draft-invoice`, body))
