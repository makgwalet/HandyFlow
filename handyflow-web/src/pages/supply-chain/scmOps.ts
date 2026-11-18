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

// ── Buying at a price different from the requisition estimate ───────────────────────────────────────────────────────────────────
export const PRICE_REASONS: { value: string; label: string }[] = [
  { value: "SUPPLIER_QUOTATION", label: "Supplier quotation" },
  { value: "NEGOTIATED_DISCOUNT", label: "Negotiated discount" },
  { value: "MARKET_PRICE_CHANGE", label: "Market price change" },
  { value: "CONTRACT_PRICE", label: "Contract price" },
  { value: "SPECIFICATION_CHANGE", label: "Specification change" },
  { value: "OTHER", label: "Other (explain)" },
]

export type PriceKind = "unchanged" | "decrease" | "within" | "beyond"
export interface PriceView { kind: PriceKind; pct: number | null; saving: number; text: string }

/** What the buyer is about to do, in words. The server decides again using the company's own tolerance. */
export function priceView(estimate: number, price: number, qty: number, tolerancePct: number): PriceView {
  if (!Number.isFinite(price) || price === estimate) return { kind: "unchanged", pct: 0, saving: 0, text: "" }
  const pct = estimate === 0 ? null : ((price - estimate) / estimate) * 100
  if (price < estimate) {
    const saving = Math.round((estimate - price) * qty * 100) / 100
    return { kind: "decrease", pct, saving, text: `Below the estimate — recorded as a saving of R ${saving.toFixed(2)}` }
  }
  const within = pct != null && pct <= tolerancePct
  return within
    ? { kind: "within", pct, saving: 0, text: `Above the estimate by ${pct!.toFixed(1)}% — within the ${tolerancePct}% tolerance` }
    : { kind: "beyond", pct, saving: 0, text: `Above the estimate${pct == null ? "" : ` by ${pct.toFixed(1)}%`} — beyond the ${tolerancePct}% tolerance, so a second person must approve it before the order can be approved` }
}

export interface ConvertLineForm { requisitionLineId: string; itemName: string; selected: boolean; quantity: string; unitCost: string; estimate: number; reason: string; note: string; quotationRef: string }
export interface ConvertForm { supplierId: string; notes: string; lines: ConvertLineForm[] }

export function convertProblem(f: ConvertForm): string | null {
  if (!f.supplierId) return "Choose the supplier"
  const picked = f.lines.filter(l => l.selected)
  if (picked.length === 0) return "Choose at least one line to order"
  for (const l of picked) {
    if (!(Number(l.quantity) > 0)) return `${l.itemName}: quantity must be above zero`
    if (l.unitCost.trim() === "" || !(Number(l.unitCost) >= 0)) return `${l.itemName}: enter the unit price`
    if (Number(l.unitCost) !== l.estimate) {
      if (!l.reason) return `${l.itemName}: say why the price differs from the estimate`
      if (l.reason === "OTHER" && !l.note.trim()) return `${l.itemName}: explain the price change`
    }
  }
  return null
}

export function convertBody(f: ConvertForm) {
  return {
    supplierId: f.supplierId, notes: f.notes.trim() || null,
    lines: f.lines.filter(l => l.selected).map(l => ({
      requisitionLineId: l.requisitionLineId, quantity: Number(l.quantity), unitCost: Number(l.unitCost), vatRate: null,
      priceChangeReason: Number(l.unitCost) !== l.estimate ? l.reason : null,
      priceChangeNote: Number(l.unitCost) !== l.estimate ? l.note.trim() || null : null,
      quotationRef: l.quotationRef.trim() || null,
    })),
  }
}

// ── Procurement policy ──────────────────────────────────────────────────────────────────────────────────────────────────────────
export const BUDGET_BASES: { value: string; label: string; help: string }[] = [
  { value: "EX_VAT", label: "Excluding VAT (recommended)", help: "Budgets count the net cost. Tick the box below to add any VAT the business cannot claim back." },
  { value: "INC_VAT", label: "Including VAT", help: "Budgets count the full invoice total." },
  { value: "NET_OF_RECOVERABLE", label: "Net of recoverable VAT", help: "Budgets count the net cost plus only the VAT the business cannot claim back." },
]

export interface PolicyForm { budgetBasis: string; vatRecoverablePct: string; includeNonRecoverableVat: boolean; priceIncreaseTolerancePct: string }

export function policyProblem(f: PolicyForm): string | null {
  const r = Number(f.vatRecoverablePct), t = Number(f.priceIncreaseTolerancePct)
  if (f.vatRecoverablePct.trim() === "" || !(r >= 0 && r <= 100)) return "Recoverable VAT must be between 0 and 100"
  if (f.priceIncreaseTolerancePct.trim() === "" || !(t >= 0 && t <= 100)) return "The price increase tolerance must be between 0 and 100"
  return null
}

export const policyBody = (f: PolicyForm) => ({
  budgetBasis: f.budgetBasis, vatRecoverablePct: Number(f.vatRecoverablePct),
  includeNonRecoverableVat: f.includeNonRecoverableVat, priceIncreaseTolerancePct: Number(f.priceIncreaseTolerancePct),
})

// ── Contracts ───────────────────────────────────────────────────────────────────────────────────────────────────────────────────
export const EXPIRING_DAYS = 60
export type ContractBucket = "ACTIVE" | "EXPIRING" | "EXPIRED" | "DRAFT" | "TERMINATED"

/** Whole days from `today` to `date` (negative when past). Dates are compared as calendar days, not instants. */
export function daysUntil(date: string | null | undefined, today: Date = new Date()): number | null {
  if (!date) return null
  const d = Date.UTC(Number(date.slice(0, 4)), Number(date.slice(5, 7)) - 1, Number(date.slice(8, 10)))
  const t = Date.UTC(today.getFullYear(), today.getMonth(), today.getDate())
  return Math.round((d - t) / 86_400_000)
}

export function contractBucket(c: { status: string; endDate: string | null; renewalNoticeDue?: boolean }, today: Date = new Date()): ContractBucket {
  if (c.status === "DRAFT") return "DRAFT"
  if (c.status === "TERMINATED") return "TERMINATED"
  const left = daysUntil(c.endDate, today)
  if (c.status === "EXPIRED" || (left != null && left < 0)) return "EXPIRED"
  if (c.renewalNoticeDue || (left != null && left <= EXPIRING_DAYS)) return "EXPIRING"
  return "ACTIVE"
}

export function contractCounts(list: { status: string; endDate: string | null; renewalNoticeDue?: boolean }[], today: Date = new Date()): Record<ContractBucket, number> {
  const out: Record<ContractBucket, number> = { ACTIVE: 0, EXPIRING: 0, EXPIRED: 0, DRAFT: 0, TERMINATED: 0 }
  for (const c of list) out[contractBucket(c, today)]++
  return out
}

/** "in 12 days" / "today" / "3 days ago" / "—". */
export function dueText(days: number | null): string {
  if (days == null) return "—"
  if (days === 0) return "today"
  return days > 0 ? `in ${days} day${days === 1 ? "" : "s"}` : `${-days} day${days === -1 ? "" : "s"} ago`
}

// ── Returns & claims ────────────────────────────────────────────────────────────────────────────────────────────────────────────
export const RETURN_STEPS = ["Request", "Authorised", "Processing", "Completed"] as const
const RETURN_STEP_OF: Record<string, number> = { DRAFT: 0, SUBMITTED: 0, APPROVED: 1, DISPATCHED: 2, CREDITED: 3 }

/** Index of the current stepper step, or -1 when the return left the path (rejected / cancelled). */
export function returnStep(status: string): number {
  return status in RETURN_STEP_OF ? RETURN_STEP_OF[status] : -1
}

export type ReturnAction = "submit" | "approve" | "reject" | "cancel" | "dispatch" | "credit"
export function returnActions(status: string, canManage: boolean, canApprove: boolean): ReturnAction[] {
  const out: ReturnAction[] = []
  if (status === "DRAFT" && canManage) out.push("submit")
  if (status === "SUBMITTED" && canApprove) out.push("approve", "reject")
  if (status === "APPROVED" && canManage) out.push("dispatch")
  if (status === "DISPATCHED" && canManage) out.push("credit")
  if (["DRAFT", "SUBMITTED", "APPROVED"].includes(status) && canManage) out.push("cancel")
  return out
}

export function creditProblem(ref: string, amount: string): string | null {
  if (!ref.trim()) return "Enter the supplier's credit note reference."
  const n = Number(amount)
  if (!amount.trim() || !Number.isFinite(n) || n <= 0) return "Enter the credit note total, more than zero."
  return null
}

// ── Quarantine ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────
export function quarantineProblem(action: "release" | "write-off", qty: string, open: number, reason: string): string | null {
  const n = Number(qty)
  if (!qty.trim() || !Number.isFinite(n) || n <= 0) return "Enter a quantity more than zero."
  if (n > open) return `Only ${open} is still held.`
  if (action === "write-off" && !reason.trim()) return "A reason is required to scrap stock."
  return null
}

// ── Replenishment ───────────────────────────────────────────────────────────────────────────────────────────────────────────────
export const URGENCY_ORDER = ["STOCKOUT", "CRITICAL", "REORDER"] as const
export const URGENCY_LABEL: Record<string, string> = { STOCKOUT: "Out of stock", CRITICAL: "Critical", REORDER: "Reorder" }
export const urgencyTone = (u: string): "bad" | "warn" | "ok" => (u === "STOCKOUT" || u === "CRITICAL" ? "bad" : "warn")

export function replenishmentPicks(items: { catalogueItemId: string; suggestedQty: number }[], selected: Record<string, boolean>, edited: Record<string, string>) {
  return items
    .filter(i => selected[i.catalogueItemId])
    .map(i => ({ catalogueItemId: i.catalogueItemId, qty: Number(edited[i.catalogueItemId] ?? i.suggestedQty) }))
}
export function replenishmentProblem(picks: { qty: number }[]): string | null {
  if (picks.length === 0) return "Tick at least one item."
  if (picks.some(p => !Number.isFinite(p.qty) || p.qty <= 0)) return "Every ticked item needs a quantity above zero."
  return null
}

// ── Dashboard ───────────────────────────────────────────────────────────────────────────────────────────────────────────────────
/** Change between two period totals as a percentage; null when there is nothing to compare with. */
export function deltaPct(current: number, previous: number): number | null {
  if (!previous) return null
  return ((current - previous) / Math.abs(previous)) * 100
}
export const deltaText = (d: number | null) => (d == null ? "—" : `${d >= 0 ? "▲" : "▼"} ${Math.abs(d).toFixed(1)}%`)

export function statusBreakdown<T extends { status: string }>(rows: T[]): { status: string; count: number }[] {
  const m = new Map<string, number>()
  for (const r of rows) m.set(r.status, (m.get(r.status) ?? 0) + 1)
  return [...m].map(([status, count]) => ({ status, count })).sort((a, b) => b.count - a.count)
}

export type AlertTone = "bad" | "warn" | "info"
export interface DashAlert { key: string; tone: AlertTone; text: string; section: string }
export function dashboardAlerts(c: { overdueInvoices: number; priceApprovals: number; quarantineOpen: number; contractsExpiring: number; stockouts: number; reorders: number; invoicesForApproval: number }): DashAlert[] {
  const out: DashAlert[] = []
  const add = (n: number, key: string, tone: AlertTone, one: string, many: string, section: string) => { if (n > 0) out.push({ key, tone, text: `${n} ${n === 1 ? one : many}`, section }) }
  add(c.overdueInvoices, "overdue", "bad", "supplier invoice overdue", "supplier invoices overdue", "invoices")
  add(c.stockouts, "stockout", "bad", "item out of stock", "items out of stock", "replenishment")
  add(c.priceApprovals, "price", "warn", "price increase awaiting approval", "price increases awaiting approval", "policy")
  add(c.invoicesForApproval, "inv-approve", "warn", "invoice awaiting approval", "invoices awaiting approval", "invoices")
  add(c.quarantineOpen, "quarantine", "warn", "quarantine hold open", "quarantine holds open", "quality")
  add(c.contractsExpiring, "contracts", "warn", "contract expiring or due for renewal", "contracts expiring or due for renewal", "contracts")
  add(c.reorders, "reorder", "info", "item to reorder", "items to reorder", "replenishment")
  return out
}

/** Last `n` calendar months ending in `today`'s month, as "YYYY-MM" labels (oldest first) — the dashboard trend axis. */
export function lastMonths(n: number, today: Date = new Date()): string[] {
  const out: string[] = []
  for (let i = n - 1; i >= 0; i--) { const d = new Date(today.getFullYear(), today.getMonth() - i, 1); out.push(`${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`) }
  return out
}
/** Fills months with no spend with zero so the trend line is not drawn across a gap. */
export function monthSeries(byMonth: { label: string; amount: number }[], months: string[]): { month: string; amount: number }[] {
  const m = new Map(byMonth.map(x => [x.label, Number(x.amount)]))
  return months.map(month => ({ month, amount: m.get(month) ?? 0 }))
}
