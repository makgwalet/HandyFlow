import { describe, it, expect } from "vitest"
import { applyBody, applyProblem, fixable, labelSummary, needReview, type LabelRow } from "./taxYearLabels.logic"

const row = (verdict: LabelRow["verdict"], id = "a"): LabelRow => ({ id, payRunNumber: "PR-" + id, payDate: "2026-05-25", status: "FINALISED", finalisedOrPaid: true, storedLabel: "2025/26", proposedLabel: "2026/27", verdict, reason: "", payslips: 3, payslipsOnOtherYear: 0 })

describe("tax-year label correction", () => {
  it("separates fixable runs from ones needing a decision", () => {
    const rows = [row("CORRECT", "a"), row("WRONG_LABEL", "b"), row("NEEDS_REVIEW", "c")]
    expect(fixable(rows).map(r => r.id)).toEqual(["b"])
    expect(needReview(rows).map(r => r.id)).toEqual(["c"])
  })
  it("summarises in plain words", () => {
    expect(labelSummary({ total: 2, correct: 2, wrongLabel: 0, needsReview: 0, rows: [] })).toMatch(/agrees/)
    expect(labelSummary({ total: 3, correct: 0, wrongLabel: 2, needsReview: 1, rows: [] })).toBe("2 pay runs have the wrong label; 1 needs a person to decide.")
    expect(labelSummary({ total: 1, correct: 0, wrongLabel: 1, needsReview: 0, rows: [] })).toBe("1 pay run has the wrong label.")
  })
  it("needs a run and a reason", () => {
    expect(applyProblem([], "because")).toMatch(/at least one/)
    expect(applyProblem(["a"], " ")).toMatch(/why/)
    expect(applyProblem(["a"], "Pay date verified")).toBeNull()
    expect(applyBody(["a"], "  ok then ")).toEqual({ payRunIds: ["a"], reason: "ok then" })
  })
})
