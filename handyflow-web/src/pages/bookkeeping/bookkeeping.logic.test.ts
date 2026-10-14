import { describe, it, expect } from "vitest"
import { answerProblem, backlog, chunks, emptyRule, handoverProblem, queryProblem, returnProblem, rowsOf, ruleProblem, ruleRequest, ruleSummary, unwrap, workRows, type WorkRow } from "./bookkeeping.logic"

const row = (name: string, u = 0, q = 0, m = 0, d = 0): WorkRow => ({ clientId: name, clientName: name, unreconciled: u, oldestUnreconciled: null, openQueries: q, missingDocuments: m, draftJournals: d })

describe("work queue", () => {
  it("hides finished clients unless asked, biggest backlog first", () => {
    const rows = [row("Zed"), row("Acme", 2), row("Beta", 5, 1), row("Cato", 2)]
    expect(workRows(rows, false).map(r => r.clientName)).toEqual(["Beta", "Acme", "Cato"])
    expect(workRows(rows, true).map(r => r.clientName)).toEqual(["Beta", "Acme", "Cato", "Zed"])
    expect(backlog(row("x", 1, 2, 3, 4))).toBe(10)
  })
})

describe("bank rule form", () => {
  const ok = { ...emptyRule(), name: "Fuel", text: " engen ", contraAccountId: "a1" }
  it("accepts a text rule and trims it", () => {
    expect(ruleProblem(ok)).toBeNull()
    expect(ruleRequest(ok)).toMatchObject({ name: "Fuel", text: "engen", bankAccountId: null, min: null, max: null, priority: 100 })
  })
  it("refuses a rule that would match every line", () => {
    expect(ruleProblem({ ...ok, text: "" })).toMatch(/every line/)
    expect(ruleProblem({ ...ok, text: "", min: "50" })).toBeNull()
  })
  it("checks name, account and amounts", () => {
    expect(ruleProblem({ ...ok, name: " " })).toMatch(/name/)
    expect(ruleProblem({ ...ok, contraAccountId: "" })).toMatch(/account/)
    expect(ruleProblem({ ...ok, min: "10", max: "5" })).toMatch(/more than the largest/)
    expect(ruleProblem({ ...ok, min: "-1" })).toMatch(/below zero/)
    expect(ruleProblem({ ...ok, min: "abc" })).toMatch(/numbers/)
  })
  it("describes a rule in words", () => {
    expect(ruleSummary({ direction: "OUT", text: "engen", field: "EITHER", min: null, max: 500 })).toBe("money out, description or reference contains “engen”, at most R 500,00".replace("500,00", (500).toLocaleString("en-ZA", { minimumFractionDigits: 2 })))
    expect(ruleSummary({ direction: "ANY", text: null, field: "EITHER", min: null, max: null })).toBe("any line")
  })
})

describe("queries and handover", () => {
  it("needs a kind and enough words", () => {
    expect(queryProblem("QUESTION", "what is this?")).toBeNull()
    expect(queryProblem("QUESTION", "hi")).toMatch(/at least 5/)
    expect(queryProblem("OTHER", "long enough")).toMatch(/Choose/)
  })
  it("answer needs text or a document location", () => {
    expect(answerProblem("", " ")).toMatch(/Record/)
    expect(answerProblem("", "folder 3")).toBeNull()
    expect(answerProblem("Paid cash", "")).toBeNull()
  })
  it("blocks a handover for the reasons the server gives", () => {
    const today = "2026-10-14"
    expect(handoverProblem("", today, undefined, false)).toMatch(/Choose/)
    expect(handoverProblem("2026-11-01", today, undefined, false)).toMatch(/future/)
    expect(handoverProblem("2026-09-30", today, row("a"), true)).toMatch(/already with the accountant/)
    expect(handoverProblem("2026-09-30", today, row("a", 1), false)).toBe("1 bank line is not categorised yet.")
    expect(handoverProblem("2026-09-30", today, row("a", 3), false)).toBe("3 bank lines are not categorised yet.")
    expect(handoverProblem("2026-09-30", today, row("a", 0, 0, 0, 2), false)).toBe("2 journals are still a draft. Post or discard them.")
    expect(handoverProblem("2026-09-30", today, row("a", 0, 4), false)).toBeNull()
  })
  it("a returned handover needs a reason", () => {
    expect(returnProblem("short")).toMatch(/at least 10/)
    expect(returnProblem("Bank lines for July are missing")).toBeNull()
  })
})

describe("helpers", () => {
  it("splits bulk work at 200", () => {
    expect(chunks(Array.from({ length: 450 }, (_, i) => i)).map(c => c.length)).toEqual([200, 200, 50])
    expect(chunks([])).toEqual([])
  })
  it("reads wrapped, paged and null responses", () => {
    expect(unwrap({ data: { data: [1] } })).toEqual([1])
    expect(rowsOf({ data: { data: { content: [1, 2] } } })).toEqual([1, 2])
    expect(rowsOf({ data: { data: [3] } })).toEqual([3])
    expect(rowsOf({ data: { data: null } })).toEqual([])
  })
})
