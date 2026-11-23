import { describe, it, expect } from "vitest"
import { emptyRegionNote, regionBody, regionOverLimit, regionRows, regionSummary, regionWarning, regionsChanged, selectedFrom, toggle, MAX_REGIONS, type RegionInfo } from "./regionAccessLogic"
import type { ScopeView } from "./siteScopeLogic"

const view = (o: Partial<ScopeView> = {}): ScopeView => ({ restricted: false, branches: [], clients: [], sites: [], visibleSites: -1, ...o })
const reg = (id: string, name: string, branchIds: string[] = [], users = 0): RegionInfo => ({ id, name, branchIds, users })
const held = (...ids: string[]) => view({ restricted: true, visibleSites: 1, regions: ids.map(i => ({ id: i, name: i.toUpperCase() })) })

describe("what the user holds", () => {
  it("reads the ids and tolerates a server without regions", () => { expect(selectedFrom(held("r1", "r2"))).toEqual(["r1", "r2"]); expect(selectedFrom(view())).toEqual([]) })
  it("builds the body with only ids", () => expect(regionBody(["r1"])).toEqual({ regionIds: ["r1"] }))
  it("sees a change in any order and ignores a reorder", () => {
    expect(regionsChanged(held("r1", "r2"), ["r2", "r1"])).toBe(false)
    expect(regionsChanged(held("r1"), ["r1", "r2"])).toBe(true)
    expect(regionsChanged(held("r1"), [])).toBe(true)
    expect(regionsChanged(view(), [])).toBe(false)
  })
  it("has a limit matching the server", () => {
    expect(regionOverLimit(Array.from({ length: MAX_REGIONS }, (_, i) => String(i)))).toBeNull()
    expect(regionOverLimit(Array.from({ length: MAX_REGIONS + 1 }, (_, i) => String(i)))).toMatch(/at most 500/)
  })
})

describe("regionRows", () => {
  it("sorts by name and keeps a held region the list did not return", () => {
    const rows = regionRows([reg("r2", "Gauteng"), reg("r1", "KZN")], held("r9"))
    expect(rows.map(r => r.name)).toEqual(["Gauteng", "KZN", "R9"])
    expect(rows[2].branchIds).toEqual([])
  })
  it("does not duplicate a region that is in both", () => expect(regionRows([reg("r1", "KZN")], held("r1"))).toHaveLength(1))
})

describe("regionWarning", () => {
  it("warns when a whole-company user is about to be limited", () => expect(regionWarning(view(), ["r1"])).toMatch(/can see every site now/))
  it("warns when the last limit is lifted", () => expect(regionWarning(held("r1"), [])).toBe("Saving removes the last limit: this user will see the whole company."))
  it("names what keeps limiting the user", () => {
    expect(regionWarning(view({ ...held("r1"), branches: [{ id: "b", name: "J", role: "VIEWER" }] }), [])).toBe("Saving removes the regions. The user stays limited to their branches.")
    expect(regionWarning(view({ ...held("r1"), branches: [{ id: "b", name: "J", role: "VIEWER" }], clients: [{ id: "c", name: "A" }], sites: [{ id: "s", name: "M" }] }), [])).toMatch(/their branches, clients and named sites\./)
    expect(regionWarning(view({ ...held("r1"), sites: [{ id: "s", name: "M" }] }), [])).toMatch(/their named sites\./)
  })
  it("says nothing for an ordinary change", () => { expect(regionWarning(held("r1"), ["r1", "r2"])).toBeNull(); expect(regionWarning(view(), [])).toBeNull() })
})

describe("emptyRegionNote", () => {
  const rows = [reg("r1", "KZN", ["b1"]), reg("r2", "Empty"), reg("r3", "Empty 2")]
  it("warns when every chosen region is empty and nothing else gives sites", () => {
    expect(emptyRegionNote(rows, ["r2"], view())).toMatch(/This region holds no branches yet/)
    expect(emptyRegionNote(rows, ["r2", "r3"], view())).toMatch(/These regions hold no branches yet/)
  })
  it("is quiet when any chosen region has branches, when nothing is chosen, or when other access gives sites", () => {
    expect(emptyRegionNote(rows, ["r1", "r2"], view())).toBeNull()
    expect(emptyRegionNote(rows, [], view())).toBeNull()
    expect(emptyRegionNote(rows, ["r2"], view({ sites: [{ id: "s", name: "M" }] }))).toBeNull()
  })
})

describe("small helpers", () => {
  it("summarises", () => { expect(regionSummary([])).toBe("No region access."); expect(regionSummary(["a"])).toBe("Access through 1 region."); expect(regionSummary(["a", "b"])).toBe("Access through 2 regions.") })
  it("toggles", () => { expect(toggle(["a"], "b")).toEqual(["a", "b"]); expect(toggle(["a", "b"], "a")).toEqual(["b"]) })
})
