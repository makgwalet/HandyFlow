import { describe, it, expect } from "vitest"
import { changeText, debtorFrom, donutArcs, inQueue, linePoints, newCaseProblem, owing, periodRange, pickedTotal, queueCounts, rowsOf, searchCases, searchReady, unpaidInvoices, type CaseRow, type Customer, type InvoiceRow } from "./collections.logic"

const inv = (id: string, status: string, total: number, extra: Partial<InvoiceRow> = {}): InvoiceRow => ({ id, invoiceNumber: id.toUpperCase(), status, customerId: null, total, amountPaid: 0, creditedAmount: 0, balanceDue: null, dueDate: "2026-09-01", walkinClientName: null, walkinClientEmail: null, walkinClientPhone: null, ...extra })
const cas = (id: string, status: string, extra: Partial<CaseRow> = {}): CaseRow => ({ id, caseNumber: id, debtorName: "Debtor " + id, status, totalOutstanding: 100, assignedToUserId: null, assignedToUserName: null, nextActionDate: null, openedDate: "2026-09-01", ...extra })

describe("choosing invoices", () => {
  it("lists only unpaid invoices, oldest due first, and searches by number or name", () => {
    const list = [inv("a", "PAID", 100), inv("b", "ISSUED", 100, { dueDate: "2026-09-10" }), inv("c", "OVERDUE", 100, { dueDate: "2026-08-01", walkinClientName: "Thandi" }), inv("d", "DRAFT", 100), inv("e", "ISSUED", 100, { amountPaid: 100 })]
    expect(unpaidInvoices(list, "").map(i => i.id)).toEqual(["c", "b"])
    expect(unpaidInvoices(list, "thandi").map(i => i.id)).toEqual(["c"])
    expect(unpaidInvoices(list, "B").map(i => i.id)).toEqual(["b"])
    expect(unpaidInvoices(list, "acme", { x: "Acme" }).length).toBe(0)
    expect(unpaidInvoices([inv("z", "ISSUED", 50, { customerId: "x" })], "acme", { x: "Acme" }).length).toBe(1)
  })
  it("works out what is owing, counting credit notes as settled", () => {
    expect(owing({ total: 1000, amountPaid: 200, creditedAmount: 300, balanceDue: null })).toBe(500)
    expect(owing({ total: 1000, amountPaid: 0, creditedAmount: 0, balanceDue: 250 })).toBe(250)
    expect(owing({ total: 100, amountPaid: 150, creditedAmount: 0, balanceDue: null })).toBe(0)
  })
  it("totals the ticked invoices", () => {
    const list = [inv("a", "ISSUED", 100.1), inv("b", "ISSUED", 200.2), inv("c", "ISSUED", 5)]
    expect(pickedTotal(list, new Set(["a", "b"]))).toBe(300.3)
  })
  it("fills the debtor from the customer or the walk-in details", () => {
    const c: Customer = { id: "1", name: "Acme", email: "a@acme.test", phone: null }
    expect(debtorFrom(c, [])).toEqual({ name: "Acme", email: "a@acme.test", phone: "" })
    expect(debtorFrom(null, [inv("a", "ISSUED", 1), inv("b", "ISSUED", 1, { walkinClientName: "Thandi", walkinClientEmail: "t@x.test", walkinClientPhone: "082" })])).toEqual({ name: "Thandi", email: "t@x.test", phone: "082" })
    expect(debtorFrom(null, [])).toEqual({ name: "", email: "", phone: "" })
  })
  it("says what is missing before a case can be opened", () => {
    const c: Customer = { id: "1", name: "Acme", email: null, phone: null }
    const out = [{ id: "i", invoiceNumber: "I", dueDate: null, total: 1, amountPaid: 0, outstanding: 1 }]
    expect(newCaseProblem("customer", null, [], 0, "")).toMatch(/Choose the customer/)
    expect(newCaseProblem("customer", c, [], 0, "")).toMatch(/no outstanding/)
    expect(newCaseProblem("customer", c, out, 0, "")).toBeNull()
    expect(newCaseProblem("invoices", null, [], 0, "X")).toMatch(/Tick at least one/)
    expect(newCaseProblem("invoices", null, [], 1, " ")).toMatch(/name/)
    expect(newCaseProblem("invoices", null, [], 1, "Thandi")).toBeNull()
  })
  it("waits for two letters before searching", () => { expect(searchReady("a")).toBe(false); expect(searchReady(" ac ")).toBe(true) })
})

describe("queue", () => {
  const today = "2026-10-14"
  const cases = [cas("1", "OPEN", { assignedToUserId: "me", nextActionDate: "2026-10-10" }), cas("2", "DISPUTED", { assignedToUserId: "me" }), cas("3", "SETTLED", { assignedToUserId: "me", nextActionDate: "2026-10-01" }),
    cas("4", "OPEN", { nextActionDate: "2026-10-14" }), cas("5", "OPEN", { nextActionDate: "2026-10-20" })]
  it("puts each case in the right queues", () => {
    expect(cases.filter(c => inQueue(c, "mine", "me", today)).map(c => c.id)).toEqual(["1", "2"])
    expect(cases.filter(c => inQueue(c, "due", "me", today)).map(c => c.id)).toEqual(["1", "4"])
    expect(cases.filter(c => inQueue(c, "disputed", "me", today)).map(c => c.id)).toEqual(["2"])
    expect(cases.filter(c => inQueue(c, "mine", null, today))).toEqual([])
    expect(queueCounts(cases, "me", today)).toEqual({ mine: 2, all: 5, due: 2, disputed: 1 })
  })
  it("searches by number, debtor or collector", () => {
    const list = [cas("DC-1", "OPEN", { debtorName: "Acme", assignedToUserName: "Sam" }), cas("DC-2", "OPEN", { debtorName: "Beta" })]
    expect(searchCases(list, "acme").length).toBe(1)
    expect(searchCases(list, "sam").length).toBe(1)
    expect(searchCases(list, "dc-").length).toBe(2)
    expect(searchCases(list, " ").length).toBe(2)
  })
})

describe("dashboard figures", () => {
  it("compares with the previous period", () => {
    expect(changeText(112, 100)).toEqual({ text: "+12% vs last period", up: true })
    expect(changeText(80, 100)).toEqual({ text: "-20% vs last period", up: false })
    expect(changeText(5, 0)).toBeNull()
    expect(changeText(null, 3)).toBeNull()
  })
  it("draws the donut from amounts that add up to the whole circle", () => {
    const b = (label: string, amount: number) => ({ label, amount, invoiceCount: 1, caseCount: 1, share: 0 })
    const arcs = donutArcs([b("0-30", 300), b("31-60", 100), b("61+", 0)], 50)
    const circ = 2 * Math.PI * 50
    expect(arcs.map(a => a.share)).toEqual([75, 25, 0])
    expect(arcs[0].dash + arcs[1].dash + arcs[2].dash).toBeCloseTo(circ, 5)
    expect(arcs[1].offset).toBeCloseTo(-arcs[0].dash, 5)
    expect(donutArcs([b("x", 0)], 50)).toEqual([])
  })
  it("scales lines on one shared scale", () => {
    const [a, b] = linePoints([[0, 50, 100], [0, 25, 50]], 200, 100)
    expect(a).toBe("0,100 100,50 200,0")
    expect(b).toBe("0,100 100,75 200,50")
    expect(linePoints([[5]], 100, 100)).toEqual(["0,0"])
  })
  it("gives date ranges", () => {
    const t = new Date(2026, 9, 14)
    expect(periodRange("month", t)).toEqual(["2026-10-01", "2026-10-14"])
    expect(periodRange("year", t)).toEqual(["2026-01-01", "2026-10-14"])
    expect(periodRange("30", t)).toEqual(["2026-09-15", "2026-10-14"])
  })
  it("reads wrapped and paged responses", () => {
    expect(rowsOf({ data: { data: { content: [1] } } })).toEqual([1])
    expect(rowsOf({ data: { data: [2] } })).toEqual([2])
    expect(rowsOf({ data: { data: null } })).toEqual([])
  })
})
