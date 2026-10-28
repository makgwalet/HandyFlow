import { describe, expect, it } from "vitest"
import { canCorrect, correctionBody, correctionProblem, EMPTY_CORRECTION } from "./payCorrection.logic"

const f = (o: Partial<typeof EMPTY_CORRECTION>) => ({ ...EMPTY_CORRECTION, ...o })

describe("correcting an issued payslip", () => {
  it("is offered only for an issued run, to people who prepare pay, for people still employed", () => {
    expect(canCorrect("FINALISED", true)).toBe(true)
    expect(canCorrect("COMPLETED", true)).toBe(true)
    expect(canCorrect("DRAFT", true)).toBe(false)
    expect(canCorrect("FINALISED", false)).toBe(false)
    expect(canCorrect("FINALISED", true, true)).toBe(false)
  })
  it("asks for a direction, an amount and a reason", () => {
    expect(correctionProblem(f({}), 1000)).toMatch(/underpaid or overpaid/)
    expect(correctionProblem(f({ direction: "UNDERPAID" }), 1000)).toBe("Enter the amount.")
    expect(correctionProblem(f({ direction: "UNDERPAID", amount: "0" }), 1000)).toBe("Enter the amount.")
    expect(correctionProblem(f({ direction: "UNDERPAID", amount: "100" }), 1000)).toBe("Say what went wrong.")
    expect(correctionProblem(f({ direction: "UNDERPAID", amount: "100", reason: "Overtime left off" }), 1000)).toBeNull()
  })
  it("keeps an overpayment within the net pay but not an underpayment", () => {
    expect(correctionProblem(f({ direction: "OVERPAID", amount: "1000", reason: "x" }), 1000)).toBeNull()
    expect(correctionProblem(f({ direction: "OVERPAID", amount: "1000.01", reason: "x" }), 1000)).toMatch(/more than the net pay/)
    expect(correctionProblem(f({ direction: "UNDERPAID", amount: "5000", reason: "x" }), 1000)).toBeNull()
  })
  it("refuses part cents and an over-long reason", () => {
    expect(correctionProblem(f({ direction: "UNDERPAID", amount: "10.005", reason: "x" }), 1000)).toMatch(/cents/)
    expect(correctionProblem(f({ direction: "UNDERPAID", amount: "10.5", reason: "x".repeat(301) }), 1000)).toMatch(/under 300/)
  })
  it("sends a trimmed body", () => {
    expect(correctionBody(f({ direction: "OVERPAID", amount: "12.50", reason: " twice " }))).toEqual({ direction: "OVERPAID", amount: 12.5, reason: "twice" })
  })
})
