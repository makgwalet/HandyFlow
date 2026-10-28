import { describe, it, expect } from "vitest"
import { activityText, timeAgo, trendView, donutArcs, engagementProgress, engagementStage, filterEngagements, filterEntries, paginate, planProgress, progressOf, quarterCounts, severityRows, shortDate, upcomingDates, daysBetween } from "./auditUi.logic"
import type { Engagement, PlanEntry } from "./auditShared"

const eng = (o: Partial<Engagement>): Engagement => ({ id: "e", planEntryId: null, universeEntryId: "u", universeEntryName: "Finance", name: "Financial reporting audit", status: "PLANNING", startDate: null, endDate: null, createdBy: null, createdAt: "", assignments: [], objectives: null, scope: null, auditCriteria: null, overallMateriality: null, performanceMateriality: null, clearlyTrivialThreshold: null, specificMateriality: [], ...o })
const entry = (o: Partial<PlanEntry>): PlanEntry => ({ id: "p", universeEntryId: "u", universeEntryName: "Procurement", riskAssessmentId: null, riskLevel: "HIGH", plannedQuarter: 1, rationale: null, status: "PLANNED", createdAt: "", ...o })

describe("progress", () => {
  it("counts by group and leaves deferred work out of the percentage", () => {
    const p = progressOf(["completed", "completed", "inProgress", "planned", "deferred"])
    expect(p).toEqual({ total: 5, planned: 1, inProgress: 1, completed: 2, deferred: 1, completionPct: 50 })
  })
  it("is nil, not a division by zero, for nothing or only deferred work", () => {
    expect(progressOf([]).completionPct).toBe(0)
    expect(progressOf(["deferred"]).completionPct).toBe(0)
  })
  it("reads engagement and plan statuses", () => {
    expect(engagementStage("FIELDWORK").group).toBe("inProgress")
    expect(engagementStage("REPORTING").group).toBe("inProgress")
    expect(engagementStage("CLOSED").label).toBe("Completed")
    expect(engagementStage("SOMETHING").group).toBe("planned")
    expect(engagementProgress([{ status: "CLOSED" }, { status: "PLANNING" }, { status: "FIELDWORK" }, { status: "CLOSED" }]).completionPct).toBe(50)
    expect(planProgress({ entries: [entry({ status: "COMPLETED" }), entry({ status: "DEFERRED" })] }).completionPct).toBe(100)
    expect(planProgress(null).total).toBe(0)
  })
  it("counts entries by quarter and those without one", () => {
    const q = quarterCounts([entry({ plannedQuarter: 2, status: "COMPLETED" }), entry({ plannedQuarter: 2 }), entry({ plannedQuarter: null }), entry({ plannedQuarter: 7 })])
    expect(q[1]).toEqual({ quarter: 2, planned: 2, completed: 1 })
    expect(q.unscheduled).toBe(2)
  })
})

describe("donut and bars", () => {
  it("shares the ring in order and skips parts of nil", () => {
    const arcs = donutArcs([{ key: "a", value: 1, color: "x" }, { key: "b", value: 0, color: "y" }, { key: "c", value: 3, color: "z" }], 100)
    expect(arcs.map(a => a.key)).toEqual(["a", "c"])
    expect(arcs[0].length).toBeCloseTo(25)
    expect(arcs[1].length).toBeCloseTo(75)
    expect(arcs[1].offset).toBeCloseTo(-25)
    expect(donutArcs([{ key: "a", value: 0, color: "x" }], 100)).toEqual([])
  })
  it("lists critical to low and sizes bars against the largest", () => {
    const r = severityRows([{ severity: "HIGH", open: 4, total: 6 }, { severity: "LOW", open: 2, total: 2 }])
    expect(r.map(x => x.severity)).toEqual(["CRITICAL", "HIGH", "MEDIUM", "LOW"])
    expect(r.map(x => x.widthPct)).toEqual([0, 100, 0, 50])
    expect(r[1].label).toBe("High")
    expect(severityRows([]).every(x => x.widthPct === 0)).toBe(true)
    expect(severityRows([{ severity: "Odd", open: 1, total: 1 }]).at(-1)?.severity).toBe("ODD")
  })
})

describe("key dates", () => {
  const today = "2026-10-28"
  it("counts days between dates", () => { expect(daysBetween("2026-10-28", "2026-11-04")).toBe(7); expect(daysBetween("2026-10-28", "2026-10-27")).toBe(-1) })
  it("lists starts and ends within the horizon, soonest first, and open work past its end", () => {
    const rows = upcomingDates([
      eng({ id: "a", name: "A", status: "PLANNING", startDate: "2026-11-10", endDate: "2026-12-20" }),
      eng({ id: "b", name: "B", status: "FIELDWORK", startDate: "2026-09-01", endDate: "2026-11-02" }),
      eng({ id: "c", name: "C", status: "REPORTING", endDate: "2026-10-20" }),
      eng({ id: "d", name: "D", status: "CLOSED", endDate: "2026-11-01" }),
      eng({ id: "e", name: "E", status: "PLANNING", startDate: "2027-06-01" }),
    ], today)
    expect(rows.map(r => `${r.title}:${r.kind}`)).toEqual(["C:late", "B:end", "A:start", "A:end"])
    expect(rows[0].detail).toContain("8 days ago")
  })
  it("respects the limit", () => {
    const many = Array.from({ length: 9 }, (_, i) => eng({ id: String(i), name: "N" + i, status: "FIELDWORK", endDate: `2026-11-0${i + 1}` }))
    expect(upcomingDates(many, today, 3)).toHaveLength(3)
  })
})

describe("lists", () => {
  const engs = [eng({ id: "1", name: "Payroll review", universeEntryName: "HR", status: "FIELDWORK" }), eng({ id: "2", name: "VAT", universeEntryName: "Tax", status: "CLOSED" })]
  it("filters engagements by stage group and text", () => {
    expect(filterEngagements(engs, { group: "all", q: "" })).toHaveLength(2)
    expect(filterEngagements(engs, { group: "inProgress", q: "" }).map(e => e.id)).toEqual(["1"])
    expect(filterEngagements(engs, { group: "all", q: " tax " }).map(e => e.id)).toEqual(["2"])
    expect(filterEngagements(engs, { group: "completed", q: "payroll" })).toEqual([])
  })
  it("filters plan entries by risk, stage and text, with unrated as its own choice", () => {
    const es = [entry({ id: "1", riskLevel: "HIGH" }), entry({ id: "2", riskLevel: null, status: "COMPLETED", universeEntryName: "Stores" }), entry({ id: "3", riskLevel: "LOW", rationale: "Prior findings" })]
    expect(filterEntries(es, { risk: "HIGH", group: "all", q: "" }).map(e => e.id)).toEqual(["1"])
    expect(filterEntries(es, { risk: "NONE", group: "all", q: "" }).map(e => e.id)).toEqual(["2"])
    expect(filterEntries(es, { risk: "all", group: "completed", q: "" }).map(e => e.id)).toEqual(["2"])
    expect(filterEntries(es, { risk: "all", group: "all", q: "prior" }).map(e => e.id)).toEqual(["3"])
  })
  it("pages a list and brings a stray page number back", () => {
    const items = Array.from({ length: 12 }, (_, i) => i)
    expect(paginate(items, 1, 5)).toMatchObject({ pages: 3, from: 1, to: 5, total: 12 })
    expect(paginate(items, 3, 5).items).toEqual([10, 11])
    expect(paginate(items, 9, 5).page).toBe(3)
    expect(paginate(items, 0, 5).page).toBe(1)
    expect(paginate([], 1, 5)).toMatchObject({ pages: 1, from: 0, to: 0, items: [] })
  })
  it("writes short dates", () => { expect(shortDate("2026-04-05T00:00:00Z")).toBe("5 Apr 2026"); expect(shortDate(null)).toBe("—") })
})

describe("trends", () => {
  const t = (delta: number, key = "openFindings", daysAgo = 85) => ({ since: "2026-08-04", daysAgo, changes: [{ key, now: 10, before: 10 - delta, delta }] }) as never
  it("is nothing without a comparison", () => {
    expect(trendView(null, "openFindings")).toBeNull()
    expect(trendView({ since: null, daysAgo: null, changes: [] }, "openFindings")).toBeNull()
    expect(trendView(t(2, "engagements"), "openFindings")).toBeNull()
  })
  it("calls fewer open findings good and more bad", () => {
    expect(trendView(t(-3), "openFindings")).toMatchObject({ arrow: "▼", tone: "good", text: "3 fewer vs 85 days ago" })
    expect(trendView(t(4), "openFindings")).toMatchObject({ arrow: "▲", tone: "bad", text: "4 more vs 85 days ago" })
    expect(trendView(t(1, "overdueActions"), "overdueActions")?.tone).toBe("bad")
    expect(trendView(t(-1, "highRiskAreas"), "highRiskAreas")?.tone).toBe("good")
  })
  it("calls a change in engagements neither good nor bad, and no change flat", () => {
    expect(trendView(t(5, "engagements"), "engagements")).toMatchObject({ arrow: "▲", tone: "flat" })
    expect(trendView(t(0), "openFindings")).toMatchObject({ arrow: "▬", tone: "flat", text: "No change vs 85 days ago" })
    expect(trendView(t(0, "openFindings", 1), "openFindings")?.text).toBe("No change vs 1 day ago")
    expect(trendView(t(2), "openFindings")?.spoken).toBe("Up 2 since 85 days ago")
  })
})

describe("activity", () => {
  it("words the codes it knows and tidies the rest", () => {
    expect(activityText("FINDING_CREATED")).toBe("Finding raised")
    expect(activityText("ACTION_PLAN_VALIDATED")).toBe("Action plan validated")
    expect(activityText("WORKPAPER_SIGN_OFF")).toBe("Workpaper sign off")
    expect(activityText("AUDIT_REPORT_PERSON_ADDED")).toBe("Report person added")
    expect(activityText("")).toBe("Updated an audit record")
  })
  it("says how long ago", () => {
    const now = new Date("2026-10-28T12:00:00Z")
    expect(timeAgo("2026-10-28T11:59:40Z", now)).toBe("just now")
    expect(timeAgo("2026-10-28T11:55:00Z", now)).toBe("5 min ago")
    expect(timeAgo("2026-10-28T09:00:00Z", now)).toBe("3 h ago")
    expect(timeAgo("2026-10-27T11:00:00Z", now)).toBe("1 day ago")
    expect(timeAgo("2026-10-20T12:00:00Z", now)).toBe("20 Oct 2026")
    expect(timeAgo("2026-10-28T12:05:00Z", now)).toBe("just now")
    expect(timeAgo("garbage", now)).toBe("")
  })
})
