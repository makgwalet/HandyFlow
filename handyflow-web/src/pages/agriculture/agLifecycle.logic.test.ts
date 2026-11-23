import { describe, it, expect } from "vitest"
import { allowedAnimalStatuses, farmChoices, countAdjustError, reasonError, countLogLabel, statusLabel } from "./agLifecycle.logic"

describe("allowedAnimalStatuses", () => {
  it("lets an active animal leave four ways", () => { expect(allowedAnimalStatuses("ACTIVE")).toEqual(["SOLD", "DECEASED", "CULLED", "TRANSFERRED_OUT"]) })
  it("lets a sold, culled or transferred animal only be put back", () => {
    for (const s of ["SOLD", "CULLED", "TRANSFERRED_OUT"]) expect(allowedAnimalStatuses(s)).toEqual(["ACTIVE"])
  })
  it("keeps a deceased animal deceased", () => { expect(allowedAnimalStatuses("DECEASED")).toEqual([]) })
  it("offers nothing for an unknown status", () => { expect(allowedAnimalStatuses("")).toEqual([]) })
})

describe("farmChoices", () => {
  it("leaves out the current farm and sorts by name", () => {
    expect(farmChoices([{ id: "1", name: "Zeta" }, { id: "2", name: "Alpha" }, { id: "3", name: "Mid" }], "3")).toEqual([{ id: "2", label: "Alpha" }, { id: "1", label: "Zeta" }])
  })
  it("is empty with only one farm", () => { expect(farmChoices([{ id: "1", name: "A" }], "1")).toEqual([]) })
})

describe("countAdjustError", () => {
  it("accepts a valid change", () => { expect(countAdjustError("decrease", "5", "Sold five", 50)).toBeNull() })
  it("needs a whole number of 1 or more", () => {
    for (const c of ["", "0", "2.5", "-3", "abc"]) expect(countAdjustError("increase", c, "Counted", 50)).toMatch(/whole number/)
  })
  it("refuses removing more than the group has but allows increasing", () => {
    expect(countAdjustError("decrease", "51", "Sold", 50)).toMatch(/only has 50/)
    expect(countAdjustError("increase", "51", "Counted", 50)).toBeNull()
  })
  it("needs a reason", () => { expect(countAdjustError("decrease", "5", "  ", 50)).toMatch(/reason/) })
})

describe("reasonError", () => {
  it("wants three characters after trimming", () => {
    expect(reasonError("ab ")).not.toBeNull()
    expect(reasonError(" abc ")).toBeNull()
  })
})

describe("labels", () => {
  it("describes a count change", () => {
    expect(countLogLabel({ changeType: "SALE", delta: -20, countBefore: 50, countAfter: 30 })).toBe("Sold -20 (50 → 30)")
    expect(countLogLabel({ changeType: "INCREASE", delta: 4, countBefore: 30, countAfter: 34 })).toBe("Added +4 (30 → 34)")
  })
  it("words a status", () => { expect(statusLabel("TRANSFERRED_OUT")).toBe("transferred out") })
})
