import { describe, it, expect } from "vitest"
import { issueIsCritical, issueLabel, orderItems, sinceText, summaryText, type FleetHealth, type FleetItem } from "./fleetHealthLogic"

const item = (o: Partial<FleetItem>): FleetItem => ({ deviceId: "d", deviceName: "A", deviceType: "PERSONAL_GUARD_DEVICE", siteId: null, guardId: null, guardName: null,
  lastSeenAt: null, batteryPct: null, appVersion: null, onSession: false, critical: false, issues: [], ...o })
const health = (o: Partial<FleetHealth>): FleetHealth => ({ generatedAt: "2026-11-21T10:00:00Z", activeDevices: 10, withIssues: 0, critical: 0, items: [], ...o })

describe("fleetHealthLogic", () => {
  it("words every issue the server can send", () => {
    for (const c of ["OFFLINE", "NEVER_SEEN", "CRITICAL_BATTERY", "LOW_BATTERY", "OLD_APP"]) expect(issueLabel(c)).not.toBe(c)
    expect(issueLabel("SOMETHING_NEW")).toBe("SOMETHING_NEW")
  })
  it("treats silence on shift and a flat battery as urgent", () => {
    expect(issueIsCritical("OFFLINE")).toBe(true)
    expect(issueIsCritical("CRITICAL_BATTERY")).toBe(true)
    expect(issueIsCritical("LOW_BATTERY")).toBe(false)
    expect(issueIsCritical("OLD_APP")).toBe(false)
  })
  it("says how long ago a phone was heard", () => {
    const now = new Date("2026-11-21T10:00:00Z")
    expect(sinceText(null, now)).toBe("never")
    expect(sinceText("2026-11-21T09:59:40Z", now)).toBe("just now")
    expect(sinceText("2026-11-21T09:15:00Z", now)).toBe("45 min ago")
    expect(sinceText("2026-11-21T04:00:00Z", now)).toBe("6 h ago")
    expect(sinceText("2026-11-15T10:00:00Z", now)).toBe("6 d ago")
  })
  it("puts urgent phones first, then by name", () => {
    const out = orderItems([item({ deviceName: "B" }), item({ deviceName: "Z", critical: true }), item({ deviceName: "A" })])
    expect(out.map(i => i.deviceName)).toEqual(["Z", "A", "B"])
  })
  it("summarises", () => {
    expect(summaryText(health({ activeDevices: 0 }))).toBe("No phones registered yet.")
    expect(summaryText(health({}))).toBe("All 10 active phones are reporting normally.")
    expect(summaryText(health({ withIssues: 1 }))).toBe("1 phone needs attention.")
    expect(summaryText(health({ withIssues: 3, critical: 2 }))).toBe("3 phones need attention, 2 urgent.")
  })
})
