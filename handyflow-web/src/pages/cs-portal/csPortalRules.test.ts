import { describe, it, expect } from "vitest"
import { quoteStatus, agreementStatus, invoiceStatus, passwordProblem, attention, nextBill } from "./csPortalRules"
import type { CsPortalAgreement, CsPortalInvoice, CsPortalQuote } from "../../api/csPortal.api"

const quote = (o: Partial<CsPortalQuote> = {}) => ({ id: "q", quoteNumber: "CQ-1", status: "SENT", validUntil: "2027-03-20", ...o }) as CsPortalQuote
const agreement = (o: Partial<CsPortalAgreement> = {}) => ({ id: "a", agreementNumber: "CA-1", status: "ACTIVE", endDate: null, nextBills: [], ...o }) as CsPortalAgreement
const invoice = (o: Partial<CsPortalInvoice> = {}) => ({ invoiceNumber: "INV-1", status: "ISSUED", outstanding: 0, ...o }) as CsPortalInvoice

describe("csPortalRules", () => {
  it("words statuses, and falls back to something readable", () => {
    expect(quoteStatus("SENT")[0]).toBe("Waiting for your answer")
    expect(quoteStatus("EXPIRED")[1]).toBe("warning")
    expect(agreementStatus("SUSPENDED")[0]).toBe("Paused")
    expect(invoiceStatus("PARTIALLY_PAID")[0]).toBe("Part paid")
    expect(invoiceStatus("SOMETHING_NEW")).toEqual(["something new", "neutral"])
  })
  it("checks the new password", () => {
    expect(passwordProblem("short", "short")).toMatch(/at least 8/)
    expect(passwordProblem("long-enough", "different1")).toMatch(/don't match/)
    expect(passwordProblem("long-enough", "long-enough")).toBeNull()
  })
  it("lists what needs attention, invoices first", () => {
    const items = attention([quote()], [agreement({ endDate: "2027-04-01" })], [invoice({ outstanding: 1000, status: "OVERDUE" }), invoice({ invoiceNumber: "INV-2", outstanding: 0, status: "PAID" })], "2027-03-10")
    expect(items.map(i => i.kind)).toEqual(["invoice", "quote", "agreement"])
    expect(items[0]).toMatchObject({ tone: "danger", text: "Invoice INV-1 has R 1000.00 still owing" })
    expect(items[1].text).toContain("(10 days)")
    expect(items[2].text).toContain("ends on 2027-04-01")
  })
  it("marks a quote close to its date as more pressing, and ignores answered quotes and far-off agreement ends", () => {
    expect(attention([quote({ validUntil: "2027-03-12" })], [], [], "2027-03-10")[0].tone).toBe("warning")
    expect(attention([quote({ status: "ACCEPTED" })], [agreement({ endDate: "2028-01-01" })], [], "2027-03-10")).toEqual([])
  })
  it("finds the next bill across live agreements only", () => {
    const a = agreement({ nextBills: [{ date: "2027-04-01", amount: 1500 }] })
    const b = agreement({ nextBills: [{ date: "2027-03-15", amount: 900 }] })
    const paused = agreement({ status: "SUSPENDED", nextBills: [{ date: "2027-03-11", amount: 5 }] })
    expect(nextBill([a, b, paused])).toEqual({ date: "2027-03-15", amount: 900 })
    expect(nextBill([])).toBeNull()
  })
})
