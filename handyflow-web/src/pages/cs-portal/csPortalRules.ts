// Wording and ordering for the client portal. Pure, so it can be tested without a screen.
import type { CsPortalAgreement, CsPortalInvoice, CsPortalQuote } from "../../api/csPortal.api"

export type Tone = "neutral" | "info" | "success" | "warning" | "danger"
export const MIN_PASSWORD = 8

export const FREQUENCY: Record<string, string> = { ONCE: "once", MONTHLY: "every month", QUARTERLY: "every quarter", ANNUALLY: "every year" }

export const QUOTE_STATUS: Record<string, [string, Tone]> = {
  SENT: ["Waiting for your answer", "info"], ACCEPTED: ["Accepted", "success"], DECLINED: ["Declined", "neutral"], EXPIRED: ["Expired", "warning"],
}
export const AGREEMENT_STATUS: Record<string, [string, Tone]> = { ACTIVE: ["Active", "success"], SUSPENDED: ["Paused", "warning"], ENDED: ["Ended", "neutral"] }
export const INVOICE_STATUS: Record<string, [string, Tone]> = {
  ISSUED: ["Issued", "info"], PARTIALLY_PAID: ["Part paid", "warning"], PAID: ["Paid", "success"], OVERDUE: ["Overdue", "danger"], OVERPAID: ["Overpaid", "neutral"],
}
const pick = (m: Record<string, [string, Tone]>, s: string): [string, Tone] => m[s] ?? [s.toLowerCase().replace(/_/g, " "), "neutral"]
export const quoteStatus = (s: string) => pick(QUOTE_STATUS, s)
export const agreementStatus = (s: string) => pick(AGREEMENT_STATUS, s)
export const invoiceStatus = (s: string) => pick(INVOICE_STATUS, s)

export function passwordProblem(password: string, confirm: string): string | null {
  if (password.length < MIN_PASSWORD) return `Password must be at least ${MIN_PASSWORD} characters`
  if (password !== confirm) return "Passwords don't match"
  return null
}

export type Item = { kind: "quote" | "invoice" | "agreement"; text: string; tone: Tone }

const daysBetween = (from: string, to: string) => Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000)

/** What needs the client's attention, most pressing first: invoices owing, quotes waiting for an answer, an agreement that is ending. */
export function attention(quotes: CsPortalQuote[], agreements: CsPortalAgreement[], invoices: CsPortalInvoice[], today: string): Item[] {
  const out: Item[] = []
  for (const i of invoices) {
    if (i.outstanding > 0) out.push({ kind: "invoice", tone: i.status === "OVERDUE" ? "danger" : "warning", text: `Invoice ${i.invoiceNumber} has R ${i.outstanding.toFixed(2)} still owing` })
  }
  for (const q of quotes) {
    if (q.status !== "SENT" || !q.validUntil) continue
    const d = daysBetween(today, q.validUntil)
    out.push({ kind: "quote", tone: d <= 7 ? "warning" : "info", text: `Quote ${q.quoteNumber} is waiting for your answer, and stands until ${q.validUntil}${d >= 0 ? ` (${d} day${d === 1 ? "" : "s"})` : ""}` })
  }
  for (const a of agreements) {
    if (a.status !== "ACTIVE" || !a.endDate) continue
    const d = daysBetween(today, a.endDate)
    if (d >= 0 && d <= 60) out.push({ kind: "agreement", tone: "info", text: `Agreement ${a.agreementNumber} ends on ${a.endDate}` })
  }
  return out
}

/** The next amount and date the client will be billed across live agreements, or null. */
export function nextBill(agreements: CsPortalAgreement[]): { date: string; amount: number } | null {
  let best: { date: string; amount: number } | null = null
  for (const a of agreements) {
    if (a.status !== "ACTIVE") continue
    const b = a.nextBills[0]
    if (b && (!best || b.date < best.date)) best = b
  }
  return best
}
