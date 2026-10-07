import { describe, it, expect } from "vitest"
import { ago, canGenerate, defaultMonth, knownCards, lastRunLine, monthLabel, reportPaths, runSummary, scopeNeeds } from "./reports.logic"
import type { Card, Run } from "./reports.logic"

const run: Run = { reportKey: "site-coverage", period: "2026-09", subject: "Centurion Mall", format: "PDF", generatedBy: "Thabo", generatedAt: "2026-10-07T08:00:00Z" }

describe("months", () => {
  it("labels a period in words and leaves other text alone", () => {
    expect(monthLabel("2026-09")).toBe("September 2026")
    expect(monthLabel("2026-12")).toBe("December 2026")
    expect(monthLabel("2026-13")).toBe("2026-13")
    expect(monthLabel("soon")).toBe("soon")
  })
  it("defaults to the month that just finished, stepping back across the new year", () => {
    expect(defaultMonth(new Date(2026, 9, 7))).toBe("2026-09")
    expect(defaultMonth(new Date(2026, 0, 15))).toBe("2025-12")
  })
})

describe("canGenerate", () => {
  const base = { month: "2026-09", siteId: "", guardId: "" }
  it("needs only a month for the company summary", () => {
    expect(canGenerate("NONE", base)).toBe(true)
    expect(canGenerate("NONE", { ...base, month: "" })).toBe(false)
    expect(canGenerate("NONE", { ...base, month: "2026-9" })).toBe(false)
  })
  it("needs the site or guard the report is about", () => {
    expect(canGenerate("SITE", base)).toBe(false)
    expect(canGenerate("SITE", { ...base, siteId: "s1" })).toBe(true)
    expect(canGenerate("GUARD", { ...base, siteId: "s1" })).toBe(false)
    expect(canGenerate("GUARD", { ...base, guardId: "g1" })).toBe(true)
  })
})

describe("reportPaths", () => {
  const p = { month: "2026-09", siteId: "s1", guardId: "g1" }
  it("builds the view and PDF addresses for every report", () => {
    expect(reportPaths("monthly-summary", p)).toEqual({ view: "/api/v1/security/reports/monthly-summary?month=2026-09", pdf: "/api/v1/security/reports/monthly-summary/pdf?month=2026-09" })
    expect(reportPaths("site-coverage", p).pdf).toBe("/api/v1/security/reports/site-coverage/pdf?siteId=s1&month=2026-09")
    expect(reportPaths("guard-attendance", p).view).toBe("/api/v1/security/reports/guard-attendance?guardId=g1&month=2026-09")
    expect(reportPaths("site-access", p).view).toBe("/api/v1/security/reports/site-access?siteId=s1&month=2026-09")
  })
})

describe("last generated wording", () => {
  const now = new Date("2026-10-07T10:00:00Z")
  it("reads naturally at each distance", () => {
    expect(ago("2026-10-07T09:59:40Z", now)).toBe("just now")
    expect(ago("2026-10-07T10:00:30Z", now)).toBe("just now")
    expect(ago("2026-10-07T09:55:00Z", now)).toBe("5 min ago")
    expect(ago("2026-10-07T07:00:00Z", now)).toBe("3 h ago")
    expect(ago("2026-10-04T10:00:00Z", now)).toBe("3 d ago")
    expect(ago("2026-01-04T10:00:00Z", now)).toMatch(/2026/)
  })
  it("says so when a report has never been generated", () => {
    expect(lastRunLine(null)).toBe("Not generated yet")
  })
  it("names who and when, and copes with a missing name", () => {
    expect(lastRunLine(run, now)).toBe("Generated 2 h ago by Thabo")
    expect(lastRunLine({ ...run, generatedBy: null }, now)).toBe("Generated 2 h ago")
  })
  it("summarises what the run was for", () => {
    expect(runSummary(run)).toBe("September 2026 · Centurion Mall · PDF")
    expect(runSummary({ ...run, subject: null, format: "VIEW" })).toBe("September 2026 · Viewed")
  })
})

describe("cards", () => {
  it("drops reports this screen does not know yet", () => {
    const c = (key: string) => ({ key, title: key, description: "", scope: "NONE", lastRun: null }) as unknown as Card
    expect(knownCards([c("site-coverage"), c("payroll-pack")]).map(x => x.key)).toEqual(["site-coverage"])
  })
  it("says what each report needs", () => {
    expect(scopeNeeds("NONE")).toBe("Pick a month")
    expect(scopeNeeds("SITE")).toBe("Pick a site and a month")
    expect(scopeNeeds("GUARD")).toBe("Pick a guard and a month")
  })
})
