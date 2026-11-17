import { describe, it, expect } from "vitest"
import { splitProblem, splitTotal, basisNote } from "./forecourtSupplyOps"

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

describe("how shifts were dated", () => {
  it("says nothing for the usual system dating", () => expect(basisNote({ basis: "SYSTEM", timezone: "Africa/Johannesburg", systemTimeFallbacks: 0 })).toBeNull())
  it("says nothing when the server sent no basis", () => expect(basisNote(undefined)).toBeNull())
  it("names the provider dating and the timezone", () => expect(basisNote({ basis: "PROVIDER", timezone: "Africa/Harare", systemTimeFallbacks: 0 })).toBe("Shifts are dated by the closing time the pump or POS provider reported, in Africa/Harare."))
  it("flags imported shifts that fell back to the system time", () => {
    expect(basisNote({ basis: "PROVIDER", timezone: "Africa/Johannesburg", systemTimeFallbacks: 1 })).toMatch(/1 imported shift had no provider time, so the system time was used/)
    expect(basisNote({ basis: "PROVIDER", timezone: "Africa/Johannesburg", systemTimeFallbacks: 3 })).toMatch(/3 imported shifts had no provider time/)
  })
})
