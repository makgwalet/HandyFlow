import { describe, it, expect } from "vitest"
import { consentStatement, GENERAL_STATEMENT, privacyUrlProblem, safePrivacyUrl } from "./consent.logic"

describe("consent wording", () => {
  it("names the months, singular and plural", () => {
    expect(consentStatement(12)).toMatch(/removed 12 months after my last activity unless I am hired/)
    expect(consentStatement(1)).toMatch(/removed 1 month after/)
  })
  it("uses the general wording without a usable number", () => {
    for (const v of [null, undefined, 0, -3, NaN]) expect(consentStatement(v)).toBe(GENERAL_STATEMENT)
  })
})

describe("privacy notice link", () => {
  it("links only http and https addresses", () => {
    expect(safePrivacyUrl("https://acme.co.za/privacy")).toBe("https://acme.co.za/privacy")
    expect(safePrivacyUrl(" http://acme.co.za ")).toBe(" http://acme.co.za ".trim())
    for (const bad of [null, undefined, "", "  ", "javascript:alert(1)", "data:text/html,x", "ftp://acme.co.za", "acme.co.za/privacy", "https://user@acme.co.za", "https://acme .co.za"])
      expect(safePrivacyUrl(bad)).toBeNull()
  })
  it("tells the administrator what is wrong", () => {
    expect(privacyUrlProblem("")).toBeNull()
    expect(privacyUrlProblem("https://acme.co.za/privacy")).toBeNull()
    expect(privacyUrlProblem("javascript:alert(1)")).toMatch(/https:\/\//)
    expect(privacyUrlProblem("https://")).toMatch(/web address/)
    expect(privacyUrlProblem("https://acme.co.za/" + "x".repeat(500))).toMatch(/too long/)
  })
})
