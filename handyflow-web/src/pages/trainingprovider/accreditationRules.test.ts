import { describe, it, expect } from "vitest"
import {
  coverageLabel, emptyScope, emptyTransition, expiringWithin, expiryNote, nameProblem, reasonProblem, scopeBody, scopeProblem, statusLabel, statusTone,
  transitionBody, transitionProblem, verifyProblem,
} from "./accreditationRules"

const today = "2026-11-25"
const approval = (over: object = {}) => ({ ...emptyTransition("APPROVED"), accreditationNumber: "ACC-1", approvalDate: "2026-11-01", expiryDate: "2029-11-01", verificationSource: "Authority letter", ...over })

describe("accreditation wording", () => {
  it("labels statuses and tones", () => {
    expect(statusLabel("CONDITIONAL_APPROVAL")).toBe("Approved with conditions")
    expect(statusTone("APPROVED")).toBe("good")
    expect(statusTone("SUSPENDED")).toBe("bad")
    expect(statusTone("RENEWAL_PENDING")).toBe("warn")
    expect(statusTone("DRAFT")).toBe("neutral")
  })
  it("describes expiry in days", () => {
    expect(expiryNote(null)).toBe("No expiry recorded")
    expect(expiryNote(0)).toBe("Expires today")
    expect(expiryNote(1)).toBe("Expires in 1 day")
    expect(expiryNote(40)).toBe("Expires in 40 days")
    expect(expiryNote(-12)).toBe("Expired 12 days ago")
  })
  it("expiringWithin uses the window the caller supplies", () => {
    expect(expiringWithin(30, 90)).toBe(true)
    expect(expiringWithin(120, 90)).toBe(false)
    expect(expiringWithin(-5, 90)).toBe(false)
    expect(expiringWithin(null, 90)).toBe(false)
  })
  it("labels cover outcomes", () => {
    expect(coverageLabel("SITE_NOT_COVERED")).toBe("Not covered at this site")
    expect(coverageLabel("SOMETHING_NEW")).toBe("SOMETHING_NEW")
  })
})

describe("status change checks", () => {
  it("refuses a move the lifecycle does not allow", () => {
    expect(transitionProblem(emptyTransition("APPROVED"), "REJECTED", [], today)).toContain("cannot move")
    expect(transitionProblem(emptyTransition(""), "DRAFT", ["APPROVED"], today)).toBe("Choose the new status")
  })
  it("needs a reason for the moves that lose cover", () => {
    for (const s of ["REJECTED", "SUSPENDED", "WITHDRAWN", "MORE_INFORMATION_REQUIRED", "EXPIRED"]) {
      expect(transitionProblem({ ...emptyTransition(s), reason: "short" }, "DRAFT", [s], today)).toContain("at least 10")
      expect(transitionProblem({ ...emptyTransition(s), reason: "A proper reason" }, "DRAFT", [s], today)).toBeNull()
    }
  })
  it("a plain move needs no reason", () => {
    expect(transitionProblem(emptyTransition("SUBMITTED"), "APPLICATION_PREPARATION", ["SUBMITTED"], today)).toBeNull()
  })
  it("an approval needs number, date, source", () => {
    expect(transitionProblem(approval(), "UNDER_REVIEW", ["APPROVED"], today)).toBeNull()
    expect(transitionProblem(approval({ accreditationNumber: " " }), "UNDER_REVIEW", ["APPROVED"], today)).toContain("number")
    expect(transitionProblem(approval({ approvalDate: "" }), "UNDER_REVIEW", ["APPROVED"], today)).toContain("approval date")
    expect(transitionProblem(approval({ verificationSource: "abc" }), "UNDER_REVIEW", ["APPROVED"], today)).toContain("verified")
  })
  it("an approval cannot be dated in the future or already be expired", () => {
    expect(transitionProblem(approval({ approvalDate: "2027-01-01", expiryDate: "" }), "UNDER_REVIEW", ["APPROVED"], today)).toContain("future")
    expect(transitionProblem(approval({ expiryDate: "2026-10-01" }), "UNDER_REVIEW", ["APPROVED"], today)).toContain("before the approval date")
    expect(transitionProblem(approval({ approvalDate: "2020-01-01", expiryDate: "2025-01-01" }), "UNDER_REVIEW", ["APPROVED"], today)).toContain("already expired")
  })
  it("a conditional approval needs its conditions", () => {
    const f = approval({ toStatus: "CONDITIONAL_APPROVAL" })
    expect(transitionProblem(f, "UNDER_REVIEW", ["CONDITIONAL_APPROVAL"], today)).toContain("conditions")
    expect(transitionProblem({ ...f, conditions: "Resubmit the site report within the period stated" }, "UNDER_REVIEW", ["CONDITIONAL_APPROVAL"], today)).toBeNull()
  })
  it("sends blanks as null", () => {
    const b = transitionBody(approval({ reason: "  ", conditions: "" }))
    expect(b.reason).toBeNull()
    expect(b.conditions).toBeNull()
    expect(b.accreditationNumber).toBe("ACC-1")
    expect(b.overrideReason).toBeNull()
  })
})

describe("other checks", () => {
  it("reasons and sources have minimum lengths", () => {
    expect(reasonProblem("too short")).toContain("at least 10")
    expect(reasonProblem("a real reason")).toBeNull()
    expect(verifyProblem("reg")).not.toBeNull()
    expect(verifyProblem("the authority register")).toBeNull()
    expect(nameProblem("ab", "site")).toContain("site")
  })
  it("a scope needs a site and a programme name", () => {
    expect(scopeProblem(emptyScope)).toContain("site")
    expect(scopeProblem({ ...emptyScope, siteId: "s1", programmeTitle: "x" })).toContain("programme")
    expect(scopeProblem({ ...emptyScope, siteId: "s1", programmeTitle: "Skills programme", nqfLevel: "11" })).toContain("1 to 10")
    expect(scopeProblem({ ...emptyScope, siteId: "s1", programmeTitle: "Skills programme", validFrom: "2026-05-01", validTo: "2026-04-01" })).toContain("cannot end before")
    expect(scopeProblem({ ...emptyScope, siteId: "s1", programmeTitle: "Skills programme", nqfLevel: "4", credits: "20" })).toBeNull()
  })
  it("builds the scope body", () => {
    const b = scopeBody({ ...emptyScope, siteId: "s1", programmeTitle: " Skills programme ", nqfLevel: "4", credits: "" })
    expect(b).toMatchObject({ siteId: "s1", programmeTitle: "Skills programme", nqfLevel: 4, credits: null, qualificationRef: null })
  })
})
