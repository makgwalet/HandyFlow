import { describe, it, expect } from "vitest"
import { allocateOldestFirst, allocationError, amountToPay, balanceOf, creditAmountError, paymentError } from "./ap.logic"

describe("ap.logic", () => {
  it("works out what a bill still owes", () => {
    expect(balanceOf({ totalAmount: 1000, paidAmount: 300, creditedAmount: 100 })).toBe(600)
    expect(balanceOf({ totalAmount: 1000, paidAmount: 900, creditedAmount: 200 })).toBe(0)
    expect(balanceOf({ totalAmount: 100 })).toBe(100)
    expect(balanceOf({ totalAmount: 100, balance: 40 })).toBe(40)
    expect(balanceOf({ totalAmount: 0.3, paidAmount: 0.1, creditedAmount: 0.1 })).toBe(0.1)
  })

  it("checks a payment before it is sent", () => {
    expect(paymentError("", 250, true)).toBeNull()
    expect(paymentError("100", 250, true)).toBeNull()
    expect(paymentError("100,50", 250, true)).toBeNull()
    expect(paymentError("0", 250, true)).toBe("Enter the amount to pay")
    expect(paymentError("abc", 250, true)).toBe("Enter the amount to pay")
    expect(paymentError("250.01", 250, true)).toMatch(/more than the/)
    expect(paymentError("", 0, true)).toBe("This bill has nothing left to pay")
    expect(paymentError("100", 250, false)).toMatch(/bank account/)
  })

  it("pays the balance when no amount is typed", () => {
    expect(amountToPay("", 250)).toBe(250)
    expect(amountToPay(" 80,5 ", 250)).toBe(80.5)
  })

  it("applies credit to the oldest bills first", () => {
    expect(allocateOldestFirst(130, [{ id: "a", balance: 100 }, { id: "b", balance: 50 }, { id: "c", balance: 70 }]))
      .toEqual([{ billId: "a", amount: 100 }, { billId: "b", amount: 30 }])
    expect(allocateOldestFirst(0, [{ id: "a", balance: 100 }])).toEqual([])
    expect(allocateOldestFirst(500, [{ id: "a", balance: 100 }, { id: "b", balance: 0 }])).toEqual([{ billId: "a", amount: 100 }])
  })

  it("checks allocations", () => {
    const open = [{ id: "a", balance: 100 }, { id: "b", balance: 50 }]
    expect(allocationError([{ billId: "a", amount: 100 }, { billId: "b", amount: 20 }], 120, open)).toBeNull()
    expect(allocationError([], 120, open)).toMatch(/at least one bill/)
    expect(allocationError([{ billId: "z", amount: 5 }], 120, open)).toMatch(/not open/)
    expect(allocationError([{ billId: "b", amount: 50.01 }], 120, open)).toMatch(/still owes/)
    expect(allocationError([{ billId: "a", amount: 100 }, { billId: "b", amount: 50 }], 120, open)).toMatch(/of credit left/)
    expect(allocationError([{ billId: "a", amount: 0 }], 120, open)).toMatch(/more than zero/)
  })

  it("checks the credit note amounts", () => {
    expect(creditAmountError("100", "15")).toBeNull()
    expect(creditAmountError("100", "")).toBeNull()
    expect(creditAmountError("", "")).toMatch(/Enter the amount/)
    expect(creditAmountError("100", "-1")).toMatch(/less than zero/)
    expect(creditAmountError("100", "101")).toMatch(/more than the amount/)
  })
})
