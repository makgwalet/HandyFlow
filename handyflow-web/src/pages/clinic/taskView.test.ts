import { describe, it, expect } from "vitest"
import { dismissProblem, dueLabel, resultFollowUp, sortTasks, titleProblem } from "./taskView"

describe("taskView", () => {
  it("words due dates", () => {
    expect(dueLabel(null, false, "2026-10-08")).toEqual({ text: "No due date", tone: "none" })
    expect(dueLabel("2026-10-08", false, "2026-10-08").tone).toBe("today")
    expect(dueLabel("2026-10-07", false, "2026-10-08").tone).toBe("overdue")
    expect(dueLabel("2026-10-09", true, "2026-10-08").tone).toBe("overdue")
    expect(dueLabel("2026-10-12", false, "2026-10-08")).toEqual({ text: "Due 2026-10-12", tone: "soon" })
  })
  it("sorts overdue, then dated, then undated", () => {
    const out = sortTasks([{ dueDate: null, overdue: false }, { dueDate: "2026-10-12", overdue: false }, { dueDate: "2026-10-01", overdue: true }, { dueDate: "2026-10-09", overdue: false }])
    expect(out.map(t => t.dueDate)).toEqual(["2026-10-01", "2026-10-09", "2026-10-12", null])
  })
  it("validates title and dismiss reason", () => {
    expect(titleProblem("ab")).not.toBeNull(); expect(titleProblem("abc")).toBeNull(); expect(titleProblem("x".repeat(201))).not.toBeNull()
    expect(dismissProblem("no")).not.toBeNull(); expect(dismissProblem("not needed")).toBeNull()
  })
  it("builds one follow-up per result", () => {
    expect(resultFollowUp({ id: "r1", patientId: "p1", patientName: "Ann" })).toEqual({ kind: "RESULT_FOLLOW_UP", patientId: "p1", sourceType: "LAB_RESULT", sourceId: "r1", title: "Follow up result for Ann" })
    expect(resultFollowUp({ id: "r2" }).title).toBe("Follow up result")
  })
})
