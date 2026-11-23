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

describe("Security navigation: Invoice drafts", () => {
  it("is offered only to administrators, because the list endpoint requires SECURITY_ADMIN", () => {
    expect(ids(["SECURITY_READ", "SECURITY_MANAGE"])).not.toContain("invoice-drafts")
    expect(ids(["SECURITY_READ", "SECURITY_ADMIN"])).toContain("invoice-drafts")
  })
  it("sends a deep link back to the default section for everyone else", () => {
    expect(findSection(SECURITY_SECTIONS, "invoice-drafts", ["SECURITY_READ"])?.section.id).not.toBe("invoice-drafts")
    expect(findSection(SECURITY_SECTIONS, "invoice-drafts", ["SECURITY_ADMIN"])?.section.id).toBe("invoice-drafts")
  })
})

describe("Security navigation: Site access", () => {
  it("is offered only to administrators, because the list endpoint requires SECURITY_ADMIN", () => {
    expect(ids(["SECURITY_READ", "SECURITY_MANAGE"])).not.toContain("site-access")
    expect(ids(["SECURITY_READ", "SECURITY_ADMIN"])).toContain("site-access")
  })
  it("sends a deep link back to the default section for everyone else", () => {
    expect(findSection(SECURITY_SECTIONS, "site-access", ["SECURITY_READ"])?.section.id).not.toBe("site-access")
    expect(findSection(SECURITY_SECTIONS, "site-access", ["SECURITY_ADMIN"])?.section.id).toBe("site-access")
  })
})
