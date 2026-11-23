// Contract rates, billing preview and invoice drafts of one site: wording and small checks only.
// The server computes every figure and enforces every rule; the checks here only save a round trip and mirror its limits.

export interface SiteRate {
  id: string; siteId: string; type: "HOURLY" | "MONTHLY_FIXED"; amountCents: number; currency: string; vatInclusive: boolean
  effectiveFrom: string; effectiveTo: string | null; note: string | null; createdAt: string
}
export interface BillingLine { rateId: string; type: string; rateCents: number; vatInclusive: boolean; hours: number; daysInForce: number; amountCents: number }
export interface BillingPreview {
  siteId: string; siteName: string; month: string; from: string; to: string; inProgress: boolean; basis: "SCHEDULED" | "ACTUAL"; currency: string
  excludesVat: boolean; lines: BillingLine[]; billedHours: number; unpricedHours: number; mixedVat: boolean; totalCents: number; warnings: string[]
}
export type DraftStatus = "DRAFT" | "APPROVED" | "VOID"
export interface InvoiceDraft {
  id: string; siteId: string; month: string; basis: string; currency: string; status: DraftStatus; totalCents: number; inProgress: boolean
  unpricedHours: number; mixedVat: boolean; note: string | null; createdAt: string; approvedAt: string | null; voidedAt: string | null; voidReason: string | null
  /** User ids of who saved, approved and voided it. Their names come with the opened draft (`people`). */
  createdBy?: string | null; approvedBy?: string | null; voidedBy?: string | null
}

/** The same limits the server applies (SiteRateRules): R100 000 an hour, R10 000 000 a month. */
export const MAX_HOURLY_CENTS = 10_000_000
export const MAX_MONTHLY_CENTS = 1_000_000_000
export const MIN_REASON = 5
export const MAX_TEXT = 500

const group = (n: string) => n.replace(/\B(?=(\d{3})+(?!\d))/g, " ")

/** 123456 -> "R1 234.56". Whole cents only, so no floating point. */
export function formatCents(cents: number, currency = "ZAR"): string {
  const sign = cents < 0 ? "-" : ""
  const abs = Math.abs(Math.round(cents))
  const symbol = currency === "ZAR" ? "R" : `${currency} `
  return `${sign}${symbol}${group(String(Math.floor(abs / 100)))}.${String(abs % 100).padStart(2, "0")}`
}

/** "185", "185.5", "185,50", "1 234.50" -> cents. Anything else (negative, zero, 3 decimals, text) -> null. */
export function parseRandsToCents(text: string): number | null {
  const t = text.replace(/\s+/g, "").replace(",", ".")
  const m = /^(\d{1,9})(?:\.(\d{1,2}))?$/.exec(t)
  if (!m) return null
  const cents = Number(m[1]) * 100 + Number((m[2] ?? "").padEnd(2, "0") || "0")
  return cents > 0 ? cents : null
}

export function hoursText(h: number): string {
  const r = Math.round(h * 100) / 100
  return `${r} ${r === 1 ? "hour" : "hours"}`
}

export function rateText(r: Pick<SiteRate, "type" | "amountCents" | "currency">): string {
  return `${formatCents(r.amountCents, r.currency)} ${r.type === "HOURLY" ? "per hour" : "per month, fixed"}`
}

export type RateState = "CURRENT" | "FUTURE" | "ENDED"
/** today is YYYY-MM-DD in the company's zone; the caller supplies it so this stays pure. */
export function rateState(r: Pick<SiteRate, "effectiveFrom" | "effectiveTo">, today: string): RateState {
  if (r.effectiveFrom > today) return "FUTURE"
  if (r.effectiveTo != null && r.effectiveTo < today) return "ENDED"
  return "CURRENT"
}
export const RATE_STATE_LABEL: Record<RateState, string> = { CURRENT: "In force", FUTURE: "Starts later", ENDED: "Ended" }

export function periodText(r: Pick<SiteRate, "effectiveFrom" | "effectiveTo">): string {
  return r.effectiveTo ? `${r.effectiveFrom} to ${r.effectiveTo}` : `From ${r.effectiveFrom}, no end date`
}

export interface RateForm { type: "HOURLY" | "MONTHLY_FIXED"; amount: string; from: string; to: string; note: string }

/** An error message for the form, or null when it looks fine. The server still has the last word. */
export function rateFormError(f: RateForm): string | null {
  const cents = parseRandsToCents(f.amount)
  if (cents == null) return "Enter the amount in rand, for example 185 or 185.50."
  const cap = f.type === "HOURLY" ? MAX_HOURLY_CENTS : MAX_MONTHLY_CENTS
  if (cents > cap) return `That is above the limit of ${formatCents(cap)} ${f.type === "HOURLY" ? "an hour" : "a month"}.`
  if (!/^\d{4}-\d{2}-\d{2}$/.test(f.from)) return "Choose the first day the rate applies."
  if (f.to && !/^\d{4}-\d{2}-\d{2}$/.test(f.to)) return "The last day is not a valid date."
  if (f.to && f.to < f.from) return "The last day cannot be before the first day."
  if (f.note.trim().length > MAX_TEXT) return `The note is too long (at most ${MAX_TEXT} characters).`
  return null
}

export function rateBody(f: RateForm, vatInclusive: boolean) {
  return { rateType: f.type, amountCents: parseRandsToCents(f.amount), vatInclusive, effectiveFrom: f.from, effectiveTo: f.to || null, note: f.note.trim() || null }
}

export const BASIS_LABEL: Record<string, string> = { SCHEDULED: "Scheduled hours", ACTUAL: "Hours actually worked" }
export const DRAFT_LABEL: Record<DraftStatus, string> = { DRAFT: "Draft", APPROVED: "Approved", VOID: "Void" }
export const DRAFT_TONE: Record<DraftStatus, "warn" | "ok" | "neutral"> = { DRAFT: "warn", APPROVED: "ok", VOID: "neutral" }

/** What stops a draft being approved, in the server's own order, or null. Unpriced hours and mixed VAT are not blocks: they need a tick. */
export function approveBlock(d: Pick<InvoiceDraft, "status" | "inProgress" | "totalCents">): string | null {
  if (d.status !== "DRAFT") return "Only a draft can be approved."
  if (d.inProgress) return "That month was still in progress when this was saved. Void it and make a new one once the month is over."
  if (d.totalCents <= 0) return "This draft bills nothing, so there is nothing to approve."
  return null
}
export function needsAcknowledgement(d: Pick<InvoiceDraft, "unpricedHours" | "mixedVat">): boolean { return d.unpricedHours > 0 || d.mixedVat }
export function acknowledgementText(d: Pick<InvoiceDraft, "unpricedHours" | "mixedVat">): string {
  const parts: string[] = []
  if (d.unpricedHours > 0) parts.push(`${hoursText(d.unpricedHours)} with no contract rate`)
  if (d.mixedVat) parts.push("a mix of VAT-inclusive and VAT-exclusive rates")
  return `I have checked this: ${parts.join(" and ")}.`
}
export const canVoid = (d: Pick<InvoiceDraft, "status">) => d.status === "DRAFT" || d.status === "APPROVED"
export const reasonError = (reason: string): string | null => {
  const t = reason.trim()
  if (t.length < MIN_REASON) return `Give a reason of at least ${MIN_REASON} characters.`
  if (t.length > MAX_TEXT) return `The reason is too long (at most ${MAX_TEXT} characters).`
  return null
}

/** A draft can be saved only for a month the preview can show; a month already holding a live draft says so instead of asking the server. */
export function createBlock(month: string, drafts: Pick<InvoiceDraft, "month" | "status">[]): string | null {
  const live = drafts.find(d => d.month === month && d.status !== "VOID")
  if (live) return `There is already ${live.status === "APPROVED" ? "an approved" : "an"} invoice draft for this month. Void it to make a new one.`
  return null
}

/** The server's own wording first; otherwise something that says what failed and what it did not do. */
export function billingErrorText(e: unknown, fallback: string): string {
  const r = (e as { response?: { status?: number; data?: { message?: unknown } } } | null)?.response
  const m = r?.data?.message
  if (typeof m === "string" && m.trim()) return m
  if (r?.status === 403) return "You do not have permission to do this."
  if (r?.status === 404) return "This site or record is not available to you."
  return fallback
}
