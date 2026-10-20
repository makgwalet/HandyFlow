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
import type { Asset, MaintenanceRecord, OperatorLog, Incident, WorkOrder, MaintenancePlan, PlanScan, StatusEvent, EarthAlert, AlertRule, Site, Reconciliation, LinkResult, PickOption, MeterReading, Inspection, LatestInspection, ActiveChecklist, ChecklistVersion, ChecklistTypeOverview, ChecklistQuestion, Profitability, Utilisation, MachineUtilisation, CustomerRevenue, Downtime, HireCharge, DeploymentOption, DraftInvoiceResult, DeploymentContract, EarthSettings, HireContext, ContractRef, ContractRates, BillingRun, BillingHistory, AutoBilling, EarthDocument, DocumentVersion, Disposal } from "./types"

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

export const correctMeterReading = async (assetId: string, body: { reading: number; reason: string }): Promise<MeterReading> =>
  unwrap<MeterReading>(await apiClient.post(`${BASE}/assets/${assetId}/meter/correction`, body))

export const disputeMeterReading = async (assetId: string, readingId: string, reason: string): Promise<MeterReading> =>
  unwrap<MeterReading>(await apiClient.post(`${BASE}/assets/${assetId}/meter/readings/${readingId}/dispute`, { reason }))

export const confirmMeterDispute = async (assetId: string, disputeId: string, note: string): Promise<MeterReading> =>
  unwrap<MeterReading>(await apiClient.patch(`${BASE}/assets/${assetId}/meter/disputes/${disputeId}/confirm`, { note }))

export const changeMeter = (assetId: string, body: Record<string, unknown>) =>
  apiClient.put(`${BASE}/assets/${assetId}/meter`, body)

// ── Inspections ──────────────────────────────────────────────────────────

export const fetchChecklist = async (): Promise<string[]> => unwrap<string[]>(await apiClient.get(`${BASE}/inspections/checklist`))

export const fetchActiveChecklist = async (type: string): Promise<ActiveChecklist | null> =>
  ((await apiClient.get(`${BASE}/checklists/active?type=${encodeURIComponent(type)}`)).data?.data ?? null) as ActiveChecklist | null
export const fetchChecklists = async (): Promise<ChecklistTypeOverview[]> => {
  const r = unwrap<unknown>(await apiClient.get(`${BASE}/checklists`))
  return Array.isArray(r) ? (r as ChecklistTypeOverview[]) : []
}
export const startChecklistDraft = async (type: string): Promise<ChecklistVersion> =>
  unwrap<ChecklistVersion>(await apiClient.post(`${BASE}/checklists/draft?type=${encodeURIComponent(type)}`))
export const saveChecklistDraft = async (id: string, body: { requiresSignoff: boolean; note: string | null; questions: ChecklistQuestion[] }): Promise<ChecklistVersion> =>
  unwrap<ChecklistVersion>(await apiClient.put(`${BASE}/checklists/versions/${id}`, body))
export const publishChecklist = async (id: string, effectiveFrom: string | null): Promise<ChecklistVersion> =>
  unwrap<ChecklistVersion>(await apiClient.post(`${BASE}/checklists/versions/${id}/publish`, { effectiveFrom }))
export const discardChecklistDraft = async (id: string): Promise<void> => { await apiClient.delete(`${BASE}/checklists/versions/${id}`) }

export const fetchInspections = async (assetId: string): Promise<Inspection[]> =>
  unwrap<Inspection[]>(await apiClient.get(`${BASE}/inspections${assetId ? `?assetId=${assetId}` : ""}`))

export const fetchLatestInspections = async (): Promise<LatestInspection[]> => {
  const r = unwrap<unknown>(await apiClient.get(`${BASE}/inspections/latest`))
  return Array.isArray(r) ? (r as LatestInspection[]) : []
}

export const fetchDeployWarnings = async (assetId: string, hire?: HireContext): Promise<string[]> => {
  const q = new URLSearchParams()
  if (hire?.customerId) q.set("customerId", hire.customerId)
  if (hire?.siteId) q.set("siteId", hire.siteId)
  if (hire?.from) q.set("from", hire.from)
  if (hire?.to) q.set("to", hire.to)
  const qs = q.toString()
  const r = unwrap<{ warnings?: string[] }>(await apiClient.get(`${BASE}/assets/${assetId}/deploy-warnings${qs ? `?${qs}` : ""}`))
  return Array.isArray(r?.warnings) ? r.warnings : []
}

export const createInspection = async (body: Record<string, unknown>): Promise<Inspection> =>
  unwrap<Inspection>(await apiClient.post(`${BASE}/inspections`, body))

// ── Reports ──────────────────────────────────────────────────────────────

export const fetchProfitability = async (from: string, to: string): Promise<Profitability> =>
  unwrap<Profitability>(await apiClient.get(`${BASE}/reports/profitability?from=${from}&to=${to}`))

export const fetchUtilisation = async (days: number): Promise<Utilisation> =>
  unwrap<Utilisation>(await apiClient.get(`${BASE}/reports/utilisation?days=${days}`))

export const fetchMachineUtilisation = async (from: string, to: string): Promise<MachineUtilisation> =>
  unwrap<MachineUtilisation>(await apiClient.get(`${BASE}/reports/machine-utilisation?from=${from}&to=${to}`))
export const fetchCustomerRevenue = async (from: string, to: string): Promise<CustomerRevenue> =>
  unwrap<CustomerRevenue>(await apiClient.get(`${BASE}/reports/customer-revenue?from=${from}&to=${to}`))
export const fetchDowntime = async (from: string, to: string): Promise<Downtime> =>
  unwrap<Downtime>(await apiClient.get(`${BASE}/reports/downtime?from=${from}&to=${to}`))

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

// ── Hire contracts and settings ──────────────────────────────────────────

export const fetchDeploymentContract = async (deploymentId: string): Promise<DeploymentContract> =>
  unwrap<DeploymentContract>(await apiClient.get(`${BASE}/deployments/${deploymentId}/contract`))

export const draftHireContract = async (deploymentId: string): Promise<DeploymentContract> =>
  unwrap<DeploymentContract>(await apiClient.post(`${BASE}/deployments/${deploymentId}/contract/draft`, {}))

export const linkHireContract = async (deploymentId: string, contractId: string): Promise<DeploymentContract> =>
  unwrap<DeploymentContract>(await apiClient.put(`${BASE}/deployments/${deploymentId}/contract`, { contractId }))

export const extendHire = async (deploymentId: string, newEndDate: string, reason: string): Promise<DeploymentContract> =>
  unwrap<DeploymentContract>(await apiClient.post(`${BASE}/deployments/${deploymentId}/contract/extension`, { newEndDate, reason }))

export const fetchSettings = async (): Promise<EarthSettings> => {
  const r = unwrap<EarthSettings | never[]>(await apiClient.get(`${BASE}/settings`))
  return Array.isArray(r) ? { contractThreshold: null } : r
}

export const saveSettings = async (contractThreshold: number | null): Promise<EarthSettings> =>
  unwrap<EarthSettings>(await apiClient.put(`${BASE}/settings`, { contractThreshold }))

export const fetchContractRates = async (deploymentId: string): Promise<ContractRates> =>
  unwrap<ContractRates>(await apiClient.get(`${BASE}/deployments/${deploymentId}/contract-rates`))

export const saveContractRate = async (deploymentId: string, body: { basis: string; unitRate: number; includedKm: number | null }): Promise<ContractRates> =>
  unwrap<ContractRates>(await apiClient.put(`${BASE}/deployments/${deploymentId}/contract-rates`, body))

export const removeContractRate = async (deploymentId: string, basis: string): Promise<ContractRates> =>
  unwrap<ContractRates>(await apiClient.delete(`${BASE}/deployments/${deploymentId}/contract-rates/${basis}`))

/** Hire agreements in Contracting, for linking an existing one. Needs the Contracting read permission. */
export const fetchHireContracts = async (): Promise<ContractRef[]> => {
  const r = unwrap<unknown>(await apiClient.get("/api/v1/contracts?type=EQUIPMENT_HIRE&size=100"))
  return (Array.isArray(r) ? r : []).map((c: { id: string; contractNumber?: string; title: string; status: string; startDate?: string | null; endDate?: string | null }) =>
    ({ id: c.id, number: c.contractNumber ?? null, title: c.title, status: c.status, startDate: c.startDate ?? null, endDate: c.endDate ?? null }))
}

// ── Monthly billing ──────────────────────────────────────────────────────

export const previewBilling = async (month: string): Promise<BillingRun> =>
  unwrap<BillingRun>(await apiClient.get(`${BASE}/billing/preview?month=${encodeURIComponent(month)}`))

export const runBilling = async (month: string, createDrafts: boolean): Promise<BillingRun> =>
  unwrap<BillingRun>(await apiClient.post(`${BASE}/billing/run`, { month, createDrafts }))

export const fetchBillingHistory = async (): Promise<BillingHistory> =>
  unwrap<BillingHistory>(await apiClient.get(`${BASE}/billing/runs`))

export const switchScheduledBilling = async (enabled: boolean): Promise<BillingHistory> =>
  unwrap<BillingHistory>(await apiClient.put(`${BASE}/billing/scheduled`, { enabled }))

export const fetchAutoBilling = async (deploymentId: string): Promise<AutoBilling> =>
  unwrap<AutoBilling>(await apiClient.get(`${BASE}/deployments/${deploymentId}/auto-billing`))

export const saveAutoBilling = async (deploymentId: string, basis: string | null): Promise<AutoBilling> =>
  unwrap<AutoBilling>(await apiClient.put(`${BASE}/deployments/${deploymentId}/auto-billing`, { basis }))

// ── Documents ────────────────────────────────────────────────────────────
// kind is ASSET or OPERATOR; the server decides who may see or change which category.

export const fetchDocuments = async (kind: string, ownerId: string, includeArchived: boolean): Promise<EarthDocument[]> => {
  const r = unwrap<unknown>(await apiClient.get(`${BASE}/documents?ownerKind=${kind}&ownerId=${ownerId}&includeArchived=${includeArchived}`))
  return Array.isArray(r) ? (r as EarthDocument[]) : []
}
export const fetchExpiringDocuments = async (withinDays: number): Promise<EarthDocument[]> => {
  const r = unwrap<unknown>(await apiClient.get(`${BASE}/documents/expiring?withinDays=${withinDays}`))
  return Array.isArray(r) ? (r as EarthDocument[]) : []
}
export const fetchDocumentVersions = async (id: string): Promise<DocumentVersion[]> => {
  const r = unwrap<unknown>(await apiClient.get(`${BASE}/documents/${id}/versions`))
  return Array.isArray(r) ? (r as DocumentVersion[]) : []
}
export interface UploadDocument { kind: string; ownerId: string; category: string; title: string; description: string | null; expiresOn: string | null; file: File }
export const uploadDocument = async (b: UploadDocument): Promise<EarthDocument> => {
  const f = new FormData()
  f.append("ownerKind", b.kind); f.append("ownerId", b.ownerId); f.append("category", b.category); f.append("title", b.title)
  if (b.description) f.append("description", b.description)
  if (b.expiresOn) f.append("expiresOn", b.expiresOn)
  f.append("file", b.file)
  return unwrap<EarthDocument>(await apiClient.post(`${BASE}/documents`, f))
}
export const addDocumentVersion = async (b: { id: string; note: string | null; expiresOn: string | null; file: File }): Promise<EarthDocument> => {
  const f = new FormData()
  if (b.note) f.append("note", b.note)
  if (b.expiresOn) f.append("expiresOn", b.expiresOn)
  f.append("file", b.file)
  return unwrap<EarthDocument>(await apiClient.post(`${BASE}/documents/${b.id}/versions`, f))
}
export const updateDocument = async (b: { id: string; title: string; description: string | null; expiresOn: string | null }): Promise<EarthDocument> =>
  unwrap<EarthDocument>(await apiClient.patch(`${BASE}/documents/${b.id}`, { title: b.title, description: b.description, expiresOn: b.expiresOn }))
export const archiveDocument = async (b: { id: string; reason: string }): Promise<EarthDocument> =>
  unwrap<EarthDocument>(await apiClient.post(`${BASE}/documents/${b.id}/archive`, { reason: b.reason }))
export const downloadDocument = async (id: string, version: number | null, fileName: string): Promise<void> => {
  const r = await apiClient.get(`${BASE}/documents/${id}/download${version ? `?version=${version}` : ""}`, { responseType: "blob" })
  const url = URL.createObjectURL(r.data as Blob)
  const a = document.createElement("a")
  a.href = url; a.download = fileName; a.rel = "noopener"
  document.body.appendChild(a); a.click(); a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

// ── Disposals ────────────────────────────────────────────────────────────
// Evidence is attached through the documents endpoints with kind DISPOSAL.

export const fetchDisposals = async (status?: string): Promise<Disposal[]> => {
  const r = unwrap<unknown>(await apiClient.get(`${BASE}/disposals${status ? `?status=${status}` : ""}`))
  return Array.isArray(r) ? (r as Disposal[]) : []
}
export interface RequestDisposal { assetId: string; reason: string; details: string; counterparty: string | null; proceeds: number | null }
export const requestDisposal = async (b: RequestDisposal): Promise<Disposal> => unwrap<Disposal>(await apiClient.post(`${BASE}/disposals`, b))
export const decideDisposal = async (b: { id: string; action: "approve" | "reject" | "cancel"; note: string | null }): Promise<Disposal> =>
  unwrap<Disposal>(await apiClient.post(`${BASE}/disposals/${b.id}/${b.action}`, { note: b.note }))
export const completeDisposal = async (id: string): Promise<Disposal> => unwrap<Disposal>(await apiClient.post(`${BASE}/disposals/${id}/complete`))
