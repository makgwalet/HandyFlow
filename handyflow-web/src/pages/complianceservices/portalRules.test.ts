import { describe, it, expect } from "vitest"
import { emailProblem, emailNote, ordered, statusLabel, dayOf, type Grant } from "./portalRules"

const g = (over: Partial<Grant>): Grant => ({ id: "1", email: "a@x.co.za", status: "ACTIVE", emailSent: true, invitedAt: "2027-03-01T08:00:00Z", acceptedAt: null, revokedAt: null, inviteExpiresAt: null, ...over })

describe("portalRules", () => {
  it("checks the email", () => {
    expect(emailProblem("")).toMatch(/required/)
    expect(emailProblem("  ")).toMatch(/required/)
    expect(emailProblem("nope")).toMatch(/does not look/)
    expect(emailProblem("a@b")).toMatch(/does not look/)
    expect(emailProblem(" jo@acme.co.za ")).toBeNull()
  })
  it("warns only when a pending invite's email did not go out", () => {
    expect(emailNote(g({ status: "PENDING", emailSent: false }))).toMatch(/could not be sent/)
    expect(emailNote(g({ status: "PENDING", emailSent: true }))).toBeNull()
    expect(emailNote(g({ status: "ACTIVE", emailSent: false }))).toBeNull()
  })
  it("lists active people first, then pending, then removed, by email", () => {
    const out = ordered([g({ id: "r", status: "REVOKED", email: "a@x.co.za" }), g({ id: "p", status: "PENDING", email: "b@x.co.za" }), g({ id: "a2", email: "z@x.co.za" }), g({ id: "a1", email: "c@x.co.za" })])
    expect(out.map(x => x.id)).toEqual(["a1", "a2", "p", "r"])
  })
  it("words states and dates", () => {
    expect(statusLabel("PENDING")).toBe("Invited, not yet signed in")
    expect(statusLabel("X")).toBe("X")
    expect(dayOf("2027-03-01T08:00:00Z")).toBe("2027-03-01")
    expect(dayOf(null)).toBe("")
  })
})
