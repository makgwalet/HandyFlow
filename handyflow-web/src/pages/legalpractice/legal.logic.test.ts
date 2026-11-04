import { describe, it, expect } from "vitest"
import { type TrustForm, canRevokeGrant, emptyTrust, evidenceProblem, fileSize, inviteProblem, payableByTrust, trustKindsFor, trustProblem, trustRequest, attorneyProblem, billable, canChangeWork, canLogWork, clientProblem, disbProblem, emptyAttorney, emptyClient, emptyDisb, emptyKeyDate, emptyMatter, emptyProfile, emptyRetainer, emptyTime, generateProblem, invoiceActions, keyDateNote, keyDateProblem, matterActions, matterProblem, matterRequest, paymentProblem, profileProblem, retainerProblem, timeProblem, timeRequest } from "./legal.logic"

const at = (s: string) => new Date(s + "T09:30:00")
describe("matter lifecycle", () => {
  it("offers the moves each status allows", () => {
    expect(matterActions("OPEN")).toEqual(["edit", "hold", "close"])
    expect(matterActions("ON_HOLD")).toEqual(["edit", "reopen", "close"])
    expect(matterActions("CLOSED")).toEqual(["reopen", "archive"])
    expect(matterActions("ARCHIVED")).toEqual([])
  })
  it("lets work be logged only on live matters, and changed only while unbilled", () => {
    expect(canLogWork("OPEN")).toBe(true); expect(canLogWork("ON_HOLD")).toBe(true); expect(canLogWork("CLOSED")).toBe(false); expect(canLogWork("ARCHIVED")).toBe(false)
    expect(canChangeWork("UNBILLED")).toBe(true); expect(canChangeWork("BILLED")).toBe(false); expect(canChangeWork("WRITTEN_OFF")).toBe(false)
  })
})
describe("matter form", () => {
  const ok = { ...emptyMatter(), clientId: "c", attorneyId: "a", matterNumber: "M-001", matterName: "Estate Dlamini" }
  it("needs client, attorney, number and name, and a fee for fixed fee work", () => {
    expect(matterProblem(ok, true)).toBeNull()
    expect(matterProblem({ ...ok, clientId: "" }, true)).toMatch(/client/); expect(matterProblem({ ...ok, clientId: "" }, false)).toBeNull()
    expect(matterProblem({ ...ok, matterNumber: " " }, true)).toMatch(/number/)
    expect(matterProblem({ ...ok, billingType: "FIXED_FEE" }, true)).toMatch(/fixed fee/)
    expect(matterProblem({ ...ok, billingType: "FIXED_FEE", fixedFeeAmount: "15000" }, true)).toBeNull()
  })
  it("drops the fixed fee when the matter is hourly and leaves creation-only fields out of an edit", () => {
    expect(matterRequest({ ...ok, fixedFeeAmount: "999" }, true)).toMatchObject({ fixedFeeAmount: null, clientId: "c", matterNumber: "M-001" })
    expect(matterRequest(ok, false)).not.toHaveProperty("clientId")
  })
})
describe("time entries", () => {
  const ok = { ...emptyTime("a"), hours: "1.5", description: "Drafted letter of demand" }
  it("falls back to the attorney's rate, and asks for one when neither exists", () => {
    expect(timeProblem(ok, false, 2200)).toBeNull()
    expect(timeProblem(ok, false, null)).toMatch(/no default rate/)
    expect(timeProblem({ ...ok, hourlyRate: "1800" }, false, null)).toBeNull()
    expect(timeProblem(ok, true, 2200)).toMatch(/hourly rate/)
  })
  it("checks hours and description", () => {
    expect(timeProblem({ ...ok, hours: "0" }, false, 1)).toMatch(/above 0/)
    expect(timeProblem({ ...ok, hours: "25" }, false, 1)).toMatch(/24/)
    expect(timeProblem({ ...ok, description: " " }, false, 1)).toMatch(/Describe/)
    expect(timeProblem({ ...ok, attorneyId: "" }, false, 1)).toMatch(/who did/)
    expect(timeRequest({ ...ok, hourlyRate: " " }, false)).toMatchObject({ hourlyRate: null, billable: true })
    expect(timeRequest(ok, true)).not.toHaveProperty("attorneyId")
  })
})
describe("expenses and key dates", () => {
  it("needs a description and a positive amount", () => {
    expect(disbProblem({ ...emptyDisb(), description: "Sheriff fees", amount: "450" })).toBeNull()
    expect(disbProblem({ ...emptyDisb(), amount: "450" })).toMatch(/Describe/)
    expect(disbProblem({ ...emptyDisb(), description: "x", amount: "0" })).toMatch(/above 0/)
  })
  it("needs a date and description", () => {
    expect(keyDateProblem({ ...emptyKeyDate(), dueDate: "2026-12-01", description: "Trial" })).toBeNull()
    expect(keyDateProblem(emptyKeyDate())).toMatch(/date/)
  })
  it("flags pending deadlines that are close or past, and nothing else", () => {
    expect(keyDateNote("PENDING", "2026-11-03", at("2026-11-04"))).toEqual({ text: "1 day overdue", tone: "danger" })
    expect(keyDateNote("PENDING", "2026-11-04", at("2026-11-04"))?.text).toBe("Due today")
    expect(keyDateNote("PENDING", "2026-11-07", at("2026-11-04"))).toEqual({ text: "Due in 3 days", tone: "danger" })
    expect(keyDateNote("PENDING", "2026-11-12", at("2026-11-04"))?.tone).toBe("warning")
    expect(keyDateNote("PENDING", "2026-12-12", at("2026-11-04"))).toBeNull()
    expect(keyDateNote("COMPLETED", "2026-11-03", at("2026-11-04"))).toBeNull()
  })
})
describe("clients, attorneys, retainers, profile", () => {
  it("checks names, emails and rates", () => {
    expect(clientProblem({ ...emptyClient(), name: "Dlamini" })).toBeNull(); expect(clientProblem(emptyClient())).toMatch(/name/); expect(clientProblem({ ...emptyClient(), name: "D", email: "x" })).toMatch(/email/)
    expect(attorneyProblem({ ...emptyAttorney(), name: "Thandi" })).toBeNull(); expect(attorneyProblem({ ...emptyAttorney(), name: "T", hourlyRate: "-1" })).toMatch(/rate/)
    expect(retainerProblem({ ...emptyRetainer(), clientId: "c", monthlyFee: "5000" }, true)).toBeNull()
    expect(retainerProblem({ ...emptyRetainer(), monthlyFee: "5000" }, true)).toMatch(/client/)
    expect(retainerProblem({ ...emptyRetainer(), monthlyFee: "0" }, false)).toMatch(/above 0/)
    expect(retainerProblem({ ...emptyRetainer(), monthlyFee: "5", startDate: "2026-02-01", endDate: "2026-01-01" }, false)).toMatch(/before/)
  })
  it("refuses to treat the trust and business accounts as one", () => {
    const p = { ...emptyProfile(), firmName: "Dlamini Inc", trustBankName: "FNB", trustAccountNumber: "123", businessBankName: "FNB", businessAccountNumber: "123" }
    expect(profileProblem(p)).toMatch(/cannot be the same/)
    expect(profileProblem({ ...p, businessAccountNumber: "456" })).toBeNull()
    expect(profileProblem({ ...emptyProfile() })).toMatch(/firm/)
  })
})
describe("invoices", () => {
  it("bills the chosen work, or the fixed fee when none is chosen", () => {
    expect(billable([{ id: "t", amount: 1650.1 }], [{ id: "d", amount: 450 }], "9999")).toBe(2100.1)
    expect(billable([], [], "15000")).toBe(15000)
    expect(billable([], [], "")).toBe(0)
  })
  it("needs something to bill", () => {
    const f = { dueDate: "", description: "", notes: "", fixedFeeAmount: "" }
    expect(generateProblem([], [], f)).toMatch(/Choose the work/)
    expect(generateProblem([{ id: "t", amount: 1 }], [], f)).toBeNull()
    expect(generateProblem([], [], { ...f, fixedFeeAmount: "100" })).toBeNull()
  })
  it("sends drafts, takes payment on sent ones, and keeps write-off for admins", () => {
    expect(invoiceActions("DRAFT", true)).toEqual(["send"])
    expect(invoiceActions("SENT", false)).toEqual(["pay"]); expect(invoiceActions("PARTIALLY_PAID", true)).toEqual(["pay", "writeOff"])
    expect(invoiceActions("PAID", true)).toEqual([]); expect(invoiceActions("WRITTEN_OFF", true)).toEqual([])
  })
  it("refuses a payment above what is outstanding", () => {
    expect(paymentProblem(500, "")).toMatch(/above 0/); expect(paymentProblem(500, "500.01")).toMatch(/more than/); expect(paymentProblem(500, "500")).toBeNull()
  })
})

describe("trust account", () => {
  const f = (o: Partial<TrustForm> = {}): TrustForm => ({ ...emptyTrust(), amount: "100", ...o })
  it("lets managers receive money but keeps withdrawals for admins", () => {
    expect(trustKindsFor(false, true)).toEqual(["receipt"])
    expect(trustKindsFor(true, true)).toEqual(["receipt", "transfer", "disbursement", "refund"])
    expect(trustKindsFor(false, false)).toEqual([])
  })
  it("accepts a receipt of any size but needs a sensible amount", () => {
    expect(trustProblem("receipt", f({ amount: "1000000" }), 0)).toBeNull()
    expect(trustProblem("receipt", f({ amount: "" }), 0)).toMatch(/amount/)
    expect(trustProblem("receipt", f({ amount: "-5" }), 0)).toMatch(/amount/)
    expect(trustProblem("receipt", f({ amount: "10.505" }), 0)).toMatch(/decimal/)
  })
  it("never lets a withdrawal exceed what the client has in trust", () => {
    expect(trustProblem("refund", f({ amount: "500.01", payee: "A" }), 500)).toMatch(/in trust/)
    expect(trustProblem("refund", f({ amount: "500", payee: "A" }), 500)).toBeNull()
    expect(trustProblem("disbursement", f({ payee: "Sheriff" }), 0)).toMatch(/in trust/)
  })
  it("needs an invoice for a transfer and a payee for payouts", () => {
    expect(trustProblem("transfer", f(), 500)).toMatch(/invoice/)
    expect(trustProblem("transfer", f({ invoiceId: "i", amount: "300" }), 500, 250)).toMatch(/still owes/)
    expect(trustProblem("transfer", f({ invoiceId: "i", amount: "250" }), 500, 250)).toBeNull()
    expect(trustProblem("disbursement", f(), 500)).toMatch(/who is being paid/)
    expect(trustProblem("refund", f(), 500)).toMatch(/refund is paid to/)
  })
  it("builds the request each endpoint expects", () => {
    expect(trustRequest("receipt", f({ reference: " R1 " }))).toEqual({ amount: 100, transactionDate: null, reference: "R1", notes: null, matterId: null })
    expect(trustRequest("transfer", f({ invoiceId: "i" }))).toMatchObject({ invoiceId: "i", amount: 100 })
    expect(trustRequest("refund", f({ payee: " Mr A " }))).toMatchObject({ payee: "Mr A" })
  })
  it("only offers sent invoices that still owe money", () => {
    expect(payableByTrust("SENT", 10)).toBe(true)
    expect(payableByTrust("PARTIALLY_PAID", 10)).toBe(true)
    expect(payableByTrust("PAID", 0)).toBe(false)
    expect(payableByTrust("DRAFT", 10)).toBe(false)
    expect(payableByTrust("SENT", 0)).toBe(false)
  })
})

describe("portal access and documents", () => {
  const grants = [{ inviteEmail: "Jo@x.co", status: "PENDING" }, { inviteEmail: "old@x.co", status: "REVOKED" }]
  it("checks an invite", () => {
    expect(inviteProblem("", grants)).toMatch(/Enter/)
    expect(inviteProblem("nope", grants)).toMatch(/look right/)
    expect(inviteProblem("jo@x.co", grants)).toMatch(/already/)
    expect(inviteProblem("old@x.co", grants)).toBeNull()
    expect(inviteProblem("new@x.co", grants)).toBeNull()
  })
  it("lets only pending or active grants be revoked", () => {
    expect(canRevokeGrant("PENDING")).toBe(true)
    expect(canRevokeGrant("ACTIVE")).toBe(true)
    expect(canRevokeGrant("REVOKED")).toBe(false)
  })
  it("checks a document before it is sent", () => {
    expect(evidenceProblem(null, "DOCUMENT")).toMatch(/Choose a file/)
    expect(evidenceProblem({ size: 0 }, "DOCUMENT")).toMatch(/empty/)
    expect(evidenceProblem({ size: 11 * 1024 * 1024 }, "DOCUMENT")).toMatch(/10 MB/)
    expect(evidenceProblem({ size: 1000 }, "")).toMatch(/kind/)
    expect(evidenceProblem({ size: 1000 }, "CONTRACT")).toBeNull()
  })
  it("writes file sizes plainly", () => {
    expect(fileSize(512)).toBe("512 B")
    expect(fileSize(2048)).toBe("2 KB")
    expect(fileSize(5 * 1024 * 1024)).toBe("5 MB")
  })
})
