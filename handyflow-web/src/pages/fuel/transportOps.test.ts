import { describe, it, expect } from "vitest"
import { parseCapacities, validateTanker, autoSplit, validateAllocation, fleetOptionText, windowText, validateFailure, reasonLabel, FAILURE_REASONS, type Compartment } from "./transportOps"

const comps: Compartment[] = [{ id: "c1", number: 1, capacityLitres: 10000 }, { id: "c2", number: 2, capacityLitres: 6000 }]

describe("transportOps", () => {
  it("parses compartment capacities", () => {
    expect(parseCapacities("10000, 6000").values).toEqual([10000, 6000])
    expect(parseCapacities("").error).toBeTruthy()
    expect(parseCapacities("10000 0").error).toBeTruthy()
    expect(parseCapacities("a").error).toBeTruthy()
    expect(parseCapacities(Array(13).fill("1").join(",")).error).toMatch(/12/)
    expect(validateTanker("", "100")).toMatch(/registration/)
    expect(validateTanker("GP 1", "100")).toBeNull()
  })
  it("auto-splits in compartment order and reports when it does not fit", () => {
    expect(autoSplit(12000, comps)).toEqual({ c1: "10000", c2: "2000" })
    expect(autoSplit(4000, comps)).toEqual({ c1: "4000" })
    expect(autoSplit(17000, comps)).toBeNull()
  })
  it("validates an allocation", () => {
    expect(validateAllocation("", "d", 100, {}, comps)).toMatch(/tanker/)
    expect(validateAllocation("t", "", 100, {}, comps)).toMatch(/driver/)
    expect(validateAllocation("t", "d", 12000, { c1: "10000", c2: "1000" }, comps)).toMatch(/add up/)
    expect(validateAllocation("t", "d", 12000, { c1: "5000", c2: "7000" }, comps)).toMatch(/holds 6000/)
    expect(validateAllocation("t", "d", 12000, { c1: "10000", c2: "2000" }, comps)).toBeNull()
  })
})

describe("fleetOptionText (C03)", () => {
  it("shows a free vehicle by its label", () => {
    expect(fleetOptionText({ id: "1", registration: "CY 1", label: "CY 1 · Volvo FM", linkedTankerRegistration: null })).toBe("CY 1 · Volvo FM")
  })
  it("says when a vehicle already backs a tanker", () => {
    expect(fleetOptionText({ id: "1", registration: "CY 1", label: "CY 1 · Volvo FM", linkedTankerRegistration: "CY 1" })).toContain("already a tanker")
  })
})

describe("delivery windows and failed attempts (C04)", () => {
  it("describes a window, open at either end", () => {
    expect(windowText(null, null)).toBeNull()
    expect(windowText("2026-11-13T08:00:00Z", "2026-11-13T10:00:00Z")).toMatch(/ to /)
    expect(windowText("2026-11-13T08:00:00Z", null)).toMatch(/^from /)
    expect(windowText(null, "2026-11-13T08:00:00Z")).toMatch(/^by /)
  })
  it("needs a reason, a note for Other, and sensible litres", () => {
    expect(validateFailure("", "", "")).toMatch(/reason/i)
    expect(validateFailure("OTHER", " ", "")).toMatch(/Describe/)
    expect(validateFailure("OTHER", "gate welded shut", "")).toBeNull()
    expect(validateFailure("SITE_CLOSED", "", "-1", 100)).toMatch(/zero or more/)
    expect(validateFailure("SITE_CLOSED", "", "101", 100)).toMatch(/more than/)
    expect(validateFailure("SITE_CLOSED", "", "5", null)).toMatch(/No loading/)
    expect(validateFailure("SITE_CLOSED", "", "0", null)).toBeNull()
    expect(validateFailure("SITE_CLOSED", "", "100", 100)).toBeNull()
  })
  it("labels every reason", () => {
    expect(FAILURE_REASONS.every(r => reasonLabel(r.code) === r.label)).toBe(true)
    expect(reasonLabel("UNKNOWN")).toBe("UNKNOWN")
  })
})
