import { describe, it, expect } from "vitest"
import { LIMITED_NOTE, isLimited, monthlyOptionLabel, pdfAvailable, reportAvailable } from "./reportsScopeLogic"

describe("isLimited", () => {
  it("is true only when the server says restricted", () => { expect(isLimited({ restricted: true })).toBe(true) })
  it("is false for the whole company", () => { expect(isLimited({ restricted: false })).toBe(false) })
  it("is false when there is no answer, so a failed call never hides reports", () => {
    expect(isLimited(null)).toBe(false)
    expect(isLimited(undefined)).toBe(false)
    expect(isLimited({})).toBe(false)
  })
})

describe("monthlyOptionLabel", () => {
  it("says all sites for the whole company", () => { expect(monthlyOptionLabel(false)).toBe("Monthly Summary (all sites)") })
  it("says your sites for a limited user", () => { expect(monthlyOptionLabel(true)).toBe("Monthly Summary (your sites)") })
})

describe("what a limited user can open", () => {
  it("cannot open guard attendance, can open the others", () => {
    expect(reportAvailable(true, "guard-attendance")).toBe(false)
    for (const k of ["monthly-summary", "site-coverage", "site-access"]) expect(reportAvailable(true, k)).toBe(true)
  })
  it("cannot download the monthly summary PDF, can download the site PDFs", () => {
    expect(pdfAvailable(true, "monthly-summary")).toBe(false)
    for (const k of ["site-coverage", "site-access", "guard-attendance"]) expect(pdfAvailable(true, k)).toBe(true)
  })
  it("the whole company can open everything", () => {
    for (const k of ["monthly-summary", "site-coverage", "guard-attendance", "site-access"]) { expect(reportAvailable(false, k)).toBe(true); expect(pdfAvailable(false, k)).toBe(true) }
  })
  it("the note names both limits", () => { expect(LIMITED_NOTE).toContain("Guard attendance"); expect(LIMITED_NOTE).toContain("PDF") })
})
