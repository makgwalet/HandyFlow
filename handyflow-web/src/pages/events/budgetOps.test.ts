import { describe, it, expect } from "vitest"
import { alertTitle, approvalOverrun, budgetActions, canApproveCommitment, canCancelCommitment, canCompleteCommitment, canEditLines, canRecordAgainst, categoryText, commitmentBody, commitmentProblem, costEntryBody, costEntryProblem, lineProblem, varianceTone } from "./budgetOps"

describe("budget workflow", () => {
  it("lines change only before submission", () => {
    expect(canEditLines("NOT_STARTED")).toBe(true); expect(canEditLines("DRAFT")).toBe(true)
    expect(canEditLines("SUBMITTED")).toBe(false); expect(canEditLines("APPROVED")).toBe(false)
  })
  it("offers the buttons that fit the state", () => {
    expect(budgetActions("DRAFT", 0)).toEqual([]); expect(budgetActions("DRAFT", 3)).toEqual(["submit"]); expect(budgetActions("NOT_STARTED", 2)).toEqual(["submit"])
    expect(budgetActions("SUBMITTED", 3)).toEqual(["approve", "reject", "reopen"]); expect(budgetActions("APPROVED", 3)).toEqual(["reopen"])
  })
})

describe("lines", () => {
  const ok = { category: "VENUE", description: "Hall hire", plannedAmount: "12000" }
  it("needs a known category, a description and an amount", () => {
    expect(lineProblem(ok)).toBeNull(); expect(lineProblem({ ...ok, plannedAmount: "0" })).toBeNull()
    expect(lineProblem({ ...ok, category: "" })).toMatch(/category/); expect(lineProblem({ ...ok, category: "SPACE" })).toMatch(/category/)
    expect(lineProblem({ ...ok, description: " " })).toMatch(/Describe/); expect(lineProblem({ ...ok, plannedAmount: "" })).toMatch(/planned/)
    expect(lineProblem({ ...ok, plannedAmount: "-5" })).toMatch(/amount/i)
  })
  it("names categories", () => expect(categoryText("AV_PRODUCTION")).toBe("Av production"))
})

describe("commitments", () => {
  const ok = { budgetLineId: "l1", description: "Catering for 200", amount: "50000", depositAmount: "", depositDueDate: "" }
  it("checks the quote", () => {
    expect(commitmentProblem(ok)).toBeNull()
    expect(commitmentProblem({ ...ok, budgetLineId: "" })).toMatch(/budget line/); expect(commitmentProblem({ ...ok, description: "" })).toMatch(/Describe/)
    expect(commitmentProblem({ ...ok, amount: "" })).toMatch(/quoted/); expect(commitmentProblem({ ...ok, amount: "0" })).toMatch(/above zero/)
    expect(commitmentProblem({ ...ok, depositAmount: "60000", depositDueDate: "2026-12-01" })).toMatch(/more than the quote/)
    expect(commitmentProblem({ ...ok, depositAmount: "10000" })).toMatch(/deposit is due/)
    expect(commitmentProblem({ ...ok, depositAmount: "10000", depositDueDate: "2026-12-01" })).toBeNull()
  })
  it("moves through its states", () => {
    expect(canApproveCommitment("QUOTED")).toBe(true); expect(canApproveCommitment("APPROVED")).toBe(false)
    expect(canCompleteCommitment("APPROVED")).toBe(true); expect(canCompleteCommitment("QUOTED")).toBe(false)
    expect(canCancelCommitment("QUOTED")).toBe(true); expect(canCancelCommitment("APPROVED")).toBe(true); expect(canCancelCommitment("COMPLETED")).toBe(false); expect(canCancelCommitment("CANCELLED")).toBe(false)
    expect(canRecordAgainst("APPROVED")).toBe(true); expect(canRecordAgainst("COMPLETED")).toBe(true); expect(canRecordAgainst("QUOTED")).toBe(false)
  })
  it("works out how far approving would go over the line", () => {
    expect(approvalOverrun({ planned: 100, committed: 60 }, 30)).toBe(0); expect(approvalOverrun({ planned: 100, committed: 60 }, 40)).toBe(0)
    expect(approvalOverrun({ planned: 100, committed: 60 }, 50.5)).toBe(10.5); expect(approvalOverrun(undefined, 50)).toBe(0)
  })
  it("builds the body with nulls for blanks", () => {
    expect(commitmentBody({ budgetLineId: "l1", vendorId: "", description: " Catering ", amount: "500", quoteReference: "", poReference: "PO-1", contractReference: "", deliverable: "", dueDate: "", depositAmount: "", depositDueDate: "" }))
      .toEqual({ budgetLineId: "l1", vendorId: null, description: "Catering", amount: 500, quoteReference: null, poReference: "PO-1", contractReference: null, deliverable: null, dueDate: null, depositAmount: null, depositDueDate: null })
  })
})

describe("cost entries", () => {
  const ok = { target: "c:abc", kind: "INVOICE", amount: "100", entryDate: "2026-11-11" }
  it("needs a target, a kind, a positive amount and a date", () => {
    expect(costEntryProblem(ok)).toBeNull()
    expect(costEntryProblem({ ...ok, target: "" })).toMatch(/Choose/); expect(costEntryProblem({ ...ok, kind: "" })).toMatch(/invoice or payment/)
    expect(costEntryProblem({ ...ok, amount: "0" })).toMatch(/above zero/); expect(costEntryProblem({ ...ok, entryDate: "" })).toMatch(/date/)
  })
  it("sends exactly one of commitment or line, and deposit only for payments", () => {
    const b = costEntryBody({ ...ok, deposit: true, reference: " INV-9 ", description: "" })
    expect(b).toMatchObject({ commitmentId: "abc", budgetLineId: null, deposit: false, amount: 100, reference: "INV-9", description: null })
    const p = costEntryBody({ target: "l:xyz", kind: "PAYMENT", deposit: true, amount: "50.5", entryDate: "2026-11-11", reference: "", description: "" })
    expect(p).toMatchObject({ commitmentId: null, budgetLineId: "xyz", deposit: true, amount: 50.5 })
  })
})

describe("untouched optional fields", () => {
  it("do not crash the body builders", () => {
    expect(costEntryBody({ target: "l:x", kind: "INVOICE", deposit: false, amount: "5", entryDate: "2026-11-11" } as any)).toMatchObject({ reference: null, description: null })
    expect(commitmentBody({ budgetLineId: "l1", description: "x", amount: "5" } as any)).toMatchObject({ quoteReference: null, depositAmount: null, dueDate: null })
    expect(commitmentProblem({ budgetLineId: "l1", description: "x", amount: "5" } as any)).toBeNull()
  })
})

describe("wording", () => {
  it("variance below zero is over budget", () => { expect(varianceTone(-1)).toBe("bad"); expect(varianceTone(1)).toBe("ok"); expect(varianceTone(0)).toBe("muted") })
  it("names alerts", () => { expect(alertTitle("DEPOSIT_DUE")).toBe("Deposit due"); expect(alertTitle("X")).toBe("X") })
})
