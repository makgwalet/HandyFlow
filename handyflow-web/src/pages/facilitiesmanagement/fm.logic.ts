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
