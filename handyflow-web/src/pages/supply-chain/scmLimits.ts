// Pure helpers for approval limits, budget allocation and supplier portal access (no React, no network).

export const LIMIT_DOCS: { value: string; label: string }[] = [
  { value: "*", label: "All documents" },
  { value: "REQUISITION", label: "Requisitions" },
  { value: "PURCHASE_ORDER", label: "Purchase orders" },
  { value: "RFQ_AWARD", label: "RFQ awards" },
  { value: "PO_AMENDMENT", label: "Order amendments" },
  { value: "SUPPLIER_INVOICE", label: "Supplier invoices" },
  { value: "PRICE_OVERRIDE", label: "Price increases" },
]
export const docLabel = (v: string) => LIMIT_DOCS.find(d => d.value === v)?.label ?? v

export interface LimitForm { subjectType: "USER" | "ROLE"; subjectId: string; documentType: string; amount: string; unlimited: boolean }
export const emptyLimit = (): LimitForm => ({ subjectType: "ROLE", subjectId: "", documentType: "*", amount: "", unlimited: false })

export function limitProblem(f: LimitForm): string | null {
  if (!f.subjectId) return f.subjectType === "ROLE" ? "Choose a role" : "Choose a person"
  if (f.unlimited) return null
  if (f.amount.trim() === "") return "Enter an amount, or tick Unlimited"
  const n = Number(f.amount)
  if (!Number.isFinite(n) || n < 0) return "The amount must be zero or more"
  return null
}

export const limitBody = (f: LimitForm) => ({
  subjectType: f.subjectType, subjectId: f.subjectId, documentType: f.documentType,
  maxAmount: f.unlimited ? null : Number(f.amount),
})

export const limitText = (max: number | null | undefined) =>
  max == null ? "Unlimited" : `R ${Number(max).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`

export function authorityText(a: { granted: boolean; unlimited: boolean; maxAmount: number | null }): string {
  if (!a.granted) return "No limit set (cannot approve)"
  return a.unlimited ? "Unlimited" : `Up to ${limitText(a.maxAmount)}`
}

/** What switching enforcement on would do, in words, so nobody turns it on blind. */
export function enforceHelp(mode: string, ready: boolean): string {
  if (mode === "ENFORCE") return "Limits are enforced: an approval above the approver's limit is refused."
  return ready
    ? "Limits are not enforced yet. Everyone can approve any amount until you switch this on."
    : "Before limits can be switched on, give a person or a role an unlimited limit for all documents, so large documents can still be approved."
}

// ── Budget allocation ──
export interface BudgetNode { id: string; scopeType: string; periodStart: string; periodEnd: string; status: string; amount: number; currency?: string }
const RANK: Record<string, number> = { DEPARTMENT: 0, COST_CENTRE: 1, PROJECT: 2 }

/** Budgets the new one could be allocated out of: active, same or broader scope, and covering its whole period. */
export function parentChoices<T extends BudgetNode>(all: T[], child: { scopeType: string; periodStart: string; periodEnd: string }): T[] {
  if (!child.periodStart || !child.periodEnd) return []
  return all.filter(b => b.status === "ACTIVE" && RANK[b.scopeType] <= RANK[child.scopeType]
    && b.periodStart <= child.periodStart && b.periodEnd >= child.periodEnd)
}

export interface AllocationLike { amount: number; allocated: number; unallocated: number; childCount: number }
export function allocationText(b: AllocationLike): string {
  if (!b.childCount) return "Not split"
  const pct = b.amount > 0 ? Math.round((b.allocated / b.amount) * 100) : 0
  return `${pct}% allocated to ${b.childCount} sub-budget${b.childCount === 1 ? "" : "s"}`
}

export function flattenTree<T extends { children: T[] }>(nodes: T[], depth = 0): { node: T; depth: number }[] {
  return nodes.flatMap(n => [{ node: n, depth }, ...flattenTree(n.children, depth + 1)])
}

// ── Supplier portal ──
export function inviteProblem(email: string): string | null {
  return /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim()) ? null : "Enter a valid email address"
}

export function grantStatusTone(status: string): "ok" | "warn" | "neutral" {
  return status === "ACTIVE" ? "ok" : status === "PENDING" ? "warn" : "neutral"
}

export function quoteStatusText(inv: string, quote: string | null, open: boolean): string {
  if (quote === "SUBMITTED") return open ? "Quoted: you can still revise it" : "Quoted"
  if (quote === "WITHDRAWN") return "Withdrawn"
  if (quote === "DISQUALIFIED") return "Disqualified"
  if (inv === "DECLINED") return "You declined"
  return open ? "Waiting for your quotation" : "Closed"
}

export function registerProblem(password: string, confirm: string, name: string): string | null {
  if (!name.trim()) return "Enter your name"
  if (password.length < 10) return "Choose a password of at least 10 characters"
  if (password !== confirm) return "The passwords do not match"
  return null
}
