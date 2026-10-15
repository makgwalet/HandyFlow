// src/pages/earthmoving/shared/types.ts
//
// WHY this file exists: AssetsTab had a full Asset interface, MaintenanceTab
// and OperatorLogsTab each hand-rolled their own *partial* Asset interface
// with just the fields they used, and DeploymentsTab/IncidentsTab gave up
// and typed assets as `any[]`. That means five different shapes of "what an
// Asset looks like" living in five files, silently drifting out of sync as
// the backend changes — exactly the class of bug that Java's type system
// prevents but TypeScript only prevents if you actually share the type.

export interface Asset {
  id: string
  name: string
  fleetNumber: string | null
  assetType: string
  make: string | null
  model: string | null
  year: number | null
  serialNumber: string | null
  registration: string | null
  ownershipType: string // OWN | HIRED_IN | HIRED_OUT
  hireSupplier: string | null
  hireStartDate: string | null
  hireEndDate: string | null
  status: string // AVAILABLE | DEPLOYED | MAINTENANCE | BREAKDOWN | HIRED_OUT | RETIRED
  currentSite: string | null
  currentClient: string | null
  dailyRate: number | null
  hourlyRate: number | null
  currentHours: number
  lastServiceHours: number
  serviceIntervalHours: number
  dueForService: boolean
  notes: string | null
  createdAt: string
}

export interface MaintenanceRecord {
  id: string
  assetId: string
  type: string
  description: string
  performedAt: string
  hoursAtService: number | null
  cost: number | null
  supplier: string | null
  invoiceRef: string | null
  createdAt: string
}

export interface OperatorLog {
  id: string
  assetId: string
  operatorName: string | null
  siteName: string | null
  startedAt: string
  endedAt: string | null
  hoursLogged: number | null
  fuelUsedLitres: number | null
  createdAt: string
}

export interface Incident {
  id: string
  assetId: string
  type: string // BREAKDOWN | ACCIDENT | THEFT | FIRE | ROLLOVER | NEAR_MISS | FUEL_SPILL | OTHER
  severity: string // LOW | MEDIUM | HIGH | CRITICAL
  title: string
  description: string | null
  operatorName: string | null
  siteName: string | null
  latitude: number | null
  longitude: number | null
  status: string // OPEN | RESOLVED
  reportedAt: string
  resolvedAt: string | null
  resolutionNotes: string | null
}

export interface WorkOrderLine {
  id: string; kind: string; description: string; quantity: number; unitCost: number; total: number; supplier: string | null
}

export interface WorkOrder {
  id: string; assetId: string; number: string; title: string; description: string | null
  type: string; priority: string // LOW | NORMAL | HIGH | URGENT
  status: string // OPEN | IN_PROGRESS | AWAITING_PARTS | COMPLETED | CANCELLED
  assignedToUserId: string | null; assignedToName: string | null
  dueDate: string | null; overdue: boolean
  source: string // MANUAL | PLAN | INCIDENT
  planId: string | null; incidentId: string | null; blocksRelease: boolean
  startedAt: string | null; completedAt: string | null
  meterHours: number | null; downtimeHours: number | null; labourHours: number | null
  workDone: string | null; testResult: string | null; maintenanceRecordId: string | null; cancelReason: string | null
  totalCost: number; lines: WorkOrderLine[]; createdAt: string
}

export interface MaintenancePlan {
  id: string; assetId: string; name: string; taskType: string
  triggerType: string // HOURS | DAYS
  intervalValue: number; leadValue: number; lastDoneHours: number | null; lastDoneDate: string | null
  priority: string; blocksRelease: boolean; active: boolean
  state: string // OK | DUE_SOON | DUE | OVERDUE
  used: number; remaining: number; unit: string
}

export interface PlanScan { checked: number; workOrdersRaised: number; raised: string[] }

export interface StatusEvent {
  id: string; assetId: string; fromStatus: string | null; toStatus: string; reason: string | null
  actorUserId: string | null; meterHours: number | null; happenedAt: string
  workOrderId: string | null; overrideReason: string | null
}

export interface AlertDelivery { userId: string | null; level: number; status: string; detail: string | null; sentAt: string }

export interface EarthAlert {
  id: string; alertType: string; severity: string // INFO | WARNING | CRITICAL
  assetId: string | null; refType: string | null; refId: string | null
  title: string; message: string; status: string // OPEN | ACKNOWLEDGED | RESOLVED
  level: number; createdAt: string; escalatedAt: string | null
  acknowledgedBy: string | null; acknowledgedAt: string | null; resolvedAt: string | null; resolutionNote: string | null
  deliveries: AlertDelivery[]
}

export interface AlertRule {
  alertType: string; enabled: boolean; escalateAfterMinutes: number | null
  initialUserIds: string[]; escalationUserIds: string[]; usesAdminFallback: boolean; canBeDisabled: boolean
}

// ── Sites and links ──────────────────────────────────────────────────────
export interface Site {
  id: string; name: string; address: string | null; latitude: number | null; longitude: number | null
  customerId: string | null; customerName: string | null; projectId: string | null; projectName: string | null
  supervisorUserId: string | null; notes: string | null; active: boolean
}
export interface LinkCandidate { id: string; name: string }
export interface ReconRow {
  legacySite: string; legacyClient: string | null; deployments: number
  siteState: string; siteCandidates: LinkCandidate[]; customerState: string; customerCandidates: LinkCandidate[]
}
export interface Reconciliation {
  totalDeployments: number; withSite: number; withClientText: number; withCustomer: number; groups: number
  siteMatched: number; siteAmbiguous: number; siteUnmatched: number
  customerMatched: number; customerAmbiguous: number; customerUnmatched: number; rows: ReconRow[]
}
export interface LinkResult { deploymentsLinkedToSite: number; deploymentsLinkedToCustomer: number; deploymentsLinkedToProject: number; groupsStillUnresolved: number }
export interface PickOption { id: string; name: string }
