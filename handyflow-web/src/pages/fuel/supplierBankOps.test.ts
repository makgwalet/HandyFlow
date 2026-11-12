import { describe, it, expect } from "vitest"
import { validateBank, toProposal, canDecide, canWithdraw, verifiedOf, pendingOf, emptyBank, type BankAccount } from "./supplierBankOps"

const acct = (o: Partial<BankAccount> = {}): BankAccount => ({ id: "a1", supplierId: "s1", accountHolder: "Acme", bankName: "Std", branchCode: null, accountNumberMasked: "****5678", accountType: "CURRENT",
  status: "PENDING_VERIFICATION", reason: null, proposedBy: "alice", proposedAt: "2026-11-12T08:00:00Z", decidedBy: null, decidedAt: null, decisionNote: null, ...o })
const form = (o = {}) => ({ ...emptyBank(), accountHolder: "Acme", bankName: "Std", accountNumber: "6201 2345-678", ...o })

describe("supplierBankOps (B04)", () => {
  it("checks the details before they are sent", () => {
    expect(validateBank(form({ accountHolder: " " }), false)).toMatch(/holder/)
    expect(validateBank(form({ bankName: "" }), false)).toMatch(/bank/i)
    expect(validateBank(form({ accountNumber: "12ab" }), false)).toMatch(/5 to 20 digits/)
    expect(validateBank(form({ accountNumber: "1".repeat(21) }), false)).toMatch(/5 to 20 digits/)
    expect(validateBank(form({ branchCode: "05;10" }), false)).toMatch(/branch/)
    expect(validateBank(form(), false)).toBeNull()
  })
  it("asks why only when there is a verified account to replace", () => {
    expect(validateBank(form(), true)).toMatch(/why/i)
    expect(validateBank(form({ reason: "Supplier moved banks" }), true)).toBeNull()
  })
  it("sends the number without spaces or dashes and blanks as null", () => {
    expect(toProposal(form())).toEqual({ accountHolder: "Acme", bankName: "Std", branchCode: null, accountNumber: "62012345678", accountType: "CURRENT", reason: null })
  })
  it("only someone other than the proposer can decide, and only the proposer can withdraw, while it waits", () => {
    expect(canDecide(acct(), "bob")).toBe(true); expect(canDecide(acct(), "alice")).toBe(false); expect(canDecide(acct(), undefined)).toBe(false)
    expect(canDecide(acct({ status: "VERIFIED" }), "bob")).toBe(false)
    expect(canWithdraw(acct(), "alice")).toBe(true); expect(canWithdraw(acct(), "bob")).toBe(false); expect(canWithdraw(acct({ status: "REJECTED" }), "alice")).toBe(false)
  })
  it("finds the verified and the waiting account", () => {
    const l = [acct({ id: "p" }), acct({ id: "v", status: "VERIFIED" }), acct({ id: "o", status: "SUPERSEDED" })]
    expect(verifiedOf(l)?.id).toBe("v"); expect(pendingOf(l)?.id).toBe("p"); expect(verifiedOf([])).toBeNull()
  })
})
