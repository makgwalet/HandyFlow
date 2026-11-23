// Invoice drafts seen across sites, and one draft opened up: wording and small checks only.
// The figures are the server's. A draft's snapshot is the billing preview exactly as it was when someone said "bill this".
import { BASIS_LABEL, formatCents, hoursText, type BillingPreview, type DraftStatus, type InvoiceDraft } from "./billingLogic"

export interface DraftOverviewItem extends InvoiceDraft { siteName: string }
export interface DraftOverview { items: DraftOverviewItem[]; truncated: boolean }
export interface DraftDetail { draft: InvoiceDraft; snapshot: unknown; people?: Record<string, string> }

export const UNKNOWN_PERSON = "a user who is no longer listed"

/** The name for a user id. Without a `people` list at all there is no name to show; with one, an id missing from it is someone who is gone. */
export function personText(id: string | null | undefined, people: Record<string, string> | undefined): string | null {
  if (!id || !people) return null
  return people[id] ?? UNKNOWN_PERSON
}

/** "Saved by Thandi Nkosi, 2 Oct 2026 10:00", or "Saved 2 Oct 2026 10:00" when nobody is named. */
export function byLine(label: string, who: string | null, when: string): string { return who ? `${label} by ${who}, ${when}` : `${label} ${when}` }

export const STATUS_FILTERS: { value: "" | DraftStatus; label: string }[] = [
  { value: "", label: "All statuses" }, { value: "DRAFT", label: "Waiting for approval" }, { value: "APPROVED", label: "Approved" }, { value: "VOID", label: "Void" },
]

/** The server wants 2026-10 or nothing. Anything else typed into the month box is not sent. */
export function monthParam(text: string): string | undefined { return /^\d{4}-(0[1-9]|1[0-2])$/.test(text.trim()) ? text.trim() : undefined }

export function overviewParams(month: string, status: string): Record<string, string> {
  const p: Record<string, string> = {}
  const m = monthParam(month); if (m) p.month = m
  if (status) p.status = status
  return p
}

export interface OverviewSummary { waiting: number; approved: number; voided: number; liveByCurrency: { currency: string; cents: number }[] }

/** Counts per status, and what the live (waiting or approved) drafts add up to, per currency so rand is never added to anything else. */
export function summarise(items: Pick<DraftOverviewItem, "status" | "totalCents" | "currency">[]): OverviewSummary {
  let waiting = 0, approved = 0, voided = 0
  const sums = new Map<string, number>()
  for (const i of items) {
    if (i.status === "VOID") { voided++; continue }
    if (i.status === "DRAFT") waiting++; else approved++
    sums.set(i.currency, (sums.get(i.currency) ?? 0) + i.totalCents)
  }
  return { waiting, approved, voided, liveByCurrency: [...sums].map(([currency, cents]) => ({ currency, cents })) }
}

export function liveTotalText(s: Pick<OverviewSummary, "liveByCurrency">): string {
  return s.liveByCurrency.length === 0 ? formatCents(0) : s.liveByCurrency.map(x => formatCents(x.cents, x.currency)).join(" + ")
}

/** Reasons to look twice, in words. Empty when there is nothing to flag. */
export function flagsOf(d: Pick<InvoiceDraft, "inProgress" | "unpricedHours" | "mixedVat">): string[] {
  const out: string[] = []
  if (d.inProgress) out.push("Month was still in progress")
  if (d.unpricedHours > 0) out.push(`${hoursText(d.unpricedHours)} with no rate`)
  if (d.mixedVat) out.push("Mixed VAT")
  return out
}

export interface SnapshotView { preview: BillingPreview | null; problem: string | null }

const isNum = (v: unknown): v is number => typeof v === "number" && Number.isFinite(v)

/**
 * The frozen preview, if it can be read, and a plain sentence when it cannot or when it does not agree with the draft it belongs to.
 * A snapshot that disagrees is shown anyway: hiding it would hide the very thing to check.
 */
export function readSnapshot(detail: DraftDetail): SnapshotView {
  const s = detail.snapshot as Partial<BillingPreview> | null
  if (!s || typeof s !== "object" || !Array.isArray(s.lines) || !isNum(s.totalCents)) {
    return { preview: null, problem: "The saved copy of the figures could not be read, so only the totals above are available." }
  }
  const lineSum = s.lines.reduce((n, l) => n + (isNum(l?.amountCents) ? l.amountCents : 0), 0)
  const preview = s as BillingPreview
  if (s.totalCents !== detail.draft.totalCents) return { preview, problem: `The saved copy totals ${formatCents(s.totalCents, detail.draft.currency)} but the draft says ${formatCents(detail.draft.totalCents, detail.draft.currency)}. Do not approve this one; void it and save again.` }
  if (lineSum !== s.totalCents) return { preview, problem: `The lines add up to ${formatCents(lineSum, detail.draft.currency)} but the total says ${formatCents(s.totalCents, detail.draft.currency)}.` }
  return { preview, problem: null }
}

export const detailHeading = (d: Pick<InvoiceDraft, "basis">): string => `Figures as they were when this was saved (${(BASIS_LABEL[d.basis] ?? d.basis).toLowerCase()})`
