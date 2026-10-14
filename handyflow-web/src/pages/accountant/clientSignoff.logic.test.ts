import { describe, it, expect } from "vitest"
import { actionsFor, allowedActions, reasonOk, rolesLeftFor, failureText } from "./clientSignoff.logic"

describe("clientSignoff.logic", () => {
  it("offers the next step per status", () => {
    expect(actionsFor({ status: "OPEN" }).map(a => a.action)).toEqual(["prepare"])
    expect(actionsFor({ status: "PREPARED" }).map(a => a.action)).toEqual(["review", "reopen"])
    expect(actionsFor({ status: "REVIEWED" }).map(a => a.action)).toEqual(["sign-off", "reopen"])
    expect(actionsFor({ status: "SIGNED_OFF" }).map(a => a.action)).toEqual(["reopen-signed-off"])
  })
  it("hides actions the person lacks permission for", () => {
    const writeOnly = (p: string) => p === "ACCOUNTANT_WRITE"
    expect(allowedActions({ status: "REVIEWED" }, writeOnly).map(a => a.action)).toEqual(["reopen"])
    expect(allowedActions({ status: "SIGNED_OFF" }, writeOnly)).toEqual([])
  })
  it("needs a reason of ten characters to send back", () => {
    expect(reasonOk("too short")).toBe(false)
    expect(reasonOk("  missing bank rec  ")).toBe(true)
  })
  it("does not offer a role the person already holds", () => {
    expect(rolesLeftFor("u1", [{ userId: "u1", role: "PREPARER" }, { userId: "u2", role: "REVIEWER" }]))
      .toEqual(["REVIEWER", "PARTNER"])
  })
  it("explains a client access refusal", () => {
    expect(failureText({ response: { status: 403, data: {} } })).toMatch(/not assigned/)
    expect(failureText({ response: { status: 400, data: { message: "Reviewer must differ" } } })).toBe("Reviewer must differ")
  })
})
