import { describe, it, expect } from "vitest"
import { asStatement, defaultRange, validateRange, invoiceLabel, toCsv, type Statement } from "./statementOps"

const stmt = (o: Partial<Statement> = {}): Statement => ({ accountId: "a1", from: "2026-11-01", to: "2026-11-30", total: 1520, invoiced: 1350, notInvoiced: 170,
  lines: [
    { date: "2026-11-06", kind: "STAY", sourceId: "s1", vehicleRegistration: "GP 1", description: "Overnight parking, 3 nights at 450", amount: 1350, invoiceState: "INVOICED", invoiceNumber: "INV-1" },
    { date: "2026-11-07", kind: "TAB", sourceId: "t1", vehicleRegistration: "CA 9", description: "2 x Shower, 1 x Meal", amount: 170, invoiceState: null, invoiceNumber: null }], ...o })

describe("statementOps", () => {
  it("accepts only a real statement", () => {
    expect(asStatement([])).toBeNull(); expect(asStatement({ lines: [] })).toBeNull(); expect(asStatement(stmt())).not.toBeNull()
  })
  it("defaults to the 30 days up to today", () => {
    expect(defaultRange("2026-11-30")).toEqual({ from: "2026-11-01", to: "2026-11-30" })
  })
  it("checks the period", () => {
    expect(validateRange("", "2026-11-30")).not.toBeNull()
    expect(validateRange("2026-11-30", "2026-11-01")).not.toBeNull()
    expect(validateRange("2025-01-01", "2026-11-30")).not.toBeNull()
    expect(validateRange("2026-11-01", "2026-11-30")).toBeNull()
  })
  it("says how far each charge got", () => {
    expect(invoiceLabel({ invoiceState: "INVOICED", invoiceNumber: "INV-1" })).toBe("Draft invoice INV-1")
    expect(invoiceLabel({ invoiceState: null, invoiceNumber: null })).toBe("Not invoiced yet")
    expect(invoiceLabel({ invoiceState: "FAILED", invoiceNumber: null })).toBe("Invoice failed")
  })
  it("writes a CSV with the totals, quoting commas", () => {
    const csv = toCsv(stmt())
    expect(csv.split("\r\n")[0]).toBe("Date,Type,Vehicle,Description,Amount (before VAT),Invoice")
    expect(csv).toContain('"2 x Shower, 1 x Meal"')
    expect(csv).toContain(",Total,1520.00,")
    expect(csv).toContain(",Not invoiced yet,170.00,")
  })
  it("keeps a spreadsheet from reading a text cell as a formula", () => {
    const csv = toCsv(stmt({ lines: [{ date: "2026-11-06", kind: "TAB", sourceId: "t", vehicleRegistration: "=1+1", description: "@x", amount: 1, invoiceState: null, invoiceNumber: null }] }))
    expect(csv).toContain("'=1+1")
    expect(csv).toContain("'@x")
  })
})

import { hasSettlement, toCsv as toCsv2, STATUS_LABEL } from "./statementOps"
describe("settlement", () => {
  const base = { accountId: "a", from: "2026-11-01", to: "2026-11-30", total: 100, invoiced: 100, notInvoiced: 0, lines: [] as any[] }
  it("only claims settlement when Invoicing was read and there are invoices", () => {
    expect(hasSettlement(base)).toBe(false)
    expect(hasSettlement({ ...base, settlementKnown: false, invoices: [{ invoiceId: "i", invoiceNumber: "1", status: "PAID", issuedAt: null, total: 1, paid: 1, credited: 0, outstanding: 0 }] })).toBe(false)
    expect(hasSettlement({ ...base, settlementKnown: true, invoices: [] })).toBe(false)
    expect(hasSettlement({ ...base, settlementKnown: true, invoices: [{ invoiceId: "i", invoiceNumber: "1", status: "PAID", issuedAt: null, total: 1, paid: 1, credited: 0, outstanding: 0 }] })).toBe(true)
  })
  it("adds the invoice standing to the CSV only when it is known", () => {
    expect(toCsv2(base)).not.toContain("Still owed")
    const csv = toCsv2({ ...base, settlementKnown: true, invoicedWithVat: 115, paid: 50, credited: 15, outstanding: 50, invoices: [{ invoiceId: "i", invoiceNumber: "INV-1", status: "PARTIALLY_PAID", issuedAt: null, total: 115, paid: 50, credited: 15, outstanding: 50 }] })
    expect(csv).toContain("Invoice,Status,Total (with VAT),Paid,Credited,Still owed")
    expect(csv).toContain("INV-1,Part paid,115.00,50.00,15.00,50.00")
    expect(STATUS_LABEL.PAID).toBe("Paid")
  })
})
