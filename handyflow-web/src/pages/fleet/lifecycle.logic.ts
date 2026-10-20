// Vehicle lifecycle rules for the screen (mirrors the server's table) and the request bodies for theft, recovery, write-off and disposal.
export const LIFECYCLE_LABEL: Record<string, string> = {
  ORDERED: "Ordered", ACQUIRED: "Acquired", ACTIVE: "Active", STOLEN: "Stolen", MISSING: "Missing", IMPOUNDED: "Impounded",
  WRITTEN_OFF: "Written off", SOLD: "Sold", TRANSFERRED: "Transferred", RETIRED: "Retired", DISPOSED: "Disposed",
}
const NEXT: Record<string, string[]> = {
  ORDERED: ["ACQUIRED", "RETIRED"], ACQUIRED: ["ACTIVE", "RETIRED"],
  ACTIVE: ["STOLEN", "MISSING", "IMPOUNDED", "WRITTEN_OFF", "SOLD", "TRANSFERRED", "RETIRED"],
  STOLEN: ["ACTIVE", "WRITTEN_OFF"], MISSING: ["ACTIVE", "STOLEN", "WRITTEN_OFF"], IMPOUNDED: ["ACTIVE", "WRITTEN_OFF", "DISPOSED"],
  WRITTEN_OFF: ["DISPOSED"], RETIRED: ["SOLD", "TRANSFERRED", "DISPOSED"], SOLD: [], TRANSFERRED: [], DISPOSED: [],
}
export type Action = "TRANSITION" | "THEFT" | "RECOVERY" | "WRITE_OFF" | "DISPOSAL"
export interface ActionOption { action: Action; label: string; target?: string; adminOnly: boolean }

/** The things that can be done to a vehicle in this lifecycle state. Write-off and disposal need FLEET_ADMIN, like the server. */
export function actionsFor(status: string): ActionOption[] {
  const next = NEXT[status] ?? []
  const out: ActionOption[] = []
  for (const t of next) {
    if (t === "ACTIVE" && (status === "STOLEN" || status === "MISSING")) out.push({ action: "RECOVERY", label: "Record recovery", adminOnly: false })
    else if (t === "STOLEN" && status === "ACTIVE") out.push({ action: "THEFT", label: "Report stolen or missing", adminOnly: false })
    else if (t === "MISSING" && status === "ACTIVE") continue
    else if (t === "STOLEN") out.push({ action: "THEFT", label: "Report as stolen", target: "STOLEN", adminOnly: false })
    else if (t === "MISSING") continue
    else if (t === "WRITTEN_OFF") out.push({ action: "WRITE_OFF", label: "Write off", adminOnly: true })
    else if (t === "SOLD" || t === "TRANSFERRED" || t === "DISPOSED") out.push({ action: "DISPOSAL", label: t === "SOLD" ? "Sell" : t === "TRANSFERRED" ? "Transfer" : "Dispose", target: t, adminOnly: true })
    else out.push({ action: "TRANSITION", label: `Mark ${LIFECYCLE_LABEL[t].toLowerCase()}`, target: t, adminOnly: false })
  }
  return out
}

const num = (v: string) => (v.trim() === "" ? null : Number(v))
const bad = (v: string) => v.trim() !== "" && !(Number(v) >= 0)

export interface TheftForm { kind: string; occurredAt: string; location: string; policeCaseNumber: string; sapsStation: string; insurer: string; claimNumber: string }
export const emptyTheft = (): TheftForm => ({ kind: "STOLEN", occurredAt: "", location: "", policeCaseNumber: "", sapsStation: "", insurer: "", claimNumber: "" })
export const theftProblem = (f: TheftForm) => (!f.occurredAt ? "Say when it was stolen or last seen." : null)
export const theftBody = (f: TheftForm) => ({
  kind: f.kind, occurredAt: new Date(f.occurredAt).toISOString(), location: f.location.trim() || null, policeCaseNumber: f.policeCaseNumber.trim() || null,
  sapsStation: f.sapsStation.trim() || null, insurer: f.insurer.trim() || null, claimNumber: f.claimNumber.trim() || null,
})

export interface RecoveryForm { recoveryStatus: string; recoveredAt: string; location: string; odometer: string }
export const emptyRecovery = (): RecoveryForm => ({ recoveryStatus: "RECOVERED", recoveredAt: "", location: "", odometer: "" })
export const recoveryProblem = (f: RecoveryForm) => (!f.recoveredAt ? "Say when it was recovered." : bad(f.odometer) ? "The odometer must be zero or more." : null)
export const recoveryBody = (f: RecoveryForm) => ({ recoveryStatus: f.recoveryStatus, recoveredAt: new Date(f.recoveredAt).toISOString(), location: f.location.trim() || null, odometer: num(f.odometer) })

export interface WriteOffForm { decisionDate: string; assessorName: string; salvageValue: string; insuranceSettlementAmount: string; accidentId: string }
export const emptyWriteOff = (): WriteOffForm => ({ decisionDate: "", assessorName: "", salvageValue: "", insuranceSettlementAmount: "", accidentId: "" })
export function writeOffProblem(f: WriteOffForm) {
  if (!f.decisionDate) return "Say when the write-off was decided."
  if (bad(f.salvageValue) || bad(f.insuranceSettlementAmount)) return "Amounts must be zero or more."
  return null
}
export const writeOffBody = (f: WriteOffForm) => ({ decisionDate: f.decisionDate, assessorName: f.assessorName.trim() || null, salvageValue: num(f.salvageValue), insuranceSettlementAmount: num(f.insuranceSettlementAmount), accidentId: f.accidentId || null })

export interface DisposalForm { target: string; counterpartyName: string; disposalDate: string; salePrice: string; odometerAtDisposal: string; financeCleared: boolean; registrationTransferred: boolean; accountingReference: string }
export const emptyDisposal = (target: string): DisposalForm => ({ target, counterpartyName: "", disposalDate: "", salePrice: "", odometerAtDisposal: "", financeCleared: false, registrationTransferred: false, accountingReference: "" })
/** A sale needs a buyer and a price; a sale or transfer needs a counterparty. Finance clearing is checked by the server (it knows if the vehicle is financed). */
export function disposalProblem(f: DisposalForm) {
  if (!f.disposalDate) return "Say the date of the sale or disposal."
  if ((f.target === "SOLD" || f.target === "TRANSFERRED") && !f.counterpartyName.trim()) return f.target === "SOLD" ? "Who bought it?" : "Who is it transferred to?"
  if (f.target === "SOLD" && f.salePrice.trim() === "") return "Enter the sale price."
  if (bad(f.salePrice) || bad(f.odometerAtDisposal)) return "Amounts must be zero or more."
  return null
}
export const disposalBody = (f: DisposalForm) => ({
  target: f.target, counterpartyName: f.counterpartyName.trim() || null, disposalDate: f.disposalDate, salePrice: num(f.salePrice), odometerAtDisposal: num(f.odometerAtDisposal),
  financeCleared: f.financeCleared, registrationTransferred: f.registrationTransferred, accountingReference: f.accountingReference.trim() || null,
})

export function gainLossText(g: number | null | undefined): string {
  if (g == null) return "—"
  const r = `R ${Math.abs(Math.round(g)).toLocaleString("en-ZA")}`
  return g > 0 ? `Gain ${r}` : g < 0 ? `Loss ${r}` : "No gain or loss"
}

// ── Editing an open theft report and a write-off, and evidence on any lifecycle event ──────────────────
export interface TheftData { kind: string; occurredAt: string; location: string | null; policeCaseNumber: string | null; sapsStation: string | null; policeReference: string | null; insurer: string | null; claimNumber: string | null; trackingInfo: string | null; notes: string | null; recoveryStatus: string | null }
export interface TheftEditForm { location: string; policeCaseNumber: string; sapsStation: string; policeReference: string; insurer: string; claimNumber: string; trackingInfo: string; notes: string }
const t = (v: string | number | null | undefined) => (v == null ? "" : String(v))
export const toTheftEdit = (d: TheftData): TheftEditForm => ({ location: t(d.location), policeCaseNumber: t(d.policeCaseNumber), sapsStation: t(d.sapsStation), policeReference: t(d.policeReference), insurer: t(d.insurer), claimNumber: t(d.claimNumber), trackingInfo: t(d.trackingInfo), notes: t(d.notes) })
/** The server replaces every detail, so blanks are sent as null to clear them. */
export const theftEditBody = (f: TheftEditForm) => Object.fromEntries(Object.entries(f).map(([k, v]) => [k, v.trim() === "" ? null : v.trim()])) as Record<keyof TheftEditForm, string | null>
/** A claim number with no insurer (or the reverse) is a half-filled claim; a case number needs its station for SAPS follow-up. */
export function theftEditProblem(f: TheftEditForm): string | null {
  if (f.claimNumber.trim() && !f.insurer.trim()) return "Name the insurer the claim was lodged with."
  if (f.policeCaseNumber.trim() && !f.sapsStation.trim()) return "Add the SAPS station that opened the case."
  return null
}

export interface WriteOffData { assessmentDate: string | null; assessorName: string | null; decisionDate: string; decisionNote: string | null; preAccidentValue: number | null; salvageValue: number | null; salvageBuyer: string | null; salvageSoldAt: string | null; insuranceSettlementAmount: number | null; insuranceSettledAt: string | null }
export interface WriteOffEditForm { assessmentDate: string; assessorName: string; decisionDate: string; decisionNote: string; preAccidentValue: string; salvageValue: string; salvageBuyer: string; salvageSoldAt: string; insuranceSettlementAmount: string; insuranceSettledAt: string }
export const toWriteOffEdit = (w: WriteOffData): WriteOffEditForm => ({ assessmentDate: t(w.assessmentDate), assessorName: t(w.assessorName), decisionDate: t(w.decisionDate), decisionNote: t(w.decisionNote), preAccidentValue: t(w.preAccidentValue), salvageValue: t(w.salvageValue), salvageBuyer: t(w.salvageBuyer), salvageSoldAt: t(w.salvageSoldAt), insuranceSettlementAmount: t(w.insuranceSettlementAmount), insuranceSettledAt: t(w.insuranceSettledAt) })
const AMOUNTS: [keyof WriteOffEditForm, string][] = [["preAccidentValue", "Pre-accident value"], ["salvageValue", "Salvage value"], ["insuranceSettlementAmount", "Insurance settlement"]]
export function writeOffEditProblem(f: WriteOffEditForm): string | null {
  if (!f.decisionDate) return "Say when the write-off was decided."
  for (const [k, label] of AMOUNTS) if (f[k].trim() !== "" && !(Number(f[k]) >= 0)) return `${label} must be zero or more.`
  if (f.salvageBuyer.trim() && f.salvageValue.trim() === "") return "Enter the salvage value the buyer paid."
  if (f.salvageSoldAt && f.salvageValue.trim() === "") return "Enter the salvage value, or clear the salvage sale date."
  if (f.insuranceSettledAt && f.insuranceSettlementAmount.trim() === "") return "Enter the settlement amount, or clear the settlement date."
  return null
}
export const writeOffEditBody = (f: WriteOffEditForm) => ({
  assessmentDate: f.assessmentDate || null, assessorName: f.assessorName.trim() || null, decisionDate: f.decisionDate, decisionNote: f.decisionNote.trim() || null,
  preAccidentValue: num(f.preAccidentValue), salvageValue: num(f.salvageValue), salvageBuyer: f.salvageBuyer.trim() || null, salvageSoldAt: f.salvageSoldAt || null,
  insuranceSettlementAmount: num(f.insuranceSettlementAmount), insuranceSettledAt: f.insuranceSettledAt || null,
})
/** One line on what the write-off recovered: salvage and the insurer's settlement against the pre-accident value. */
export function writeOffSummary(w: WriteOffData): string {
  const R = (n: number | null) => (n == null ? "—" : `R ${Math.round(n).toLocaleString("en-ZA")}`)
  return `Pre-accident ${R(w.preAccidentValue)} · Salvage ${R(w.salvageValue)} · Settlement ${R(w.insuranceSettlementAmount)}`
}

export const EVIDENCE_TYPES = ["POLICE_REPORT", "SALE_AGREEMENT", "PROOF_OF_PAYMENT", "FINANCE_SETTLEMENT_LETTER", "REGISTRATION_TRANSFER", "ASSESSOR_REPORT", "INSURANCE_SETTLEMENT", "TRACKING_REPORT", "PHOTO", "OTHER"]
export const EVIDENCE_LABEL: Record<string, string> = { POLICE_REPORT: "Police report", SALE_AGREEMENT: "Sale agreement", PROOF_OF_PAYMENT: "Proof of payment", FINANCE_SETTLEMENT_LETTER: "Finance settlement letter", REGISTRATION_TRANSFER: "Registration transfer", ASSESSOR_REPORT: "Assessor report", INSURANCE_SETTLEMENT: "Insurance settlement", TRACKING_REPORT: "Tracking report", PHOTO: "Photo", OTHER: "Other" }
export const suggestedEvidenceType = (eventType: string): string => ({ THEFT_REPORTED: "POLICE_REPORT", WRITE_OFF: "ASSESSOR_REPORT", DISPOSAL: "SALE_AGREEMENT", RECOVERED: "PHOTO" } as Record<string, string>)[eventType] ?? ""
