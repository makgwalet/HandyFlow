import { describe, expect, it } from "vitest"
import { currencyBody, currencyProblem, evidenceBody, evidenceDecisionProblem, evidenceProblem, frameworkBody, frameworkProblem, emptyEvidence } from "./frameworkRules"

const TODAY = "2026-11-25"
const blankForm = { effectiveFrom: "", reviewDueOn: "", sourceType: "", sourceReference: "", sourceVersionDate: "", sourceLocation: "" }

describe("dates and source", () => {
  it("accepts nothing at all", () => {
    expect(frameworkProblem(blankForm, { approvalDate: null, expiryDate: null }, TODAY)).toBeNull()
  })
  it("wants the type and a reference once any of the source is given", () => {
    expect(frameworkProblem({ ...blankForm, sourceReference: "Letter 22/3" }, { approvalDate: null, expiryDate: null }, TODAY)).toMatch(/kind of document/)
    expect(frameworkProblem({ ...blankForm, sourceType: "LETTER", sourceReference: "abc" }, { approvalDate: null, expiryDate: null }, TODAY)).toMatch(/reference/)
    expect(frameworkProblem({ ...blankForm, sourceType: "LETTER", sourceReference: "Letter 22/3" }, { approvalDate: null, expiryDate: null }, TODAY)).toBeNull()
  })
  it("keeps the dates in order", () => {
    expect(frameworkProblem({ ...blankForm, effectiveFrom: "2026-05-01" }, { approvalDate: null, expiryDate: "2026-04-01" }, TODAY)).toMatch(/expiry/)
    expect(frameworkProblem({ ...blankForm, reviewDueOn: "2026-01-01" }, { approvalDate: "2026-03-01", expiryDate: null }, TODAY)).toMatch(/review/)
  })
  it("sends nulls for what is left blank", () => {
    expect(frameworkBody(blankForm)).toEqual({ effectiveFrom: null, reviewDueOn: null, sourceType: null, sourceReference: null, sourceVersionDate: null, sourceLocation: null })
  })
})

describe("currency", () => {
  it("needs a note for transitional and an end date for historical", () => {
    expect(currencyProblem({ currency: "CURRENT", note: "", endedOn: "", supersededById: "" }, null, TODAY)).toBeNull()
    expect(currencyProblem({ currency: "TRANSITIONAL", note: "short", endedOn: "", supersededById: "" }, null, TODAY)).toMatch(/Say why/)
    expect(currencyProblem({ currency: "HISTORICAL", note: "Replaced by the new one", endedOn: "", supersededById: "" }, null, TODAY)).toMatch(/stopped applying/)
    expect(currencyProblem({ currency: "HISTORICAL", note: "Replaced by the new one", endedOn: "2027-01-01", supersededById: "" }, null, TODAY)).toMatch(/future/)
    expect(currencyProblem({ currency: "HISTORICAL", note: "Replaced by the new one", endedOn: "2026-01-01", supersededById: "" }, "2026-03-01", TODAY)).toMatch(/before it was approved/)
    expect(currencyProblem({ currency: "HISTORICAL", note: "Replaced by the new one", endedOn: "2026-06-01", supersededById: "" }, "2026-03-01", TODAY)).toBeNull()
  })
  it("sends only what applies", () => {
    expect(currencyBody({ currency: "TRANSITIONAL", note: " Under the transition ", endedOn: "2026-01-01", supersededById: "x" })).toEqual({ currency: "TRANSITIONAL", note: "Under the transition", endedOn: null, supersededById: null })
    expect(currencyBody({ currency: "CURRENT", note: "old", endedOn: "", supersededById: "" })).toMatchObject({ note: null })
  })
})

describe("evidence", () => {
  it("is checked when added", () => {
    const f = { ...emptyEvidence(TODAY), description: "Approval letter" }
    expect(evidenceProblem({ ...f, description: "ab" }, TODAY)).toMatch(/Describe/)
    expect(evidenceProblem({ ...f, receivedOn: "2027-01-01" }, TODAY)).toMatch(/future/)
    expect(evidenceProblem({ ...f, documentDate: "2026-12-01" }, TODAY)).toMatch(/dated after/)
    expect(evidenceProblem(f, TODAY)).toBeNull()
    expect(evidenceBody(f)).toMatchObject({ kind: "LETTER", reference: null, receivedOn: TODAY })
  })
  it("needs a reason to reject or withdraw but not to verify", () => {
    expect(evidenceDecisionProblem("VERIFIED", "")).toBeNull()
    expect(evidenceDecisionProblem("REJECTED", "no")).toMatch(/Say why/)
    expect(evidenceDecisionProblem("WITHDRAWN", "Superseded by a newer letter")).toBeNull()
  })
})
