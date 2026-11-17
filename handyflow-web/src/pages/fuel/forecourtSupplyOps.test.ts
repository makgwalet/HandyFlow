import { describe, it, expect } from "vitest"
import { splitProblem, splitTotal } from "./forecourtSupplyOps"

describe("supply split rules", () => {
  it("no lines is fine (it removes the split)", () => expect(splitProblem([])).toBeNull())
  it("adds the percentages", () => expect(splitTotal([{ supplyingDepotId: "a", percentage: "33.33" }, { supplyingDepotId: "b", percentage: "66.67" }])).toBe(100))
  it("must total 100", () => expect(splitProblem([{ supplyingDepotId: "a", percentage: "60" }, { supplyingDepotId: "b", percentage: "30" }])).toMatch(/total 100\. They total 90/))
  it("needs a depot on every line and no repeats", () => {
    expect(splitProblem([{ supplyingDepotId: "", percentage: "100" }])).toMatch(/Choose a depot/)
    expect(splitProblem([{ supplyingDepotId: "a", percentage: "50" }, { supplyingDepotId: "a", percentage: "50" }])).toMatch(/only once/)
  })
  it("each percentage must be above 0 and at most 100", () => {
    expect(splitProblem([{ supplyingDepotId: "a", percentage: "0" }])).toMatch(/above 0/)
    expect(splitProblem([{ supplyingDepotId: "a", percentage: "100" }])).toBeNull()
  })
})
