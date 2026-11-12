import { describe, it, expect } from "vitest"
import { emptyContract, validateContract, toContractRequest, progressText, daysLeftText, type Contract } from "./contractOps"

const form = (o: any = {}) => ({ ...emptyContract(), accountId: "a1", contractNumber: "C-1", title: "Supply", startDate: "2026-10-01", endDate: "2026-12-31", ...o })
const contract = (o: Partial<Contract> = {}): Contract => ({ id: "c", accountId: "a", customerName: "Acme", contractNumber: "C-1", title: "t", startDate: "2026-10-01", endDate: "2026-12-31", fuelType: null,
  committedLitres: 10000, terms: null, status: "ACTIVE", cancelledReason: null, deliveredLitres: 4500, percentOfCommitment: 45, litresRemaining: 5500, daysLeft: 49, linkedPrices: 0, prices: [], createdAt: "x", ...o })

describe("contractOps", () => {
  it("needs an account and number when adding, a title, both dates in order, and a positive commitment or none", () => {
    expect(validateContract(form(), true)).toBeNull()
    expect(validateContract(form({ accountId: "" }), true)).toMatch(/customer account/)
    expect(validateContract(form({ contractNumber: " " }), true)).toMatch(/number/)
    expect(validateContract(form({ accountId: "", contractNumber: "" }), false)).toBeNull()   // not asked when editing
    expect(validateContract(form({ title: "" }), true)).toMatch(/title/)
    expect(validateContract(form({ endDate: "" }), true)).toMatch(/dates/)
    expect(validateContract(form({ endDate: "2026-09-30" }), true)).toMatch(/before the start/)
    expect(validateContract(form({ committedLitres: "0" }), true)).toMatch(/more than zero/)
    expect(validateContract(form({ committedLitres: "abc" }), true)).toMatch(/more than zero/)
    expect(validateContract(form({ committedLitres: "5000" }), true)).toBeNull()
  })
  it("builds the request: the account and number only when adding, blanks as null", () => {
    expect(toContractRequest(form({ committedLitres: "5000", fuelType: " diesel " }), true)).toMatchObject({ accountId: "a1", contractNumber: "C-1", committedLitres: 5000, fuelType: "diesel", terms: null })
    const edit = toContractRequest(form(), false) as any
    expect(edit.accountId).toBeUndefined(); expect(edit.contractNumber).toBeUndefined(); expect(edit.committedLitres).toBeNull(); expect(edit.fuelType).toBeNull()
  })
  it("shows progress against the commitment, or just litres when there is none", () => {
    expect(progressText(contract())).toMatch(/4\s?500 of 10\s?000 L \(45\.0%\)/)
    expect(progressText(contract({ committedLitres: null, percentOfCommitment: null }))).toMatch(/4\s?500 L delivered/)
  })
  it("says days left while active, 'Ends today', and nothing otherwise", () => {
    expect(daysLeftText(contract())).toBe("49 days left"); expect(daysLeftText(contract({ daysLeft: 1 }))).toBe("1 day left")
    expect(daysLeftText(contract({ daysLeft: 0 }))).toBe("Ends today"); expect(daysLeftText(contract({ daysLeft: null, status: "EXPIRED" }))).toBeNull()
  })
})
