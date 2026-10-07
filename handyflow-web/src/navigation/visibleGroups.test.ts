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
