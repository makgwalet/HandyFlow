import { describe, it, expect } from "vitest"
import { SECURITY_SECTIONS, findSection, visibleGroups } from "./moduleSections"

const ids = (perms: string[]) => visibleGroups(SECURITY_SECTIONS, perms).flatMap(g => g.sections.map(s => s.id))

describe("Security navigation: Close Protection", () => {
  it("is offered only to people who hold VIP_DETAIL_ACCESS, because every Close Protection endpoint requires it", () => {
    expect(ids(["SECURITY_READ", "SECURITY_MANAGE"])).not.toContain("close-protection")
    expect(ids(["SECURITY_READ", "VIP_DETAIL_ACCESS"])).toContain("close-protection")
  })
  it("sends a deep link to Close Protection back to the default section for everyone else", () => {
    const perms = ["SECURITY_READ", "SECURITY_MANAGE"]
    const found = findSection(SECURITY_SECTIONS, "close-protection", perms)
    expect(found?.section.id).not.toBe("close-protection")
    expect(findSection(SECURITY_SECTIONS, "close-protection", ["VIP_DETAIL_ACCESS"])?.section.id).toBe("close-protection")
  })
  it("keeps the other security sections visible without it", () => {
    const seen = ids(["SECURITY_READ", "SECURITY_MANAGE"])
    for (const id of ["coverage", "reports"]) expect(seen).toContain(id)
  })
})
