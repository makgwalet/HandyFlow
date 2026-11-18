// Pure helpers for the Supply Chain screens: requisitions, budgets, RFQs. No React, so they are tested on their own.
// The server judges everything again; these only stop obviously wrong input from leaving the screen.

/** Arrays come back as { data: [...] }, { data: { content: [...] } } or the bare array. */
export function asList<T>(res: unknown): T[] {
  const r = res as { data?: { data?: unknown } & Record<string, unknown> } | undefined
  const d = (r?.data?.data ?? r?.data ?? []) as unknown
  if (Array.isArray(d)) return d as T[]
  const c = (d as { content?: unknown } | null)?.content
  return Array.isArray(c) ? (c as T[]) : []
}

export function asOne<T>(res: unknown): T | undefined {
  const r = res as { data?: { data?: unknown } & Record<string, unknown> } | undefined
  return (r?.data?.data ?? r?.data) as T | undefined
}

export function errorText(e: unknown, fallback = "Something went wrong"): string {
  const x = e as { response?: { data?: { message?: string } }; message?: string }
  return x?.response?.data?.message || x?.message || fallback
}

// ── Requisitions ────────────────────────────────────────────────────────────────────────────────────────────────────────────────
export const REQ_TYPES = ["OPERATIONAL", "STOCK_REPLENISHMENT", "CAPEX", "PROJECT", "EMERGENCY", "SERVICE", "CONTRACT_RENEWAL", "RECURRING"] as const

export interface ReqLineForm { itemName: string; quantity: string; estimatedUnitCost: string; vatRate: string }
export interface ReqForm { type: string; title: string; businessReason: string; department: string; costCentre: string; projectRef: string; requiredByDate: string; lines: ReqLineForm[] }

export const emptyReqLine = (): ReqLineForm => ({ itemName: "", quantity: "1", estimatedUnitCost: "", vatRate: "" })

const num = (s: string) => (s.trim() === "" ? NaN : Number(s))

export function requisitionTotal(lines: ReqLineForm[]): number {
  return lines.reduce((t, l) => {
    const q = num(l.quantity), c = num(l.estimatedUnitCost)
    return Number.isFinite(q) && Number.isFinite(c) ? t + q * c : t
  }, 0)
}

export function requisitionProblem(f: ReqForm): string | null {
  if (!f.title.trim()) return "Give the requisition a title"
  if (!f.department.trim() && !f.costCentre.trim() && !f.projectRef.trim()) return "Say which department, cost centre or project this is for — the budget check needs it"
  if (f.lines.length === 0) return "Add at least one line"
  for (const [i, l] of f.lines.entries()) {
    if (!l.itemName.trim()) return `Line ${i + 1}: say what is needed`
    const q = num(l.quantity), c = num(l.estimatedUnitCost)
    if (!(q > 0)) return `Line ${i + 1}: quantity must be above zero`
    if (!(c >= 0)) return `Line ${i + 1}: enter the estimated unit cost`
  }
  return null
}

export function requisitionBody(f: ReqForm) {
  return {
    type: f.type, title: f.title.trim(), businessReason: f.businessReason.trim() || null,
    department: f.department.trim() || null, costCentre: f.costCentre.trim() || null, projectRef: f.projectRef.trim() || null,
    requiredByDate: f.requiredByDate || null, currency: "ZAR",
    lines: f.lines.map(l => ({ catalogueItemId: null, itemName: l.itemName.trim(), description: null, quantity: Number(l.quantity),
      estimatedUnitCost: Number(l.estimatedUnitCost), vatRate: l.vatRate.trim() === "" ? null : Number(l.vatRate), suggestedSupplierId: null })),
  }
}

export type ReqAction = "submit" | "approve" | "reject" | "withdraw" | "reopen" | "cancel" | "convert"

/** What a person may do next to a requisition in this status. Approving needs a reason only when rejecting; the server decides who may. */
export function requisitionActions(status: string, fulfilment: string | null, isApprover: boolean, isRequester: boolean): ReqAction[] {
  switch (status) {
    case "DRAFT": return isRequester ? ["submit", "cancel"] : []
    case "PENDING_APPROVAL": return [...(isApprover ? ["approve", "reject"] as ReqAction[] : []), ...(isRequester ? ["withdraw"] as ReqAction[] : [])]
    case "REJECTED": return isRequester ? ["reopen", "cancel"] : []
    case "APPROVED": return [...(isRequester ? (fulfilment === "ORDERED" ? [] : ["convert"] as ReqAction[]) : []), ...(isRequester && fulfilment === "NOT_ORDERED" ? ["cancel"] as ReqAction[] : [])]
    default: return []
  }
}

export const REASON_REQUIRED: ReqAction[] = ["reject", "cancel"]

// ── Budgets ─────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
export const SCOPES = ["DEPARTMENT", "COST_CENTRE", "PROJECT"] as const
export const MODES = ["NONE", "WARN", "HARD_STOP"] as const
export const MODE_HELP: Record<string, string> = {
  NONE: "Track only — never warns or blocks",
  WARN: "Warn when an order would take it over",
  HARD_STOP: "Block the order, unless someone with override permission approves it with a reason",
}

export interface BudgetForm { name: string; scopeType: string; scopeValue: string; periodStart: string; periodEnd: string; amount: string; controlMode: string }
export const emptyBudget = (): BudgetForm => ({ name: "", scopeType: "DEPARTMENT", scopeValue: "", periodStart: "", periodEnd: "", amount: "", controlMode: "WARN" })

export function budgetProblem(f: BudgetForm): string | null {
  if (!f.name.trim()) return "Give the budget a name"
  if (!f.scopeValue.trim()) return "Say which department, cost centre or project it covers"
  if (!f.periodStart || !f.periodEnd) return "Choose the period it covers"
  if (f.periodEnd < f.periodStart) return "The period cannot end before it starts"
  if (!(Number(f.amount) > 0)) return "The amount must be above zero"
  return null
}

export function utilisationTone(state: string): "ok" | "warn" | "bad" {
  return state === "EXCEEDED" ? "bad" : state === "NEAR_LIMIT" ? "warn" : "ok"
}

// ── RFQs ────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────
export interface Weights { price: number; leadTime: number; technical: number; performance: number; bbbee: number }
export const DEFAULT_WEIGHTS: Weights = { price: 50, leadTime: 15, technical: 15, performance: 10, bbbee: 10 }
export const weightsTotal = (w: Weights) => w.price + w.leadTime + w.technical + w.performance + w.bbbee

export function weightsProblem(w: Weights): string | null {
  for (const v of Object.values(w)) if (!Number.isInteger(v) || v < 0 || v > 100) return "Each weight must be a whole number from 0 to 100"
  const t = weightsTotal(w)
  return t === 100 ? null : `The weights add up to ${t}; they must add up to 100`
}

export interface RfqLineForm { itemName: string; quantity: string; estimatedUnitCost: string }
export interface RfqForm { title: string; description: string; requiredByDate: string; closesAt: string; supplierIds: string[]; weights: Weights; lines: RfqLineForm[] }
export const emptyRfq = (): RfqForm => ({ title: "", description: "", requiredByDate: "", closesAt: "", supplierIds: [], weights: { ...DEFAULT_WEIGHTS }, lines: [{ itemName: "", quantity: "1", estimatedUnitCost: "" }] })

export function rfqProblem(f: RfqForm): string | null {
  if (!f.title.trim()) return "Give the RFQ a title"
  if (f.lines.length === 0) return "Add at least one line"
  for (const [i, l] of f.lines.entries()) {
    if (!l.itemName.trim()) return `Line ${i + 1}: say what is needed`
    if (!(Number(l.quantity) > 0)) return `Line ${i + 1}: quantity must be above zero`
  }
  return weightsProblem(f.weights)
}

export function rfqBody(f: RfqForm) {
  return {
    title: f.title.trim(), description: f.description.trim() || null, requisitionId: null, deliverToLocation: null,
    requiredByDate: f.requiredByDate || null, closesAt: f.closesAt ? new Date(f.closesAt).toISOString() : null, currency: "ZAR",
    weights: f.weights, supplierIds: f.supplierIds,
    lines: f.lines.map(l => ({ requisitionLineId: null, catalogueItemId: null, itemName: l.itemName.trim(), description: null,
      quantity: Number(l.quantity), estimatedUnitCost: l.estimatedUnitCost.trim() === "" ? null : Number(l.estimatedUnitCost) })),
  }
}

export interface QuoteLineForm { rfqLineId: string; noBid: boolean; quantity: string; unitPrice: string; discountPct: string }
export interface QuoteForm { supplierId: string; quoteReference: string; validUntil: string; leadTimeDays: string; warrantyMonths: string; paymentTermsDays: string; deliveryCost: string; otherCharges: string; lines: QuoteLineForm[] }

export function quoteProblem(f: QuoteForm): string | null {
  if (!f.supplierId) return "Choose the supplier"
  if (!(Number(f.leadTimeDays) >= 0) || f.leadTimeDays.trim() === "") return "Enter the lead time in days"
  if (!f.lines.some(l => !l.noBid)) return "Price at least one line, or this is not a quotation"
  for (const [i, l] of f.lines.entries()) {
    if (l.noBid) continue
    if (!(Number(l.quantity) > 0)) return `Line ${i + 1}: quantity must be above zero`
    if (!(Number(l.unitPrice) >= 0) || l.unitPrice.trim() === "") return `Line ${i + 1}: enter the unit price or mark it no bid`
  }
  return null
}

export function quoteBody(f: QuoteForm) {
  const n = (s: string) => (s.trim() === "" ? null : Number(s))
  return {
    supplierId: f.supplierId, quoteReference: f.quoteReference.trim() || null, validUntil: f.validUntil || null,
    leadTimeDays: Number(f.leadTimeDays), warrantyMonths: n(f.warrantyMonths), paymentTermsDays: n(f.paymentTermsDays),
    deliveryCost: n(f.deliveryCost) ?? 0, otherCharges: n(f.otherCharges) ?? 0, notes: null,
    lines: f.lines.map(l => l.noBid
      ? { rfqLineId: l.rfqLineId, noBid: true, quantity: null, unitPrice: null, discountPct: null, vatRate: null }
      : { rfqLineId: l.rfqLineId, noBid: false, quantity: Number(l.quantity), unitPrice: Number(l.unitPrice), discountPct: n(l.discountPct), vatRate: null }),
  }
}

/** Choosing anything other than the recommended quotation is allowed, and is recorded as overriding the recommendation. */
export function overridesRecommendation(chosenQuotationId: string, recommendedQuotationId: string | null | undefined): boolean {
  return !!recommendedQuotationId && chosenQuotationId !== recommendedQuotationId
}

export function awardProblem(chosenQuotationId: string, reason: string): string | null {
  if (!chosenQuotationId) return "Choose the quotation to award"
  if (!reason.trim()) return "Record why this supplier was chosen"
  return null
}

export type RfqAction = "publish" | "close" | "extend" | "cancel" | "award" | "create-po" | "capture"
export function rfqActions(status: string, hasPo: boolean): RfqAction[] {
  switch (status) {
    case "DRAFT": return ["publish", "cancel"]
    case "OPEN": return ["capture", "close", "extend", "cancel"]
    case "CLOSED": return ["capture", "award", "extend", "cancel"]
    case "AWARDED": return hasPo ? [] : ["create-po"]
    default: return []
  }
}

// ── Analytics ───────────────────────────────────────────────────────────────────────────────────────────────────────────────────
export const FLAG_TEXT: Record<string, string> = {
  HIGH_SPEND_LOW_SCORE: "Large share of spend, low score — act first",
  LOW_SCORE: "Low score",
}

/** "12.5%" or an em dash when there is nothing to compare against. */
export const pctText = (v: number | null | undefined) => (v == null ? "—" : `${v.toFixed(1)}%`)

/** Width of a bar for a share, kept visible but within the cell. */
export const barWidth = (v: number | null | undefined) => `${Math.max(2, Math.min(100, v ?? 0))}%`

export function defaultRange(today: Date = new Date()): { from: string; to: string } {
  const iso = (d: Date) => d.toISOString().slice(0, 10)
  const from = new Date(today); from.setFullYear(from.getFullYear() - 1)
  return { from: iso(from), to: iso(today) }
}

export function rangeProblem(from: string, to: string): string | null {
  if (!from || !to) return "Choose both dates"
  if (to < from) return "The period cannot end before it starts"
  return null
}
