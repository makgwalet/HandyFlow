import { describe, expect, it } from "vitest"
import { CRM_SECTIONS, findSection, sectionsForPath } from "../../navigation/moduleSections"

describe("CRM sidebar sections", () => {
  const ids = CRM_SECTIONS.groups.flatMap(g => g.sections.map(s => s.id))
  it("has unique section ids", () => expect(new Set(ids).size).toBe(ids.length))
  it("keeps every old tab reachable as a route section", () => {
    for (const id of ["dashboard", "customers", "pipeline", "opportunities", "groups", "rules", "automation", "reports", "audit", "integrations", "campaigns", "tasks", "funnel", "forecast", "team", "segments", "duplicates", "fields"])
      expect(findSection(CRM_SECTIONS, id)).toBeTruthy()
  })
  it("is picked for /customers paths", () => expect(sectionsForPath("/customers/pipeline")).toBe(CRM_SECTIONS))
})
