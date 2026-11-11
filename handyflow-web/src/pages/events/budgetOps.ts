// src/pages/events/budgetOps.ts
//
// Rules and wording for the event budget, supplier commitments and cost entries. The server enforces all of them (locked lines, approved budget before
// approving a quote, separate approver, over-budget reasons); these only keep staff from sending an obviously wrong request and say what each state means.
import { moneyProblem } from "./guestOps"

export const CATEGORIES = ["VENUE", "CATERING", "AV_PRODUCTION", "SECURITY", "TRANSPORT", "ENTERTAINMENT", "MARKETING", "STAFFING", "PRINTING_SIGNAGE", "INSURANCE_PERMITS", "DECOR", "ACCOMMODATION", "CONTINGENCY", "OTHER"]
export const categoryText = (c: string) => { const s = c.toLowerCase().replace(/_/g, " "); return s.charAt(0).toUpperCase() + s.slice(1) }

export const budgetStatusLabel = (s: string) => ({ NOT_STARTED: "Not started", DRAFT: "Draft", SUBMITTED: "Awaiting approval", APPROVED: "Approved" } as Record<string, string>)[s] ?? s

/** Lines can be added, changed and removed only until the budget is submitted. */
export const canEditLines = (status: string) => status === "NOT_STARTED" || status === "DRAFT"

export type BudgetAction = "submit" | "approve" | "reject" | "reopen"
/** The workflow buttons that make sense for a budget in this state (permission is checked separately). */
export function budgetActions(status: string, lineCount: number): BudgetAction[] {
  if (status === "SUBMITTED") return ["approve", "reject", "reopen"]
  if (status === "APPROVED") return ["reopen"]
  return lineCount > 0 ? ["submit"] : []
}

export function lineProblem(f: { category: string; description: string; plannedAmount: string }): string | null {
  if (!CATEGORIES.includes(f.category)) return "Choose a category."
  if (!f.description.trim()) return "Describe what the line is for."
  if (f.plannedAmount.trim() === "") return "Enter the planned amount."
  return moneyProblem(f.plannedAmount)
}

export type CommitmentStatus = "QUOTED" | "APPROVED" | "COMPLETED" | "CANCELLED"
export const commitmentStatusLabel = (s: string) => ({ QUOTED: "Quote", APPROVED: "Committed", COMPLETED: "Completed", CANCELLED: "Cancelled" } as Record<string, string>)[s] ?? s
export const canApproveCommitment = (s: string) => s === "QUOTED"
export const canCompleteCommitment = (s: string) => s === "APPROVED"
export const canCancelCommitment = (s: string) => s === "QUOTED" || s === "APPROVED"
/** Costs can be recorded against a commitment that is approved or completed (it is a real obligation then). */
export const canRecordAgainst = (s: string) => s === "APPROVED" || s === "COMPLETED"

export function commitmentProblem(g: { budgetLineId: string; description: string; amount: string; depositAmount: string; depositDueDate: string }): string | null {
  const f = { budgetLineId: g.budgetLineId ?? "", description: g.description ?? "", amount: g.amount ?? "", depositAmount: g.depositAmount ?? "", depositDueDate: g.depositDueDate ?? "" }
  if (!f.budgetLineId) return "Choose the budget line this belongs to."
  if (!f.description.trim()) return "Describe what the supplier will provide."
  if (f.amount.trim() === "") return "Enter the quoted amount."
  const m = moneyProblem(f.amount); if (m) return m
  if (Number(f.amount) <= 0) return "The amount must be above zero."
  const d = moneyProblem(f.depositAmount); if (d) return d
  if (f.depositAmount.trim() !== "" && Number(f.depositAmount) > Number(f.amount)) return "The deposit cannot be more than the quote."
  if (f.depositAmount.trim() !== "" && Number(f.depositAmount) > 0 && !f.depositDueDate) return "Say when the deposit is due."
  return null
}

/** Approving takes a line over its plan when what is already committed plus this quote exceeds it; the server then wants a reason. */
export function approvalOverrun(line: { planned: number; committed: number } | undefined, amount: number): number {
  if (!line) return 0
  const over = line.committed + amount - line.planned
  return over > 0.005 ? Math.round(over * 100) / 100 : 0
}

export function costEntryProblem(f: { target: string; kind: string; amount: string; entryDate: string }): string | null {
  if (!f.target) return "Choose the commitment or budget line this cost belongs to."
  if (f.kind !== "INVOICE" && f.kind !== "PAYMENT") return "Choose invoice or payment."
  if (f.amount.trim() === "") return "Enter the amount."
  const m = moneyProblem(f.amount); if (m) return m
  if (Number(f.amount) <= 0) return "The amount must be above zero."
  if (!f.entryDate) return "Give the date of the invoice or payment."
  return null
}

/** target is "c:<commitmentId>" or "l:<budgetLineId>"; the server wants exactly one of them. */
export function costEntryBody(f: { target: string; kind: string; deposit: boolean; amount: string; entryDate: string; reference: string; description: string }) {
  const [t, id] = [f.target.slice(0, 1), f.target.slice(2)]
  return {
    commitmentId: t === "c" ? id : null, budgetLineId: t === "l" ? id : null, kind: f.kind, deposit: f.kind === "PAYMENT" && f.deposit,
    amount: Number(f.amount), entryDate: f.entryDate, reference: (f.reference ?? "").trim() || null, description: (f.description ?? "").trim() || null, sourceModule: null, sourceRef: null,
  }
}

export function commitmentBody(f: { budgetLineId: string; vendorId: string; description: string; amount: string; quoteReference: string; poReference: string; contractReference: string; deliverable: string; dueDate: string; depositAmount: string; depositDueDate: string }) {
  const nz = (v: string | undefined) => ((v ?? "").trim() === "" ? null : (v as string).trim())
  return {
    budgetLineId: f.budgetLineId, vendorId: f.vendorId || null, description: (f.description ?? "").trim(), amount: Number(f.amount), quoteReference: nz(f.quoteReference),
    poReference: nz(f.poReference), contractReference: nz(f.contractReference), deliverable: nz(f.deliverable), dueDate: f.dueDate || null,
    depositAmount: (f.depositAmount ?? "").trim() === "" ? null : Number(f.depositAmount), depositDueDate: f.depositDueDate || null,
  }
}

/** Variance is planned minus forecast, so below zero is over budget. */
export const varianceTone = (v: number): "ok" | "bad" | "muted" => (v < -0.005 ? "bad" : v > 0.005 ? "ok" : "muted")

export const alertTitle = (type: string) => ({
  OVER_BUDGET: "Over budget", OVER_COMMITMENT: "Committed above plan", DEPOSIT_DUE: "Deposit due", DELIVERABLE_OVERDUE: "Delivery overdue",
  PAID_ON_CANCELLED: "Paid on a cancelled quote", UNRECONCILED: "Not reconciled",
} as Record<string, string>)[type] ?? type
