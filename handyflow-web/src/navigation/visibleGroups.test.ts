import { describe, it, expect } from "vitest"
import { CLINIC_SECTIONS, visibleGroups } from "./moduleSections"

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
