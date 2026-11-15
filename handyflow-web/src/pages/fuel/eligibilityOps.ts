// Eligibility set-up helpers: site rules, driver approvals, tanker specs, compartment ratings, changeover rules and cleanings.
export interface SiteRule {
  siteId: string; hasRule: boolean; maxCapacityLitres: number | null; maxGvmKg: number | null; maxLengthM: number | null; maxWidthM: number | null; maxHeightM: number | null
  approvedTypes: string[]; accessNotes: string | null; approvedTankers: { tankerId: string; registration: string }[]; requireApprovedDrivers: boolean; requiredTraining: string | null
}
export interface SiteRuleForm { cap: string; gvm: string; len: string; wid: string; hgt: string; types: string; notes: string; tankerIds: string[]; requireDrivers: boolean; training: string }
export interface DriverApproval { employeeId: string; driverName: string; status: string; inductionDate: string | null; inductionExpiry: string | null; trainingRef: string | null; trainingExpiry: string | null; conditions: string | null; state: string }
export interface DriverForm { employeeId: string; status: string; inductionDate: string; inductionExpiry: string; trainingRef: string; trainingExpiry: string; conditions: string }
export interface CompartmentRating { compartmentId: string; number: number; capacityLitres: number; fuelTypes: string[]; lastCargo: string | null; lastCargoAt: string | null; lastCleanedAt: string | null }
export interface ChangeoverRule { id: string; fromFuel: string; toFuel: string; note: string | null }
export interface Cleaning { id: string; compartmentId: string; cleanedAt: string; method: string | null; certificateRef: string | null; evidenceNote: string | null }

export const APPROVAL_STATUSES: [string, string][] = [["APPROVED", "Approved"], ["RESTRICTED", "Restricted (needs conditions)"], ["SUSPENDED", "Suspended (blocks)"]]
export const STATE_LABEL: Record<string, string> = {
  OK: "Current", RESTRICTED: "Restricted", SUSPENDED: "Suspended", EXPIRED: "Induction expired", NO_EXPIRY: "No induction expiry", TRAINING_MISSING: "Training missing",
  TRAINING_EXPIRED: "Training expired", TRAINING_NO_EXPIRY: "No training expiry",
}
/** Anything other than Current or Restricted stops, or needs checking, at allocation. */
export const stateBlocks = (s: string) => ["SUSPENDED", "EXPIRED", "TRAINING_MISSING", "TRAINING_EXPIRED"].includes(s)

const optNum = (s: string): number | null => (s.trim() === "" ? null : Number(s))
const bad = (s: string) => s.trim() !== "" && (Number.isNaN(Number(s)) || Number(s) <= 0)

export function emptySiteForm(r?: SiteRule | null): SiteRuleForm {
  const s = (v: number | null | undefined) => (v == null ? "" : String(v))
  return { cap: s(r?.maxCapacityLitres), gvm: s(r?.maxGvmKg), len: s(r?.maxLengthM), wid: s(r?.maxWidthM), hgt: s(r?.maxHeightM), types: (r?.approvedTypes ?? []).join(", "),
    notes: r?.accessNotes ?? "", tankerIds: (r?.approvedTankers ?? []).map(t => t.tankerId), requireDrivers: r?.requireApprovedDrivers ?? false, training: r?.requiredTraining ?? "" }
}
export function validateSiteForm(f: SiteRuleForm): string | null {
  for (const [label, v] of [["Maximum capacity", f.cap], ["Maximum gross vehicle mass", f.gvm], ["Maximum length", f.len], ["Maximum width", f.wid], ["Maximum height", f.hgt]] as const) {
    if (bad(v)) return `${label} must be a number above zero, or left empty for no limit.`
  }
  if (f.gvm.trim() !== "" && !Number.isInteger(Number(f.gvm))) return "Maximum gross vehicle mass is a whole number of kilograms."
  if (f.training.trim() !== "" && !f.requireDrivers) return "Required training only applies when approved drivers are required."
  return null
}
export function toSiteRequest(f: SiteRuleForm) {
  return { maxCapacityLitres: optNum(f.cap), maxGvmKg: optNum(f.gvm), maxLengthM: optNum(f.len), maxWidthM: optNum(f.wid), maxHeightM: optNum(f.hgt),
    approvedTypes: f.types.split(/[,\n]+/).map(t => t.trim().toUpperCase()).filter(Boolean), accessNotes: f.notes.trim() || null, approvedTankerIds: f.tankerIds,
    requireApprovedDrivers: f.requireDrivers, requiredTraining: f.training.trim() || null }
}
/** "Nothing restricts this site" in words, so an empty form is not mistaken for a strict one. */
export function describeSiteRule(f: SiteRuleForm): string {
  const parts: string[] = []
  if (f.tankerIds.length) parts.push(`only ${f.tankerIds.length} listed tanker${f.tankerIds.length === 1 ? "" : "s"}`)
  if (f.cap.trim()) parts.push(`at most ${f.cap} L`)
  if (f.gvm.trim()) parts.push(`at most ${f.gvm} kg gross`)
  if (f.len.trim() || f.wid.trim() || f.hgt.trim()) parts.push("size limits")
  if (f.types.trim()) parts.push(`types ${f.types.trim()}`)
  if (f.requireDrivers) parts.push("approved drivers only")
  return parts.length ? `This site allows ${parts.join(", ")}.` : "Nothing is restricted at this site."
}

export const emptyDriverForm = (): DriverForm => ({ employeeId: "", status: "APPROVED", inductionDate: "", inductionExpiry: "", trainingRef: "", trainingExpiry: "", conditions: "" })
export function fromApproval(a: DriverApproval): DriverForm {
  return { employeeId: a.employeeId, status: a.status, inductionDate: a.inductionDate ?? "", inductionExpiry: a.inductionExpiry ?? "", trainingRef: a.trainingRef ?? "", trainingExpiry: a.trainingExpiry ?? "", conditions: a.conditions ?? "" }
}
export function validateDriverForm(f: DriverForm): string | null {
  if (!f.employeeId) return "Choose the driver."
  if (f.status !== "SUSPENDED" && !f.inductionDate) return "Give the induction date."
  if (f.inductionDate && f.inductionExpiry && f.inductionExpiry < f.inductionDate) return "The induction cannot expire before it was done."
  if (f.trainingExpiry && !f.trainingRef.trim()) return "Give the training reference that the expiry belongs to."
  if (f.status === "RESTRICTED" && f.conditions.trim().length < 3) return "Say what the restriction is."
  if (f.status === "SUSPENDED" && f.conditions.trim().length < 3) return "Say why the driver is suspended."
  return null
}
export function toDriverRequest(f: DriverForm) {
  const d = (s: string) => s || null
  return { status: f.status, inductionDate: d(f.inductionDate), inductionExpiry: d(f.inductionExpiry), trainingRef: f.trainingRef.trim() || null, trainingExpiry: d(f.trainingExpiry), conditions: f.conditions.trim() || null }
}

export interface SpecsForm { type: string; gvm: string; len: string; wid: string; hgt: string }
export function validateSpecs(f: SpecsForm): string | null {
  for (const [label, v] of [["Gross vehicle mass", f.gvm], ["Length", f.len], ["Width", f.wid], ["Height", f.hgt]] as const) if (bad(v)) return `${label} must be a number above zero, or left empty.`
  if (f.gvm.trim() !== "" && !Number.isInteger(Number(f.gvm))) return "Gross vehicle mass is a whole number of kilograms."
  return null
}
export const toSpecsRequest = (f: SpecsForm) => ({ tankerType: f.type.trim().toUpperCase() || null, gvmKg: optNum(f.gvm), lengthM: optNum(f.len), widthM: optNum(f.wid), heightM: optNum(f.hgt) })

export const parseProducts = (text: string): string[] => [...new Set(text.split(/[,\s]+/).map(t => t.trim().toUpperCase()).filter(Boolean))]
export const ratingText = (c: CompartmentRating) => (c.fuelTypes.length ? `Rated for ${c.fuelTypes.join(", ")}` : "Not rated (carries anything)")

export function validateChangeover(from: string, to: string): string | null {
  if (!from.trim() || !to.trim()) return "Give both products."
  if (from.trim().toUpperCase() === to.trim().toUpperCase()) return "A changeover is from one product to a different one."
  return null
}
export function validateCleaning(cleanedAt: string, cert: string, evidence: string, now = new Date()): string | null {
  if (!cleanedAt) return "Give when it was cleaned."
  if (new Date(cleanedAt).getTime() > now.getTime() + 5 * 60_000) return "A cleaning cannot be dated in the future."
  if (!cert.trim() && !evidence.trim()) return "Give the certificate reference, or describe the evidence."
  return null
}
