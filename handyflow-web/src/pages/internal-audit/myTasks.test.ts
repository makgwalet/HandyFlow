import { describe, it, expect } from "vitest"
import { dueLabel, groupTasks } from "./myTasks.logic"
import type { MyTask } from "../../api/auditMyTasks.api"

const t = (o: Partial<MyTask>): MyTask => ({ kind: "ACTION", id: "x", engagementId: "e", title: "t", context: null, severity: null, dueDate: null, overdue: false, daysOverdue: 0, ...o })

describe("groupTasks", () => {
  it("splits into overdue, next 7 days, later and undated, in that order, leaving out empty groups", () => {
    const g = groupTasks([
      t({ id: "a", dueDate: "2026-10-20", overdue: true, daysOverdue: 8 }),
      t({ id: "b", dueDate: "2026-10-28" }), t({ id: "c", dueDate: "2026-11-04" }),
      t({ id: "d", dueDate: "2026-11-05" }), t({ id: "e" }),
    ], "2026-10-28")
    expect(g.map(x => [x.key, x.tasks.map(y => y.id)])).toEqual([["overdue", ["a"]], ["week", ["b", "c"]], ["later", ["d"]], ["undated", ["e"]]])
    expect(groupTasks([t({ id: "z" })], "2026-10-28").map(x => x.key)).toEqual(["undated"])
    expect(groupTasks([], "2026-10-28")).toEqual([])
  })
  it("counts a week across a month end", () => {
    expect(groupTasks([t({ dueDate: "2026-11-04" })], "2026-10-28")[0].key).toBe("week")
    expect(groupTasks([t({ dueDate: "2027-01-02" })], "2026-12-26")[0].key).toBe("week")
  })
})

describe("dueLabel", () => {
  it("words lateness and due dates", () => {
    expect(dueLabel(t({ overdue: true, daysOverdue: 1, dueDate: "2026-10-27" }))).toBe("1 day late")
    expect(dueLabel(t({ overdue: true, daysOverdue: 9, dueDate: "2026-10-19" }))).toBe("9 days late")
    expect(dueLabel(t({ dueDate: "2026-11-02" }))).toBe("Due 2026-11-02")
    expect(dueLabel(t({}))).toBe("No due date")
  })
})
