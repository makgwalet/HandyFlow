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

export interface MatterForm { clientId: string; attorneyId: string; matterNumber: string; matterType: string; matterName: string; description: string; billingType: string; fixedFeeAmount: string; openedDate: string; notes: string; conflictCheckId: string }
export const emptyMatter = (): MatterForm => ({ clientId: "", attorneyId: "", matterNumber: "", matterType: "LITIGATION", matterName: "", description: "", billingType: "HOURLY", fixedFeeAmount: "", openedDate: "", notes: "", conflictCheckId: "" })
export function matterProblem(f: MatterForm, creating: boolean): string | null {
  if (creating && !f.clientId) return "Choose the client"
  if (creating && !f.conflictCheckId) return "Run a conflict check for this client first"
  if (!f.attorneyId) return "Choose the responsible attorney"
  if (creating && !f.matterNumber.trim()) return "Enter the matter number"
  if (!f.matterName.trim()) return "Enter the matter name"
  if (f.billingType === "FIXED_FEE") { const n = num(f.fixedFeeAmount); if (n == null || !Number.isFinite(n) || n <= 0) return "A fixed fee matter needs a fee above 0" }
  return null
}
export function matterRequest(f: MatterForm, creating: boolean) {
  return {
    ...(creating ? { clientId: f.clientId, matterNumber: f.matterNumber.trim(), matterType: f.matterType, openedDate: t(f.openedDate), conflictCheckId: f.conflictCheckId } : {}),
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

// ── Trust account ────────────────────────────────────────────────────────────────────────────────────────────────────
// Money held for a client. A receipt adds to the balance straight away. The other three take money out: they are REQUESTED here
// and only move once a second person approves (see the Approvals section), and can never take more than is held.
export type TrustKind = "receipt" | "transfer" | "disbursement" | "refund"
export const TRUST_KINDS: Record<TrustKind, { label: string; path: string; paymentType: string | null; takesOut: boolean }> = {
  receipt: { label: "Money received", path: "trust-transactions/receipt", paymentType: null, takesOut: false },
  transfer: { label: "Request: move to the firm's account", path: "trust-payment-requests", paymentType: "TRANSFER_TO_BUSINESS", takesOut: true },
  disbursement: { label: "Request: pay out on the client's behalf", path: "trust-payment-requests", paymentType: "DISBURSEMENT_PAYMENT", takesOut: true },
  refund: { label: "Request: refund to the client", path: "trust-payment-requests", paymentType: "REFUND", takesOut: true },
}
export const TRUST_TYPE: Record<string, { label: string; tone: Tone }> = {
  RECEIPT: { label: "Received", tone: "success" }, TRANSFER_TO_BUSINESS: { label: "Moved to firm", tone: "info" },
  DISBURSEMENT_PAYMENT: { label: "Paid out", tone: "warning" }, REFUND: { label: "Refunded", tone: "neutral" },
}
/** Which trust actions this person sees: receipts if they may record money in, requests if they may ask for money out. */
export const trustKindsFor = (canReceive: boolean, canRequest: boolean): TrustKind[] =>
  (Object.keys(TRUST_KINDS) as TrustKind[]).filter(k => (TRUST_KINDS[k].takesOut ? canRequest : canReceive))
export interface TrustForm { amount: string; transactionDate: string; reference: string; notes: string; payee: string; invoiceId: string; matterId: string }
export const emptyTrust = (): TrustForm => ({ amount: "", transactionDate: "", reference: "", notes: "", payee: "", invoiceId: "", matterId: "" })
/** Invoices a trust transfer can pay: only those that are sent and still owe something. */
export const payableByTrust = (status: string, outstanding: number) => (status === "SENT" || status === "PARTIALLY_PAID") && outstanding > 0.005
export function trustProblem(kind: TrustKind, f: TrustForm, balance: number, invoiceOutstanding?: number): string | null {
  const n = num(f.amount)
  if (n == null || !Number.isFinite(n) || n <= 0) return "Enter an amount above 0"
  if (Math.round(n * 100) / 100 !== n) return "Use at most two decimal places"
  if (TRUST_KINDS[kind].takesOut && n > balance + 0.005) return "That is more than the client has in trust"
  if (kind === "transfer") {
    if (!f.invoiceId) return "Choose the invoice this pays"
    if (invoiceOutstanding != null && n > invoiceOutstanding + 0.005) return "That is more than the invoice still owes"
  }
  if ((kind === "disbursement" || kind === "refund") && !f.payee.trim()) return kind === "refund" ? "Say who the refund is paid to" : "Say who is being paid"
  return null
}
export function trustRequest(kind: TrustKind, f: TrustForm, idempotencyKey?: string) {
  const base = { ...(TRUST_KINDS[kind].paymentType ? { paymentType: TRUST_KINDS[kind].paymentType } : {}), amount: Number(f.amount), transactionDate: t(f.transactionDate), reference: t(f.reference), notes: t(f.notes), matterId: t(f.matterId), ...(idempotencyKey ? { idempotencyKey } : {}) }
  if (kind === "transfer") return { ...base, invoiceId: f.invoiceId }
  if (kind === "disbursement" || kind === "refund") return { ...base, payee: f.payee.trim() }
  return base
}
/**
 * One key per attempt. Sending the same request again (a double click, a retry after a timeout) reuses the key, so the server posts it once.
 * Changing anything in the form makes it a different request, so it gets a fresh key instead of being refused as a clash.
 */
export interface Attempt { sig: string; key: string }
export function nextAttempt(prev: Attempt | null, kind: TrustKind, f: TrustForm, newKey: () => string): Attempt {
  const sig = JSON.stringify([kind, trustRequest(kind, f)])
  return prev && prev.sig === sig ? prev : { sig, key: newKey() }
}

// ── Approving payments out of trust ──────────────────────────────────────────────────────────────────────────────────
// One person asks, a different person approves. A firm with a single attorney can switch on a recorded exception: that person may
// approve their own request, but must say why each time, and the request is marked as self-approved.
export const REQUEST_STATUS: Record<string, { label: string; tone: Tone }> = {
  PENDING: { label: "Waiting for approval", tone: "warning" }, APPROVED: { label: "Approved and paid", tone: "success" },
  REJECTED: { label: "Rejected", tone: "danger" }, CANCELLED: { label: "Withdrawn", tone: "neutral" },
}
export const REQUEST_TYPE: Record<string, string> = { TRANSFER_TO_BUSINESS: "Move to the firm's account", DISBURSEMENT_PAYMENT: "Pay out for the client", REFUND: "Refund to the client" }
export const MIN_REASON = 10
export const reasonProblem = (text: string): string | null => text.trim().length < MIN_REASON ? `Write at least ${MIN_REASON} characters` : null
export type ApprovalMode = "approve" | "self" | "blocked" | "none"
/** What this person can do with a request: approve it, approve it as an excepted self-approval, or nothing. */
export function approvalMode(r: { status: string; requestedBy: string }, me: string | undefined, selfAllowed: boolean): ApprovalMode {
  if (r.status !== "PENDING" || !me) return "none"
  if (r.requestedBy !== me) return "approve"
  return selfAllowed ? "self" : "blocked"
}
export const canWithdraw = (r: { status: string; requestedBy: string }, me: string | undefined) => r.status === "PENDING" && !!me && r.requestedBy === me

// ── Conflict checks ──────────────────────────────────────────────────────────────────────────────────────────────────
// Before a matter is opened the firm searches the client and the other parties against its clients and earlier matters.
// Nothing found: clear at once. Something found: a second person decides. A check opens one matter, within 30 days.
export interface ConflictHit { subjectName: string; matchKind: string; matchedKind: string; matchedName: string; matchedRole: string | null; matchedDetail: string | null; matchedClientId: string | null; matchedMatterId: string | null }
export interface ConflictCheck { id: string; clientId: string; clientName: string | null; status: string; truncated: boolean; subjects: { name: string; idNumber: string | null; role: string }[]; hits: ConflictHit[]; runByName: string | null; runAt: string; decidedByName: string | null; decidedAt: string | null; decisionReason: string | null; selfReviewed: boolean; validUntil: string | null; matterId: string | null; usable: boolean }
export const CONFLICT_STATUS: Record<string, { label: string; tone: Tone }> = {
  CLEAR: { label: "Clear", tone: "success" }, PENDING_REVIEW: { label: "Needs review", tone: "warning" },
  CLEARED_AFTER_REVIEW: { label: "Cleared after review", tone: "success" }, PROCEED_WITH_CONSENT: { label: "Proceed with consent", tone: "info" },
  DECLINED: { label: "Declined", tone: "danger" },
}
export const PARTY_ROLES: (readonly [string, string])[] = [["OPPOSING_PARTY", "Other side"], ["CO_PARTY", "Co-party"], ["RELATED_PARTY", "Related party"], ["WITNESS", "Witness"], ["OTHER", "Other"]]
export const MATCH_KIND: Record<string, string> = { EXACT_ID: "Same ID or registration number", EXACT_NAME: "Same name", SIMILAR: "Similar name" }
export const REVIEW_OUTCOMES: (readonly [string, string])[] = [["CLEARED_AFTER_REVIEW", "Not a conflict: clear"], ["PROCEED_WITH_CONSENT", "A conflict, but we may act with consent"], ["DECLINED", "Decline to act"]]
export interface PartyDraft { name: string; idNumber: string; role: string }
export const emptyParty = (): PartyDraft => ({ name: "", idNumber: "", role: "OPPOSING_PARTY" })
/** The parties to search: blank rows dropped, text trimmed. The client themselves are always searched by the server. */
export const partiesRequest = (rows: PartyDraft[]) => rows.filter(r => r.name.trim()).map(r => ({ name: r.name.trim(), idNumber: r.idNumber.trim() || null, role: r.role }))
export const decisionProblem = (outcome: string, reason: string): string | null =>
  !outcome ? "Choose an outcome" : reason.trim().length < MIN_REASON ? `Write at least ${MIN_REASON} characters to explain the decision` : null
/** What a person should be told about a check that has just run. */
export function conflictHeadline(c: Pick<ConflictCheck, "status" | "hits" | "truncated">): string {
  if (c.status === "CLEAR") return "No conflicts found. You can open the matter."
  if (c.status === "PENDING_REVIEW") {
    const n = c.hits.length
    const found = n === 0 ? "" : `${n} possible conflict${n === 1 ? "" : "s"} found. `
    const broad = c.truncated ? "The search was too broad to compare everything. " : ""
    return `${found}${broad}A second person must review this before a matter can be opened.`
  }
  if (c.status === "DECLINED") return "The firm declined to act."
  return "Reviewed and cleared. You can open the matter."
}
/** Checks that could open a matter for this client right now, newest first. */
export const usableChecks = (checks: ConflictCheck[], clientId: string) => checks.filter(c => c.clientId === clientId && c.usable).sort((a, b) => b.runAt.localeCompare(a.runAt))

// ── Client portal access ─────────────────────────────────────────────────────────────────────────────────────────────
export const GRANT_STATUS: Record<string, { label: string; tone: Tone }> = { PENDING: { label: "Invite sent", tone: "info" }, ACTIVE: { label: "Has access", tone: "success" }, REVOKED: { label: "Revoked", tone: "neutral" } }
export const canRevokeGrant = (status: string) => status === "PENDING" || status === "ACTIVE"
export function inviteProblem(email: string, existing: { inviteEmail: string; status: string }[]): string | null {
  const e = email.trim().toLowerCase()
  if (!e) return "Enter the email address to invite"
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e)) return "That email address does not look right"
  if (existing.some(g => g.inviteEmail.toLowerCase() === e && canRevokeGrant(g.status))) return "That person has already been invited"
  return null
}

// ── Evidence ─────────────────────────────────────────────────────────────────────────────────────────────────────────
export const EVIDENCE_TYPES = [["DOCUMENT", "Document"], ["CONTRACT", "Contract"], ["CORRESPONDENCE", "Correspondence"], ["COURT_PAPER", "Court paper"], ["ID_DOCUMENT", "ID document"], ["PROOF_OF_PAYMENT", "Proof of payment"], ["OTHER", "Other"]] as const
export const MAX_EVIDENCE_BYTES = 10 * 1024 * 1024
export function evidenceProblem(file: { size: number } | null, evidenceType: string): string | null {
  if (!file) return "Choose a file"
  if (file.size <= 0) return "That file is empty"
  if (file.size > MAX_EVIDENCE_BYTES) return "That file is over 10 MB"
  if (!evidenceType) return "Choose what kind of document it is"
  return null
}
export const fileSize = (b: number) => (b < 1024 ? `${b} B` : b < 1024 * 1024 ? `${Math.round(b / 102.4) / 10} KB` : `${Math.round(b / 104857.6) / 10} MB`)

// ── Dashboard and calendar ───────────────────────────────────────────────────────────────────────────────────────────
const iso = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`
export const isoDay = iso
export const MONTHS = ["January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"]
export const shiftMonth = (y: number, m: number, delta: number) => { const d = new Date(y, m + delta, 1); return { year: d.getFullYear(), month: d.getMonth() } }
/** The weeks shown for a month, Monday first, padded with the neighbouring months' days. */
export function monthGrid(year: number, month: number): { date: string; inMonth: boolean }[][] {
  const first = new Date(year, month, 1), lead = (first.getDay() + 6) % 7
  const start = new Date(year, month, 1 - lead), weeks: { date: string; inMonth: boolean }[][] = []
  for (let w = 0; w < 6; w++) {
    const row = [] as { date: string; inMonth: boolean }[]
    for (let d = 0; d < 7; d++) { const x = new Date(start.getFullYear(), start.getMonth(), start.getDate() + w * 7 + d); row.push({ date: iso(x), inMonth: x.getMonth() === month }) }
    weeks.push(row)
  }
  while (weeks.length > 4 && weeks[weeks.length - 1].every(c => !c.inMonth)) weeks.pop()
  return weeks
}
export const gridRange = (year: number, month: number) => { const g = monthGrid(year, month); return { from: g[0][0].date, to: g[g.length - 1][6].date } }
export function groupByDay<T extends { dueDate: string }>(items: T[]): Record<string, T[]> {
  const out: Record<string, T[]> = {}
  for (const i of items) (out[i.dueDate] ??= []).push(i)
  return out
}
/** The tone of a key date on the calendar: finished ones are quiet, pending ones follow how close they are. */
export function dateTone(status: string, due: string, today = new Date()): Tone {
  if (status === "COMPLETED") return "success"
  if (status === "MISSED") return "neutral"
  const d = daysUntil(due, today)
  return d == null ? "info" : d < 0 ? "danger" : d <= 3 ? "danger" : d <= 14 ? "warning" : "info"
}
export interface DashMatter { status: string }
export interface DashInvoice { status: string; outstandingBalance: number; dueDate: string | null }
export interface DashClient { trustBalance: number; status: string }
export function dashboardStats(matters: DashMatter[], invoices: DashInvoice[], clients: DashClient[], today: string) {
  const byStatus: Record<string, number> = {}
  for (const m of matters) byStatus[m.status] = (byStatus[m.status] ?? 0) + 1
  const owed = invoices.filter(i => ["SENT", "PARTIALLY_PAID"].includes(i.status) && i.outstandingBalance > 0.005)
  const late = owed.filter(i => !!i.dueDate && i.dueDate < today)
  const sum = (xs: DashInvoice[]) => Math.round(xs.reduce((s, i) => s + i.outstandingBalance, 0) * 100) / 100
  return {
    total: matters.length, open: byStatus.OPEN ?? 0, onHold: byStatus.ON_HOLD ?? 0, closed: (byStatus.CLOSED ?? 0) + (byStatus.ARCHIVED ?? 0), byStatus,
    outstanding: sum(owed), overdueCount: late.length, overdue: sum(late), drafts: invoices.filter(i => i.status === "DRAFT").length,
    trustHeld: Math.round(clients.reduce((s, c) => s + (c.trustBalance ?? 0), 0) * 100) / 100,
  }
}
/** Pending deadlines from today on, soonest first, plus anything already overdue at the front. */
export function upcomingDates<T extends { dueDate: string; status: string }>(items: T[], limit = 8): T[] {
  return items.filter(i => i.status === "PENDING").sort((a, b) => a.dueDate.localeCompare(b.dueDate)).slice(0, limit)
}
export const keyDateActions = (status: string, acknowledged: boolean, canWrite: boolean): ("acknowledge" | "complete" | "mark-missed")[] =>
  !canWrite || status !== "PENDING" ? [] : [...(acknowledged ? [] : ["acknowledge" as const]), "complete", "mark-missed"]

// ── Deadline engine ─────────────────────────────────────────────────────────────────────────────────────────────────────

export const DEADLINE_UNITS = [["COURT_DAYS", "Court days"], ["CALENDAR_DAYS", "Calendar days"], ["MONTHS", "Months"], ["YEARS", "Years"]] as const
export const DEADLINE_DIRECTIONS = [["AFTER", "After the trigger date"], ["BEFORE", "Before the trigger date"]] as const
export const DEADLINE_ROLLOVERS = [["NONE", "Stay on the last day"], ["SKIP_SUNDAY_AND_HOLIDAYS", "Move off a Sunday or public holiday"], ["SKIP_NON_COURT_DAYS", "Move to the next court day"]] as const
export const CALENDAR_KINDS = [["PUBLIC_HOLIDAY", "Public holiday (proclaimed)"], ["COURT_CLOSURE", "Court closed"]] as const
export const COURTS = [["HIGH_COURT", "High Court"], ["MAGISTRATES_COURT", "Magistrates' Court"], ["LABOUR", "Labour"], ["OTHER", "Other"]] as const
/** The most a period may be, by unit; the server and the database use the same limits. */
export const MAX_PERIOD: Record<string, number> = { COURT_DAYS: 3660, CALENDAR_DAYS: 3660, MONTHS: 360, YEARS: 30 }
const RECESS_RE = /^(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])$/
const MONTHS_SHORT = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]

export interface DeadlineRule { id: string; code: string; name: string; description: string | null; court: string | null; triggerLabel: string; keyDateType: string; amount: number; unit: string; direction: string; rollover: string; recessStart: string | null; recessEnd: string | null; sourceReference: string | null; active: boolean }
export interface DeadlinePreview { ruleId: string; ruleName: string; triggerLabel: string; triggerDate: string; dueDate: string; dueDayOfWeek: string; explanation: string[] }
export interface DeadlineComputation { id: string; matterId: string; keyDateId: string; supersedesId: string | null; current: boolean; ruleCode: string; ruleName: string; amount: number; unit: string; direction: string; rollover: string; recessStart: string | null; recessEnd: string | null; triggerLabel: string; triggerDate: string; dueDate: string; explanation: string[]; reason: string | null; computedByName: string | null; computedAt: string; keyDateDueDate: string | null; keyDateStatus: string | null; drifted: boolean }
export interface CalendarDay { id: string | null; date: string; kind: string; name: string; source: string }

/** "16 Dec to 15 Jan" from "12-16" and "01-15". */
export function recessLabel(start: string | null | undefined, end: string | null | undefined): string | null {
  if (!start || !end || !RECESS_RE.test(start) || !RECESS_RE.test(end)) return null
  const f = (s: string) => `${Number(s.slice(3))} ${MONTHS_SHORT[Number(s.slice(0, 2)) - 1]}`
  return `${f(start)} to ${f(end)}`
}

/** A rule in a sentence, for lists and for the calculator: "20 court days after the trigger date; 16 Dec to 15 Jan not counted". */
export function describeRule(r: Pick<DeadlineRule, "amount" | "unit" | "direction" | "rollover" | "recessStart" | "recessEnd">): string {
  const unit = (labelOf(DEADLINE_UNITS, r.unit) ?? "").toLowerCase()
  const noun = r.amount === 1 ? unit.replace(/s$/, "") : unit
  const parts = [`${r.amount} ${noun} ${r.direction === "BEFORE" ? "before" : "after"} the trigger date`]
  const recess = ["COURT_DAYS", "CALENDAR_DAYS"].includes(r.unit) ? recessLabel(r.recessStart, r.recessEnd) : null
  if (recess) parts.push(`${recess} not counted`)
  if (r.unit !== "COURT_DAYS") {
    const way = r.direction === "BEFORE" ? "earlier" : "later"
    if (r.rollover === "SKIP_SUNDAY_AND_HOLIDAYS") parts.push(`a Sunday or public holiday moves it ${way}`)
    else if (r.rollover === "SKIP_NON_COURT_DAYS") parts.push(`a weekend, holiday or closure moves it ${way}`)
  }
  return parts.join("; ")
}

export interface RuleForm { code: string; name: string; description: string; court: string; triggerLabel: string; keyDateType: string; amount: string; unit: string; direction: string; rollover: string; recessStart: string; recessEnd: string; sourceReference: string }
export const emptyRule = (): RuleForm => ({ code: "", name: "", description: "", court: "HIGH_COURT", triggerLabel: "", keyDateType: "FILING_DEADLINE", amount: "", unit: "COURT_DAYS", direction: "AFTER", rollover: "NONE", recessStart: "", recessEnd: "", sourceReference: "" })
export const ruleToForm = (r: DeadlineRule): RuleForm => ({ code: r.code, name: r.name, description: r.description ?? "", court: r.court ?? "", triggerLabel: r.triggerLabel, keyDateType: r.keyDateType, amount: String(r.amount), unit: r.unit, direction: r.direction, rollover: r.rollover, recessStart: r.recessStart ?? "", recessEnd: r.recessEnd ?? "", sourceReference: r.sourceReference ?? "" })
export function ruleProblem(f: RuleForm): string | null {
  if (f.code.trim().length < 2) return "Give the rule a short code (at least 2 characters)"
  if (!f.name.trim()) return "Give the rule a name"
  if (!f.triggerLabel.trim()) return "Say what the trigger date is, for example 'Date the summons was served'"
  const n = Number(f.amount)
  const max = MAX_PERIOD[f.unit] ?? 3660
  if (!f.amount.trim() || !Number.isInteger(n) || n < 1 || n > max) return `The period must be a whole number from 1 to ${max}`
  const rs = f.recessStart.trim(), re = f.recessEnd.trim()
  if (!!rs !== !!re) return "Give both ends of the recess window, or neither"
  if (rs && (!RECESS_RE.test(rs) || !RECESS_RE.test(re))) return "Write the recess dates as MM-DD, for example 12-16 and 01-15"
  return null
}
export const ruleRequest = (f: RuleForm) => ({
  code: f.code.trim(), name: f.name.trim(), description: t(f.description), court: t(f.court), triggerLabel: f.triggerLabel.trim(), keyDateType: f.keyDateType,
  amount: Number(f.amount), unit: f.unit, direction: f.direction, rollover: f.rollover, recessStart: t(f.recessStart), recessEnd: t(f.recessEnd), sourceReference: t(f.sourceReference),
})

/** The dates the engine accepts as a starting point (2000 to 2100). */
export const TRIGGER_MIN = "2000-01-01"
export const TRIGGER_MAX = "2100-12-31"
export function triggerProblem(ruleId: string, trigger: string): string | null {
  if (!ruleId) return "Choose the rule"
  if (!trigger) return "Enter the trigger date"
  if (trigger < TRIGGER_MIN || trigger > TRIGGER_MAX) return "The trigger date must be between 2000 and 2100"
  return null
}
export function correctionProblem(trigger: string, reason: string): string | null {
  if (!trigger) return "Enter the correct trigger date"
  if (trigger < TRIGGER_MIN || trigger > TRIGGER_MAX) return "The trigger date must be between 2000 and 2100"
  if (reason.trim().length < 10) return "Say why the date is being corrected (at least 10 characters)"
  return null
}
export function calendarProblem(date: string, reason: string): string | null {
  if (!date) return "Choose the date"
  if (date < TRIGGER_MIN || date > TRIGGER_MAX) return "Choose a date between 2000 and 2100"
  if (reason.trim().length < 3) return "Give a reason (at least 3 characters)"
  return null
}
/** A date is only offered for adding to the diary once the person has seen it worked out from exactly these inputs. */
export const previewMatches = (p: Pick<DeadlinePreview, "ruleId" | "triggerDate"> | null, ruleId: string, trigger: string) => !!p && p.ruleId === ruleId && p.triggerDate === trigger
export const KIND_TONE: Record<string, Tone> = { PUBLIC_HOLIDAY: "info", COURT_CLOSURE: "warning" }
/** Newest year first is not wanted here: a short list around today, for the calendar's year picker. */
export const calendarYears = (today = new Date()): string[] => { const y = today.getFullYear(); return [y - 1, y, y + 1, y + 2].map(String) }

// ── Trust reconciliation ────────────────────────────────────────────────────────────────────────────────────────────────

export const RECON_STATUS: Record<string, { label: string; tone: Tone }> = { DRAFT: { label: "Draft", tone: "neutral" }, SUBMITTED: { label: "Awaiting approval", tone: "warning" }, APPROVED: { label: "Approved", tone: "success" } }
export const RECON_KINDS = [["OUTSTANDING_DEPOSIT", "Deposit the bank has not yet credited"], ["OUTSTANDING_PAYMENT", "Payment the bank has not yet debited"]] as const

export interface ReconItem { id: string; kind: string; amount: number; itemDate: string; description: string; reference: string | null; trustTransactionId: string | null }
export interface ReconClientAmount { clientId: string; clientName: string; amount: number }
export interface ReconMismatch { clientId: string; clientName: string; clientRecord: number; ledger: number }
export interface Reconciliation {
  id: string; periodEnd: string; status: string; statementBalance: number; statementNote: string | null; cashBookBalance: number; clientLedgerTotal: number
  outstandingDeposits: number; outstandingPayments: number; adjustedBankBalance: number; difference: number; balanced: boolean; live: boolean; changedSinceSubmitted: boolean
  ledgerMismatchCount: number; items: ReconItem[]; negativeClients: ReconClientAmount[]; ledgerMismatches: ReconMismatch[]
  preparedByName: string | null; preparedAt: string; submittedAt: string | null; returnReason: string | null; returnedByName: string | null
  approvedByName: string | null; approvedAt: string | null; approvalNote: string | null; selfApproved: boolean
}
export interface ReconSummary { id: string; periodEnd: string; status: string; statementBalance: number; difference: number | null; preparedByName: string | null; preparedAt: string; approvedByName: string | null; approvedAt: string | null }

const cents = (n: number) => Math.round(n * 100) / 100
const rand = (n: number) => `R${Math.abs(cents(n)).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

/** The date is not in the future, is after the last reconciled date, and the statement balance is a number (it may be negative or nil). */
export function startReconProblem(periodEnd: string, statement: string, today: string, lockedThrough: string | null): string | null {
  if (!periodEnd) return "Choose the date the statement balance is as at"
  if (periodEnd > today) return "The date cannot be in the future"
  if (lockedThrough && periodEnd <= lockedThrough) return `The trust account is already reconciled up to ${lockedThrough}`
  if (!statement.trim() || !Number.isFinite(Number(statement))) return "Enter the closing balance from the bank statement"
  return null
}
export interface ReconItemForm { kind: string; amount: string; itemDate: string; description: string; reference: string }
export const emptyReconItem = (): ReconItemForm => ({ kind: "OUTSTANDING_DEPOSIT", amount: "", itemDate: "", description: "", reference: "" })
export function reconItemProblem(f: ReconItemForm, periodEnd: string): string | null {
  const n = Number(f.amount)
  if (!f.amount.trim() || !Number.isFinite(n) || n <= 0) return "Enter an amount above nil"
  if (!f.itemDate) return "Enter the date of the item"
  if (f.itemDate > periodEnd) return `The item must be dated on or before ${periodEnd}`
  if (f.description.trim().length < 3) return "Describe the item"
  return null
}
export const reconItemRequest = (f: ReconItemForm) => ({ kind: f.kind, amount: Number(f.amount), itemDate: f.itemDate, description: f.description.trim(), reference: t(f.reference), trustTransactionId: null as string | null })

/** What the person should do next, in words. */
export function reconNextStep(r: Pick<Reconciliation, "status" | "difference" | "cashBookBalance" | "clientLedgerTotal" | "changedSinceSubmitted" | "periodEnd">): { text: string; tone: Tone } {
  if (r.status === "APPROVED") return { text: `Approved. The trust account is locked through ${r.periodEnd}: nothing dated on or before it can be posted.`, tone: "success" }
  if (r.status === "SUBMITTED") {
    return r.changedSinceSubmitted
      ? { text: "The trust ledger has changed since this was submitted. Return it to draft and submit it again.", tone: "danger" }
      : { text: "Waiting for a different person to approve it. Approving locks the period.", tone: "warning" }
  }
  const d = cents(r.difference)
  if (d > 0) return { text: `${rand(d)} more in the ledger than the bank explains. Add a deposit the bank has not yet credited, or look for a payment missing from the ledger.`, tone: "danger" }
  if (d < 0) return { text: `${rand(d)} less in the ledger than the bank explains. Add a payment the bank has not yet debited, or look for a deposit missing from the ledger.`, tone: "danger" }
  if (cents(r.cashBookBalance) !== cents(r.clientLedgerTotal)) return { text: "The client ledgers do not add up to the trust ledger. Do not submit this; ask for the ledger to be checked.", tone: "danger" }
  return { text: "It balances. Submit it for approval.", tone: "success" }
}
export const canSubmitRecon = (r: Pick<Reconciliation, "status" | "balanced">, canPrepare: boolean) => canPrepare && r.status === "DRAFT" && r.balanced
export const canEditRecon = (r: Pick<Reconciliation, "status">, canPrepare: boolean) => canPrepare && r.status === "DRAFT"
export function approveProblem(mismatchCount: number, note: string): string | null {
  return mismatchCount > 0 && note.trim().length < 10 ? "Some client balances disagree with the ledger. Say what you checked (at least 10 characters)." : null
}

// ---- ethical walls ----------------------------------------------------------------------------------------------------

export const WALL_SCOPES = [["MATTER", "One matter"], ["CLIENT", "All of a client's matters"]] as const
export interface Wall { id: string; scope: string; matterId: string | null; clientId: string | null; targetLabel: string; userId: string; userName: string | null; reason: string; createdByName: string | null; createdAt: string; active: boolean; liftedByName: string | null; liftedAt: string | null; liftReason: string | null }
export interface WallDenial { id: string; userId: string; userName: string | null; matterId: string; matterLabel: string | null; clientId: string | null; wallId: string; deniedAt: string }
export interface WallForm { scope: string; matterId: string; clientId: string; userId: string; reason: string }
export const emptyWall = (): WallForm => ({ scope: "MATTER", matterId: "", clientId: "", userId: "", reason: "" })
export const MIN_WALL_REASON = 10

export function wallProblem(f: WallForm): string | null {
  if (f.scope === "MATTER" && !f.matterId) return "Choose the matter"
  if (f.scope === "CLIENT" && !f.clientId) return "Choose the client"
  if (f.scope !== "MATTER" && f.scope !== "CLIENT") return "Choose what the wall covers"
  if (!f.userId) return "Choose the person the wall bars"
  if (f.reason.trim().length < MIN_WALL_REASON) return `Say why, in at least ${MIN_WALL_REASON} characters`
  return null
}
/** Only the target that matches the scope is sent, so a stale pick from the other scope never reaches the server. */
export function wallRequest(f: WallForm, userName: string) {
  return { scope: f.scope, matterId: f.scope === "MATTER" ? f.matterId : null, clientId: f.scope === "CLIENT" ? f.clientId : null, userId: f.userId, userName, reason: f.reason.trim() }
}
/** The person a wall bars cannot lift it, and a lifted wall cannot be lifted again; the server enforces both. */
export function canLiftWall(w: Wall, me: string | undefined, canManage: boolean): boolean {
  return canManage && w.active && !!me && w.userId !== me
}
export function liftProblem(reason: string): string | null {
  return reason.trim().length < MIN_WALL_REASON ? `Say why the wall is being lifted, in at least ${MIN_WALL_REASON} characters` : null
}
export function wallHeadline(w: Wall): string {
  const who = w.userName ?? "This person"
  return w.scope === "CLIENT" ? `${who} is barred from all of ${w.targetLabel}'s matters` : `${who} is barred from ${w.targetLabel}`
}

// ---- client due diligence (FICA intake) ---------------------------------------------------------------------------------

export const KYC_STATUS: Record<string, { label: string; tone: Tone }> = {
  NOT_STARTED: { label: "Not started", tone: "neutral" },
  DRAFT: { label: "In preparation", tone: "info" },
  SUBMITTED: { label: "Awaiting approval", tone: "warning" },
  VERIFIED: { label: "Verified", tone: "success" },
  DUE_SOON: { label: "Review due soon", tone: "warning" },
  EXPIRED: { label: "Review overdue", tone: "danger" },
}
export const KYC_SUBJECTS = [["INDIVIDUAL", "Individual"], ["COMPANY", "Company or close corporation"], ["TRUST", "Trust"], ["PARTNERSHIP", "Partnership"]] as const
export const KYC_LEVELS = [["LOW", "Low"], ["MEDIUM", "Medium"], ["HIGH", "High"]] as const
export const KYC_PEP = [["NONE", "Not politically exposed"], ["DPEP", "Domestic politically exposed person"], ["PIP", "Prominent influential person"], ["FPEP", "Foreign politically exposed person"]] as const
export const KYC_SANCTIONS = [["CLEAR", "No match"], ["MATCH", "Possible match, not yet resolved"]] as const
export const KYC_METHODS = [["OWNERSHIP", "Holds shares or voting rights"], ["CONTROL", "Controls the entity in another way"], ["MANAGEMENT", "Senior management (last resort)"]] as const
const ROLE_LABEL: Record<string, string> = { BENEFICIAL_OWNER: "Beneficial owner", AUTHORISED_PERSON: "Authorised person", TRUSTEE: "Trustee", FOUNDER: "Founder", BENEFICIARY: "Beneficiary", PARTNER: "Partner" }
export const kycRoleLabel = (r: string) => ROLE_LABEL[r] ?? r
const KIND_LABEL: Record<string, string> = { IDENTITY: "Identity document", ADDRESS: "Proof of address", REGISTRATION: "Registration documents", OWNERSHIP_STRUCTURE: "Ownership and control structure", TRUST_DEED: "Trust deed and letters of authority", OTHER: "Other" }
export const kycKindLabel = (k: string) => KIND_LABEL[k] ?? k
export const kycSubjectLabel = (s: string) => KYC_SUBJECTS.find(([v]) => v === s)?.[1] ?? s

/** Who can be recorded for each kind of client. */
export function kycRolesFor(subject: string): (readonly [string, string])[] {
  const r = (...k: string[]) => k.map(x => [x, ROLE_LABEL[x]] as const)
  switch (subject) {
    case "COMPANY": return r("AUTHORISED_PERSON", "BENEFICIAL_OWNER")
    case "TRUST": return r("TRUSTEE", "FOUNDER", "BENEFICIARY", "AUTHORISED_PERSON")
    case "PARTNERSHIP": return r("PARTNER", "AUTHORISED_PERSON")
    default: return []
  }
}
/** The documents that make sense for each kind of client. */
export function kycKindsFor(subject: string): (readonly [string, string])[] {
  const k = (...x: string[]) => x.map(v => [v, KIND_LABEL[v]] as const)
  switch (subject) {
    case "COMPANY": return k("ADDRESS", "REGISTRATION", "OWNERSHIP_STRUCTURE", "IDENTITY", "OTHER")
    case "TRUST": return k("ADDRESS", "TRUST_DEED", "IDENTITY", "OTHER")
    case "PARTNERSHIP": return k("ADDRESS", "IDENTITY", "OTHER")
    default: return k("IDENTITY", "ADDRESS", "OTHER")
  }
}
/** An individual client is verified as an individual; any other client is a company, trust or partnership. */
export const kycSubjectsFor = (clientType: string): (readonly [string, string])[] =>
  clientType === "INDIVIDUAL" ? KYC_SUBJECTS.filter(([v]) => v === "INDIVIDUAL") : KYC_SUBJECTS.filter(([v]) => v !== "INDIVIDUAL")

export interface KycOverviewRow { clientId: string; clientName: string; clientType: string; status: string; profileId: string | null; riskRating: string | null; validUntil: string | null; reviewOpen: boolean }
export interface KycParty { id: string; role: string; name: string; idNumber: string | null; percent: number | null; method: string | null; pep: string | null; verifiedOn: string | null; verifiedByName: string | null; note: string | null }
export interface KycItem { id: string; kind: string; partyId: string | null; description: string; documentRef: string | null; evidenceId: string | null; verifiedOn: string; verifiedByName: string | null }
export interface KycProfile {
  id: string; clientId: string; clientName: string; subjectKind: string; status: string
  riskGeography: string | null; riskService: string | null; riskClient: string | null; riskChannel: string | null; riskRating: string | null; dueDiligenceLevel: string; reviewMonths: number
  pepStatus: string | null; pepCheckedOn: string | null; pepNote: string | null; sanctionsCheckedOn: string | null; sanctionsResult: string | null
  businessNature: string | null; sourceOfFunds: string | null; sourceOfWealth: string | null
  startedByName: string | null; startedAt: string; submittedBy: string | null; submittedByName: string | null; submittedAt: string | null; returnReason: string | null
  approvedByName: string | null; approvedAt: string | null; approvalNote: string | null; selfApproved: boolean; seniorApproved: boolean; validUntil: string | null
  needsSenior: boolean; missing: string[]; parties: KycParty[]; items: KycItem[]
}
export interface KycSettings { enforce: boolean; history: { enforce: boolean; reason: string; changedByName: string | null; changedAt: string }[] }

export interface KycAssessmentForm { geography: string; service: string; client: string; channel: string; pep: string; pepCheckedOn: string; pepNote: string; sanctionsCheckedOn: string; sanctionsResult: string; businessNature: string; sourceOfFunds: string; sourceOfWealth: string }
export const emptyAssessment = (): KycAssessmentForm => ({ geography: "", service: "", client: "", channel: "", pep: "", pepCheckedOn: "", pepNote: "", sanctionsCheckedOn: "", sanctionsResult: "", businessNature: "", sourceOfFunds: "", sourceOfWealth: "" })
export const profileToAssessment = (p: KycProfile): KycAssessmentForm => ({
  geography: p.riskGeography ?? "", service: p.riskService ?? "", client: p.riskClient ?? "", channel: p.riskChannel ?? "", pep: p.pepStatus ?? "", pepCheckedOn: p.pepCheckedOn ?? "", pepNote: p.pepNote ?? "",
  sanctionsCheckedOn: p.sanctionsCheckedOn ?? "", sanctionsResult: p.sanctionsResult ?? "", businessNature: p.businessNature ?? "", sourceOfFunds: p.sourceOfFunds ?? "", sourceOfWealth: p.sourceOfWealth ?? "",
})
const orNull = (s: string) => (s.trim() ? s.trim() : null)
export const assessmentRequest = (f: KycAssessmentForm) => ({
  geography: orNull(f.geography), service: orNull(f.service), client: orNull(f.client), channel: orNull(f.channel), pep: orNull(f.pep), pepCheckedOn: orNull(f.pepCheckedOn), pepNote: orNull(f.pepNote),
  sanctionsCheckedOn: orNull(f.sanctionsCheckedOn), sanctionsResult: orNull(f.sanctionsResult), businessNature: orNull(f.businessNature), sourceOfFunds: orNull(f.sourceOfFunds), sourceOfWealth: orNull(f.sourceOfWealth),
})
/** The same rule the server applies, so the rating shows while the form is being filled in: the highest indicator, never below high for a politically exposed person. */
export function liveRisk(f: Pick<KycAssessmentForm, "geography" | "service" | "client" | "channel" | "pep">): string | null {
  const four = [f.geography, f.service, f.client, f.channel]
  if (four.some(x => !x)) return null
  if (f.pep && f.pep !== "NONE") return "HIGH"
  return four.includes("HIGH") ? "HIGH" : four.includes("MEDIUM") ? "MEDIUM" : "LOW"
}
export const needsSource = (f: Pick<KycAssessmentForm, "geography" | "service" | "client" | "channel" | "pep">) => liveRisk(f) === "HIGH"
export const levelLabel = (level: string) => level === "SIMPLIFIED" ? "Simplified due diligence" : level === "ENHANCED" ? "Enhanced due diligence" : "Standard due diligence"
export const riskTone = (r: string | null): Tone => r === "HIGH" ? "danger" : r === "MEDIUM" ? "warning" : r === "LOW" ? "success" : "neutral"

export const MIN_KYC_TEXT = 10
export function assessmentProblem(f: KycAssessmentForm, today: string): string | null {
  if (f.pepCheckedOn > today && f.pepCheckedOn) return "The date the politically exposed check was done cannot be in the future"
  if (f.sanctionsCheckedOn > today && f.sanctionsCheckedOn) return "The date of the sanctions screening cannot be in the future"
  if (f.pep && !f.pepCheckedOn) return "Say when the politically exposed check was done"
  if (f.sanctionsResult && !f.sanctionsCheckedOn) return "Say when the sanctions screening was done"
  return null
}

export interface KycPartyForm { role: string; name: string; idNumber: string; percent: string; method: string; verifiedOn: string; note: string }
export const emptyKycParty = (subject: string): KycPartyForm => ({ role: kycRolesFor(subject)[0]?.[0] ?? "", name: "", idNumber: "", percent: "", method: "OWNERSHIP", verifiedOn: "", note: "" })
export function kycPartyProblem(f: KycPartyForm, today: string): string | null {
  if (!f.role) return "Choose their role"
  if (f.name.trim().length < 2) return "Enter their full name"
  if (f.role === "BENEFICIAL_OWNER") {
    if (!f.method) return "Say how this owner was identified"
    if (f.method === "OWNERSHIP") {
      const n = Number(f.percent)
      if (!f.percent.trim() || !Number.isFinite(n) || n <= 0 || n > 100) return "Enter the percentage held, above 0 and up to 100"
    }
  }
  if (f.verifiedOn && f.verifiedOn > today) return "The verification date cannot be in the future"
  return null
}
export const kycPartyRequest = (f: KycPartyForm) => ({
  role: f.role, name: f.name.trim(), idNumber: orNull(f.idNumber),
  percent: f.role === "BENEFICIAL_OWNER" && f.method === "OWNERSHIP" ? Number(f.percent) : null,
  method: f.role === "BENEFICIAL_OWNER" ? f.method : null, pep: null as string | null, verifiedOn: orNull(f.verifiedOn), note: orNull(f.note),
})

export interface KycItemForm { kind: string; partyId: string; description: string; documentRef: string; verifiedOn: string }
export const emptyKycItem = (subject: string, today: string): KycItemForm => ({ kind: kycKindsFor(subject)[0][0], partyId: "", description: "", documentRef: "", verifiedOn: today })
export function kycItemProblem(f: KycItemForm, today: string): string | null {
  if (!f.kind) return "Choose the kind of document"
  if (f.description.trim().length < 3) return "Say what was checked, for example 'Green ID book, original seen'"
  if (!f.verifiedOn) return "Enter the date you checked it"
  if (f.verifiedOn > today) return "The date you checked it cannot be in the future"
  return null
}
export const kycItemRequest = (f: KycItemForm) => ({ kind: f.kind, partyId: orNull(f.partyId), description: f.description.trim(), documentRef: orNull(f.documentRef), evidenceId: null as string | null, verifiedOn: f.verifiedOn })

export interface KycPermissions { prepare: boolean; approve: boolean; senior: boolean }
export interface KycActions { canEdit: boolean; canSubmit: boolean; canReturn: boolean; canApprove: boolean; approveHint: string | null; selfSubmitted: boolean }
/** What this person can do with this cycle. The server decides in the end; this only shows the right buttons and says why one is missing. */
export function kycActions(p: Pick<KycProfile, "status" | "missing" | "needsSenior" | "submittedBy">, perms: KycPermissions, me: string | undefined): KycActions {
  const draft = p.status === "DRAFT", submitted = p.status === "SUBMITTED"
  const selfSubmitted = !!me && p.submittedBy === me
  let hint: string | null = null
  if (submitted && perms.approve) {
    if (p.needsSenior && !perms.senior) hint = "This client is high risk or politically exposed. Senior management must approve it."
    else if (selfSubmitted && p.needsSenior) hint = "You submitted this, so someone else must approve it."
    else if (selfSubmitted) hint = "You submitted this. Another person must approve it, unless your firm allows you to approve your own work, with a written reason."
  }
  return {
    canEdit: perms.prepare && draft,
    canSubmit: perms.prepare && draft && p.missing.length === 0,
    canReturn: perms.approve && submitted,
    canApprove: perms.approve && submitted && !(p.needsSenior && !perms.senior) && !(p.needsSenior && selfSubmitted),
    approveHint: hint,
    selfSubmitted,
  }
}
export function kycApproveProblem(selfSubmitted: boolean, note: string): string | null {
  return selfSubmitted && note.trim().length < MIN_KYC_TEXT ? `Approving your own work needs a written reason, in at least ${MIN_KYC_TEXT} characters` : null
}
export const kycReturnProblem = (reason: string): string | null => reason.trim().length < MIN_KYC_TEXT ? `Say what needs fixing, in at least ${MIN_KYC_TEXT} characters` : null

/** Whole days from one date to another, ignoring the time of day. */
export function kycDays(date: string, today: string): number {
  const a = Date.UTC(+date.slice(0, 4), +date.slice(5, 7) - 1, +date.slice(8, 10)), b = Date.UTC(+today.slice(0, 4), +today.slice(5, 7) - 1, +today.slice(8, 10))
  return Math.round((a - b) / 86400000)
}
export function reviewText(validUntil: string | null, today: string): string {
  if (!validUntil) return ""
  const d = kycDays(validUntil, today)
  if (d < 0) return `Review overdue by ${-d} day${d === -1 ? "" : "s"}`
  if (d === 0) return "Review due today"
  return `Review due in ${d} day${d === 1 ? "" : "s"}`
}
/** What to do next, in words. */
export function kycNextStep(p: Pick<KycProfile, "status" | "missing" | "returnReason" | "validUntil">, today: string): { text: string; tone: Tone } {
  if (p.status === "VERIFIED") {
    return p.validUntil && p.validUntil < today
      ? { text: "Verified, but past the review date. Open a review.", tone: "danger" }
      : { text: `Verified. ${reviewText(p.validUntil, today)} (${p.validUntil}).`, tone: "success" }
  }
  if (p.status === "SUBMITTED") return { text: "Waiting for a different person to approve it.", tone: "warning" }
  if (p.missing.length > 0) return { text: `${p.returnReason ? `Returned: ${p.returnReason}. ` : ""}${p.missing.length} thing${p.missing.length === 1 ? "" : "s"} still to do before it can be submitted.`, tone: p.returnReason ? "danger" : "info" }
  return { text: "Complete. Submit it for approval.", tone: "success" }
}
export function kycSettingProblem(next: boolean, current: boolean, reason: string): string | null {
  if (next === current) return "That is already the current setting"
  return reason.trim().length < MIN_KYC_TEXT ? `Say why, in at least ${MIN_KYC_TEXT} characters` : null
}
/** The client's cycles, newest first, split into the one to work on and the history behind it. */
export function splitCycles(cycles: KycProfile[]): { current: KycProfile | null; earlier: KycProfile[] } {
  return { current: cycles[0] ?? null, earlier: cycles.slice(1) }
}
/** A review can be opened once the latest cycle is verified (before or after it is due). */
export const canOpenReview = (cycles: KycProfile[], canPrepare: boolean) => canPrepare && cycles.length > 0 && cycles[0].status === "VERIFIED"

// ---- matter documents with versions -----------------------------------------------------------------------------------------

export const DOC_CATEGORIES = [["CORRESPONDENCE", "Correspondence"], ["PLEADING", "Pleading"], ["COURT_ORDER", "Court order"], ["CONTRACT", "Contract"], ["ADVICE", "Advice"], ["EVIDENCE", "Evidence"], ["FILE_NOTE", "File note"], ["OTHER", "Other"]] as const
export const docCategoryLabel = (c: string) => DOC_CATEGORIES.find(([v]) => v === c)?.[1] ?? c
export const DOC_EXTENSIONS = ["pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "rtf", "txt", "csv", "eml", "msg", "png", "jpg", "jpeg"]
export const DOC_ACCEPT = DOC_EXTENSIONS.map(e => "." + e).join(",")
export const MAX_DOC_BYTES = 20 * 1024 * 1024
export const MIN_DOC_REASON = 10

export interface DocSummary { id: string; matterId: string; matterLabel: string; clientId: string; title: string; category: string; status: string; currentVersion: number; legalHold: boolean; checkedOutBy: string | null; checkedOutByName: string | null; checkedOutAt: string | null; updatedAt: string }
export interface DocVersion { versionNo: number; fileName: string; contentType: string; sizeBytes: number; sha256: string; changeNote: string | null; uploadedByName: string | null; uploadedAt: string }
export interface DocEvent { eventType: string; versionNo: number | null; actorName: string | null; detail: string | null; occurredAt: string }
export interface DocDetail { document: DocSummary; holdReason: string | null; holdSetByName: string | null; holdSetAt: string | null; archivedReason: string | null; createdByName: string | null; createdAt: string; versions: DocVersion[]; events: DocEvent[] }

const docExt = (name: string) => (name.includes(".") ? name.slice(name.lastIndexOf(".") + 1).toLowerCase() : "")
/** The same limits the server applies, so a bad file is caught before it is sent. The server also checks what is inside the file. */
export function docFileProblem(file: { name: string; size: number } | null): string | null {
  if (!file) return "Choose a file"
  if (file.size <= 0) return "That file is empty"
  if (file.size > MAX_DOC_BYTES) return "That file is over 20 MB"
  if (!DOC_EXTENSIONS.includes(docExt(file.name))) return "Use a PDF, Word, Excel, PowerPoint, text, e-mail or image file"
  return null
}
export interface DocUploadForm { matterId: string; title: string; category: string; note: string }
export const emptyDocUpload = (): DocUploadForm => ({ matterId: "", title: "", category: "CORRESPONDENCE", note: "" })
export function docUploadProblem(f: DocUploadForm, file: { name: string; size: number } | null): string | null {
  if (!f.matterId) return "Choose the matter"
  if (f.title.trim().length < 2) return "Give the document a title"
  if (!DOC_CATEGORIES.some(([v]) => v === f.category)) return "Choose what kind of document it is"
  return docFileProblem(file)
}
export const docReasonProblem = (reason: string, what: string): string | null =>
  reason.trim().length < MIN_DOC_REASON ? `Say why you are ${what}, in at least ${MIN_DOC_REASON} characters` : null
/** A title for the dialog's default: the file name without its extension. */
export const titleFromFile = (name: string) => name.replace(/\.[^.]+$/, "").replace(/[_]+/g, " ").trim()

export interface DocPermissions { write: boolean; hold: boolean; override: boolean }
export interface DocActions { canAddVersion: boolean; canCheckOut: boolean; canCheckIn: boolean; canCancelCheckout: boolean; canOverride: boolean; canHold: boolean; canReleaseHold: boolean; canArchive: boolean; canRestore: boolean; canEdit: boolean; archiveBlockedBy: string | null }
/** What this person can do with this document. The server decides in the end; this shows the right buttons and says why archiving is unavailable. */
export function docActions(d: Pick<DocSummary, "status" | "legalHold" | "checkedOutBy">, p: DocPermissions, me: string | undefined): DocActions {
  const active = d.status === "ACTIVE", mine = !!me && d.checkedOutBy === me, lockedByOther = !!d.checkedOutBy && !mine
  const archiveBlockedBy = d.legalHold ? "It is under legal hold." : d.checkedOutBy ? "It is checked out." : null
  return {
    canAddVersion: p.write && active && !lockedByOther,
    canCheckOut: p.write && active && !d.checkedOutBy,
    canCheckIn: p.write && active && mine,
    canCancelCheckout: p.write && active && mine,
    canOverride: p.override && !!d.checkedOutBy && !mine,
    canHold: p.hold && active && !d.legalHold,
    canReleaseHold: p.hold && d.legalHold,
    canArchive: p.write && active && !archiveBlockedBy,
    canRestore: p.write && !active,
    canEdit: p.write && active,
    archiveBlockedBy: active ? archiveBlockedBy : null,
  }
}
export function docBadges(d: Pick<DocSummary, "status" | "legalHold" | "checkedOutByName">): { label: string; tone: Tone }[] {
  const b: { label: string; tone: Tone }[] = []
  if (d.status === "ARCHIVED") b.push({ label: "Archived", tone: "neutral" })
  if (d.legalHold) b.push({ label: "Legal hold", tone: "danger" })
  if (d.checkedOutByName) b.push({ label: `Checked out by ${d.checkedOutByName}`, tone: "warning" })
  return b
}
const EVENT_TEXT: Record<string, string> = {
  UPLOADED: "added the document", NEW_VERSION: "added a new version", DOWNLOADED: "downloaded it", VERIFIED: "checked the file against its hash", EDITED: "changed the title or kind",
  CHECKED_OUT: "checked it out", CHECKOUT_CANCELLED: "cancelled the check-out", LOCK_OVERRIDDEN: "released someone else's check-out", HOLD_SET: "placed it under legal hold",
  HOLD_RELEASED: "released the legal hold", ARCHIVED: "archived it", RESTORED: "restored it",
}
export function docEventText(e: Pick<DocEvent, "eventType" | "versionNo" | "actorName" | "detail">): string {
  const who = e.actorName ?? "Someone"
  const what = EVENT_TEXT[e.eventType] ?? e.eventType.toLowerCase()
  const ver = e.versionNo != null && ["NEW_VERSION", "DOWNLOADED", "VERIFIED"].includes(e.eventType) ? ` (version ${e.versionNo})` : ""
  const more = e.detail && !["UPLOADED", "DOWNLOADED"].includes(e.eventType) ? `: ${e.detail}` : ""
  return `${who} ${what}${ver}${more}`
}
export const shortHash = (h: string) => h.slice(0, 8) + "…" + h.slice(-6)

// ── Litigation: court cases, hearings and court orders ───────────────────────────────────────────────────────────────
export const LIT_COURT_LEVELS = [["MAGISTRATES", "Magistrates' Court"], ["HIGH_COURT", "High Court"], ["SCA", "Supreme Court of Appeal"], ["CONSTITUTIONAL", "Constitutional Court"], ["LABOUR", "Labour Court"], ["TAX", "Tax Court"], ["EQUALITY", "Equality Court"], ["OTHER", "Other"]] as const
export const LIT_ROLES = [["PLAINTIFF", "Plaintiff"], ["DEFENDANT", "Defendant"], ["APPLICANT", "Applicant"], ["RESPONDENT", "Respondent"], ["APPELLANT", "Appellant"], ["OTHER", "Other"]] as const
export const LIT_STAGES = [["PRE_ACTION", "Pre-action"], ["PLEADINGS", "Pleadings"], ["DISCOVERY", "Discovery"], ["PRE_TRIAL", "Pre-trial"], ["TRIAL", "Trial"], ["JUDGMENT", "Judgment"], ["APPEAL", "Appeal"], ["EXECUTION", "Execution"], ["CONCLUDED", "Concluded"]] as const
export const LIT_OPEN_STAGES = LIT_STAGES.filter(s => s[0] !== "CONCLUDED")
export const LIT_OUTCOMES = [["WON", "Won"], ["LOST", "Lost"], ["SETTLED", "Settled"], ["WITHDRAWN", "Withdrawn"], ["STRUCK_OFF", "Struck off the roll"], ["OTHER", "Other"]] as const
export const LIT_HEARING_TYPES = [["TRIAL", "Trial"], ["MOTION", "Motion"], ["APPLICATION", "Application"], ["CASE_MANAGEMENT", "Case management"], ["PRE_TRIAL", "Pre-trial"], ["APPEAL", "Appeal"], ["JUDGMENT", "Judgment"], ["OTHER", "Other"]] as const
const labelIn = (list: readonly (readonly [string, string])[], v: string | null | undefined) => list.find(x => x[0] === v)?.[1] ?? v ?? ""
export const litStageLabel = (v: string) => labelIn(LIT_STAGES, v)
export const litCourtLabel = (v: string) => labelIn(LIT_COURT_LEVELS, v)
export const litRoleLabel = (v: string) => labelIn(LIT_ROLES, v)
export const litOutcomeLabel = (v: string | null) => labelIn(LIT_OUTCOMES, v)
export const litHearingLabel = (v: string) => labelIn(LIT_HEARING_TYPES, v)
export const LIT_MIN_TEXT = 10

export type LitHearing = { id: string; hearingType: string; hearingDate: string; hearingTime: string | null; courtRoom: string | null; appearing: string | null; status: string; postponements: number; outcome: string | null; cancelReason: string | null; keyDateId: string | null }
export type LitOrder = { id: string; hearingId: string | null; orderDate: string; summary: string; complianceDue: string | null; compliedOn: string | null; compliedNote: string | null; outstanding: boolean; keyDateId: string | null; createdByName: string | null }
export type LitEvent = { eventType: string; actorName: string | null; detail: string | null; occurredAt: string }
export type LitCaseSummary = { id: string; matterId: string; matterLabel: string; clientId: string; courtLevel: string; courtName: string; caseNumber: string | null; ourRole: string; stage: string; opposingParty: string | null; nextHearing: string | null; outcome: string | null; updatedAt: string }
export type LitCase = {
  id: string; matterId: string; matterLabel: string; clientId: string; courtLevel: string; courtName: string; caseNumber: string | null; ourRole: string; stage: string; open: boolean
  opposingParty: string | null; opposingAttorney: string | null; opposingAttorneyContact: string | null; judge: string | null; issuedOn: string | null; servedOn: string | null
  outcome: string | null; concludedOn: string | null; outcomeNote: string | null; notes: string | null; createdByName: string | null; createdAt: string; updatedAt: string
  hearings: LitHearing[]; orders: LitOrder[]; events: LitEvent[]; warnings: string[]
}
export type LitRollRow = { hearingId: string; caseId: string; matterId: string; matterLabel: string; courtName: string; caseNumber: string | null; hearingType: string; hearingDate: string; hearingTime: string | null; courtRoom: string | null; appearing: string | null; postponements: number; warnings: string[] }

export type LitCaseForm = { matterId: string; courtLevel: string; courtName: string; ourRole: string; caseNumber: string; stage: string; opposingParty: string; opposingAttorney: string; opposingAttorneyContact: string; judge: string; issuedOn: string; servedOn: string; notes: string }
export const emptyLitCase = (): LitCaseForm => ({ matterId: "", courtLevel: "HIGH_COURT", courtName: "", ourRole: "PLAINTIFF", caseNumber: "", stage: "PRE_ACTION", opposingParty: "", opposingAttorney: "", opposingAttorneyContact: "", judge: "", issuedOn: "", servedOn: "", notes: "" })
export const litCaseToForm = (c: LitCase): LitCaseForm => ({ matterId: c.matterId, courtLevel: c.courtLevel, courtName: c.courtName, ourRole: c.ourRole, caseNumber: c.caseNumber ?? "", stage: c.stage, opposingParty: c.opposingParty ?? "", opposingAttorney: c.opposingAttorney ?? "", opposingAttorneyContact: c.opposingAttorneyContact ?? "", judge: c.judge ?? "", issuedOn: c.issuedOn ?? "", servedOn: c.servedOn ?? "", notes: c.notes ?? "" })

const longEnough = (s: string, what: string) => (s.trim().length < LIT_MIN_TEXT ? `Say ${what}, in at least ${LIT_MIN_TEXT} characters` : null)
export function litCaseProblem(f: LitCaseForm, creating: boolean, today: string): string | null {
  if (creating && !f.matterId) return "Choose the matter"
  if (f.courtName.trim().length < 2) return "Enter the name of the court"
  if (f.stage !== "PRE_ACTION" && f.caseNumber.trim().length < 2) return "Enter the case number. Proceedings that have been issued have one"
  if (f.issuedOn && f.issuedOn > today) return "The date issued cannot be in the future"
  if (f.servedOn && f.servedOn > today) return "The date served cannot be in the future"
  if (f.issuedOn && f.servedOn && f.servedOn < f.issuedOn) return "The case cannot be served before it was issued"
  return null
}
export function litCaseRequest(f: LitCaseForm, creating: boolean) {
  const n = (s: string) => s.trim() || null
  const base = { courtLevel: f.courtLevel, courtName: f.courtName.trim(), ourRole: f.ourRole, caseNumber: n(f.caseNumber), opposingParty: n(f.opposingParty), opposingAttorney: n(f.opposingAttorney),
    opposingAttorneyContact: n(f.opposingAttorneyContact), judge: n(f.judge), issuedOn: f.issuedOn || null, servedOn: f.servedOn || null, notes: n(f.notes) }
  return creating ? { ...base, matterId: f.matterId, stage: f.stage } : base
}
export const litStageProblem = (from: string, to: string, note: string): string | null => {
  if (!to || to === "CONCLUDED") return "Choose a stage"
  if (to === from) return "The case is already at that stage"
  const order = LIT_STAGES.map(s => s[0] as string)
  return order.indexOf(to) < order.indexOf(from) ? longEnough(note, "why the case is moving back") : null
}
export const litConcludeProblem = (outcome: string, on: string, note: string, issuedOn: string | null, today: string): string | null => {
  if (!outcome) return "Choose the outcome"
  if (!on) return "Enter the date the case ended"
  if (on > today) return "The date the case ended cannot be in the future"
  if (issuedOn && on < issuedOn) return "The case cannot end before it was issued"
  return longEnough(note, "how it ended")
}
export const litReopenProblem = (stage: string, reason: string): string | null => (!stage ? "Choose the stage to reopen it at" : longEnough(reason, "why the case is being reopened"))
export const litHearingProblem = (type: string, date: string): string | null => (!type ? "Choose the kind of hearing" : !date ? "Enter the date of the hearing" : null)
export const litPostponeProblem = (current: { hearingDate: string; hearingTime: string | null }, date: string, time: string, reason: string): string | null => {
  if (!date) return "Enter the new date"
  if (date === current.hearingDate && (time || "") === (current.hearingTime ?? "").slice(0, 5)) return "That is the date and time it is already set for"
  return longEnough(reason, "why it was postponed")
}
export const litHeldProblem = (hearingDate: string, outcome: string, today: string): string | null => (hearingDate > today ? "A hearing that has not happened yet cannot be marked as held" : longEnough(outcome, "what happened at the hearing"))
export const litOrderProblem = (orderDate: string, summary: string, due: string, today: string): string | null => {
  if (!orderDate) return "Enter the date of the order"
  if (orderDate > today) return "The date of the order cannot be in the future"
  if (due && due < orderDate) return "The date to comply by cannot be before the order"
  return longEnough(summary, "what the court ordered")
}
export const litCompliedProblem = (orderDate: string, on: string, today: string): string | null => (!on ? "Enter the date the order was complied with" : on > today ? "The date cannot be in the future" : on < orderDate ? "The order cannot be complied with before it was made" : null)

/** What the person can do on a case, from its state and their permissions. */
export function litActions(c: Pick<LitCase, "open">, p: { write: boolean; conclude: boolean }) {
  return { canEdit: p.write && c.open, canStage: p.write && c.open, canSchedule: p.write && c.open, canOrder: p.write && c.open, canConclude: p.conclude && c.open, canReopen: p.conclude && !c.open }
}
export const litHearingActions = (h: Pick<LitHearing, "status">, caseOpen: boolean, write: boolean) => ({ canChange: write && caseOpen && h.status === "SCHEDULED" })
export const litHearingTone = (status: string): Tone => (status === "SCHEDULED" ? "info" : status === "HELD" ? "success" : "neutral")
export const litStageTone = (stage: string): Tone => (stage === "CONCLUDED" ? "neutral" : stage === "TRIAL" || stage === "APPEAL" ? "warning" : "info")
export const litOutcomeTone = (o: string | null): Tone => (o === "WON" ? "success" : o === "LOST" ? "danger" : "neutral")
export const litTime = (t: string | null) => (t ? t.slice(0, 5) : "")
const LIT_EVENT_TEXT: Record<string, string> = {
  CASE_OPENED: "opened the case", DETAILS_EDITED: "changed the case details", STAGE_CHANGED: "moved the case", CASE_CONCLUDED: "concluded the case", CASE_REOPENED: "reopened the case",
  HEARING_SET: "set a hearing", HEARING_MOVED: "postponed a hearing", HEARING_HELD: "recorded a hearing as held", HEARING_CANCELLED: "cancelled a hearing", ORDER_RECORDED: "recorded a court order", ORDER_COMPLIED: "recorded compliance with an order",
}
export const litEventText = (e: Pick<LitEvent, "eventType" | "actorName" | "detail">) => `${e.actorName ?? "Someone"} ${LIT_EVENT_TEXT[e.eventType] ?? e.eventType.toLowerCase()}${e.detail ? `: ${e.detail}` : ""}`
/** Days from today to a date, for "in 3 days" / "2 days ago" on the court roll. */
export const litDaysAway = (date: string, today: string) => Math.round((Date.parse(date + "T00:00:00Z") - Date.parse(today + "T00:00:00Z")) / 86400000)
export const litDaysText = (n: number) => (n === 0 ? "Today" : n === 1 ? "Tomorrow" : n === -1 ? "Yesterday" : n > 0 ? `In ${n} days` : `${-n} days ago`)

// ---- tasks and workflows ---------------------------------------------------------------------------------------------------
export const TASK_PRIORITIES = [["LOW", "Low"], ["NORMAL", "Normal"], ["HIGH", "High"], ["URGENT", "Urgent"]] as const
export const TASK_STATUSES = [["OPEN", "Open"], ["IN_PROGRESS", "In progress"], ["DONE", "Done"], ["CANCELLED", "Cancelled"]] as const
export const TASK_ASSIGN_TO = [["STARTER", "Whoever applies the workflow"], ["NONE", "Nobody (assign later)"]] as const
export const TASK_MIN_REASON = 10, TASK_MAX_STEPS = 30, TASK_MAX_OFFSET = 730

export interface TaskSummary { id: string; matterId: string; matterLabel: string; clientId: string; title: string; priority: string; status: string; assigneeId: string | null; assigneeName: string | null; dueDate: string | null; overdue: boolean; workflowRunId: string | null; updatedAt: string }
export interface TaskEvent { eventType: string; actorName: string | null; detail: string | null; occurredAt: string }
export interface TaskDetail { task: TaskSummary; description: string | null; completedAt: string | null; completedByName: string | null; cancelReason: string | null; createdByName: string | null; createdAt: string; events: TaskEvent[] }
export interface WorkflowStep { stepNo: number; title: string; description: string | null; priority: string; offsetDays: number; assignTo: string }
export interface WorkflowSummary { id: string; name: string; description: string | null; matterType: string | null; active: boolean; steps: number; updatedAt: string }
export interface WorkflowDetail { template: WorkflowSummary; createdByName: string | null; steps: WorkflowStep[] }
export interface WorkflowRun { id: string; templateId: string; templateName: string; startDate: string; tasksCreated: number; appliedByName: string | null; appliedAt: string }
export interface ApplyResult { run: WorkflowRun; tasks: TaskSummary[]; warnings: string[] }

export interface TaskForm { matterId: string; title: string; description: string; priority: string; dueDate: string; assigneeId: string }
export const emptyTask = (matterId = ""): TaskForm => ({ matterId, title: "", description: "", priority: "NORMAL", dueDate: "", assigneeId: "" })
export const taskToForm = (t: TaskDetail): TaskForm => ({ matterId: t.task.matterId, title: t.task.title, description: t.description ?? "", priority: t.task.priority, dueDate: t.task.dueDate ?? "", assigneeId: t.task.assigneeId ?? "" })

export function taskProblem(f: TaskForm, creating: boolean): string | null {
  if (creating && !f.matterId) return "Choose the matter"
  if (f.title.trim().length < 2) return "Give the task a title"
  if (f.title.trim().length > 200) return "Keep the title to 200 characters"
  if (f.description.trim().length > 2000) return "Keep the description to 2000 characters"
  return null
}
export const taskRequest = (f: TaskForm, creating: boolean, assigneeName: string | null) => ({
  ...(creating ? { matterId: f.matterId, assigneeId: f.assigneeId || null, assigneeName: f.assigneeId ? assigneeName : null } : {}),
  title: f.title.trim(), description: f.description.trim() || null, priority: f.priority, dueDate: f.dueDate || null,
})
export const taskReasonProblem = (reason: string, what: string): string | null => (reason.trim().length < TASK_MIN_REASON ? `Say ${what}, in at least ${TASK_MIN_REASON} characters` : null)

/** What can be done to a task, from its status and the person's permission. A cancelled task is final; a done one can only be reopened. */
export function taskActions(status: string, write: boolean) {
  const live = status === "OPEN" || status === "IN_PROGRESS"
  return { canEdit: write && live, canAssign: write && live, canStart: write && status === "OPEN", canStop: write && status === "IN_PROGRESS", canComplete: write && live, canReopen: write && status === "DONE", canCancel: write && live }
}
export const taskTone = (status: string, overdue: boolean): Tone => (overdue ? "danger" : status === "DONE" ? "success" : status === "IN_PROGRESS" ? "info" : status === "CANCELLED" ? "neutral" : "warning")
export const taskStatusLabel = (s: string) => TASK_STATUSES.find(x => x[0] === s)?.[1] ?? s
export const taskPriorityLabel = (p: string) => TASK_PRIORITIES.find(x => x[0] === p)?.[1] ?? p
export const taskPriorityTone = (p: string): Tone => (p === "URGENT" ? "danger" : p === "HIGH" ? "warning" : "neutral")
/** "Overdue by 3 days", "Due today", "Due in 2 days" — or nothing for a finished task. */
export function taskDueText(due: string | null, status: string, today: string): string {
  if (!due || status === "DONE" || status === "CANCELLED") return due ? "" : "No due date"
  const n = Math.round((Date.parse(due + "T00:00:00Z") - Date.parse(today + "T00:00:00Z")) / 86400000)
  return n === 0 ? "Due today" : n === 1 ? "Due tomorrow" : n > 1 ? `Due in ${n} days` : n === -1 ? "Overdue by 1 day" : `Overdue by ${-n} days`
}
const TASK_EVENT_TEXT: Record<string, string> = { CREATED: "added the task", EDITED: "changed the task", REASSIGNED: "reassigned the task", STARTED: "started the task", STOPPED: "put the task back to open", COMPLETED: "completed the task", REOPENED: "reopened the task", CANCELLED: "cancelled the task" }
export const taskEventText = (e: Pick<TaskEvent, "eventType" | "actorName" | "detail">) => `${e.actorName ?? "Someone"} ${TASK_EVENT_TEXT[e.eventType] ?? e.eventType.toLowerCase()}${e.detail ? `: ${e.detail}` : ""}`

export interface StepForm { title: string; description: string; priority: string; offsetDays: string; assignTo: string }
export interface WorkflowForm { name: string; description: string; matterType: string; steps: StepForm[] }
export const emptyStep = (): StepForm => ({ title: "", description: "", priority: "NORMAL", offsetDays: "0", assignTo: "STARTER" })
export const emptyWorkflow = (): WorkflowForm => ({ name: "", description: "", matterType: "", steps: [emptyStep()] })
export const workflowToForm = (w: WorkflowDetail): WorkflowForm => ({
  name: w.template.name, description: w.template.description ?? "", matterType: w.template.matterType ?? "",
  steps: w.steps.map(s => ({ title: s.title, description: s.description ?? "", priority: s.priority, offsetDays: String(s.offsetDays), assignTo: s.assignTo })),
})
export function workflowProblem(f: WorkflowForm): string | null {
  if (f.name.trim().length < 2) return "Give the workflow a name"
  if (f.name.trim().length > 120) return "Keep the name to 120 characters"
  if (f.steps.length === 0) return "Add at least one step"
  if (f.steps.length > TASK_MAX_STEPS) return `A workflow can have up to ${TASK_MAX_STEPS} steps`
  for (let i = 0; i < f.steps.length; i++) {
    const s = f.steps[i], n = `Step ${i + 1}: `
    if (s.title.trim().length < 2) return n + "give it a title"
    if (s.title.trim().length > 200) return n + "keep the title to 200 characters"
    const o = Number(s.offsetDays)
    if (s.offsetDays.trim() === "" || !Number.isInteger(o) || o < 0 || o > TASK_MAX_OFFSET) return n + `the due date must be a whole number of days from 0 to ${TASK_MAX_OFFSET} after the start`
  }
  return null
}
export const workflowRequest = (f: WorkflowForm) => ({
  name: f.name.trim(), description: f.description.trim() || null, matterType: f.matterType.trim() || null,
  steps: f.steps.map(s => ({ title: s.title.trim(), description: s.description.trim() || null, priority: s.priority, offsetDays: Number(s.offsetDays), assignTo: s.assignTo })),
})
export const moveStep = <T,>(list: T[], from: number, to: number): T[] => {
  if (to < 0 || to >= list.length || from === to) return list
  const out = list.slice(); const [x] = out.splice(from, 1); out.splice(to, 0, x); return out
}
export const applyProblem = (matterId: string, start: string, today: string): string | null => {
  if (!matterId) return "Choose the matter"
  if (!start) return "Choose the start date"
  const days = Math.round((Date.parse(start + "T00:00:00Z") - Date.parse(today + "T00:00:00Z")) / 86400000)
  return Math.abs(days) > 365 ? "The start date must be within a year of today" : null
}
/** The due date each step would get, so the person sees it before applying. */
export const stepDue = (start: string, offset: number): string => { const d = new Date(start + "T12:00:00"); d.setDate(d.getDate() + offset); return iso(d) }

// ---- firm reports ----------------------------------------------------------------------------------------------------------
export const REPORTS = [["matters", "Matters"], ["unbilled", "Unbilled work"], ["debtors", "Debtors"], ["billing", "Billing"], ["trust", "Trust balances"]] as const
export type ReportKey = (typeof REPORTS)[number][0]
export interface MattersReport { rows: { status: string; matterType: string; count: number }[]; total: number; open: number; hidden: boolean }
export interface UnbilledReport { rows: { matterId: string; matterLabel: string; clientName: string; hours: number; timeValue: number; disbursements: number; total: number }[]; hours: number; timeValue: number; disbursements: number; total: number; truncated: boolean; hidden: boolean }
export interface DebtorsReport { rows: { invoiceId: string; invoiceNumber: string; clientName: string; matterLabel: string | null; issueDate: string; dueDate: string | null; total: number; paid: number; outstanding: number; daysOverdue: number; bucket: string }[]; buckets: { key: string; label: string; amount: number; count: number }[]; outstanding: number; overdue: number; truncated: boolean; hidden: boolean }
export interface BillingReport { from: string; to: string; months: { month: string; invoices: number; invoiced: number; paid: number; writtenOff: number }[]; invoiced: number; vat: number; paid: number; writtenOff: number; collectedPercent: number; hidden: boolean }
export interface TrustReport { asAt: string; rows: { clientId: string; clientName: string; balance: number; lastMovement: string | null }[]; total: number; overdrawn: number; hidden: boolean }

/** The first day of the current year, for the billing report's default range. */
export const reportYearStart = (today: string) => `${today.slice(0, 4)}-01-01`
export function reportRangeProblem(from: string, to: string, today: string): string | null {
  if (!from || !to) return "Choose a start date and an end date"
  if (to < from) return "The end date cannot be before the start date"
  if (to > today) return "The end date cannot be in the future"
  if (Date.parse(to + "T00:00:00Z") - Date.parse(from + "T00:00:00Z") > 3660 * 86400000) return "Choose a range of up to ten years"
  return null
}
export const reportAsAtProblem = (asAt: string, today: string): string | null => (!asAt ? "Choose a date" : asAt > today ? "The date cannot be in the future" : null)
export const reportUrl = (key: ReportKey, params: { from?: string; to?: string; asAt?: string }, csv = false) => ({
  path: `${csv ? `${key}.csv` : key}`,
  params: key === "billing" ? { from: params.from, to: params.to } : key === "trust" ? { asAt: params.asAt || undefined } : {},
})
export const bucketTone = (key: string): Tone => (key === "CURRENT" ? "success" : key === "D1_30" ? "info" : key === "D31_60" ? "warning" : "danger")
export const matterStatusText = (s: string) => ({ OPEN: "Open", ON_HOLD: "On hold", CLOSED: "Closed", ARCHIVED: "Archived" } as Record<string, string>)[s] ?? s
export const reportMonthText = (ym: string) => { const [y, m] = ym.split("-"); return `${MONTHS[Number(m) - 1] ?? m} ${y}` }
/** Rows of the matters report as a status by kind grid: the kinds that appear, the statuses that appear, and the counts. */
export function matterGrid(rows: MattersReport["rows"]) {
  const kinds = [...new Set(rows.map(r => r.matterType))].sort(), statuses = ["OPEN", "ON_HOLD", "CLOSED", "ARCHIVED"].filter(s => rows.some(r => r.status === s))
  return { kinds, statuses, cell: (s: string, k: string) => rows.find(r => r.status === s && r.matterType === k)?.count ?? 0 }
}
export const reportWallNote = "Some matters or clients are left out because of ethical walls, so these figures are not the whole firm's."

// ---- access: permissions, role templates and checks ------------------------------------------------------------------------
export interface AccessPermission { code: string; group: string; kind: string; level: string; secondPerson: boolean; description: string }
export interface AccessFinding { severity: string; code: string; message: string }
export interface AccessTemplate { key: string; name: string; description: string; permissions: string[]; findings: AccessFinding[] }
export interface AccessCatalogue { permissions: AccessPermission[]; templates: AccessTemplate[] }

export const accessLevelTone = (level: string): Tone => (level === "FINANCIAL" ? "danger" : level === "SENSITIVE" ? "warning" : "neutral")
export const accessLevelText = (level: string) => (level === "FINANCIAL" ? "Financial" : level === "SENSITIVE" ? "Sensitive" : "Normal")
export const accessFindingTone = (severity: string): Tone => (severity === "WARNING" ? "warning" : "info")
/** The permissions grouped in the order the groups first appear. */
export function accessGroups(perms: AccessPermission[]): { group: string; items: AccessPermission[] }[] {
  const out: { group: string; items: AccessPermission[] }[] = []
  for (const p of perms) { const g = out.find(x => x.group === p.group); if (g) g.items.push(p); else out.push({ group: p.group, items: [p] }) }
  return out
}
/** Plain text of a template's permissions, one per line, for pasting into the Roles screen's search or a ticket. */
export const accessClipboard = (t: Pick<AccessTemplate, "name" | "permissions">) => `${t.name}\n${[...t.permissions].sort().join("\n")}`
export const accessToggle = (set: string[], code: string): string[] => (set.includes(code) ? set.filter(c => c !== code) : [...set, code])
/** What the template gives, counted by how sensitive it is, e.g. "9 permissions, 2 financial". */
export function accessSummary(t: AccessTemplate, perms: AccessPermission[]): string {
  const by = new Map(perms.map(p => [p.code, p])), fin = t.permissions.filter(c => by.get(c)?.level === "FINANCIAL").length, sens = t.permissions.filter(c => by.get(c)?.level === "SENSITIVE").length
  const parts = [`${t.permissions.length} ${t.permissions.length === 1 ? "permission" : "permissions"}`]
  if (fin) parts.push(`${fin} financial`)
  if (sens) parts.push(`${sens} sensitive`)
  return parts.join(", ")
}
