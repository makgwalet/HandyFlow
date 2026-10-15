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
  /** HOURS or KILOMETRES: the unit of the three numbers above (their names are older than meter types). */
  meterType?: string
  meterUnit?: string
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

export interface MeterReading {
  id: string; meterType: string; reading: number; previousReading: number | null
  source: string; reason: string | null; actorUserId: string | null; recordedAt: string
  /** The reading this one replaces, when it is a correction. */
  correctsId?: string | null
  /** The latest dispute on this reading: OPEN, CONFIRMED or CORRECTED. */
  disputeId?: string | null; disputeStatus?: string | null; disputeReason?: string | null
  /** Only on the answer to a correction, dispute or confirmation: what happened to the kilometre charges on this machine. */
  chargesRecalculated?: number | null; chargesHeld?: number | null; chargesInvoiced?: number | null
}

// ── Inspections ──────────────────────────────────────────────────────────
export interface InspectionItem {
  item: string; outcome: string; comment: string | null; incidentId: string | null
  section?: string | null; answerType?: string; valueNumber?: number | null; valueText?: string | null; evidenceRef?: string | null; unit?: string | null
}
export interface Inspection {
  id: string; assetId: string; machine: string; inspectorUserId: string | null; performedAt: string
  meterReading: number | null; result: string; notes: string | null; items: InspectionItem[]
  inspectionType: string // PRE_USE | HANDOVER | RETURN | MAINTENANCE | SAFETY
  templateVersionId?: string | null; templateVersionNo?: number | null; signOffName?: string | null
}

// ── Inspection checklists ────────────────────────────────────────────────
export interface ChecklistQuestion {
  id?: string | null; section: string | null; text: string; answerType: string; required: boolean; builtIn: boolean
  min: number | null; max: number | null; unit: string | null; failOutcome: string
}
/** The checklist in force for one inspection type. source is BUILT_IN until the company publishes its own. */
export interface ActiveChecklist {
  versionId: string | null; versionNo: number | null; inspectionType: string; requiresSignoff: boolean
  effectiveFrom: string | null; source: "COMPANY" | "BUILT_IN"; questions: ChecklistQuestion[]
}
export interface ChecklistVersion {
  id: string; inspectionType: string; versionNo: number; status: "DRAFT" | "PUBLISHED"; effectiveFrom: string | null
  requiresSignoff: boolean; note: string | null; createdAt: string; publishedAt: string | null; questions: ChecklistQuestion[]
}
export interface ChecklistTypeOverview {
  inspectionType: string; active: ActiveChecklist | null; draft: ChecklistVersion | null
  history: { id: string; versionNo: number; status: string; effectiveFrom: string | null; publishedAt: string | null; questions: number }[]
}

export interface LatestInspection { assetId: string; performedAt: string; result: string }

// ── Profitability ────────────────────────────────────────────────────────
export interface ProfitRow {
  assetId: string | null; machine: string; charges: number; revenue: number; invoiced: number; notInvoiced: number
  maintenanceCost: number; margin: number; marginPercent: number | null
}
export interface MachineUtilisationRow { assetId: string; machine: string; shifts: number; hours: number; daysWorked: number; daysWorkedPercent: number }
export interface MachineUtilisation { from: string; to: string; days: number; totalHours: number; machines: MachineUtilisationRow[] }
export interface CustomerRevenueRow { customerId: string | null; customer: string; charges: number; revenue: number; invoiced: number; notInvoiced: number; sharePercent: number }
export interface CustomerRevenue { from: string; to: string; totals: CustomerRevenueRow; customers: CustomerRevenueRow[] }
export interface DowntimeRow { assetId: string | null; machine: string; breakdowns: number; breakdownHours: number; maintenanceHours: number; totalHours: number; availabilityPercent: number | null }
export interface Downtime { from: string; to: string; totals: DowntimeRow; machines: DowntimeRow[] }
export interface Utilisation { from: string; to: string; machines: number; totalHours: number; perDay: number[] }
export interface Profitability { from: string; to: string; totals: ProfitRow; machines: ProfitRow[] }

// ── Hire charges ─────────────────────────────────────────────────────────
export interface HireCharge {
  id: string | null; deploymentId: string; assetId: string; machine: string; customerId: string | null; customerName: string | null
  siteName: string | null; periodFrom: string; periodTo: string; basis: string; quantity: number; unitRate: number
  vatRate: number; amount: number; vatAmount: number; total: number; description: string
  status: string // PENDING | INVOICED | VOIDED (PREVIEW when not saved yet)
  invoiceId: string | null; invoiceNumber: string | null; note: string | null; createdAt: string | null
  /** CONTRACT, ASSET (the machine's own rate) or AGREED (for this charge only). */
  rateSource?: string | null; includedKm?: number | null
  /** Why a pending charge cannot be invoiced yet; null when it can. */
  attention?: string | null; recalculatedAt?: string | null
}
export interface BillingLine {
  deploymentId: string | null; assetId: string | null; machine: string | null; customerName: string | null; siteName: string | null
  basis: string | null; periodFrom: string; periodTo: string
  state: string // READY | ALREADY_BILLED | EXCEPTION
  message: string | null; quantity: number | null; unitRate: number | null; amount: number | null; total: number | null; rateSource: string | null
}
export interface BillingRun {
  month: string; applied: boolean; lines: BillingLine[]; ready: number; alreadyBilled: number; exceptions: number
  chargesCreated: number; invoices: DraftInvoiceResult[]
}
export interface BillingHistory {
  autoBillingEnabled: boolean
  runs: { id: string; month: string; trigger: string; draftsRequested: boolean; chargesCreated: number; alreadyBilled: number
    exceptions: number; invoicesCreated: number; createdBy: string | null; createdAt: string }[]
}
export interface AutoBilling { deploymentId: string; basis: string | null }
export interface ContractRate { basis: string; unitRate: number; includedKm: number | null; updatedAt: string | null }
export interface ContractRates { deploymentId: string; contractId: string | null; contractNumber: string | null; rates: ContractRate[] }
export interface DeploymentOption { id: string; assetId: string; siteName: string | null; clientName: string | null; deployedAt: string | null; returnedAt: string | null }
export interface DraftInvoiceResult { invoiceId: string; invoiceNumber: string; subtotal: number; vatTotal: number; total: number; charges: number }

export interface ContractRef { id: string; number: string | null; title: string; status: string; startDate: string | null; endDate: string | null }
export interface DeploymentContract {
  deploymentId: string; contract: ContractRef | null; addenda: ContractRef[]
  expectedValue: number | null; threshold: number | null; expected: boolean; warning: string | null
}
/** vatRegistered and vatRate are the company's VAT treatment today (Settings > Company > VAT). */
export interface EarthSettings { contractThreshold: number | null; vatRegistered?: boolean; vatRate?: number }
/** What a deployment is for, so the warnings can say whether a signed contract is expected. */
export interface HireContext { customerId?: string; siteId?: string; from?: string; to?: string }
