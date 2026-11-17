import { describe, it, expect } from "vitest"
import { onlyTabs, sellable, validateQuantity, validateNewTab, tabActions, tabInvoiceText, canRaiseTabInvoice, paidText, type Tab } from "./tabOps"

const tab = (o: Partial<Tab> = {}): Tab => ({ id: "t1", vehicleRegistration: "GP 1", driverName: null, accountId: null, status: "OPEN", paymentMethod: null, openedAt: "2026-11-17T08:00:00Z", settledAt: null,
  voidReason: null, invoiceState: null, invoiceNumber: null, invoiceError: null, lines: [{ id: "l1", offeringId: "o1", description: "Shower", quantity: 1, unitPrice: 45, amount: 45 }], total: 45, ...o })

describe("tabOps", () => {
  it("keeps only tab-shaped rows and only sellable services", () => {
    expect(onlyTabs([{ id: "x" }, tab()])).toHaveLength(1)
    expect(onlyTabs({})).toEqual([])
    expect(sellable([{ id: "1", serviceName: "Shower", active: true, price: 45 }, { id: "2", serviceName: "Wash", active: false, price: 90 }, { id: "3", serviceName: "Free", active: true, price: null }, { id: "4" }]).map(o => o.id)).toEqual(["1"])
  })
  it("checks the quantity", () => {
    expect(validateQuantity("")).not.toBeNull(); expect(validateQuantity("0")).not.toBeNull(); expect(validateQuantity("-2")).not.toBeNull(); expect(validateQuantity("1000")).not.toBeNull()
    expect(validateQuantity("1.5")).toBeNull()
  })
  it("needs a registration to open a tab", () => {
    expect(validateNewTab("  ")).not.toBeNull(); expect(validateNewTab("GP 1")).toBeNull()
  })
  it("offers settle at the till for a tab with no account, charge for one with an account, and neither with no lines", () => {
    expect(tabActions(tab())).toEqual(["settle", "addLines", "void"])
    expect(tabActions(tab({ accountId: "a1" }))).toEqual(["charge", "addLines", "void"])
    expect(tabActions(tab({ lines: [], total: 0 }))).toEqual(["addLines", "void"])
    expect(tabActions(tab({ status: "SETTLED" }))).toEqual([])
    expect(tabActions(tab({ status: "VOID" }))).toEqual([])
  })
  it("describes the invoice and allows a retry only when it was not raised or failed", () => {
    const s = (o: Partial<Tab>) => tab({ status: "SETTLED", accountId: "a1", paymentMethod: "ACCOUNT", ...o })
    expect(tabInvoiceText(tab())).toBeNull()
    expect(tabInvoiceText(s({ invoiceState: "INVOICED", invoiceNumber: "INV-1" }))).toBe("Draft invoice INV-1")
    expect(tabInvoiceText(s({ invoiceState: "FAILED", invoiceError: "No series" }))).toBe("Invoice failed: No series")
    expect(tabInvoiceText(s({ invoiceState: "LINK_FAILED", invoiceNumber: "INV-9" }))).toMatch(/Do not raise it again/)
    expect(canRaiseTabInvoice(s({ invoiceState: "FAILED" }))).toBe(true)
    expect(canRaiseTabInvoice(s({ invoiceState: "PENDING" }))).toBe(true)
    expect(canRaiseTabInvoice(s({ invoiceState: "INVOICED" }))).toBe(false)
    expect(canRaiseTabInvoice(s({ invoiceState: "LINK_FAILED" }))).toBe(false)
    expect(canRaiseTabInvoice(tab({ status: "SETTLED", paymentMethod: "CASH" }))).toBe(false)
  })
  it("says how a tab was or will be paid", () => {
    expect(paidText(tab())).toBe("Not paid yet")
    expect(paidText(tab({ accountId: "a1" }), "Acme")).toBe("Will be charged to Acme")
    expect(paidText(tab({ status: "SETTLED", paymentMethod: "CARD" }))).toBe("Paid by card")
    expect(paidText(tab({ status: "SETTLED", paymentMethod: "ACCOUNT", accountId: "a1" }), "Acme")).toBe("Charged to Acme")
    expect(paidText(tab({ status: "VOID", voidReason: "Left without paying" }))).toBe("Void: Left without paying")
  })
})

import { vatBreakdown as vb, vatText as vt } from "./tabOps"
describe("VAT breakdown", () => {
  it("adds VAT once on an ex-VAT amount", () => {
    const b = vb(170, { registered: true, ratePercent: 15 })!
    expect(b.vat).toBe(25.5); expect(b.total).toBe(195.5)
  })
  it("adds nothing when not registered", () => {
    const b = vb(170, { registered: false, ratePercent: 15 })!
    expect(b.vat).toBe(0); expect(b.total).toBe(170)
    expect(vt(b, n => `R${n}`)).toBe("R170, no VAT charged")
  })
  it("says nothing until the rate is known", () => { expect(vb(170, undefined)).toBeNull(); expect(vb(170, { x: 1 })).toBeNull() })
  it("words the breakdown", () => { expect(vt(vb(100, { registered: true, ratePercent: 15 }), n => `R${n}`)).toBe("R100 ex VAT + R15 VAT (15%) = R115") })
})

import { asTakings, takingsRangeError } from "./tabOps"
describe("takings helpers", () => {
  it("accepts only a report-shaped answer", () => {
    expect(asTakings([])).toBeNull(); expect(asTakings({})).toBeNull()
    expect(asTakings({ days: [], totals: [], takingsExVat: 0 })).not.toBeNull()
  })
  it("checks the date range", () => {
    expect(takingsRangeError("", "2026-11-01")).not.toBeNull()
    expect(takingsRangeError("2026-11-02", "2026-11-01")).toMatch(/before/)
    expect(takingsRangeError("2026-01-01", "2026-11-01")).toMatch(/93/)
    expect(takingsRangeError("2026-11-01", "2026-11-07")).toBeNull()
  })
})
