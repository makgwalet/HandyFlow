import { describe, it, expect } from "vitest"
import { cleanName, deleteBlock, moveWarning, nameProblem, regionLine, regionOfBranch } from "./regionsLogic"

const regions = [
  { id: "r1", name: "KZN", branchIds: ["b1", "b2"], users: 2 },
  { id: "r2", name: "Gauteng", branchIds: ["b3"], users: 0 },
  { id: "r3", name: "Western Cape", branchIds: [], users: 1 },
]

describe("names", () => {
  it("cleans ends and runs of spaces like the server", () => expect(cleanName("  Western   Cape ")).toBe("Western Cape"))
  it("refuses empty, too long and duplicate names, ignoring case and spaces", () => {
    expect(nameProblem("  ", regions)).toBe("Give the region a name.")
    expect(nameProblem("x".repeat(101), regions)).toMatch(/at most 100/)
    expect(nameProblem("x".repeat(100), regions)).toBeNull()
    expect(nameProblem(" gauteng ", regions)).toBe("A region with that name already exists.")
    expect(nameProblem("Western  cape", regions)).toBe("A region with that name already exists.")
    expect(nameProblem("Free State", regions)).toBeNull()
  })
  it("lets a region keep its own name when renamed", () => { expect(nameProblem("kzn", regions, "r1")).toBeNull(); expect(nameProblem("kzn", regions, "r2")).not.toBeNull() })
})

describe("lines and delete", () => {
  it("describes branches and users", () => {
    expect(regionLine(regions[0])).toBe("2 branches · 2 users limited to it")
    expect(regionLine(regions[1])).toBe("1 branch · no users limited to it")
    expect(regionLine({ branchIds: [], users: 1 })).toBe("0 branches · 1 user limited to it")
  })
  it("blocks deleting a region users are limited to", () => {
    expect(deleteBlock(regions[2])).toBe("1 user is limited to this region. Take it off them first.")
    expect(deleteBlock(regions[0])).toBe("2 users are limited to this region. Take it off them first.")
    expect(deleteBlock(regions[1])).toBeNull()
  })
})

describe("moving a branch", () => {
  it("finds the region a branch is in", () => { expect(regionOfBranch(regions, "b3")).toBe("r2"); expect(regionOfBranch(regions, "zz")).toBe("") })
  it("says who gains and who loses sight of the branch's sites", () => {
    expect(moveWarning(regions, "b3", "r1")).toBe("2 users limited to KZN will start seeing this branch's sites.")
    expect(moveWarning(regions, "b1", "r2")).toBe("2 users limited to KZN will stop seeing them.")
    expect(moveWarning(regions, "b1", "r3")).toBe("1 user limited to Western Cape will start seeing this branch's sites; 2 users limited to KZN will stop seeing them.")
  })
  it("says nothing when nobody is affected or nothing changes", () => {
    expect(moveWarning(regions, "b1", "r1")).toBeNull()
    expect(moveWarning(regions, "b3", "")).toBeNull()
    expect(moveWarning(regions, "new", "r2")).toBeNull()
  })
  it("a new branch put in a region users hold is called out", () => expect(moveWarning(regions, "", "r1")).toBe("2 users limited to KZN will start seeing this branch's sites."))
})
