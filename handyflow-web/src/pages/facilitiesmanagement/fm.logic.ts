// Facilities Management (provider) front end: what each work order may do in each status, what the forms offer, and the form checks.
// The checks mirror the server's own so people see the reason before pressing a button; the server still decides.
export type Tone = "neutral" | "info" | "success" | "warning" | "danger"

export const PRIORITIES = [["LOW", "Low"], ["NORMAL", "Normal"], ["HIGH", "High"], ["URGENT", "Urgent"], ["EMERGENCY", "Emergency"]] as const
export const REQUEST_CATEGORIES = [["CORRECTIVE", "Repair"], ["EMERGENCY", "Emergency"], ["INSPECTION", "Inspection"], ["OTHER", "Other"]] as const
export const CATEGORIES = [["CORRECTIVE", "Repair"], ["PREVENTIVE", "Planned maintenance"], ["EMERGENCY", "Emergency"], ["INSPECTION", "Inspection"], ["OTHER", "Other"]] as const
export const COST_TYPES = [["LABOUR", "Labour"], ["PARTS", "Parts"], ["TRAVEL", "Travel"], ["CONTRACTOR", "Contractor"], ["OTHER", "Other"]] as const
export const SITE_TYPES = [["OFFICE", "Office"], ["WAREHOUSE", "Warehouse"], ["RETAIL", "Retail"], ["FACTORY", "Factory"], ["OTHER", "Other"]] as const
export const CRITICALITY = [["LOW", "Low"], ["MEDIUM", "Medium"], ["HIGH", "High"], ["CRITICAL", "Critical"]] as const
export const ASSET_STATUS: Record<string, { label: string; tone: Tone }> = {
  OPERATIONAL: { label: "Operational", tone: "success" }, DOWN: { label: "Down", tone: "danger" }, MAINTENANCE: { label: "In maintenance", tone: "warning" }, DECOMMISSIONED: { label: "Decommissioned", tone: "neutral" },
}
export const labelOf = (pairs: readonly (readonly [string, string])[], v: string | null | undefined) =>
  pairs.find(p => p[0] === v)?.[1] ?? (v ? v.replace(/_/g, " ").toLowerCase().replace(/^./, c => c.toUpperCase()) : "")

export const WO_STATUS: Record<string, { label: string; tone: Tone }> = {
  REQUESTED: { label: "Requested", tone: "info" }, REJECTED: { label: "Declined", tone: "neutral" }, OPEN: { label: "Open", tone: "warning" },
  ASSIGNED: { label: "Assigned", tone: "info" }, IN_PROGRESS: { label: "In progress", tone: "info" }, ON_HOLD: { label: "On hold", tone: "warning" },
  PENDING_VERIFICATION: { label: "Awaiting check", tone: "warning" }, COMPLETED: { label: "Completed", tone: "success" }, CANCELLED: { label: "Cancelled", tone: "neutral" },
}
export const PRIORITY_TONE: Record<string, Tone> = { LOW: "neutral", NORMAL: "neutral", HIGH: "warning", URGENT: "danger", EMERGENCY: "danger" }

export type WoAction = "approve" | "reject" | "assign" | "start" | "hold" | "complete" | "cancel" | "verify" | "rework"
export interface Who { manage: boolean; assign: boolean; execute: boolean }
/** The authorities, as the server reads them: triage is manage or assign; running the job is manage or technician. */
export function whoFrom(has: (p: string) => boolean): Who {
  const admin = has("FACILITIESMANAGEMENT_ADMIN"), manage = admin || has("FACILITIESMANAGEMENT_MANAGE")
  return { manage, assign: manage || has("FACILITIESMANAGEMENT_WORKORDER_ASSIGN"), execute: manage || has("FACILITIESMANAGEMENT_TECHNICIAN") }
}
/** What this person can do to a work order in this status. Server-side checks (own request, billing) still apply. */
export function woActions(status: string, invoiced: boolean, w: Who): WoAction[] {
  const out: WoAction[] = []
  switch (status) {
    case "REQUESTED": if (w.assign) out.push("approve", "reject"); if (w.assign) out.push("cancel"); break
    case "OPEN": if (w.assign) out.push("assign", "cancel"); break
    case "ASSIGNED": if (w.assign) out.push("assign"); if (w.execute) out.push("start", "complete"); if (w.assign) out.push("cancel"); break
    case "IN_PROGRESS": if (w.assign) out.push("assign"); if (w.execute) out.push("hold", "complete"); if (w.assign) out.push("cancel"); break
    case "ON_HOLD": if (w.assign) out.push("assign"); if (w.execute) out.push("start", "complete"); if (w.assign) out.push("cancel"); break
    case "PENDING_VERIFICATION": if (w.assign) out.push("verify", "rework"); break
    case "COMPLETED": break
  }
  return invoiced ? out.filter(a => a !== "cancel") : out
}
/** Cost lines can be recorded while the job is still being done. */
export const canCostLines = (status: string) => ["ASSIGNED", "IN_PROGRESS", "ON_HOLD", "PENDING_VERIFICATION"].includes(status)
export const isTerminal = (status: string) => status === "COMPLETED" || status === "CANCELLED" || status === "REJECTED"

/** How an SLA target stands: the server stamps the verdict once responded or resolved; before that, compare the due time to now. */
export function slaNote(due: string | null | undefined, verdict: string | null | undefined, done: boolean, now: Date): { text: string; tone: Tone } | null {
  if (verdict) return { text: verdict === "MET" ? "Met" : verdict === "BREACHED" ? "Missed" : verdict.replace(/_/g, " ").toLowerCase(), tone: verdict === "MET" ? "success" : verdict === "BREACHED" ? "danger" : "neutral" }
  if (!due || done) return null
  const ms = new Date(due).getTime() - now.getTime()
  if (ms < 0) return { text: "Overdue", tone: "danger" }
  const h = ms / 36e5
  return { text: h < 24 ? `Due in ${Math.max(1, Math.round(h))} h` : `Due in ${Math.round(h / 24)} d`, tone: h < 4 ? "warning" : "neutral" }
}

const num = (s: string) => (s.trim() === "" ? null : Number(s))
const t = (s: string) => s.trim() || null

export interface RequestForm { clientId: string; siteId: string; assetId: string; description: string; category: string; priority: string; safetyImpact: boolean; reportedBy: string }
export const emptyRequest = (): RequestForm => ({ clientId: "", siteId: "", assetId: "", description: "", category: "CORRECTIVE", priority: "NORMAL", safetyImpact: false, reportedBy: "" })
export function requestProblem(f: RequestForm): string | null {
  if (!f.clientId) return "Choose the client"
  if (!f.siteId) return "Choose the site"
  if (!f.description.trim()) return "Describe the problem"
  return null
}
export const requestBody = (f: RequestForm) => ({ clientId: f.clientId, siteId: f.siteId, assetId: t(f.assetId), description: f.description.trim(), category: f.category, priority: f.priority, safetyImpact: f.safetyImpact, reportedBy: t(f.reportedBy) })

export interface OrderForm { clientId: string; siteId: string; assetId: string; category: string; priority: string; description: string; reportedBy: string; scheduledDate: string; requiresVerification: boolean }
export const emptyOrder = (): OrderForm => ({ clientId: "", siteId: "", assetId: "", category: "CORRECTIVE", priority: "NORMAL", description: "", reportedBy: "", scheduledDate: "", requiresVerification: false })
export function orderProblem(f: OrderForm): string | null {
  if (!f.clientId) return "Choose the client"
  if (!f.siteId) return "Choose the site"
  if (!f.description.trim()) return "Describe the work"
  return null
}
export const orderBody = (f: OrderForm) => ({ clientId: f.clientId, siteId: f.siteId, assetId: t(f.assetId), category: f.category, priority: f.priority, description: f.description.trim(), reportedBy: t(f.reportedBy), scheduledDate: t(f.scheduledDate), requiresVerification: f.requiresVerification })

/** Assignment is to a technician or a vendor, never both and never neither. */
export function assignProblem(technicianId: string, vendorId: string): string | null {
  if (!technicianId && !vendorId) return "Choose a technician or a vendor"
  if (technicianId && vendorId) return "Choose a technician or a vendor, not both"
  return null
}
export interface Named { id: string; name: string }
export const assignBody = (technicianId: string, vendorId: string, techs: Named[], vendors: Named[]) => ({
  technicianId: technicianId || null, technicianName: techs.find(x => x.id === technicianId)?.name ?? null,
  vendorId: vendorId || null, vendorName: vendors.find(x => x.id === vendorId)?.name ?? null,
})

export interface CompleteForm { completionNotes: string; cost: string; completedAt: string; downtimeMinutes: string; failureCause: string }
export const emptyComplete = (): CompleteForm => ({ completionNotes: "", cost: "", completedAt: "", downtimeMinutes: "", failureCause: "" })
export function completeProblem(f: CompleteForm, createdAt: string | null, now: Date): string | null {
  if (!f.completionNotes.trim()) return "Say what was done"
  const c = num(f.cost); if (c != null && (!Number.isFinite(c) || c < 0)) return "The cost must be 0 or more"
  const d = num(f.downtimeMinutes); if (d != null && (!Number.isInteger(d) || d < 0)) return "Downtime must be a whole number of minutes"
  if (f.completedAt) {
    const when = new Date(f.completedAt)
    if (Number.isNaN(when.getTime())) return "That finish time does not look right"
    if (when.getTime() > now.getTime() + 5 * 60_000) return "The finish time cannot be in the future"
    if (createdAt && when.getTime() < new Date(createdAt).getTime()) return "The finish time cannot be before the work order was raised"
  }
  return null
}
export const completeBody = (f: CompleteForm) => ({
  completionNotes: f.completionNotes.trim(), cost: num(f.cost), completedAt: f.completedAt ? new Date(f.completedAt).toISOString() : null,
  downtimeMinutes: num(f.downtimeMinutes), failureCause: t(f.failureCause),
})

export interface CostForm { costType: string; description: string; quantity: string; unitCost: string; unitCharge: string; billable: boolean }
export const emptyCost = (): CostForm => ({ costType: "LABOUR", description: "", quantity: "1", unitCost: "", unitCharge: "", billable: true })
export function costProblem(f: CostForm): string | null {
  if (!f.description.trim()) return "Describe the cost"
  const q = num(f.quantity); if (q == null || !Number.isFinite(q) || q < 0.001) return "The quantity must be at least 0.001"
  const u = num(f.unitCost); if (u == null || !Number.isFinite(u) || u < 0) return "Enter the unit cost (0 or more)"
  const c = num(f.unitCharge); if (c != null && (!Number.isFinite(c) || c < 0)) return "The charge must be 0 or more"
  return null
}
export const costBody = (f: CostForm) => ({ costType: f.costType, description: f.description.trim(), quantity: Number(f.quantity), unitCost: Number(f.unitCost), unitCharge: num(f.unitCharge), billable: f.billable })
export const round2 = (n: number) => Math.round(n * 100) / 100
/** What a cost line comes to: cost, and what is charged to the client (the cost again when no charge is set). */
export function costTotals(f: CostForm) {
  const q = Number(f.quantity), u = Number(f.unitCost), c = num(f.unitCharge)
  if (!Number.isFinite(q) || !Number.isFinite(u)) return { cost: 0, charge: 0 }
  return { cost: round2(q * u), charge: f.billable ? round2(q * (c != null && Number.isFinite(c) ? c : u)) : 0 }
}

export interface ClientForm { tradingName: string; registrationNumber: string; contactName: string; contactEmail: string; contactPhone: string; address: string; vatNumber: string }
export const emptyClient = (): ClientForm => ({ tradingName: "", registrationNumber: "", contactName: "", contactEmail: "", contactPhone: "", address: "", vatNumber: "" })
export const looksLikeEmail = (s: string) => !s.trim() || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s.trim())
export function clientProblem(f: ClientForm): string | null {
  if (!f.tradingName.trim()) return "Enter the client's trading name"
  if (!looksLikeEmail(f.contactEmail)) return "That email address does not look right"
  if (f.vatNumber.trim() && !/^\d{10}$/.test(f.vatNumber.replace(/\s/g, ""))) return "A VAT number is 10 digits"
  return null
}
export const clientBody = (f: ClientForm) => ({ tradingName: f.tradingName.trim(), registrationNumber: t(f.registrationNumber), contactName: t(f.contactName), contactEmail: t(f.contactEmail), contactPhone: t(f.contactPhone), address: t(f.address), vatNumber: f.vatNumber.trim() ? f.vatNumber.replace(/\s/g, "") : null })

export interface SiteForm { clientId: string; name: string; siteType: string; street: string; city: string; province: string; postalCode: string; notes: string }
export const emptySite = (): SiteForm => ({ clientId: "", name: "", siteType: "OFFICE", street: "", city: "", province: "", postalCode: "", notes: "" })
export function siteProblem(f: SiteForm, creating: boolean): string | null {
  if (creating && !f.clientId) return "Choose the client"
  if (!f.name.trim()) return "Enter the site name"
  return null
}
export function siteAddress(f: SiteForm): Record<string, string> {
  const a: Record<string, string> = {}
  for (const k of ["street", "city", "province", "postalCode"] as const) if (f[k].trim()) a[k] = f[k].trim()
  return a
}
export const siteBody = (f: SiteForm, creating: boolean) => ({ ...(creating ? { clientId: f.clientId } : {}), name: f.name.trim(), siteType: f.siteType, address: siteAddress(f), notes: t(f.notes) })
export const addressLine = (a: Record<string, string> | null | undefined) => a ? [a.street, a.city, a.province, a.postalCode].filter(Boolean).join(", ") : ""

export interface AssetForm { siteId: string; assetTag: string; name: string; assetType: string; location: string; manufacturer: string; model: string; serialNumber: string; installDate: string; warrantyExpiryDate: string; criticality: string; notes: string }
export const emptyAsset = (): AssetForm => ({ siteId: "", assetTag: "", name: "", assetType: "", location: "", manufacturer: "", model: "", serialNumber: "", installDate: "", warrantyExpiryDate: "", criticality: "MEDIUM", notes: "" })
export function assetProblem(f: AssetForm, creating: boolean): string | null {
  if (creating && !f.siteId) return "Choose the site"
  if (!f.name.trim()) return "Enter the asset's name"
  if (creating && !f.assetType.trim()) return "Say what kind of asset it is"
  if (f.installDate && f.warrantyExpiryDate && f.warrantyExpiryDate < f.installDate) return "The warranty cannot end before the install date"
  return null
}
export function assetBody(f: AssetForm, creating: boolean) {
  const common = { name: f.name.trim(), location: t(f.location), manufacturer: t(f.manufacturer), model: t(f.model), serialNumber: t(f.serialNumber), warrantyExpiryDate: t(f.warrantyExpiryDate), criticality: f.criticality, notes: t(f.notes) }
  return creating ? { ...common, siteId: f.siteId, assetTag: t(f.assetTag), assetType: f.assetType.trim(), installDate: t(f.installDate) } : common
}
/** An asset that is not decommissioned can be moved between the three working states, except into the one it is already in. */
export const assetMoves = (status: string): string[] => status === "DECOMMISSIONED" ? [] : ["OPERATIONAL", "DOWN", "MAINTENANCE"].filter(s => s !== status)
export const canDecommission = (status: string) => status !== "DECOMMISSIONED"
/** Warranty note: shows the end date, flagged when it has run out or runs out within 60 days. */
export function warrantyNote(end: string | null | undefined, now: Date): { text: string; tone: Tone } | null {
  if (!end) return null
  const days = Math.round((new Date(end + "T00:00:00").getTime() - new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()) / 864e5)
  if (days < 0) return { text: "Warranty ended", tone: "neutral" }
  if (days <= 60) return { text: `Warranty ends in ${days} d`, tone: "warning" }
  return null
}

// ── Planned maintenance ──────────────────────────────────────────────────────────────────────────────────────────────
export const FREQUENCIES = [["7", "Weekly"], ["30", "Monthly"], ["90", "Every 3 months"], ["180", "Every 6 months"], ["365", "Yearly"]] as const
export interface PpmForm { taskName: string; description: string; frequencyDays: string; startDate: string }
export const emptyPpm = (): PpmForm => ({ taskName: "", description: "", frequencyDays: "30", startDate: "" })
export function ppmProblem(f: PpmForm): string | null {
  if (!f.taskName.trim()) return "Name the task"
  const n = num(f.frequencyDays)
  if (n == null || !Number.isInteger(n) || n <= 0) return "Say how many days between visits (a whole number above 0)"
  return null
}
export const ppmBody = (f: PpmForm, creating: boolean, assetId?: string) => creating
  ? { assetId, taskName: f.taskName.trim(), description: t(f.description), frequencyDays: Number(f.frequencyDays), startDate: t(f.startDate) }
  : { taskName: f.taskName.trim(), description: t(f.description), frequencyDays: Number(f.frequencyDays) }
/** "Every 30 days" in words, using the common names where they fit. */
export const everyText = (days: number) => ({ 7: "Weekly", 30: "Monthly", 90: "Every 3 months", 180: "Every 6 months", 365: "Yearly" } as Record<number, string>)[days] ?? `Every ${days} days`
/** When a schedule is next due, flagged when it is late or within a week. */
export function ppmDueNote(active: boolean, nextDue: string | null | undefined, now: Date): { text: string; tone: Tone } | null {
  if (!active || !nextDue) return null
  const days = Math.round((new Date(nextDue + "T00:00:00").getTime() - new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()) / 864e5)
  if (days < 0) return { text: `${-days} d overdue`, tone: "danger" }
  if (days === 0) return { text: "Due today", tone: "warning" }
  if (days <= 7) return { text: `Due in ${days} d`, tone: "warning" }
  return null
}

// ── Technicians ──────────────────────────────────────────────────────────────────────────────────────────────────────
export interface TechForm { name: string; contactPhone: string; contactEmail: string; specialization: string; linkedUserId: string }
export const emptyTech = (): TechForm => ({ name: "", contactPhone: "", contactEmail: "", specialization: "", linkedUserId: "" })
export function techProblem(f: TechForm): string | null {
  if (!f.name.trim()) return "Enter the technician's name"
  if (!looksLikeEmail(f.contactEmail)) return "That email address does not look right"
  return null
}
export const techBody = (f: TechForm) => ({ name: f.name.trim(), contactPhone: t(f.contactPhone), contactEmail: t(f.contactEmail), specialization: t(f.specialization), linkedUserId: t(f.linkedUserId) })

// ── Vendors ──────────────────────────────────────────────────────────────────────────────────────────────────────────
export interface VendorForm { companyName: string; serviceType: string; contactName: string; contactPhone: string; contactEmail: string; notes: string }
export const emptyVendor = (): VendorForm => ({ companyName: "", serviceType: "", contactName: "", contactPhone: "", contactEmail: "", notes: "" })
export function vendorProblem(f: VendorForm): string | null {
  if (!f.companyName.trim()) return "Enter the company name"
  if (!looksLikeEmail(f.contactEmail)) return "That email address does not look right"
  return null
}
export const vendorBody = (f: VendorForm) => ({ companyName: f.companyName.trim(), serviceType: t(f.serviceType), contactName: t(f.contactName), contactPhone: t(f.contactPhone), contactEmail: t(f.contactEmail), notes: t(f.notes) })
export const ONBOARDING: Record<string, { label: string; tone: Tone }> = { PENDING: { label: "Awaiting approval", tone: "warning" }, APPROVED: { label: "Approved", tone: "success" }, SUSPENDED: { label: "Suspended", tone: "danger" } }
export type VendorAction = "approve" | "suspend"
/** Approval is offered until approved; suspension once approved. People who may approve are the same who may suspend. */
export const vendorActions = (status: string, canApprove: boolean): VendorAction[] => !canApprove ? [] : status === "APPROVED" ? ["suspend"] : status === "SUSPENDED" ? ["approve"] : ["approve", "suspend"]
export const DOC_TYPES = [["PUBLIC_LIABILITY_INSURANCE", "Public liability insurance"], ["COIDA_LETTER", "COIDA letter of good standing"], ["TAX_CLEARANCE", "Tax clearance"], ["TRADE_LICENCE", "Trade licence"], ["BEE_CERTIFICATE", "B-BBEE certificate"], ["OTHER", "Other"]] as const
export interface DocForm { docType: string; reference: string; issuedBy: string; issueDate: string; expiryDate: string; coverAmount: string; documentRef: string }
export const emptyDoc = (): DocForm => ({ docType: "PUBLIC_LIABILITY_INSURANCE", reference: "", issuedBy: "", issueDate: "", expiryDate: "", coverAmount: "", documentRef: "" })
/** Every kind of document except "other" needs an expiry date. */
export function docProblem(f: DocForm): string | null {
  if (f.docType !== "OTHER" && !f.expiryDate) return "An expiry date is needed for this kind of document"
  if (f.issueDate && f.expiryDate && f.expiryDate < f.issueDate) return "The expiry date cannot be before the issue date"
  const c = num(f.coverAmount); if (c != null && (!Number.isFinite(c) || c < 0)) return "The cover amount must be 0 or more"
  return null
}
export const docBody = (f: DocForm) => ({ docType: f.docType, reference: t(f.reference), issuedBy: t(f.issuedBy), issueDate: t(f.issueDate), expiryDate: t(f.expiryDate), coverAmount: num(f.coverAmount), documentRef: t(f.documentRef) })
export const docNote = (status: string, inForce: boolean, expiry: string | null, now: Date): { text: string; tone: Tone } => {
  if (status === "REVOKED") return { text: "Revoked", tone: "neutral" }
  if (!inForce) return { text: "Expired", tone: "danger" }
  if (expiry) {
    const days = Math.round((new Date(expiry + "T00:00:00").getTime() - new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()) / 864e5)
    if (days <= 30) return { text: `Expires in ${days} d`, tone: "warning" }
  }
  return { text: "In force", tone: "success" }
}

// ── Service agreements ───────────────────────────────────────────────────────────────────────────────────────────────
export const BILLING_TYPES = [["TIME_AND_MATERIALS", "Time and materials"], ["RETAINER", "Monthly retainer"]] as const
export const AGREEMENT_CATEGORIES = [["PPM", "Planned maintenance"], ["CORRECTIVE", "Repair"], ["EMERGENCY", "Emergency"], ["INSPECTION", "Inspection"], ["OTHER", "Other"]] as const
export const CLOCKS = [["24X7", "Round the clock"], ["BUSINESS_HOURS", "Business hours only"]] as const
export interface TargetForm { priority: string; responseMinutes: string; resolutionMinutes: string; clock: string }
export interface AgreementForm { clientId: string; name: string; billingType: string; monthlyFee: string; hourlyRate: string; partsMarkupPercent: string; startDate: string; endDate: string; siteIds: string[]; categories: string[]; targets: TargetForm[] }
export const emptyAgreement = (): AgreementForm => ({ clientId: "", name: "", billingType: "TIME_AND_MATERIALS", monthlyFee: "", hourlyRate: "", partsMarkupPercent: "", startDate: "", endDate: "", siteIds: [], categories: [], targets: [] })
export const emptyTarget = (used: string[]): TargetForm => ({ priority: PRIORITIES.map(p => p[0]).find(p => !used.includes(p)) ?? "NORMAL", responseMinutes: "60", resolutionMinutes: "480", clock: "BUSINESS_HOURS" })
/** Minutes in words: "45 min", "4 h", "1 d 2 h" (a day is 24 hours, as the round-the-clock clock counts it). */
export function minutesText(m: number | null | undefined): string {
  if (m == null) return "—"
  if (m < 60) return `${m} min`
  const h = Math.floor(m / 60), r = m % 60
  if (h < 24) return r ? `${h} h ${r} min` : `${h} h`
  const d = Math.floor(h / 24), hh = h % 24
  return hh ? `${d} d ${hh} h` : `${d} d`
}
export function agreementProblem(f: AgreementForm, creating: boolean): string | null {
  if (creating && !f.clientId) return "Choose the client"
  if (!f.name.trim()) return "Give the agreement a name"
  if (creating && !f.startDate) return "Enter the start date"
  if (f.startDate && f.endDate && f.endDate <= f.startDate) return "The end date must be after the start date"
  const fee = num(f.monthlyFee)
  if (f.billingType === "RETAINER" && (fee == null || !(fee > 0))) return "A retainer needs a monthly fee above 0"
  if (fee != null && (!Number.isFinite(fee) || fee < 0)) return "The monthly fee cannot be negative"
  const rate = num(f.hourlyRate); if (rate != null && (!Number.isFinite(rate) || rate < 0)) return "The hourly rate cannot be negative"
  const mk = num(f.partsMarkupPercent); if (mk != null && (!Number.isFinite(mk) || mk < 0 || mk > 1000)) return "The parts mark-up must be between 0 and 1000 %"
  const seen = new Set<string>()
  for (const x of f.targets) {
    if (seen.has(x.priority)) return `There are two targets for ${labelOf(PRIORITIES, x.priority).toLowerCase()} priority`
    seen.add(x.priority)
    const r = num(x.responseMinutes), s = num(x.resolutionMinutes)
    if (r == null || !Number.isInteger(r) || r < 1) return "Each target needs a response time of at least 1 minute"
    if (s == null || !Number.isInteger(s) || s < r) return "A fix time cannot be shorter than the response time"
  }
  return null
}
const targetsBody = (ts: TargetForm[]) => ts.map(x => ({ priority: x.priority, responseMinutes: Number(x.responseMinutes), resolutionMinutes: Number(x.resolutionMinutes), clock: x.clock }))
export const agreementBody = (f: AgreementForm, creating: boolean) => creating
  ? { clientId: f.clientId, name: f.name.trim(), billingType: f.billingType, monthlyFee: num(f.monthlyFee), hourlyRate: num(f.hourlyRate), partsMarkupPercent: num(f.partsMarkupPercent), startDate: f.startDate, endDate: t(f.endDate), siteIds: f.siteIds, categories: f.categories, slaTargets: targetsBody(f.targets) }
  : { name: f.name.trim(), monthlyFee: num(f.monthlyFee), hourlyRate: num(f.hourlyRate), partsMarkupPercent: num(f.partsMarkupPercent), endDate: t(f.endDate), siteIds: f.siteIds, categories: f.categories, slaTargets: targetsBody(f.targets) }
/** What an agreement covers, in words: every site and kind of work when the lists are empty. */
export const coverageText = (siteIds: string[], categories: string[], siteName: (id: string) => string) =>
  `${siteIds.length === 0 ? "All sites" : siteIds.map(siteName).filter(Boolean).join(", ") || `${siteIds.length} sites`} · ${categories.length === 0 ? "all kinds of work" : categories.map(c => labelOf(AGREEMENT_CATEGORIES, c)).join(", ")}`
export function agreementState(status: string, endDate: string | null | undefined, now: Date): { text: string; tone: Tone } {
  if (status === "ENDED") return { text: "Ended", tone: "neutral" }
  if (endDate) {
    const days = Math.round((new Date(endDate + "T00:00:00").getTime() - new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()) / 864e5)
    if (days < 0) return { text: "Past its end date", tone: "warning" }
    if (days <= 30) return { text: `Ends in ${days} d`, tone: "warning" }
  }
  return { text: "Active", tone: "success" }
}

// ── SLA business calendar ────────────────────────────────────────────────────────────────────────────────────────────
export const WEEKDAYS = [["MON", "Mon"], ["TUE", "Tue"], ["WED", "Wed"], ["THU", "Thu"], ["FRI", "Fri"], ["SAT", "Sat"], ["SUN", "Sun"]] as const
export interface CalendarForm { startTime: string; endTime: string; workingDays: string[]; timeZone: string }
export const trimTime = (s: string | null | undefined) => (s ?? "").slice(0, 5)
export function calendarProblem(f: CalendarForm): string | null {
  if (!f.startTime || !f.endTime) return "Enter the opening and closing times"
  if (f.endTime <= f.startTime) return "Closing time must be after the opening time"
  if (f.workingDays.length === 0) return "Pick at least one working day"
  if (!f.timeZone.trim()) return "Enter the time zone"
  return null
}
export const calendarBody = (f: CalendarForm) => ({ startTime: f.startTime, endTime: f.endTime, workingDays: WEEKDAYS.map(d => d[0]).filter(d => f.workingDays.includes(d)), timeZone: f.timeZone.trim() })
export function holidayProblem(date: string, existing: string[]): string | null {
  if (!date) return "Pick the date"
  if (existing.includes(date)) return "That date is already a holiday"
  return null
}
export const daysText = (days: string[]) => {
  const on = WEEKDAYS.map(d => d[0]).filter(d => days.includes(d))
  if (on.length === 7) return "Every day"
  if (on.join() === "MON,TUE,WED,THU,FRI") return "Mon – Fri"
  return on.map(d => labelOf(WEEKDAYS, d)).join(", ")
}

// ── Quotes ───────────────────────────────────────────────────────────────────────────────────────────────────────────
export const QUOTE_STATUS: Record<string, { label: string; tone: Tone }> = {
  DRAFT: { label: "Draft", tone: "neutral" }, SENT: { label: "Sent", tone: "info" }, ACCEPTED: { label: "Accepted", tone: "success" },
  DECLINED: { label: "Declined", tone: "danger" }, EXPIRED: { label: "Expired", tone: "warning" }, WITHDRAWN: { label: "Withdrawn", tone: "neutral" },
}
export interface QuoteLineForm { description: string; quantity: string; unitPrice: string }
export interface QuoteForm { clientId: string; siteId: string; assetId: string; title: string; description: string; validUntil: string; internalNote: string; lines: QuoteLineForm[] }
export const emptyQuoteLine = (): QuoteLineForm => ({ description: "", quantity: "1", unitPrice: "" })
export const emptyQuote = (): QuoteForm => ({ clientId: "", siteId: "", assetId: "", title: "", description: "", validUntil: "", internalNote: "", lines: [emptyQuoteLine()] })
const cents = (n: number) => Math.round(n * 100) / 100
export const lineTotal = (l: QuoteLineForm) => { const q = num(l.quantity), p = num(l.unitPrice); return q == null || p == null || !Number.isFinite(q) || !Number.isFinite(p) ? 0 : cents(q * p) }
export const quoteSubtotal = (ls: QuoteLineForm[]) => cents(ls.reduce((s, l) => s + lineTotal(l), 0))
export function quoteProblem(f: QuoteForm, creating: boolean): string | null {
  if (creating && !f.clientId) return "Choose the client"
  if (creating && !f.siteId) return "Choose the site"
  if (!f.title.trim()) return "Give the quote a title"
  if (f.lines.length === 0) return "Add at least one line"
  if (f.lines.length > 50) return "A quote can have at most 50 lines"
  for (const [i, l] of f.lines.entries()) {
    const n = i + 1, q = num(l.quantity), p = num(l.unitPrice)
    if (!l.description.trim()) return `Line ${n} needs a description`
    if (q == null || !Number.isFinite(q) || q < 0.001 || q > 1_000_000) return `Line ${n}: the quantity must be between 0.001 and 1,000,000`
    if (p == null || !Number.isFinite(p) || p < 0 || p > 100_000_000) return `Line ${n}: enter the price (0 or more)`
  }
  return null
}
export const quoteBody = (f: QuoteForm, creating: boolean) => ({
  ...(creating ? { clientId: f.clientId, siteId: f.siteId, assetId: t(f.assetId) } : {}),
  title: f.title.trim(), description: t(f.description), validUntil: t(f.validUntil), internalNote: t(f.internalNote),
  lines: f.lines.map(l => ({ description: l.description.trim(), quantity: Number(l.quantity), unitPrice: Number(l.unitPrice) })),
})
/** What may be done to a quote: only a draft is edited or sent, and only a sent one is withdrawn. The client decides in their portal. */
export const quoteActions = (status: string, canWrite: boolean): ("edit" | "send" | "withdraw")[] => !canWrite ? [] : status === "DRAFT" ? ["edit", "send"] : status === "SENT" ? ["withdraw"] : []
/** Why a draft cannot be sent yet, from what the server checks. */
export function sendProblem(validUntil: string | null | undefined, total: number, today: string): string | null {
  if (!(total > 0)) return "The total must be above 0 before it can be sent"
  if (validUntil && validUntil < today) return "The valid-until date has passed"
  return null
}
export const quoteValidity = (status: string, validUntil: string | null | undefined, today: string): { text: string; tone: Tone } | null =>
  status === "SENT" && validUntil ? (validUntil < today ? { text: "Past its valid-until date", tone: "warning" } : null) : null

// ── Billing ──────────────────────────────────────────────────────────────────────────────────────────────────────────
export const INVOICE_STATUS: Record<string, { label: string; tone: Tone }> = {
  DRAFT: { label: "Draft", tone: "neutral" }, APPROVED: { label: "Approved", tone: "info" }, SENT: { label: "Sent", tone: "info" }, PARTIAL: { label: "Part paid", tone: "warning" },
  PAID: { label: "Paid", tone: "success" }, CREDITED: { label: "Credited", tone: "neutral" }, CANCELLED: { label: "Cancelled", tone: "neutral" }, OVERDUE: { label: "Overdue", tone: "danger" },
}
export const CREDIT_STATUS: Record<string, { label: string; tone: Tone }> = { DRAFT: { label: "Draft", tone: "neutral" }, APPROVED: { label: "Approved", tone: "success" }, CANCELLED: { label: "Cancelled", tone: "neutral" } }
export const GL_STATUS: Record<string, { label: string; tone: Tone }> = { POSTED: { label: "In the ledger", tone: "success" }, PENDING: { label: "Waiting to post", tone: "warning" }, FAILED: { label: "Not posted", tone: "danger" } }
export const glNote = (s: string | null | undefined): { label: string; tone: Tone } | null => s ? (GL_STATUS[s] ?? { label: s.replace(/_/g, " ").toLowerCase(), tone: "neutral" }) : null
export const PAY_METHODS = [["EFT", "EFT"], ["CASH", "Cash"], ["CARD", "Card"], ["CHEQUE", "Cheque"], ["OTHER", "Other"]] as const
export const BILL_BASES = [["WORK_ORDERS", "Completed chargeable work"], ["RETAINER", "A retainer's monthly fee"]] as const
export type InvoiceAction = "approve" | "send" | "cancel" | "pay" | "credit"
export interface BillWho { prepare: boolean; approve: boolean }
/** Preparing is for billing managers; approving for billing approvers (each includes admin). */
export function billWho(has: (p: string) => boolean): BillWho {
  const admin = has("FACILITIESMANAGEMENT_ADMIN")
  return { prepare: admin || has("FACILITIESMANAGEMENT_BILLING_MANAGE"), approve: admin || has("FACILITIESMANAGEMENT_BILLING_APPROVE") }
}
/** The server's lifecycle: draft → approve → send → payments; credit notes apply once approved; a draft can be cancelled, an approved invoice cannot. */
export function invoiceActions(status: string, balance: number, w: BillWho): InvoiceAction[] {
  const out: InvoiceAction[] = []
  switch (status) {
    case "DRAFT": if (w.approve) out.push("approve"); if (w.prepare) out.push("cancel"); break
    case "APPROVED": if (w.prepare) out.push("send", "credit"); break
    case "SENT": case "PARTIAL": case "OVERDUE": if (w.prepare) { if (balance > 0) out.push("pay"); out.push("credit") } break
  }
  return out
}
export interface GenerateForm { basis: string; periodStart: string; periodEnd: string; agreementId: string }
export const emptyGenerate = (today: string): GenerateForm => {
  const d = new Date(today + "T00:00:00"), a = new Date(d.getFullYear(), d.getMonth() - 1, 1), b = new Date(d.getFullYear(), d.getMonth(), 0)
  const iso = (x: Date) => `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, "0")}-${String(x.getDate()).padStart(2, "0")}`
  return { basis: "WORK_ORDERS", periodStart: iso(a), periodEnd: iso(b), agreementId: "" }
}
export function generateProblem(f: GenerateForm): string | null {
  if (!f.periodStart || !f.periodEnd) return "Enter both ends of the period"
  if (f.periodEnd < f.periodStart) return "The period cannot end before it starts"
  return null
}
export const generateBody = (f: GenerateForm) => ({ periodStart: f.periodStart, periodEnd: f.periodEnd, basis: f.basis, agreementId: f.basis === "RETAINER" ? t(f.agreementId) : null })
export interface PayForm { amount: string; paymentDate: string; method: string; reference: string }
export const emptyPay = (balance: number, today: string): PayForm => ({ amount: balance > 0 ? balance.toFixed(2) : "", paymentDate: today, method: "EFT", reference: "" })
export function payProblem(f: PayForm, balance: number): string | null {
  const a = num(f.amount)
  if (a == null || !Number.isFinite(a) || a < 0.01) return "Enter the amount paid (at least 0.01)"
  if (cents(a) > cents(balance)) return `That is more than the ${balance.toFixed(2)} still owing`
  return null
}
export const payBody = (f: PayForm) => ({ amount: Number(f.amount), paymentDate: t(f.paymentDate), method: t(f.method), reference: t(f.reference) })
export interface CreditForm { mode: "full" | "part"; subtotal: string; reason: string }
export const emptyCredit = (): CreditForm => ({ mode: "full", subtotal: "", reason: "" })
export function creditProblem(f: CreditForm): string | null {
  if (!f.reason.trim()) return "Say why the credit is being given"
  if (f.reason.trim().length > 500) return "Keep the reason under 500 characters"
  if (f.mode === "part") { const s = num(f.subtotal); if (s == null || !Number.isFinite(s) || s < 0.01) return "Enter the amount to credit before VAT (at least 0.01)" }
  return null
}
export const creditBody = (f: CreditForm) => f.mode === "full" ? { creditFullBalance: true, reason: f.reason.trim() } : { subtotal: Number(f.subtotal), creditFullBalance: false, reason: f.reason.trim() }
export const creditActions = (status: string, w: BillWho): ("approve" | "cancel")[] => status !== "DRAFT" ? [] : [...(w.approve ? ["approve" as const] : []), ...(w.prepare ? ["cancel" as const] : [])]
/** Due-date note on an invoice that is still owed. */
export function dueNote(status: string, balance: number, due: string | null | undefined, today: string): { text: string; tone: Tone } | null {
  if (!due || balance <= 0 || !["SENT", "PARTIAL", "OVERDUE"].includes(status)) return null
  if (due < today) return { text: `${Math.round((new Date(today).getTime() - new Date(due).getTime()) / 864e5)} d overdue`, tone: "danger" }
  return null
}

// ── Reports ──────────────────────────────────────────────────────────────────────────────────────────────────────────
const isoDate = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
/** The last `days` days up to and including `today` (both as yyyy-mm-dd). */
export function defaultRange(today: string, days = 90): { from: string; to: string } {
  const d = new Date(today + "T00:00:00"); d.setDate(d.getDate() - (days - 1))
  return { from: isoDate(d), to: today }
}
export const rangeProblem = (from: string, to: string): string | null => !from || !to ? "Pick both dates" : to < from ? "The end date cannot be before the start date" : null
export const pct = (n: number | null | undefined) => n == null ? "—" : `${Math.round(n * 10) / 10}%`
/** Compliance is good at 90 % and above, worth a look from 75 %, and poor below that. No jobs with a target means no verdict. */
export const complianceTone = (p: number | null | undefined): Tone => p == null ? "neutral" : p >= 90 ? "success" : p >= 75 ? "warning" : "danger"
export const marginTone = (p: number | null | undefined): Tone => p == null ? "neutral" : p < 0 ? "danger" : "success"
export function hoursText(h: number | null | undefined): string {
  if (h == null) return "—"
  if (h >= 48) return `${Math.round(h / 24 * 10) / 10} d`
  return `${Math.round(h * 10) / 10} h`
}
export function minutesToHours(m: number | null | undefined): string { return m == null ? "—" : hoursText(m / 60) }
/** A bar's width as a share of the biggest value, never so thin it vanishes. */
export const barShare = (n: number, max: number) => max <= 0 || n <= 0 ? 0 : Math.max(3, Math.round(n / max * 100))
const WO_ORDER = ["REQUESTED", "OPEN", "ASSIGNED", "IN_PROGRESS", "ON_HOLD", "PENDING_VERIFICATION", "COMPLETED", "CANCELLED", "REJECTED"]
export const statusRows = (m: Record<string, number> | null | undefined) => Object.entries(m ?? {}).filter(([, n]) => n > 0)
  .sort((a, b) => (WO_ORDER.indexOf(a[0]) + 100) % 100 - (WO_ORDER.indexOf(b[0]) + 100) % 100)
  .map(([k, n]) => ({ key: k, label: WO_STATUS[k]?.label ?? labelOf([], k), count: n, tone: WO_STATUS[k]?.tone ?? "neutral" as Tone }))
export const priorityRows = (m: Record<string, number> | null | undefined) => PRIORITIES.map(([k, l]) => ({ key: k as string, label: l as string, count: (m ?? {})[k] ?? 0 })).filter(r => r.count > 0).reverse()

// ── Audit log and access ─────────────────────────────────────────────────────────────────────────────────────────────
export const AUDIT_ENTITIES = [["WORK_ORDER", "Work orders"], ["QUOTE", "Quotes"], ["INVOICE", "Invoices"], ["CREDIT_NOTE", "Credit notes"], ["AGREEMENT", "Agreements"], ["VENDOR", "Vendors"], ["TECHNICIAN", "Technicians"], ["CLIENT", "Clients"], ["SITE", "Sites"], ["ASSET", "Assets"], ["USER", "Users"], ["CALENDAR", "Working hours"], ["BILLING", "Billing"]] as const
/** "WORK_ORDER_ASSIGNED" → "Work order assigned". */
export const auditLabel = (a: string) => a.replace(/_/g, " ").toLowerCase().replace(/^./, c => c.toUpperCase())
export const accessSummary = (restricted: boolean, count: number) => !restricted ? "Sees every client" : count === 1 ? "Restricted to 1 client" : `Restricted to ${count} clients`
/** An empty set removes the restriction, so saving with nothing ticked widens access; the screen says so. */
export const accessWarning = (picked: number, wasRestricted: boolean) => picked === 0 && wasRestricted ? "With no client ticked this person will see every client." : null
export const GRANT_STATUS: Record<string, { label: string; tone: Tone }> = { PENDING: { label: "Invited", tone: "warning" }, ACTIVE: { label: "Has access", tone: "success" }, REVOKED: { label: "Revoked", tone: "neutral" } }
export const inviteProblem = (email: string): string | null => !email.trim() ? "Enter the contact's email address" : !looksLikeEmail(email) ? "That email address does not look right" : null
export const grantCanRevoke = (status: string, canRevoke: boolean) => canRevoke && status !== "REVOKED"

// ── Client portal ────────────────────────────────────────────────────────────────────────────────────────────────────
export const PORTAL_CATEGORIES = [["CORRECTIVE", "Something is broken"], ["EMERGENCY", "Emergency"], ["OTHER", "Something else"]] as const
export const PORTAL_STATUS: Record<string, { label: string; tone: Tone }> = {
  REQUESTED: { label: "Received", tone: "info" }, REJECTED: { label: "Declined", tone: "neutral" }, OPEN: { label: "Accepted", tone: "info" }, ASSIGNED: { label: "Scheduled", tone: "info" },
  IN_PROGRESS: { label: "In progress", tone: "warning" }, ON_HOLD: { label: "On hold", tone: "warning" }, PENDING_VERIFICATION: { label: "In progress", tone: "warning" }, COMPLETED: { label: "Completed", tone: "success" }, CANCELLED: { label: "Cancelled", tone: "neutral" },
}
export interface RaiseForm { siteId: string; assetId: string; category: string; description: string; safetyImpact: boolean; contactName: string }
export const emptyRaise = (): RaiseForm => ({ siteId: "", assetId: "", category: "CORRECTIVE", description: "", safetyImpact: false, contactName: "" })
export function raiseProblem(f: RaiseForm): string | null {
  if (!f.siteId) return "Choose the site"
  const n = f.description.trim().length
  if (n < 10) return "Please describe the problem in a little more detail (at least 10 characters)"
  if (n > 2000) return "Keep the description under 2000 characters"
  return null
}
export const raiseBody = (f: RaiseForm) => ({ siteId: f.siteId, assetId: t(f.assetId), category: f.category, description: f.description.trim(), safetyImpact: f.safetyImpact, contactName: t(f.contactName) })
/** Only a request still waiting for staff can be withdrawn. The server also checks that you submitted it. */
export const canWithdraw = (status: string) => status === "REQUESTED"
export const declineProblem = (note: string): string | null => !note.trim() ? "Tell us why you are declining" : note.trim().length > 1000 ? "Keep it under 1000 characters" : null
export const portalOwing = (s: { outstandingBalance: number | null; overdueBalance: number | null; overdueInvoices: number } | null | undefined) =>
  !s ? null : { owing: s.outstandingBalance ?? 0, overdue: s.overdueBalance ?? 0, overdueCount: s.overdueInvoices }
