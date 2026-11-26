import { describe, it, expect } from "vitest"
import { emptyHours, hoursBody, hoursProblem, sourceLine, withdrawProblem, type HoursView } from "./programmeHoursRules"

const ok = () => ({ ...emptyHours(), hours: "120", sourceType: "CURRICULUM", sourceReference: "Curriculum document", sourceSection: "Section 4", effectiveDate: "2020-02-01", verifiedBy: "Sam Dube", verifiedOn: "2020-03-01" })

describe("hoursProblem", () => {
  it("accepts a complete source note", () => expect(hoursProblem(ok(), "2020-03-10")).toBeNull())
  it("needs a sensible number", () => {
    expect(hoursProblem({ ...ok(), hours: "" }, "2020-03-10")).toBe("Hours must be more than 0 and no more than 20000")
    expect(hoursProblem({ ...ok(), hours: "0" }, "2020-03-10")).toBe("Hours must be more than 0 and no more than 20000")
    expect(hoursProblem({ ...ok(), hours: "1.234" }, "2020-03-10")).toBe("Hours can have at most two decimal places")
  })
  it("needs every part of the source note", () => {
    expect(hoursProblem({ ...ok(), sourceType: "" }, "2020-03-10")).toBe("Say what kind of source the hours come from")
    expect(hoursProblem({ ...ok(), sourceReference: "ab" }, "2020-03-10")).toBe("Give the document or reference the hours come from")
    expect(hoursProblem({ ...ok(), sourceSection: " " }, "2020-03-10")).toBe("Give the section or page where the hours are stated")
    expect(hoursProblem({ ...ok(), effectiveDate: "" }, "2020-03-10")).toBe("Give the date the source says the hours take effect")
    expect(hoursProblem({ ...ok(), verifiedBy: "" }, "2020-03-10")).toBe("Say who checked the source")
    expect(hoursProblem({ ...ok(), verifiedOn: "" }, "2020-03-10")).toBe("Give the day it was checked")
  })
  it("checks the dates", () => {
    expect(hoursProblem({ ...ok(), verifiedOn: "2020-04-01" }, "2020-03-10")).toBe("The check cannot be dated in the future")
    expect(hoursProblem({ ...ok(), sourceVersionDate: "2020-03-05" }, "2020-03-10")).toBe("The source's own date cannot be after the day it was checked")
  })
})
describe("body and wording", () => {
  it("sends a number and leaves out blanks", () => {
    const b = hoursBody(ok())
    expect(b.hours).toBe(120); expect(b.covers).toBeNull(); expect(b.sourceVersionDate).toBeNull()
  })
  it("needs a reason to withdraw", () => {
    expect(withdrawProblem("short")).toContain("at least 10")
    expect(withdrawProblem("Entered against the wrong version")).toBeNull()
  })
  it("writes the source next to the figure without calling it a requirement met", () => {
    const h = { hours: 120, sourceType: "CURRICULUM", sourceReference: "Curriculum document", sourceSection: "Section 4", sourceVersionDate: null, effectiveDate: "2020-02-01", verifiedBy: "Sam Dube", verifiedOn: "2020-03-01" } as HoursView
    expect(sourceLine(h)).toBe("120 workplace hours. Source: Curriculum, Curriculum document, Section 4; effective 2020-02-01; checked by Sam Dube on 2020-03-01.")
  })
})
