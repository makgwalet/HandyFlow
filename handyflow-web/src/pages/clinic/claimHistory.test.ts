import { describe, it, expect } from "vitest"
import { canReversePayment, effectOnOwed, entryLabel, withBalances, type LedgerEntry } from "./claimHistory"

const e = (o: Partial<LedgerEntry>): LedgerEntry => ({ kind: "SCHEME_PAYMENT", amount: 100, date: "2026-10-01", recordedAt: "2026-10-01T10:00:00Z", ...o })

describe("claim money history", () => {
  it("labels each kind, and marks a payment that was reversed", () => {
    expect(entryLabel(e({}))).toBe("Scheme payment")
    expect(entryLabel(e({ reversed: true }))).toBe("Scheme payment (reversed)")
    expect(entryLabel(e({ kind: "PAYMENT_REVERSAL" }))).toBe("Payment reversed")
    expect(entryLabel(e({ kind: "WRITE_OFF" }))).toBe("Write-off")
    expect(entryLabel(e({ kind: "CREDIT_NOTE" }))).toBe("Credit note")
  })

  it("a reversal puts back what the payment took off", () => {
    expect(effectOnOwed(e({ amount: 400 }))).toBe(-400)
    expect(effectOnOwed(e({ kind: "PAYMENT_REVERSAL", amount: 400 }))).toBe(400)
    expect(effectOnOwed(e({ kind: "WRITE_OFF", amount: 50 }))).toBe(-50)
  })

  it("shows what the scheme owed after each entry, newest first", () => {
    const rows = withBalances(1000, [
      e({ kind: "PAYMENT_REVERSAL", amount: 400, recordedAt: "2026-10-03T09:00:00Z" }),
      e({ amount: 400, recordedAt: "2026-10-01T09:00:00Z", reversed: true }),
      e({ kind: "WRITE_OFF", amount: 100, recordedAt: "2026-10-02T09:00:00Z" }),
    ])
    expect(rows.map(r => r.kind)).toEqual(["PAYMENT_REVERSAL", "WRITE_OFF", "SCHEME_PAYMENT"])
    expect(rows.map(r => r.owedAfter)).toEqual([900, 500, 600])
  })

  it("never shows a balance below zero", () => {
    expect(withBalances(100, [e({ amount: 150 })])[0].owedAfter).toBe(0)
  })

  it("offers Reverse only on a payment that stands, on a claim that received money, to someone who may", () => {
    const p = e({ id: "pay-1" })
    expect(canReversePayment(p, "PAID", true)).toBe(true)
    expect(canReversePayment(p, "PAID", false)).toBe(false)
    expect(canReversePayment({ ...p, reversed: true }, "PAID", true)).toBe(false)
    expect(canReversePayment({ ...p, id: null }, "PAID", true)).toBe(false)
    expect(canReversePayment(p, "VOIDED", true)).toBe(false)
    expect(canReversePayment(e({ kind: "WRITE_OFF", id: "x" }), "PAID", true)).toBe(false)
    expect(canReversePayment(e({ kind: "PAYMENT_REVERSAL", id: "x" }), "PAID", true)).toBe(false)
  })
})
