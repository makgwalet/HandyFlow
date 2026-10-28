import { describe, it, expect } from "vitest"
import { consentStatement, GENERAL_STATEMENT } from "./consent.logic"

describe("consent wording", () => {
  it("names the months, singular and plural", () => {
    expect(consentStatement(12)).toMatch(/removed 12 months after my last activity unless I am hired/)
    expect(consentStatement(1)).toMatch(/removed 1 month after/)
  })
  it("uses the general wording without a usable number", () => {
    for (const v of [null, undefined, 0, -3, NaN]) expect(consentStatement(v)).toBe(GENERAL_STATEMENT)
  })
})
