import { describe, it, expect } from "vitest"
import { votingMembers, grantorChoices, holderChoices, formProblem, revokeValid, type PMember, type LiveProxy } from "./proxyLogic"

const m = (id: string, role = "MEMBER", from = "2026-01-01", to: string | null = null): PMember => ({ id, memberName: id, memberRole: role, appointedOn: from, endedOn: to, active: to === null })
const D = "2026-12-01"
const members = [m("anna", "CHAIR"), m("ben"), m("cara"), m("dev"), m("eve"), m("obi", "OBSERVER"), m("new", "MEMBER", "2026-12-15"), m("gone", "MEMBER", "2026-01-01", "2026-11-01")]
const p = (g: string, h: string, status = "ACTIVE"): LiveProxy => ({ id: g + h, grantorMemberId: g, holderMemberId: h, status })

describe("proxyLogic", () => {
  it("only voting members on the committee on the meeting date are offered", () => {
    expect(votingMembers(members, D).map(x => x.id)).toEqual(["anna", "ben", "cara", "dev", "eve"])
  })
  it("a member who has given or holds a proxy is not offered as a grantor; a revoked proxy does not count", () => {
    expect(grantorChoices(members, D, [p("dev", "ben")]).map(x => x.id)).toEqual(["anna", "cara", "eve"])
    expect(grantorChoices(members, D, [p("dev", "ben", "REVOKED")]).map(x => x.id)).toEqual(["anna", "ben", "cara", "dev", "eve"])
  })
  it("holders exclude the grantor, anyone who has given a proxy, and anyone already carrying two", () => {
    expect(holderChoices(members, D, [], "dev").map(x => x.id)).toEqual(["anna", "ben", "cara", "eve"])
    expect(holderChoices(members, D, [p("cara", "ben")], "dev").map(x => x.id)).toEqual(["anna", "ben", "eve"])
    expect(holderChoices(members, D, [p("cara", "ben"), p("eve", "ben")], "dev").map(x => x.id)).toEqual(["anna"])
  })
  it("the form says what is missing, in order", () => {
    expect(formProblem({ grantor: "", holder: "", clause: "", charter: false })).toContain("giving")
    expect(formProblem({ grantor: "dev", holder: "", clause: "", charter: false })).toContain("hold")
    expect(formProblem({ grantor: "dev", holder: "dev", clause: "7.2", charter: true })).toContain("own proxy")
    expect(formProblem({ grantor: "dev", holder: "ben", clause: " 7 ", charter: true })).toContain("clause")
    expect(formProblem({ grantor: "dev", holder: "ben", clause: "7.2", charter: false })).toContain("Confirm")
    expect(formProblem({ grantor: "dev", holder: "ben", clause: "7.2", charter: true })).toBeNull()
  })
  it("a revocation needs ten characters", () => { expect(revokeValid("too short")).toBe(false); expect(revokeValid("  Dev can attend  ")).toBe(true) })
})
