// Legal Practice front end: what the forms offer, what each record may do in each status, and the form checks. The checks mirror
// the server's own so people see the reason before pressing a button; the server still decides.

export const MATTER_TYPES = [["LITIGATION", "Litigation"], ["CONVEYANCING", "Conveyancing"], ["ESTATES", "Estates"], ["CORPORATE_COMMERCIAL", "Corporate and commercial"], ["FAMILY_LAW", "Family law"], ["CRIMINAL_DEFENSE", "Criminal defence"], ["LABOUR", "Labour"], ["OTHER", "Other"]] as const
export const BILLING_TYPES = [["HOURLY", "Hourly"], ["FIXED_FEE", "Fixed fee"]] as const
export const CLIENT_TYPES = [["INDIVIDUAL", "Individual"], ["ENTITY", "Company or other entity"]] as const
export const ROLES = [["PRINCIPAL", "Principal"], ["PARTNER", "Partner"], ["ASSOCIATE", "Associate"], ["CANDIDATE_ATTORNEY", "Candidate attorney"], ["CONSULTANT", "Consultant"]] as const
export const DATE_TYPES = [["COURT_DATE", "Court date"], ["PRESCRIPTION_DEADLINE", "Prescription deadline"], ["FILING_DEADLINE", "Filing deadline"], ["LIMITATION_PERIOD", "Limitation period"], ["OTHER", "Other"]] as const
export const labelOf = (pairs: readonly (readonly [string, string])[], v: string | null | undefined) => pairs.find(p => p[0] === v)?.[1] ?? (v ? v.replace(/_/g, " ").toLowerCase().replace(/^./, c => c.toUpperCase()) : "—")
export type Tone = "neutral" | "info" | "success" | "warning" | "danger"

export const MATTER_STATUS: Record<string, { label: string; tone: Tone }> = { OPEN: { label: "Open", tone: "success" }, ON_HOLD: { label: "On hold", tone: "warning" }, CLOSED: { label: "Closed", tone: "neutral" }, ARCHIVED: { label: "Archived", tone: "neutral" } }
export const INVOICE_STATUS: Record<string, { label: string; tone: Tone }> = { DRAFT: { label: "Draft", tone: "neutral" }, SENT: { label: "Sent", tone: "info" }, PARTIALLY_PAID: { label: "Part paid", tone: "warning" }, PAID: { label: "Paid", tone: "success" }, WRITTEN_OFF: { label: "Written off", tone: "danger" } }
export const WORK_STATUS: Record<string, { label: string; tone: Tone }> = { UNBILLED: { label: "Unbilled", tone: "warning" }, BILLED: { label: "Billed", tone: "success" }, WRITTEN_OFF: { label: "Written off", tone: "neutral" }, NON_BILLABLE: { label: "Not billable", tone: "neutral" } }
export const KEYDATE_STATUS: Record<string, { label: string; tone: Tone }> = { PENDING: { label: "Pending", tone: "info" }, COMPLETED: { label: "Done", tone: "success" }, MISSED: { label: "Missed", tone: "danger" } }

export type MatterAction = "edit" | "hold" | "reopen" | "close" | "archive"
export function matterActions(status: string): MatterAction[] {
  switch (status) {
    case "OPEN": return ["edit", "hold", "close"]
    case "ON_HOLD": return ["edit", "reopen", "close"]
    case "CLOSED": return ["reopen", "archive"]
    default: return []
  }
}
/** Time, disbursements and key dates can be added while the matter is not closed or archived. */
export const canLogWork = (status: string) => status === "OPEN" || status === "ON_HOLD"
/** Only unbilled work can be corrected or written off. */
export const canChangeWork = (status: string) => status === "UNBILLED"

const DAY = 86_400_000
const asDate = (d: string) => new Date(d.length === 10 ? d + "T00:00:00" : d)
export function daysUntil(due: string | null | undefined, today = new Date()): number | null {
  if (!due) return null
  const t = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime()
  return Math.round((asDate(due).getTime() - t) / DAY)
}
/** A pending deadline is flagged when it is close or past; finished ones are not. */
export function keyDateNote(status: string, due: string, today = new Date()): { text: string; tone: Tone } | null {
  if (status !== "PENDING") return null
  const d = daysUntil(due, today)
  if (d == null) return null
  if (d < 0) return { text: `${-d} day${-d === 1 ? "" : "s"} overdue`, tone: "danger" }
  if (d === 0) return { text: "Due today", tone: "danger" }
  if (d <= 14) return { text: `Due in ${d} day${d === 1 ? "" : "s"}`, tone: d <= 3 ? "danger" : "warning" }
  return null
}

const num = (s: string) => (s.trim() === "" ? null : Number(s))
const t = (s: string) => s.trim() || null
const bad = (n: number | null) => n != null && (!Number.isFinite(n) || n < 0)

export interface MatterForm { clientId: string; attorneyId: string; matterNumber: string; matterType: string; matterName: string; description: string; billingType: string; fixedFeeAmount: string; openedDate: string; notes: string }
export const emptyMatter = (): MatterForm => ({ clientId: "", attorneyId: "", matterNumber: "", matterType: "LITIGATION", matterName: "", description: "", billingType: "HOURLY", fixedFeeAmount: "", openedDate: "", notes: "" })
export function matterProblem(f: MatterForm, creating: boolean): string | null {
  if (creating && !f.clientId) return "Choose the client"
  if (!f.attorneyId) return "Choose the responsible attorney"
  if (creating && !f.matterNumber.trim()) return "Enter the matter number"
  if (!f.matterName.trim()) return "Enter the matter name"
  if (f.billingType === "FIXED_FEE") { const n = num(f.fixedFeeAmount); if (n == null || !Number.isFinite(n) || n <= 0) return "A fixed fee matter needs a fee above 0" }
  return null
}
export function matterRequest(f: MatterForm, creating: boolean) {
  return {
    ...(creating ? { clientId: f.clientId, matterNumber: f.matterNumber.trim(), matterType: f.matterType, openedDate: t(f.openedDate) } : {}),
    attorneyId: f.attorneyId, matterName: f.matterName.trim(), description: t(f.description), billingType: f.billingType,
    fixedFeeAmount: f.billingType === "FIXED_FEE" ? num(f.fixedFeeAmount) : null, notes: t(f.notes),
  }
}

export interface TimeForm { attorneyId: string; entryDate: string; hours: string; hourlyRate: string; description: string; billable: boolean }
export const emptyTime = (attorneyId = ""): TimeForm => ({ attorneyId, entryDate: "", hours: "", hourlyRate: "", description: "", billable: true })
export function timeProblem(f: TimeForm, editing: boolean, attorneyRate: number | null | undefined): string | null {
  if (!editing && !f.attorneyId) return "Choose who did the work"
  const h = num(f.hours)
  if (h == null || !Number.isFinite(h) || h <= 0) return "Enter the hours (above 0)"
  if (h > 24) return "A single entry cannot be more than 24 hours"
  const r = num(f.hourlyRate)
  if (bad(r)) return "The rate must be 0 or more"
  if (r == null && (editing || attorneyRate == null)) return editing ? "Enter the hourly rate" : "This attorney has no default rate, so enter one"
  if (!f.description.trim()) return "Describe the work"
  return null
}
export const timeRequest = (f: TimeForm, editing: boolean) => editing
  ? { entryDate: t(f.entryDate), hours: num(f.hours), hourlyRate: num(f.hourlyRate), description: f.description.trim() }
  : { attorneyId: f.attorneyId, entryDate: t(f.entryDate), hours: num(f.hours), hourlyRate: num(f.hourlyRate), description: f.description.trim(), billable: f.billable }

export interface DisbForm { disbursementDate: string; description: string; amount: string; paidFromTrust: boolean }
export const emptyDisb = (): DisbForm => ({ disbursementDate: "", description: "", amount: "", paidFromTrust: false })
export function disbProblem(f: DisbForm): string | null {
  if (!f.description.trim()) return "Describe the expense"
  const a = num(f.amount)
  if (a == null || !Number.isFinite(a) || a <= 0) return "Enter the amount (above 0)"
  return null
}
export const disbRequest = (f: DisbForm, editing: boolean) => ({ disbursementDate: t(f.disbursementDate), description: f.description.trim(), amount: num(f.amount), ...(editing ? {} : { paidFromTrust: f.paidFromTrust }) })

export interface KeyDateForm { dateType: string; dueDate: string; description: string; notes: string }
export const emptyKeyDate = (): KeyDateForm => ({ dateType: "COURT_DATE", dueDate: "", description: "", notes: "" })
export function keyDateProblem(f: KeyDateForm): string | null {
  if (!f.dueDate) return "Enter the date"
  if (!f.description.trim()) return "Describe what is due"
  return null
}
export const keyDateRequest = (f: KeyDateForm) => ({ dateType: f.dateType, dueDate: f.dueDate, description: f.description.trim(), notes: t(f.notes) })

export interface ClientForm { name: string; email: string; phone: string; clientType: string; idOrRegistrationNumber: string; notes: string }
export const emptyClient = (): ClientForm => ({ name: "", email: "", phone: "", clientType: "INDIVIDUAL", idOrRegistrationNumber: "", notes: "" })
export const looksLikeEmail = (s: string) => !s.trim() || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(s.trim())
export function clientProblem(f: ClientForm): string | null {
  if (!f.name.trim()) return "Enter the client's name"
  if (!looksLikeEmail(f.email)) return "That email address does not look right"
  return null
}
export const clientRequest = (f: ClientForm) => ({ name: f.name.trim(), email: t(f.email), phone: t(f.phone), clientType: f.clientType, idOrRegistrationNumber: t(f.idOrRegistrationNumber), notes: t(f.notes) })

export interface AttorneyForm { name: string; email: string; phone: string; role: string; admissionNumber: string; hourlyRate: string }
export const emptyAttorney = (): AttorneyForm => ({ name: "", email: "", phone: "", role: "ASSOCIATE", admissionNumber: "", hourlyRate: "" })
export function attorneyProblem(f: AttorneyForm): string | null {
  if (!f.name.trim()) return "Enter the attorney's name"
  if (!looksLikeEmail(f.email)) return "That email address does not look right"
  if (bad(num(f.hourlyRate))) return "The rate must be 0 or more"
  return null
}
export const attorneyRequest = (f: AttorneyForm, employeeId: string | null) => ({ name: f.name.trim(), email: t(f.email), phone: t(f.phone), role: f.role, admissionNumber: t(f.admissionNumber), hourlyRate: num(f.hourlyRate), employeeId })

export interface RetainerForm { clientId: string; monthlyFee: string; startDate: string; endDate: string; notes: string }
export const emptyRetainer = (): RetainerForm => ({ clientId: "", monthlyFee: "", startDate: "", endDate: "", notes: "" })
export function retainerProblem(f: RetainerForm, creating: boolean): string | null {
  if (creating && !f.clientId) return "Choose the client"
  const m = num(f.monthlyFee)
  if (m == null || !Number.isFinite(m) || m <= 0) return "Enter the monthly fee (above 0)"
  if (f.startDate && f.endDate && f.endDate < f.startDate) return "The end date cannot be before the start date"
  return null
}
export const retainerRequest = (f: RetainerForm, creating: boolean) => ({ ...(creating ? { clientId: f.clientId, startDate: t(f.startDate) } : {}), monthlyFee: num(f.monthlyFee), endDate: t(f.endDate), notes: t(f.notes) })

export interface InvoiceItem { id: string; amount: number }
export interface GenerateForm { dueDate: string; description: string; notes: string; fixedFeeAmount: string }
/** What an invoice would bill before VAT: the chosen time and expenses, or the fixed fee when nothing is chosen. */
export function billable(time: InvoiceItem[], disb: InvoiceItem[], fixedFee: string): number {
  const items = [...time, ...disb].reduce((s, i) => s + i.amount, 0)
  if (items > 0) return Math.round(items * 100) / 100
  const f = num(fixedFee)
  return f != null && Number.isFinite(f) && f > 0 ? f : 0
}
export function generateProblem(time: InvoiceItem[], disb: InvoiceItem[], f: GenerateForm): string | null {
  if (time.length + disb.length === 0 && billable([], [], f.fixedFeeAmount) <= 0) return "Choose the work to bill, or enter a fixed fee"
  if (f.fixedFeeAmount.trim() !== "" && bad(num(f.fixedFeeAmount))) return "The fixed fee must be 0 or more"
  return null
}
export type InvoiceAction = "send" | "pay" | "writeOff"
export function invoiceActions(status: string, isAdmin: boolean): InvoiceAction[] {
  switch (status) {
    case "DRAFT": return ["send"]
    case "SENT":
    case "PARTIALLY_PAID": return isAdmin ? ["pay", "writeOff"] : ["pay"]
    default: return []
  }
}
export function paymentProblem(outstanding: number, amount: string): string | null {
  const n = Number(amount)
  if (amount.trim() === "" || !Number.isFinite(n) || n <= 0) return "Enter an amount above 0"
  if (n > outstanding + 0.005) return "That is more than is outstanding"
  return null
}

export interface ProfileForm { firmName: string; practiceNumber: string; vatNumber: string; contactEmail: string; contactPhone: string; trustBankName: string; trustAccountNumber: string; businessBankName: string; businessAccountNumber: string }
export const emptyProfile = (): ProfileForm => ({ firmName: "", practiceNumber: "", vatNumber: "", contactEmail: "", contactPhone: "", trustBankName: "", trustAccountNumber: "", businessBankName: "", businessAccountNumber: "" })
export function profileProblem(f: ProfileForm): string | null {
  if (!f.firmName.trim()) return "Enter the firm's name"
  if (!looksLikeEmail(f.contactEmail)) return "That email address does not look right"
  if (f.trustAccountNumber.trim() && f.trustAccountNumber.trim() === f.businessAccountNumber.trim() && f.trustBankName.trim() === f.businessBankName.trim()) return "The trust and business accounts cannot be the same account"
  return null
}
export const profileRequest = (f: ProfileForm) => Object.fromEntries(Object.entries(f).map(([k, v]) => [k, k === "firmName" ? v.trim() : t(v)]))
