import { describe, it, expect } from "vitest"
import { actionsFor, noteProblem, invoiceText, byUrgency, stageLabel } from "./billingRules"

describe("billingRules", () => {
  it("offers what the server allows, only to people who can manage", () => {
    expect(actionsFor(["raise", "skip"], true).map(a => a.id)).toEqual(["raise", "skip"])
    expect(actionsFor(["raise", "skip"], false)).toEqual([])
    expect(actionsFor(["bogus"], true)).toEqual([])
  })
  it("asks for a reason where one is needed", () => {
    const [raise, skip] = actionsFor(["raise", "skip"], true)
    expect(noteProblem(raise, "")).toBeNull()
    expect(noteProblem(skip, "short")).toMatch(/at least 10/)
    expect(noteProblem(skip, "Client prepaid by EFT")).toBeNull()
  })
  it("says where the invoice stands", () => {
    expect(invoiceText(null, null)).toBe("")
    expect(invoiceText("INV-1", null)).toBe("INV-1 · not found in Invoicing")
    expect(invoiceText("INV-1", { status: "DRAFT", total: 1725, paid: null, credited: null })).toBe("INV-1 · draft")
    expect(invoiceText("INV-1", { status: "ISSUED", total: 1725, paid: null, credited: null })).toBe("INV-1 · issued · 1725.00 owing")
    expect(invoiceText("INV-1", { status: "PARTIALLY_PAID", total: 1725, paid: 725, credited: 0 })).toBe("INV-1 · partially paid · 1000.00 owing")
    expect(invoiceText("INV-1", { status: "PAID", total: 1725, paid: 1725, credited: null })).toBe("INV-1 · paid · settled")
    expect(invoiceText("INV-1", { status: "CANCELLED", total: 1725, paid: null, credited: null })).toBe("INV-1 · cancelled")
  })
  it("sorts the most urgent first, then by date", () => {
    const rows = [
      { stage: "UPCOMING", dueDate: "2027-05-01" }, { stage: "INVOICED", dueDate: "2027-01-01" }, { stage: "DUE", dueDate: "2027-03-12" },
      { stage: "OVERDUE_TO_RAISE", dueDate: "2027-03-01" }, { stage: "OVERDUE_TO_RAISE", dueDate: "2027-02-01" },
    ]
    expect(byUrgency(rows).map(r => `${r.stage}:${r.dueDate}`)).toEqual([
      "OVERDUE_TO_RAISE:2027-02-01", "OVERDUE_TO_RAISE:2027-03-01", "DUE:2027-03-12", "UPCOMING:2027-05-01", "INVOICED:2027-01-01"])
  })
  it("words stages", () => { expect(stageLabel("OVERDUE_TO_RAISE")).toBe("Overdue to raise"); expect(stageLabel("X")).toBe("X") })
})
