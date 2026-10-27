// src/api/facilities.api.ts
//
// Typed client for the internal Facilities backend (/api/v1/facilities). Types mirror the Java DTOs in
// platform/.../facilities/dto. The API wraps everything in { data }, and lists in a Spring page ({ content }).
import { apiClient } from './client'

export interface Page<T> { content: T[]; totalElements: number; totalPages: number; number: number; size: number }
export type Id = string

export type WorkOrderStatus =
  | 'REQUESTED' | 'OPEN' | 'ASSIGNED' | 'IN_PROGRESS' | 'ON_HOLD' | 'PENDING_VERIFICATION' | 'COMPLETED' | 'REJECTED' | 'CANCELLED'
export type Priority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT' | 'EMERGENCY'

export interface WorkOrder {
  id: Id; workOrderNumber: string; siteId: Id; assetId: Id | null; ppmScheduleId: Id | null
  category: string; priority: Priority | string; status: WorkOrderStatus | string; description: string; reportedBy: string | null
  technicianId: Id | null; technicianName: string | null; vendorId: Id | null; vendorName: string | null
  scheduledDate: string | null; completedAt: string | null; completionNotes: string | null; holdReason: string | null
  cost: number | null; cancellationReason: string | null; createdAt: string
  requestedByUserId: Id | null; safetyImpact: boolean; duplicateOfWorkOrderId: Id | null; approvedAt: string | null
  rejectionReason: string | null; requiresVerification: boolean; verifiedAt: string | null
  reworkCount: number; lastReworkReason: string | null; downtimeMinutes: number | null; failureCause: string | null
}

export interface Site { id: Id; name: string; siteType: string; address: Record<string, string> | null; notes: string | null; status: string; createdAt: string }

export interface Asset {
  id: Id; siteId: Id; assetTag: string; name: string; assetType: string; location: string | null
  manufacturer: string | null; model: string | null; serialNumber: string | null; installDate: string | null
  warrantyExpiryDate: string | null; criticality: string | null; status: string; notes: string | null; createdAt: string
}

export interface Certificate {
  id: Id; siteId: Id | null; assetId: Id | null; certificateType: string; certificateNumber: string | null; issuedBy: string | null
  issueDate: string | null; expiryDate: string; documentRef: string | null; status: string; revokedReason: string | null; createdAt: string
}

export interface Vendor {
  id: Id; companyName: string; serviceType: string | null; contactName: string | null; contactPhone: string | null
  contactEmail: string | null; notes: string | null; active: boolean; createdAt: string; onboardingStatus: 'PENDING' | 'APPROVED' | 'SUSPENDED' | string
}

export interface Technician {
  id: Id; name: string; contactPhone: string | null; contactEmail: string | null; specialization: string | null
  linkedUserId: Id | null; active: boolean; createdAt: string
}

export interface Dashboard {
  generatedAt: string; open: number; unassigned: number; onHold: number; overdue: number; awaitingApproval: number
  awaitingVerification: number; openByStatus: Record<string, number>; openByPriority: Record<string, number>
  completedLast30Days: number; avgCompletionHoursLast30Days: number | null; reworkRatePercentLast30Days: number | null
  downtimeMinutesLast30Days: number; costLast30Days: number; ppmSchedulesOverdue: number
  certificatesExpiringIn30Days: number; certificatesExpired: number
}

export interface ActionItem {
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM'; type: string; siteId: Id | null; assetId: Id | null
  title: string; detail: string; dueDate: string | null; entityType: string; entityId: Id
}
export interface ActionCentre { critical: number; high: number; medium: number; items: ActionItem[] }

export interface CostLine {
  id: Id; workOrderId: Id; costType: string; description: string; quantity: number; unitCost: number
  totalCost: number; recordedByUserId: Id | null; recordedAt: string
}

export interface StockItem {
  id: Id; sku: string; name: string; unit: string; location: string | null; onHand: number; reorderLevel: number
  reorderQty: number; unitCost: number; stockValue: number; belowReorderLevel: boolean; active: boolean
}
export interface StockMovement {
  id: Id; itemId: Id; movementType: 'RECEIPT' | 'ISSUE' | 'RETURN' | 'ADJUSTMENT'; quantity: number; unitCost: number
  balanceAfter: number; workOrderId: Id | null; costLineId: Id | null; purchaseOrderId: Id | null; reference: string | null
  createdByUserId: Id | null; createdAt: string
}
export interface ReorderSuggestion {
  itemId: Id; sku: string; name: string; unit: string; onHand: number; reorderLevel: number; onOrder: number
  suggestedQuantity: number; unitCost: number
}

export interface PurchaseOrderLine {
  id: Id; lineNo: number; itemId: Id; description: string; orderedQty: number; receivedQty: number
  outstandingQty: number; unitCost: number; lineTotal: number
}
export interface PurchaseOrder {
  id: Id; poNumber: string; vendorId: Id; vendorName: string | null
  status: 'DRAFT' | 'APPROVED' | 'PARTLY_RECEIVED' | 'RECEIVED' | 'CANCELLED' | string
  expectedDate: string | null; notes: string | null; totalValue: number; createdByUserId: Id | null
  approvedByUserId: Id | null; approvedAt: string | null; closedReason: string | null; createdAt: string; lines: PurchaseOrderLine[]
}

export interface InspectionTemplate {
  id: Id; name: string; description: string | null; assetCategory: string | null; active: boolean; revision: number
  items: { id: Id; position: number; prompt: string; critical: boolean }[]; createdAt: string; updatedAt: string
}
export interface InspectionResult { itemId: Id; position: number; prompt: string; critical: boolean; outcome: 'PASS' | 'FAIL' | 'NA' | null; comment: string | null }
export interface Inspection {
  id: Id; templateId: Id; templateName: string; templateRevision: number; siteId: Id; assetId: Id | null
  inspectorUserId: Id | null; inspectorName: string | null; status: 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED' | string
  result: 'PASS' | 'FAIL' | null; notes: string | null; answered: number; total: number
  startedAt: string; completedAt: string | null; results: InspectionResult[]; actions: Id[]
}
export interface CorrectiveAction {
  id: Id; sourceType: string; sourceId: Id | null; siteId: Id; assetId: Id | null; description: string; priority: string
  critical: boolean; status: 'OPEN' | 'IN_PROGRESS' | 'CLOSED' | 'CANCELLED' | string; dueDate: string; overdue: boolean
  workOrderId: Id | null; closureNote: string | null; closedAt: string | null; createdAt: string
}
export interface PpmSchedule {
  id: Id; assetId: Id; taskName: string; description: string | null; frequencyDays: number
  nextDueDate: string | null; lastCompletedDate: string | null; active: boolean; createdAt: string
}

// ── request bodies ──────────────────────────────────────────────────────────────────────────
export interface CreateWorkOrderBody {
  siteId: Id; assetId?: Id | null; category: string; priority?: string; description: string
  reportedBy?: string; scheduledDate?: string | null; requiresVerification?: boolean
}
export interface AssignBody { technicianId?: Id | null; technicianName?: string | null; vendorId?: Id | null; vendorName?: string | null }
export interface CompleteBody { completionNotes?: string; cost?: number | null; downtimeMinutes?: number | null; failureCause?: string }

export interface SiteBody { name: string; siteType?: string; address?: Record<string, string>; notes?: string }
export interface AssetBody {
  siteId?: Id; assetTag?: string; name: string; assetType?: string; location?: string; manufacturer?: string; model?: string
  serialNumber?: string; installDate?: string | null; warrantyExpiryDate?: string | null; criticality?: string; notes?: string
}
export interface VendorBody { companyName: string; serviceType?: string; contactName?: string; contactPhone?: string; contactEmail?: string; notes?: string }
export interface StockItemBody {
  sku: string; name: string; unit: string; location?: string; reorderLevel?: number; reorderQty?: number
  openingQuantity?: number; openingUnitCost?: number
}
export interface PurchaseOrderBody { vendorId: Id; expectedDate?: string | null; notes?: string; lines: { itemId: Id; quantity: number; unitCost: number }[] }

const B = '/api/v1/facilities'
/** The API wraps results as { data }; tolerate an unwrapped body too. */
const data = <T,>(r: { data: unknown }): T => {
  const body = r.data as { data?: T } | null
  return (body && typeof body === 'object' && 'data' in body ? body.data : r.data) as T
}
const page = <T,>(r: { data: unknown }): T[] => {
  const p = data<T[] | { content?: T[] }>(r)
  return Array.isArray(p) ? p : (p?.content ?? [])
}

export const facilitiesApi = {
  dashboard: async () => data<Dashboard>(await apiClient.get(`${B}/reports/dashboard`)),
  actionCentre: async () => data<ActionCentre>(await apiClient.get(`${B}/action-centre`)),

  workOrders: async (status?: string, size = 100) =>
    page<WorkOrder>(await apiClient.get(`${B}/work-orders`, { params: { status: status || undefined, size, sort: 'createdAt,desc' } })),
  workOrder: async (id: Id) => data<WorkOrder>(await apiClient.get(`${B}/work-orders/${id}`)),
  costs: async (id: Id) => data<CostLine[]>(await apiClient.get(`${B}/work-orders/${id}/costs`)),
  createWorkOrder: async (b: CreateWorkOrderBody) => data<WorkOrder>(await apiClient.post(`${B}/work-orders`, b)),
  /** POST /work-orders/{id}/{action}: approve, reject, assign, start, hold, complete, verify, reject-verification, cancel. */
  workOrderAction: async (id: Id, action: string, body: object = {}) =>
    data<WorkOrder>(await apiClient.post(`${B}/work-orders/${id}/${action}`, body)),

  sites: async () => page<Site>(await apiClient.get(`${B}/sites`, { params: { size: 200 } })),
  assets: async (siteId?: Id) => page<Asset>(await apiClient.get(`${B}/assets`, { params: { siteId: siteId || undefined, size: 200 } })),
  certificates: async () => page<Certificate>(await apiClient.get(`${B}/compliance`, { params: { size: 200, sort: 'expiryDate,asc' } })),
  vendors: async () => page<Vendor>(await apiClient.get(`${B}/vendors`, { params: { size: 200 } })),
  createSite: async (b: SiteBody) => data<Site>(await apiClient.post(`${B}/sites`, b)),
  updateSite: async (id: Id, b: SiteBody) => data<Site>(await apiClient.put(`${B}/sites/${id}`, b)),
  siteAction: async (id: Id, action: 'close' | 'reopen') => data<Site>(await apiClient.post(`${B}/sites/${id}/${action}`)),
  createAsset: async (b: AssetBody) => data<Asset>(await apiClient.post(`${B}/assets`, b)),
  updateAsset: async (id: Id, b: AssetBody) => data<Asset>(await apiClient.put(`${B}/assets/${id}`, b)),
  setAssetStatus: async (id: Id, status: string) => data<Asset>(await apiClient.patch(`${B}/assets/${id}/status`, { status })),
  createVendor: async (b: VendorBody) => data<Vendor>(await apiClient.post(`${B}/vendors`, b)),
  updateVendor: async (id: Id, b: VendorBody) => data<Vendor>(await apiClient.put(`${B}/vendors/${id}`, b)),
  /** suspend needs { reason }. */
  vendorAction: async (id: Id, action: 'approve' | 'suspend' | 'deactivate' | 'reactivate', body: object = {}) => data<Vendor>(await apiClient.post(`${B}/vendors/${id}/${action}`, body)),
  approveVendor: async (id: Id) => data<Vendor>(await apiClient.post(`${B}/vendors/${id}/approve`)),
  technicians: async () => page<Technician>(await apiClient.get(`${B}/technicians`, { params: { size: 200 } })),

  inspectionTemplates: async (includeInactive = false) =>
    page<InspectionTemplate>(await apiClient.get(`${B}/inspection-templates`, { params: { includeInactive } })),
  saveInspectionTemplate: async (id: Id | null, b: { name: string; description?: string; assetCategory?: string; items: { prompt: string; critical: boolean }[] }) =>
    data<InspectionTemplate>(id ? await apiClient.put(`${B}/inspection-templates/${id}`, b) : await apiClient.post(`${B}/inspection-templates`, b)),
  templateActive: async (id: Id, action: 'activate' | 'deactivate') => data<InspectionTemplate>(await apiClient.post(`${B}/inspection-templates/${id}/${action}`)),
  inspections: async (status?: string, siteId?: Id) =>
    page<Inspection>(await apiClient.get(`${B}/inspections`, { params: { status: status || undefined, siteId: siteId || undefined, size: 100, sort: 'startedAt,desc' } })),
  inspection: async (id: Id) => data<Inspection>(await apiClient.get(`${B}/inspections/${id}`)),
  startInspection: async (b: { templateId: Id; siteId: Id; assetId?: Id | null }) => data<Inspection>(await apiClient.post(`${B}/inspections`, b)),
  answerInspection: async (id: Id, answers: { itemId: Id; outcome: string; comment?: string }[]) =>
    data<Inspection>(await apiClient.post(`${B}/inspections/${id}/answers`, { answers })),
  completeInspection: async (id: Id, notes?: string) => data<Inspection>(await apiClient.post(`${B}/inspections/${id}/complete`, { notes })),
  cancelInspection: async (id: Id) => data<Inspection>(await apiClient.post(`${B}/inspections/${id}/cancel`)),

  correctiveActions: async (status?: string) =>
    page<CorrectiveAction>(await apiClient.get(`${B}/corrective-actions`, { params: { status: status || undefined, size: 100, sort: 'dueDate,asc' } })),
  logFinding: async (b: { siteId: Id; assetId?: Id | null; description: string; priority?: string; dueDate: string }) =>
    data<CorrectiveAction>(await apiClient.post(`${B}/corrective-actions`, { ...b, sourceType: 'MANUAL' })),
  /** raise-work-order takes no body; close and cancel need { reason }; reschedule needs { dueDate }. */
  correctiveAction: async (id: Id, action: 'work-order' | 'close' | 'cancel' | 'reschedule', body: object = {}) =>
    data<CorrectiveAction>(await apiClient.post(`${B}/corrective-actions/${id}/${action}`, body)),

  ppmSchedules: async (assetId: Id) => data<PpmSchedule[]>(await apiClient.get(`${B}/assets/${assetId}/ppm-schedules`)),
  createPpm: async (b: { assetId: Id; taskName: string; description?: string; frequencyDays: number; startDate?: string | null }) =>
    data<PpmSchedule>(await apiClient.post(`${B}/ppm-schedules`, b)),
  updatePpm: async (id: Id, b: { taskName?: string; description?: string; frequencyDays?: number }) =>
    data<PpmSchedule>(await apiClient.put(`${B}/ppm-schedules/${id}`, b)),
  ppmActive: async (id: Id, action: 'deactivate' | 'reactivate') => data<PpmSchedule>(await apiClient.post(`${B}/ppm-schedules/${id}/${action}`)),

  stock: async (q?: string, includeInactive = false) =>
    page<StockItem>(await apiClient.get(`${B}/stock`, { params: { q: q || undefined, includeInactive, size: 200 } })),
  stockMovements: async (id: Id) => page<StockMovement>(await apiClient.get(`${B}/stock/${id}/movements`, { params: { size: 50 } })),
  reorderSuggestions: async () => data<ReorderSuggestion[]>(await apiClient.get(`${B}/stock/reorder-suggestions`)),
  createStockItem: async (b: StockItemBody) => data<StockItem>(await apiClient.post(`${B}/stock`, b)),
  updateStockItem: async (id: Id, b: StockItemBody) => data<StockItem>(await apiClient.put(`${B}/stock/${id}`, b)),
  stockActive: async (id: Id, action: 'deactivate' | 'reactivate') => data<StockItem>(await apiClient.post(`${B}/stock/${id}/${action}`)),
  stockAction: async (id: Id, action: 'receive' | 'issue' | 'return' | 'adjust', body: object) =>
    data<StockItem>(await apiClient.post(`${B}/stock/${id}/${action}`, body)),

  purchaseOrders: async (status?: string) =>
    page<PurchaseOrder>(await apiClient.get(`${B}/purchase-orders`, { params: { status: status || undefined, size: 100 } })),
  createPurchaseOrder: async (b: PurchaseOrderBody) => data<PurchaseOrder>(await apiClient.post(`${B}/purchase-orders`, b)),
  updatePurchaseOrder: async (id: Id, b: PurchaseOrderBody) => data<PurchaseOrder>(await apiClient.put(`${B}/purchase-orders/${id}`, b)),
  purchaseOrderAction: async (id: Id, action: 'approve' | 'cancel' | 'close-short', body: object = {}) =>
    data<PurchaseOrder>(await apiClient.post(`${B}/purchase-orders/${id}/${action}`, body)),
  receiveOrder: async (id: Id, body: { reference?: string; lines: { lineId: Id; quantity: number; unitCost?: number }[] }) =>
    data<PurchaseOrder>(await apiClient.post(`${B}/purchase-orders/${id}/receipts`, body)),
}
