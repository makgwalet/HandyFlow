import { describe, it, expect } from "vitest"
import { readdirSync, readFileSync } from "node:fs"
import { join } from "node:path"

// Pages must read days and clock times in the company's zone (companyZone.ts), never a fixed one. No file may name a fixed zone or print "(SAST)" by hand.
const DIR = "src/pages/security"
const FIXED = /Africa\/Johannesburg|\(SAST\)|T\d\d:\d\d:\d\d[+-]\d\d:\d\d`|:00\+02:00/g
const ALLOWED = new Set(["companyZone.ts", "companyZoneLogic.ts"]) // the default zone itself, and the zone suggestions

const sources = () => readdirSync(DIR).filter(f => /\.(ts|tsx)$/.test(f) && !/\.test\./.test(f) && !ALLOWED.has(f))
const hits = (f: string) => readFileSync(join(DIR, f), "utf8").match(FIXED) ?? []

describe("no fixed-zone dates in the security pages", () => {
  it("no file names a fixed zone or a fixed +02:00 offset", () => {
    const bad = sources().filter(f => hits(f).length > 0).map(f => `${f}: ${hits(f).join(" | ")}`)
    expect(bad, "Use getCompanyZone() / instantOf() from ./companyZone instead").toEqual([])
  })
  it("the scanner itself sees each kind of fixed zone", () => {
    for (const s of ['timeZone: "Africa/Johannesburg"', "Printed (SAST)", "new Date(`${day}T${start}:00+02:00`)", "`${key}T00:00:00+02:00`"]) {
      expect(s.match(FIXED) ?? [], s).not.toHaveLength(0)
    }
  })
  it("it still looks at the files it should", () => {
    expect(sources().length).toBeGreaterThan(50)
    expect(sources()).toContain("schedule.logic.ts")
  })
  it("every converted page asks for the zone and every formatter reads it", () => {
    const formatters = ["SharedDevicesPanel.tsx", "PatrolsTab.tsx", "GateDashboardTab.tsx", "GuardDevicesPanel.tsx", "PatrolDetailPage.tsx", "ComplaintDetailPage.tsx", "GuardScreeningPanel.tsx",
      "accessLogLogic.ts", "GuardProfilePage.tsx", "SiteDetailPage.tsx", "IncidentDetailPage.tsx", "coverageLogic.ts", "CheckpointsTab.tsx", "LiveMapTab.tsx", "faceLogic.ts", "GuardCompetencyPanel.tsx", "schedule.logic.ts"]
    for (const f of formatters) expect(readFileSync(join(DIR, f), "utf8"), f).toContain("getCompanyZone()")
    const pages = ["SharedDevicesPanel", "PatrolsTab", "GateDashboardTab", "GuardDevicesPanel", "PatrolDetailPage", "ComplaintDetailPage", "GuardScreeningPanel", "GuardProfilePage", "SiteDetailPage",
      "IncidentDetailPage", "CheckpointsTab", "LiveMapTab", "GuardCompetencyPanel", "AccessLogTab", "GuardFaceCard", "CoverageTab", "SchedulerTab"]
    for (const f of pages) expect(readFileSync(join(DIR, f + ".tsx"), "utf8"), f).toMatch(/useCompanyZone\(\)/)
  })
})
