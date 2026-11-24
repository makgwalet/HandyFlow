// Allowed values and checks for quotes. The server prices every line and enforces every rule; this keeps obviously
// wrong input from being sent, says what a person may do next, and words the status.
import { inputsNeeded, calcProblem, percentProblem, type Disb, type Extra } from "./pricingRules"

export const MIN_NOTE = 10
export const MIN_EXCEPTION_REASON = 15
export const MAX_VALID_DAYS = 365

export const STATUS_LABELS: Record<string, string> = {
  DRAFT: "Draft", PENDING_APPROVAL: "Waiting for approval", APPROVED: "Approved", SENT: "Sent", ACCEPTED: "Accepted", DECLINED: "Declined",
  WITHDRAWN: "Withdrawn", EXPIRED: "Expired",
}
export const FILTERS: [string, string][] = [["", "All"], ["PENDING_APPROVAL", "Waiting for approval"], ["APPROVED", "Approved"], ["SENT", "Sent"], ["ACCEPTED", "Accepted"], ["DRAFT", "Draft"], ["EXPIRED", "Expired"]]

/** Who must agree the discounts on a quote. These are proposed starting limits, not approved policy. */
export const APPROVER_LABELS: Record<string, string> = {
  CONSULTANT: "No approval needed",
  COMPLIANCE_MANAGER: "A compliance manager must approve",
  PRACTICE_OWNER: "The practice owner must approve",
  PRACTICE_OWNER_WITH_EXCEPTION: "The practice owner must approve and record an exception",
}

export const statusLabel = (s: string) => STATUS_LABELS[s] ?? s

export type QuoteLineDraft = {
  serviceCode: string; description: string; units: string; hours: string; complexityPct: string; urgent: boolean; discountPct: string
  extras: Extra[]; disbursements: Disb[]
}
export type QuoteDraft = { title: string; validUntil: string; notes: string; terms: string; lines: QuoteLineDraft[] }
export type ServiceBasis = { serviceCode: string; pricingModel: string; hourlyGrade: string | null }

export const emptyQuoteLine = (): QuoteLineDraft => ({ serviceCode: "", description: "", units: "", hours: "", complexityPct: "", urgent: false, discountPct: "", extras: [], disbursements: [] })

export const num = (s: string) => (s.trim() === "" ? null : Number(s))

export const daysBetween = (from: string, to: string) => Math.round((Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`)) / 86_400_000)

export function validUntilProblem(validUntil: string, today: string): string | null {
  if (!validUntil) return "Say how long the quote stands"
  const d = daysBetween(today, validUntil)
  if (Number.isNaN(d)) return "Choose a valid date"
  if (d < 0) return "A quote cannot stand until a date that has passed"
  if (d > MAX_VALID_DAYS) return `A quote can stand for at most ${MAX_VALID_DAYS} days`
  return null
}

/** The problem with one line, or null. */
export function lineProblem(l: QuoteLineDraft, basis: ServiceBasis | undefined): string | null {
  if (!l.serviceCode) return "Choose the service"
  if (!basis) return `${l.serviceCode} has no price in the price book in force`
  const needs = inputsNeeded(basis.pricingModel, !!basis.hourlyGrade)
  return calcProblem({ units: l.units, hours: l.hours, complexityPct: l.complexityPct, discountPct: l.discountPct, extras: l.extras, disbursements: l.disbursements }, needs, basis.pricingModel)
}

/** Anything that stops the draft being saved at all. */
export function draftProblem(d: QuoteDraft, bases: ServiceBasis[], today: string): string | null {
  if (d.validUntil) { const v = validUntilProblem(d.validUntil, today); if (v) return v }
  const codes = d.lines.map(l => l.serviceCode).filter(Boolean)
  const dup = codes.find((c, i) => codes.indexOf(c) !== i)
  if (dup) return `${dup} is on this quote twice. Change its quantity or add an extra instead`
  for (const l of d.lines) { const p = lineProblem(l, bases.find(b => b.serviceCode === l.serviceCode)); if (p) return p }
  return null
}

/** What the quote still needs before it can be submitted. */
export function missingToSubmit(d: QuoteDraft, today: string): string[] {
  const m: string[] = []
  if (d.lines.length === 0) m.push("at least one service")
  const v = validUntilProblem(d.validUntil, today)
  if (v) m.push("a date it stands until")
  return m
}

export function toBody(d: QuoteDraft) {
  return {
    title: d.title.trim() || null, validUntil: d.validUntil || null, notes: d.notes.trim() || null, terms: d.terms.trim() || null,
    lines: d.lines.map(l => ({
      serviceCode: l.serviceCode, description: l.description.trim() || null, units: num(l.units), hours: num(l.hours), complexityPct: num(l.complexityPct),
      urgent: l.urgent, discountPct: num(l.discountPct),
      extras: l.extras.map(e => ({ description: e.description.trim(), quantity: num(e.quantity), unitPrice: num(e.unitPrice) })),
      disbursements: l.disbursements.map(x => ({ description: x.description.trim(), amount: num(x.amount), vatable: x.vatable })),
    })),
  }
}

export type ActionId = "submit" | "approve" | "reject" | "sent" | "accept" | "decline" | "withdraw"
export type Action = { id: ActionId; label: string; noteLabel?: string; minNote?: number }

/** What a person may do with a quote in its status. Approving and sending back are for administrators. */
export function actionsFor(status: string, canManage: boolean, canApprove: boolean): Action[] {
  const out: Action[] = []
  if (status === "DRAFT" && canManage) out.push({ id: "submit", label: "Submit" })
  if (status === "PENDING_APPROVAL" && canApprove) {
    out.push({ id: "approve", label: "Approve" })
    out.push({ id: "reject", label: "Send back", noteLabel: "What needs to change", minNote: MIN_NOTE })
  }
  if (status === "APPROVED" && canManage) out.push({ id: "sent", label: "Mark as sent" })
  if (status === "SENT" && canManage) {
    out.push({ id: "accept", label: "Record acceptance", noteLabel: "How and when the client accepted", minNote: MIN_NOTE })
    out.push({ id: "decline", label: "Record decline", noteLabel: "The client's reason", minNote: MIN_NOTE })
  }
  if (["DRAFT", "PENDING_APPROVAL", "APPROVED", "SENT"].includes(status) && canManage) out.push({ id: "withdraw", label: "Withdraw", noteLabel: "Why it is withdrawn", minNote: MIN_NOTE })
  return out
}

/** The reason an approver must give when they wrote or submitted the quote. */
export function approveNeedsReason(q: { createdBy: string | null; submittedBy: string | null }, userId: string | null | undefined): boolean {
  return !!userId && (q.createdBy === userId || q.submittedBy === userId)
}

export function actionProblem(a: Action, note: string, needsReason: boolean, reason: string): string | null {
  if (a.id === "approve" && needsReason && reason.trim().length < MIN_EXCEPTION_REASON) {
    return `You wrote or submitted this, so someone else should approve the discount. If no one else can, give a reason of at least ${MIN_EXCEPTION_REASON} characters.`
  }
  if (a.minNote && note.trim().length < a.minNote) return `${a.noteLabel} needs at least ${a.minNote} characters`
  return null
}

export function expiryText(validUntil: string | null, today: string, status: string): string {
  if (!validUntil) return ""
  if (["ACCEPTED", "DECLINED", "WITHDRAWN"].includes(status)) return `Stood until ${validUntil}`
  const d = daysBetween(today, validUntil)
  if (d < 0) return `Expired ${validUntil}`
  if (d === 0) return "Stands until today"
  return `Stands until ${validUntil} (${d} day${d === 1 ? "" : "s"})`
}

export { percentProblem }
