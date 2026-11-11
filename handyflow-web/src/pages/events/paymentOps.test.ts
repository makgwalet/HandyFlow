import { describe, it, expect } from "vitest"
import { canCancelCheckout, canConfirm, canRefund, canRefundAll, confirmProblem, confirmWarning, paymentStatusText, paymentStatusTone, rand, refundableNow, refundProblem, totals, type Payment } from "./paymentOps"

const pay = (o: Partial<Payment> = {}): Payment => ({ id: "p1", guestId: "g1", ticketNumber: "T-1", amount: 500, receivedAmount: 500, feeAmount: 10, refundedAmount: 0, refundableAmount: 500,
  currency: "ZAR", provider: "MANUAL", providerRef: null, status: "PAID", expiresAt: null, paidAt: null, failureReason: null, reviewReason: null, createdAt: "2026-11-11T08:00:00Z", ...o })

describe("what staff can do", () => {
  it("confirms or cancels only a payment that is still waiting", () => {
    expect(canConfirm({ status: "PENDING" })).toBe(true); expect(canCancelCheckout({ status: "PENDING" })).toBe(true)
    for (const s of ["PAID", "EXPIRED", "FAILED", "CANCELLED", "REQUIRES_REFUND"]) { expect(canConfirm({ status: s })).toBe(false); expect(canCancelCheckout({ status: s })).toBe(false) }
  })
  it("refunds only money still held and not already promised to a refund", () => {
    expect(canRefund(pay())).toBe(true)
    expect(canRefund(pay({ refundableAmount: 0 }))).toBe(false)
    expect(canRefund(pay({ refundableAmount: null }))).toBe(false)
    const waiting = [{ paymentId: "p1", amount: 500, status: "REQUESTED" }]
    expect(canRefund(pay(), waiting)).toBe(false)
    expect(refundableNow(pay(), [{ paymentId: "p1", amount: 200, status: "REQUESTED" }, { paymentId: "p1", amount: 100, status: "FAILED" }, { paymentId: "p2", amount: 50, status: "REQUESTED" }])).toBe(300)
  })
  it("offers refund-everyone only for a cancelled event with something to refund", () => {
    expect(canRefundAll("CANCELLED", [pay()])).toBe(true)
    expect(canRefundAll("LIVE", [pay()])).toBe(false)
    expect(canRefundAll("CANCELLED", [pay({ refundableAmount: 0 })])).toBe(false)
    expect(canRefundAll("CANCELLED", [pay()], [{ id: "r", paymentId: "p1", amount: 500, status: "REQUESTED" } as any])).toBe(false)
  })
})

describe("confirming money", () => {
  it("needs a positive amount with at most two decimals", () => {
    expect(confirmProblem("", "")).toMatch(/Enter/)
    expect(confirmProblem("0", "")).toMatch(/above zero/)
    expect(confirmProblem("12.345", "")).toMatch(/amount/i)
    expect(confirmProblem("500", "abc")).toMatch(/amount/i)
    expect(confirmProblem("500", "")).toBeNull(); expect(confirmProblem("500.00", "12.50")).toBeNull()
  })
  it("warns before a wrong amount, because the ticket will not be issued", () => {
    expect(confirmWarning("500", 500)).toBeNull(); expect(confirmWarning("500.00", 500)).toBeNull(); expect(confirmWarning("", 500)).toBeNull()
    expect(confirmWarning("450", 500)).toMatch(/not the R\s?500[.,]00/); expect(confirmWarning("450", 500)).toMatch(/refund/)
  })
})

describe("refunding", () => {
  it("blank means everything; otherwise within what is left; always with a reason", () => {
    expect(refundProblem("", 500, "cancelled")).toBeNull()
    expect(refundProblem("100", 500, "fee kept")).toBeNull()
    expect(refundProblem("500.01", 500, "x y z")).toMatch(/At most/)
    expect(refundProblem("0", 500, "reason")).toMatch(/above zero/)
    expect(refundProblem("100", 500, " ")).toMatch(/reason/)
  })
})

describe("wording and totals", () => {
  it("says statuses in words and tones", () => {
    expect(paymentStatusText("REQUIRES_REFUND")).toBe("Needs a refund"); expect(paymentStatusText("NEW")).toBe("NEW")
    expect(paymentStatusTone("PAID")).toBe("ok"); expect(paymentStatusTone("PENDING")).toBe("warn"); expect(paymentStatusTone("FAILED")).toBe("bad"); expect(paymentStatusTone("EXPIRED")).toBe("muted")
  })
  it("formats rand and totals received, refunded, fees, held and flagged", () => {
    expect(rand(1250)).toMatch(/^R\s?1\s?250[.,]00$/); expect(rand(null)).toBe("–")
    const t = totals([pay(), pay({ id: "p2", status: "PENDING", receivedAmount: null, feeAmount: null, amount: 300 }), pay({ id: "p3", status: "REQUIRES_REFUND", refundedAmount: 50 })])
    expect(t).toEqual({ received: 1000, refunded: 50, fees: 20, held: 300, needReview: 1 })
  })
})
