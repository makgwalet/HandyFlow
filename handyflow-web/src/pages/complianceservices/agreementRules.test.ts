import { describe, it, expect } from "vitest"
import { addMonths, schedule, termsProblem, previewTotal, defaultTerms, toBody, actionsFor, actionProblem, frequencyLabel, statusLabel, type TermsDraft } from "./agreementRules"

const base: TermsDraft = { ...defaultTerms("Annual", 1500, "2027-03-01"), billingFrequency: "MONTHLY", endDate: "2028-02-29" }

describe("addMonths", () => {
  it("clamps to the last day of a short month and comes back", () => {
    expect(addMonths("2027-01-31", 1)).toBe("2027-02-28")
    expect(addMonths("2027-01-31", 2)).toBe("2027-03-31")
    expect(addMonths("2028-01-31", 1)).toBe("2028-02-29")
  })
  it("crosses years", () => { expect(addMonths("2027-11-15", 3)).toBe("2028-02-15") })
})

describe("schedule", () => {
  it("bills once on the first date", () => { expect(schedule("2027-04-01", "", "ONCE")).toEqual(["2027-04-01"]) })
  it("anchors every date on the first", () => {
    expect(schedule("2027-01-31", "2027-04-30", "MONTHLY")).toEqual(["2027-01-31", "2027-02-28", "2027-03-31", "2027-04-30"])
  })
  it("counts quarters and years", () => {
    expect(schedule("2027-01-01", "2027-12-31", "QUARTERLY")).toHaveLength(4)
    expect(schedule("2027-01-01", "2029-12-31", "ANNUALLY")).toHaveLength(3)
  })
  it("caps an open-ended preview and gives nothing when the first date is after the end", () => {
    expect(schedule("2027-03-01", "", "MONTHLY")).toHaveLength(24)
    expect(schedule("2028-01-01", "2027-12-31", "MONTHLY")).toEqual([])
  })
})

describe("termsProblem", () => {
  it("accepts good terms", () => { expect(termsProblem(base)).toBeNull() })
  it("checks dates in order and the ten-year limit", () => {
    expect(termsProblem({ ...base, startDate: "" })).toMatch(/start date/)
    expect(termsProblem({ ...base, endDate: "2027-02-01" })).toMatch(/before the start/)
    expect(termsProblem({ ...base, endDate: "2037-03-01" })).toBeNull()
    expect(termsProblem({ ...base, endDate: "2037-03-02" })).toMatch(/10 years/)
  })
  it("checks the amount", () => {
    expect(termsProblem({ ...base, billingAmount: "" })).toMatch(/more than zero/)
    expect(termsProblem({ ...base, billingAmount: "0" })).toMatch(/more than zero/)
    expect(termsProblem({ ...base, billingAmount: "10.005" })).toMatch(/two decimal/)
  })
  it("checks the first billing date and notice", () => {
    expect(termsProblem({ ...base, firstBillingDate: "" })).toMatch(/first billing/)
    expect(termsProblem({ ...base, firstBillingDate: "2027-02-01" })).toMatch(/before the start/)
    expect(termsProblem({ ...base, firstBillingDate: "2028-03-05" })).toMatch(/after the end/)
    expect(termsProblem({ ...base, noticeDays: "366" })).toMatch(/Notice/)
    expect(termsProblem({ ...base, noticeDays: "" })).toMatch(/Notice/)
  })
})

describe("previewTotal and toBody", () => {
  it("multiplies the amount by the dates, only when there is an end", () => {
    expect(previewTotal(base)).toBe(18000)
    expect(previewTotal({ ...base, endDate: "" })).toBeNull()
  })
  it("sends numbers and nulls for blanks", () => {
    expect(toBody({ ...base, title: "  ", notes: "" })).toMatchObject({ title: null, billingAmount: 1500, noticeDays: 30, notes: null, endDate: "2028-02-29" })
  })
  it("starts from the quote", () => {
    expect(defaultTerms(null, 4500, "2027-03-01")).toMatchObject({ title: "", billingAmount: "4500", billingFrequency: "ONCE", startDate: "2027-03-01", firstBillingDate: "2027-03-01" })
  })
})

describe("actions", () => {
  it("offers what the server allows, only to people who can manage", () => {
    expect(actionsFor(["activate", "cancel"], true).map(a => a.id)).toEqual(["activate", "cancel"])
    expect(actionsFor(["activate", "cancel"], false)).toEqual([])
    expect(actionsFor(["nonsense"], true)).toEqual([])
  })
  it("asks for a note and, for ending, a date", () => {
    const [activate] = actionsFor(["activate"], true)
    expect(actionProblem(activate, "ok", "", "2027-03-01")).toMatch(/at least 10/)
    expect(actionProblem(activate, "Signed copy returned", "", "2027-03-01")).toBeNull()
    const [end] = actionsFor(["end"], true)
    expect(actionProblem(end, "Client left the practice", "", "2027-03-01")).toMatch(/date it ends/)
    expect(actionProblem(end, "Client left the practice", "2027-02-01", "2027-03-01")).toMatch(/before the start/)
    expect(actionProblem(end, "Client left the practice", "2027-06-01", "2027-03-01")).toBeNull()
    const [resume] = actionsFor(["resume"], true)
    expect(actionProblem(resume, "", "", "2027-03-01")).toBeNull()
  })
  it("words labels", () => {
    expect(statusLabel("SUSPENDED")).toBe("Suspended")
    expect(frequencyLabel("QUARTERLY")).toBe("Every quarter")
    expect(statusLabel("X")).toBe("X")
  })
})
