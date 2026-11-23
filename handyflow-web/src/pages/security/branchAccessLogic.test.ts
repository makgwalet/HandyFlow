import { describe, it, expect } from "vitest"
import { branchBody, branchRows, branchSummary, branchWarning, branchesChanged, choicesFrom, overLimit, setChoice, type BranchInfo } from "./branchAccessLogic"
import type { ScopeView } from "./siteScopeLogic"

const all: BranchInfo[] = [{ id: "b2", name: "Cape Town", active: true }, { id: "b1", name: "Johannesburg", region: "Gauteng", active: true }, { id: "b3", name: "Old", active: false }]
const view = (o: Partial<ScopeView> = {}): ScopeView => ({ restricted: false, branches: [], clients: [], sites: [], visibleSites: -1, ...o })

describe("choicesFrom", () => {
  it("maps a role to a choice, and anything that is not MANAGER to view", () => {
    expect(choicesFrom([{ id: "b1", name: "J", role: "MANAGER" }, { id: "b2", name: "C", role: "VIEWER" }, { id: "b3", name: "X", role: "GUARD" }])).toEqual({ b1: "MANAGER", b2: "VIEWER", b3: "VIEWER" })
    expect(choicesFrom([])).toEqual({})
  })
})

describe("branchRows", () => {
  it("lists active branches by name, and leaves out switched-off ones the user does not hold", () => {
    expect(branchRows(all, []).map(r => r.id)).toEqual(["b2", "b1"])
  })
  it("adds a switched-off branch the user still holds, marked off", () => {
    const rows = branchRows(all, [{ id: "b3", name: "Old", role: "VIEWER" }])
    expect(rows.find(r => r.id === "b3")).toMatchObject({ off: true, name: "Old" })
    expect(rows.filter(r => r.off)).toHaveLength(1)
  })
  it("adds a branch the list does not know at all (removed from the list)", () => {
    expect(branchRows([], [{ id: "bx", name: "Gone", role: "MANAGER" }])).toEqual([{ id: "bx", name: "Gone", region: null, off: true }])
  })
})

describe("branchBody", () => {
  const rows = branchRows(all, [{ id: "b3", name: "Old", role: "VIEWER" }])
  it("sends only branches with access, with their role, in name order", () => {
    expect(branchBody({ b1: "MANAGER", b2: "VIEWER" }, rows)).toEqual({ branches: [{ branchId: "b2", role: "VIEWER" }, { branchId: "b1", role: "MANAGER" }] })
    expect(branchBody({ b1: "NONE" }, rows)).toEqual({ branches: [] })
  })
  it("never sends a switched-off branch, which the server refuses", () => {
    expect(branchBody({ b3: "VIEWER" }, rows).branches).toEqual([])
  })
})

describe("branchesChanged", () => {
  const rows = branchRows(all, [])
  const have = [{ id: "b1", name: "J", role: "MANAGER" }]
  it("is false for what the user already has", () => expect(branchesChanged(have, choicesFrom(have), rows)).toBe(false))
  it("sees a role change, an addition and a removal", () => {
    expect(branchesChanged(have, { b1: "VIEWER" }, rows)).toBe(true)
    expect(branchesChanged(have, { b1: "MANAGER", b2: "VIEWER" }, rows)).toBe(true)
    expect(branchesChanged(have, { b1: "NONE" }, rows)).toBe(true)
  })
  it("sees that saving would drop a switched-off branch", () => {
    const offRows = branchRows(all, [{ id: "b3", name: "Old", role: "VIEWER" }])
    expect(branchesChanged([{ id: "b3", name: "Old", role: "VIEWER" }], choicesFrom([{ id: "b3", name: "Old", role: "VIEWER" }]), offRows)).toBe(true)
  })
  it("is false when nothing is held and nothing chosen", () => expect(branchesChanged([], {}, rows)).toBe(false))
})

describe("setChoice, overLimit, summary", () => {
  it("replaces one choice without touching the others or the original", () => {
    const a = { b1: "VIEWER" as const }; const b = setChoice(a, "b2", "MANAGER")
    expect(b).toEqual({ b1: "VIEWER", b2: "MANAGER" }); expect(a).toEqual({ b1: "VIEWER" })
  })
  it("refuses more than the server accepts", () => {
    const many: Record<string, "VIEWER"> = {}; for (let i = 0; i < 501; i++) many["b" + i] = "VIEWER"
    expect(overLimit(many)).toMatch(/at most 500/); delete many.b0; expect(overLimit(many)).toBeNull()
  })
  it("counts branches in words", () => {
    const rows = branchRows(all, [])
    expect(branchSummary(rows, {})).toBe("No branch access.")
    expect(branchSummary(rows, { b1: "VIEWER" })).toBe("Access through 1 branch.")
    expect(branchSummary(rows, { b1: "VIEWER", b2: "MANAGER" })).toBe("Access through 2 branches.")
  })
})

describe("branchWarning", () => {
  it("warns when a whole-company user is about to be limited", () => expect(branchWarning(view(), 1)).toMatch(/can see every site now/))
  it("warns when the last limit is lifted", () => expect(branchWarning(view({ restricted: true, branches: [{ id: "b", name: "J", role: "VIEWER" }] }), 0)).toMatch(/see the whole company/))
  it("says the user stays limited when clients or sites remain", () =>
    expect(branchWarning(view({ restricted: true, branches: [{ id: "b", name: "J", role: "VIEWER" }], sites: [{ id: "s", name: "M" }] }), 0)).toMatch(/stays limited to their clients/))
  it("is quiet for an ordinary change", () => {
    expect(branchWarning(view({ restricted: true, branches: [{ id: "b", name: "J", role: "VIEWER" }] }), 2)).toBeNull()
    expect(branchWarning(view(), 0)).toBeNull()
  })
})

describe("branchWarning with regions", () => {
  const held = { restricted: true, branches: [{ id: "b", name: "J", role: "VIEWER" }], regions: [{ id: "r1", name: "KZN" }] }
  it("does not call it the last limit while a region is held", () => {
    expect(branchWarning(view(held), 0)).toBe("Saving removes the branches. The user stays limited to their regions.")
    expect(branchWarning(view(held), 0)).not.toMatch(/whole company/)
  })
  it("lists everything that is kept", () => {
    expect(branchWarning(view({ ...held, clients: [{ id: "c", name: "A" }], sites: [{ id: "s", name: "M" }] }), 0)).toMatch(/their regions, clients and named sites\./)
    expect(branchWarning(view({ ...held, sites: [{ id: "s", name: "M" }] }), 0)).toMatch(/their regions and named sites\./)
  })
  it("keeps the old wording when no region is held", () => {
    expect(branchWarning(view({ restricted: true, branches: held.branches, sites: [{ id: "s", name: "M" }] }), 0)).toBe("Saving removes the branches. The user stays limited to their clients and named sites.")
  })
})
