// Allowed values and checks for agreements. The server enforces every rule; this keeps obviously wrong input from
// being sent, previews the billing dates while the terms are being filled in, and says what a person may do next.

export const MIN_NOTE = 10
export const MAX_TERM_YEARS = 10
export const MAX_OPEN_DATES = 24
export const MAX_DATES = 120

export const STATUS_LABELS: Record<string, string> = { DRAFT: "Draft", ACTIVE: "Active", SUSPENDED: "Suspended", ENDED: "Ended", CANCELLED: "Cancelled" }
export const FILTERS: [string, string][] = [["", "All"], ["ACTIVE", "Active"], ["DRAFT", "Draft"], ["SUSPENDED", "Suspended"], ["ENDED", "Ended"], ["CANCELLED", "Cancelled"]]
export const FREQUENCIES: [string, string][] = [["ONCE", "Once"], ["MONTHLY", "Every month"], ["QUARTERLY", "Every quarter"], ["ANNUALLY", "Every year"]]

export const statusLabel = (s: string) => STATUS_LABELS[s] ?? s
export const frequencyLabel = (f: string) => FREQUENCIES.find(x => x[0] === f)?.[1] ?? f

export type TermsDraft = {
  title: string; startDate: string; endDate: string; billingFrequency: string; billingAmount: string; firstBillingDate: string
  autoRenew: boolean; noticeDays: string; notes: string; terms: string
}

export const num = (s: string) => (s.trim() === "" ? null : Number(s))

const monthsPer = (f: string) => (f === "MONTHLY" ? 1 : f === "QUARTERLY" ? 3 : f === "ANNUALLY" ? 12 : 0)

const parse = (s: string) => { const [y, m, d] = s.split("-").map(Number); return { y, m, d } }
const pad = (n: number) => String(n).padStart(2, "0")

/** The date a number of months after another, clamped to the last day of a short month (31 Jan + 1 month = 28 Feb). */
export function addMonths(date: string, months: number): string {
  const { y, m, d } = parse(date)
  const total = (m - 1) + months
  const ny = y + Math.floor(total / 12)
  const nm = ((total % 12) + 12) % 12
  const last = new Date(Date.UTC(ny, nm + 1, 0)).getUTCDate()
  return `${ny}-${pad(nm + 1)}-${pad(Math.min(d, last))}`
}

/** Billing dates counted from the first, so the 31st comes back after a short month. Open-ended terms show a capped preview. */
export function schedule(first: string, end: string, freq: string): string[] {
  if (!first || !freq) return []
  if (end && first > end) return []
  if (freq === "ONCE") return [first]
  const step = monthsPer(freq)
  if (!step) return []
  const cap = end ? MAX_DATES : MAX_OPEN_DATES
  const out: string[] = []
  for (let i = 0; i < cap; i++) {
    const d = addMonths(first, i * step)
    if (end && d > end) break
    out.push(d)
  }
  return out
}

export function termsProblem(d: TermsDraft): string | null {
  if (!d.startDate) return "A start date is required"
  if (d.endDate) {
    if (d.endDate < d.startDate) return "The end date cannot be before the start date"
    if (d.endDate > addMonths(d.startDate, MAX_TERM_YEARS * 12)) return `An agreement cannot run for more than ${MAX_TERM_YEARS} years. Use a shorter term and renew it`
  }
  if (!FREQUENCIES.some(f => f[0] === d.billingFrequency)) return "Choose how often the client is billed"
  const amount = num(d.billingAmount)
  if (amount === null || Number.isNaN(amount) || amount <= 0) return "The amount billed each time must be more than zero"
  if (Math.round(amount * 100) / 100 !== amount) return "The amount cannot have more than two decimal places"
  if (!d.firstBillingDate) return "A first billing date is required"
  if (d.firstBillingDate < d.startDate) return "The first billing date cannot be before the start date"
  if (d.endDate && d.firstBillingDate > d.endDate) return "The first billing date cannot be after the end date"
  const notice = num(d.noticeDays)
  if (notice === null || !Number.isInteger(notice) || notice < 0 || notice > 365) return "Notice must be between 0 and 365 days"
  return null
}

/** What the draft will bill over the term, or null when it has no end to count to. */
export function previewTotal(d: TermsDraft): number | null {
  const amount = num(d.billingAmount)
  if (!d.endDate || amount === null || Number.isNaN(amount)) return null
  return Math.round(amount * schedule(d.firstBillingDate, d.endDate, d.billingFrequency).length * 100) / 100
}

export function defaultTerms(title: string | null, professional: number, today: string): TermsDraft {
  return {
    title: title ?? "", startDate: today, endDate: "", billingFrequency: "ONCE", billingAmount: String(professional), firstBillingDate: today,
    autoRenew: false, noticeDays: "30", notes: "", terms: "",
  }
}

export function toBody(d: TermsDraft) {
  return {
    title: d.title.trim() || null, startDate: d.startDate || null, endDate: d.endDate || null, billingFrequency: d.billingFrequency,
    billingAmount: num(d.billingAmount), firstBillingDate: d.firstBillingDate || null, autoRenew: d.autoRenew, noticeDays: num(d.noticeDays),
    notes: d.notes.trim() || null, terms: d.terms.trim() || null,
  }
}

export type ActionId = "activate" | "suspend" | "resume" | "end" | "cancel"
export type Action = { id: ActionId; label: string; noteLabel?: string; minNote?: number; needsDate?: boolean }

const ALL: Record<ActionId, Action> = {
  activate: { id: "activate", label: "Activate", noteLabel: "How the client confirmed", minNote: MIN_NOTE },
  suspend: { id: "suspend", label: "Suspend", noteLabel: "Why it is suspended", minNote: MIN_NOTE },
  resume: { id: "resume", label: "Resume" },
  end: { id: "end", label: "End", noteLabel: "Why it is ending", minNote: MIN_NOTE, needsDate: true },
  cancel: { id: "cancel", label: "Cancel draft", noteLabel: "Why it is cancelled", minNote: MIN_NOTE },
}

/** What a person may do next. The server decides what is allowed by status; this only words and gates the buttons. */
export function actionsFor(serverActions: string[], canManage: boolean): Action[] {
  if (!canManage) return []
  const out: Action[] = []
  for (const id of serverActions) { const a = ALL[id as ActionId]; if (a) out.push(a) }
  return out
}

export function actionProblem(a: Action, note: string, endDate: string, startDate: string): string | null {
  if (a.needsDate) {
    if (!endDate) return "Say the date it ends"
    if (endDate < startDate) return "The end date cannot be before the start date"
  }
  if (a.minNote && note.trim().length < a.minNote) return `${a.noteLabel} needs at least ${a.minNote} characters`
  return null
}
