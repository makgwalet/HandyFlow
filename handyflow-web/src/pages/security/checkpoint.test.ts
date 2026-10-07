import { describe, it, expect } from "vitest"
import { deactivateWarning, filterCheckpoints, groupBySite, isQuiet, methods, summarise, type CheckpointRow } from "./checkpoint.logic"

const c = (over: Partial<CheckpointRow> = {}): CheckpointRow => ({
  id: "c", siteId: "s1", siteName: "Centurion Mall", name: "North Gate", description: null, active: true, hasNfc: false, hasBle: false,
  siteRequiresSignedQr: true, scans30d: 5, lastScanAt: "2026-10-05T10:00:00Z", activeRoutes: 0, ...over,
})

describe("isQuiet", () => {
  it("flags an active checkpoint with no recent scans, never-scanned included", () => {
    expect(isQuiet(c({ scans30d: 0 }))).toBe(true)
    expect(isQuiet(c({ scans30d: 0, lastScanAt: null }))).toBe(true)
    expect(isQuiet(c())).toBe(false)
  })
  it("does not flag an inactive checkpoint", () => { expect(isQuiet(c({ active: false, scans30d: 0 }))).toBe(false) })
})

describe("summarise", () => {
  it("counts active, inactive, quiet, on routes and sites not enforcing signed QR", () => {
    const s = summarise([c(), c({ id: "2", scans30d: 0, activeRoutes: 2 }), c({ id: "3", active: false }), c({ id: "4", siteId: "s2", siteRequiresSignedQr: false })])
    expect(s).toEqual({ total: 4, active: 3, inactive: 1, quiet: 1, onRoutes: 1, unsignedSites: 1 })
  })
})

describe("filterCheckpoints", () => {
  const list = [c({ id: "a" }), c({ id: "b", name: "Server Room", siteName: "Sandton Estate", scans30d: 0 })]
  it("searches name and site", () => {
    expect(filterCheckpoints(list, { search: "server", quietOnly: false }).map(x => x.id)).toEqual(["b"])
    expect(filterCheckpoints(list, { search: "sandton", quietOnly: false }).map(x => x.id)).toEqual(["b"])
  })
  it("can show only the quiet ones", () => { expect(filterCheckpoints(list, { search: "", quietOnly: true }).map(x => x.id)).toEqual(["b"]) })
})

describe("deactivateWarning", () => {
  it("warns when routes depend on it, with singular and plural", () => {
    expect(deactivateWarning(c({ activeRoutes: 1 }))).toContain("1 active patrol route.")
    expect(deactivateWarning(c({ activeRoutes: 3 }))).toContain("3 active patrol routes.")
  })
  it("still says what deactivating does when nothing depends on it, and nothing for an inactive one", () => {
    expect(deactivateWarning(c())).toContain("history is kept")
    expect(deactivateWarning(c({ active: false }))).toBeNull()
  })
})

describe("methods and grouping", () => {
  it("lists QR always and the others when set", () => {
    expect(methods({ hasNfc: false, hasBle: false })).toEqual(["QR"])
    expect(methods({ hasNfc: true, hasBle: true })).toEqual(["QR", "NFC", "Bluetooth"])
  })
  it("groups by site keeping order", () => {
    const g = groupBySite([c({ id: "1" }), c({ id: "2", siteId: "s2", siteName: "Sandton" }), c({ id: "3" })])
    expect(g.map(x => [x.siteName, x.items.length])).toEqual([["Centurion Mall", 2], ["Sandton", 1]])
  })
})
