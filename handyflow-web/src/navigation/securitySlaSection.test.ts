import { describe, it, expect } from "vitest"
import { SECURITY_SECTIONS, findSection, visibleGroups } from "./moduleSections"

const ids = (perms: string[]) => visibleGroups(SECURITY_SECTIONS, perms).flatMap((g) => g.sections.map((s) => s.id))

describe("Security navigation: Service levels", () => {
  it("is offered to anyone who can read security, because reading needs only SECURITY_READ", () => {
    expect(ids(["SECURITY_READ"])).toContain("sla")
  })
  it("sits with the operations sections, next to Incidents", () => {
    const group = SECURITY_SECTIONS.groups.find((g) => g.sections.some((s) => s.id === "sla"))
    expect(group?.label).toBe("Operations")
    const order = group!.sections.map((s) => s.id)
    expect(order.indexOf("sla")).toBe(order.indexOf("incidents") + 1)
  })
  it("opens as a deep link", () => {
    expect(findSection(SECURITY_SECTIONS, "sla", ["SECURITY_READ"])?.section.id).toBe("sla")
  })
})
