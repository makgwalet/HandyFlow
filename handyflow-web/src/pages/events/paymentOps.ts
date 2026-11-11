// src/pages/events/paymentOps.ts
//
// Rules and wording for the payments screen. The server decides everything that matters (it re-checks every amount under a lock); these keep staff from
// sending an obviously wrong request and explain what each status and action means in plain words.
import { moneyProblem } from "./guestOps"

export interface Payment {
  id: string; guestId: string | null; ticketNumber: string | null; amount: number; receivedAmount: number | null; feeAmount: number | null
  refundedAmount: number | null; refundableAmount: number | null; currency: string; provider: string; providerRef: string | null; status: string
  expiresAt: string | null; paidAt: string | null; failureReason: string | null; reviewReason: string | null; createdAt: string
}
export interface Refund { id: string; paymentId: string; amount: number; reason: string; status: string; providerRef: string | null; requestedByName: string | null; requestedAt: string; completedAt: string | null; failureReason: string | null }

export const PAYMENT_STATUSES = ["PENDING", "PAID", "REQUIRES_REFUND", "EXPIRED", "FAILED", "CANCELLED"]

export const paymentStatusText = (s: string) => ({
  PENDING: "Waiting for payment", PAID: "Paid", FAILED: "Failed", EXPIRED: "Hold expired", CANCELLED: "Cancelled", REQUIRES_REFUND: "Needs a refund",
} as Record<string, string>)[s] ?? s

export const paymentStatusTone = (s: string): "ok" | "warn" | "bad" | "muted" =>
  s === "PAID" ? "ok" : s === "PENDING" ? "warn" : s === "REQUIRES_REFUND" || s === "FAILED" ? "bad" : "muted"

export const refundStatusText = (s: string) => ({ REQUESTED: "Awaiting payout", COMPLETED: "Paid out", FAILED: "Failed" } as Record<string, string>)[s] ?? s

/** Money as shown on screen: R 1 250.00. */
export const rand = (n: number | null | undefined) => (n == null ? "–" : `R ${Number(n).toLocaleString("en-ZA", { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`)

/** Staff can confirm only a payment that is still waiting; a cancel (of the checkout) likewise. */
export const canConfirm = (p: Pick<Payment, "status">) => p.status === "PENDING"
export const canCancelCheckout = (p: Pick<Payment, "status">) => p.status === "PENDING"
/** What can still be refunded: the money held, less refunds already raised and awaiting payout (the server counts those too). */
export function refundableNow(p: Pick<Payment, "id" | "refundableAmount">, refunds: Pick<Refund, "paymentId" | "amount" | "status">[] = []): number {
  const waiting = refunds.filter(r => r.paymentId === p.id && r.status === "REQUESTED").reduce((a, r) => a + r.amount, 0)
  return Math.max(0, Math.round(((p.refundableAmount ?? 0) - waiting) * 100) / 100)
}
/** A refund can be raised while money is still held and not already covered by a refund awaiting payout. */
export const canRefund = (p: Pick<Payment, "id" | "refundableAmount">, refunds: Pick<Refund, "paymentId" | "amount" | "status">[] = []) => refundableNow(p, refunds) > 0

export function confirmProblem(received: string, fee: string): string | null {
  if (received.trim() === "") return "Enter the amount that arrived."
  const m = moneyProblem(received); if (m) return m
  if (Number(received) <= 0) return "The amount must be above zero."
  return moneyProblem(fee)
}

/** Paying the wrong amount does not issue the ticket: the server flags the payment for refund, so say so before the click. */
export function confirmWarning(received: string, expected: number): string | null {
  if (received.trim() === "" || moneyProblem(received)) return null
  const n = Number(received)
  if (Math.abs(n - expected) < 0.005) return null
  return `This is not the ${rand(expected)} that was asked for. The ticket will not be issued and the payment will be marked as needing a refund.`
}

/** Blank amount means "everything still refundable". */
export function refundProblem(amount: string, refundable: number, reason: string): string | null {
  const m = moneyProblem(amount); if (m) return m
  if (amount.trim() !== "") {
    const n = Number(amount)
    if (n <= 0) return "The refund must be above zero."
    if (n - refundable > 0.005) return `At most ${rand(refundable)} can still be refunded.`
  }
  if (reason.trim().length < 3) return "Give a reason for the refund."
  return null
}

/** Refund-everything-for-a-cancelled-event is only meaningful once the event is cancelled. */
export const canRefundAll = (eventStatus: string | undefined, payments: Payment[], refunds: Refund[] = []) =>
  eventStatus === "CANCELLED" && payments.some(p => canRefund(p, refunds))

export function totals(payments: Payment[]) {
  const sum = (f: (p: Payment) => number | null) => payments.reduce((a, p) => a + (f(p) ?? 0), 0)
  return {
    received: sum(p => p.receivedAmount), refunded: sum(p => p.refundedAmount), fees: sum(p => p.feeAmount),
    held: payments.filter(p => p.status === "PENDING").reduce((a, p) => a + p.amount, 0),
    needReview: payments.filter(p => p.status === "REQUIRES_REFUND").length,
  }
}
