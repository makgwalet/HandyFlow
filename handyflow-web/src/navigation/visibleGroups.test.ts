import { describe, it, expect } from "vitest"
import { CLINIC_SECTIONS, FUEL_SECTIONS, findSection, visibleGroups } from "./moduleSections"

const ids = (perms: string[]) => visibleGroups(CLINIC_SECTIONS, perms).flatMap(g => g.sections.map(s => s.id))

describe("visibleGroups: clinic admin sections", () => {
  it("hides the access log and question library from ordinary users", () => {
    const seen = ids(["CLINIC_READ"])
    expect(seen).not.toContain("access-log")
    expect(seen).not.toContain("question-library")
  })
  it("shows the access log to a clinic administrator", () => {
    expect(ids(["CLINIC_ADMIN"])).toContain("access-log")
  })
  it("shows the question library to an author or to a reviewer who cannot author", () => {
    expect(ids(["CLINIC_CONTENT_ADMIN"])).toContain("question-library")
    expect(ids(["CLINIC_CONTENT_APPROVE"])).toContain("question-library")
  })
})

describe("clinic navigation structure", () => {
  const all = CLINIC_SECTIONS.groups.flatMap(g => g.sections.map(s => s.id))
  it("lists every section exactly once", () => {
    expect(new Set(all).size).toBe(all.length)
  })
  it("keeps every section id the page can render, so deep links still work", () => {
    for (const id of ["dashboard","patients","schedule","practitioners","claims","billing","recalls",
      "waitlist","handoff","drafts","access-log","waiting-room","time-off","working-hours","closures","rooms",
      "lab-inbox","question-library"]) expect(all).toContain(id)
  })
  it("puts front-desk work ahead of setup", () => {
    const labels = CLINIC_SECTIONS.groups.map(g => g.label)
    expect(labels.indexOf("Front desk")).toBeLessThan(labels.indexOf("Practice setup"))
  })
  it("gives every section its own icon within its group", () => {
    for (const g of CLINIC_SECTIONS.groups) {
      const icons = g.sections.map(s => s.icon)
      expect(new Set(icons).size).toBe(icons.length)
    }
  })
})

describe("visibleGroups: clinic sections follow the permission catalogue (W-5)", () => {
  it("keeps everything but money for someone who still holds only the old CLINIC_READ", () => {
    const seen = ids(["CLINIC_READ"])
    for (const id of ["dashboard", "patients", "schedule", "waiting-room", "waitlist", "recalls", "lab-inbox", "practitioners", "rooms", "working-hours", "time-off", "closures"])
      expect(seen).toContain(id)
    expect(seen).not.toContain("claims"); expect(seen).not.toContain("billing")
  })
  it("shows money to the old billing permission and to the new ones", () => {
    expect(ids(["CLINIC_BILLING_READ"])).toEqual(expect.arrayContaining(["claims", "billing"]))
    expect(ids(["CLINIC_CLAIM_READ"])).toContain("claims")
    expect(ids(["CLINIC_CLAIM_READ"])).not.toContain("billing")
    expect(ids(["CLINIC_BILL_READ"])).toContain("billing")
  })
  it("a billing clerk with only the new permissions sees money but no clinical sections", () => {
    const seen = ids(["CLINIC_CLAIM_READ", "CLINIC_BILL_READ", "CLINIC_PATIENT_READ"])
    expect(seen).toEqual(expect.arrayContaining(["dashboard", "patients", "claims", "billing"]))
    for (const id of ["lab-inbox", "schedule", "recalls", "drafts", "handoff"]) expect(seen).not.toContain(id)
  })
  it("a nurse with only the new permissions sees care and scheduling but not money", () => {
    const seen = ids(["CLINIC_PATIENT_READ", "CLINIC_APPOINTMENT_READ", "CLINIC_RESULT_READ", "CLINIC_RECALL_READ"])
    expect(seen).toEqual(expect.arrayContaining(["patients", "schedule", "waiting-room", "lab-inbox", "recalls"]))
    expect(seen).not.toContain("claims"); expect(seen).not.toContain("billing"); expect(seen).not.toContain("practitioners")
  })
  it("always leaves the dashboard so the default section never redirects away", () => {
    expect(ids(["CLINIC_PATIENT_READ"])).toContain("dashboard")
  })
})

describe("visibleGroups: fuel divisions", () => {
  const P = ["FUEL_READ", "FUEL_MANAGE", "FUEL_CONFIGURATION_MANAGE", "FUEL_MARGIN_READ"]
  const fuel = (d?: string[] | null) => visibleGroups(FUEL_SECTIONS, P, d).flatMap(g => g.sections.map(s => s.id))
  it("hides nothing while divisions are unknown", () => {
    expect(fuel(null)).toContain("forecourt"); expect(fuel(undefined)).toContain("planning")
  })
  it("hides sections of divisions that are off and keeps shared ones", () => {
    const seen = fuel(["RETAIL_FORECOURT"])
    expect(seen).toContain("forecourt"); expect(seen).toContain("purchase-orders")
    expect(seen).not.toContain("planning"); expect(seen).not.toContain("orders"); expect(seen).not.toContain("customers")
    for (const shared of ["dashboard", "tanks", "stock", "compliance", "suppliers", "setup", "legal-entities"]) expect(seen).toContain(shared)
  })
  it("shows wholesale screens for either wholesale or distribution", () => {
    expect(fuel(["DISTRIBUTION"])).toContain("orders"); expect(fuel(["WHOLESALE_SUPPLY"])).toContain("orders")
    expect(fuel(["DISTRIBUTION"])).not.toContain("purchase-orders")
  })
  it("never hides the dashboard, even with no division on", () => {
    expect(fuel([])).toContain("dashboard")
  })
  it("does not resolve a hidden section, so the page redirects to the default", () => {
    expect(findSection(FUEL_SECTIONS, "forecourt", P, ["DISTRIBUTION"])).toBeUndefined()
    expect(findSection(FUEL_SECTIONS, "forecourt", P, null)?.section.id).toBe("forecourt")
  })
})
