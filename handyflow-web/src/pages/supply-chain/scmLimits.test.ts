import { describe, it, expect } from "vitest"
import { limitProblem, limitBody, emptyLimit, limitText, authorityText, enforceHelp, parentChoices, allocationText, flattenTree,
  inviteProblem, grantStatusTone, quoteStatusText, registerProblem } from "./scmLimits"

describe("approval limits", () => {
  it("needs a subject and either an amount or unlimited", () => {
    expect(limitProblem({ ...emptyLimit() })).toBe("Choose a role")
    expect(limitProblem({ ...emptyLimit(), subjectType: "USER" })).toBe("Choose a person")
    expect(limitProblem({ ...emptyLimit(), subjectId: "r" })).toMatch(/Enter an amount/)
    expect(limitProblem({ ...emptyLimit(), subjectId: "r", amount: "-1" })).toMatch(/zero or more/)
    expect(limitProblem({ ...emptyLimit(), subjectId: "r", amount: "0" })).toBeNull()
    expect(limitProblem({ ...emptyLimit(), subjectId: "r", unlimited: true })).toBeNull()
  })
  it("sends null for unlimited and a number otherwise", () => {
    expect(limitBody({ ...emptyLimit(), subjectId: "r", unlimited: true, amount: "5" }).maxAmount).toBeNull()
    expect(limitBody({ ...emptyLimit(), subjectId: "r", amount: "50000" }).maxAmount).toBe(50000)
  })
  it("describes limits and authority", () => {
    expect(limitText(null)).toBe("Unlimited")
    expect(limitText(1500)).toContain("1")
    expect(authorityText({ granted: false, unlimited: false, maxAmount: null })).toMatch(/cannot approve/)
    expect(authorityText({ granted: true, unlimited: true, maxAmount: null })).toBe("Unlimited")
    expect(authorityText({ granted: true, unlimited: false, maxAmount: 10 })).toMatch(/^Up to R/)
  })
  it("explains what switching on needs", () => {
    expect(enforceHelp("OFF", false)).toMatch(/unlimited limit/)
    expect(enforceHelp("OFF", true)).toMatch(/not enforced/)
    expect(enforceHelp("ENFORCE", true)).toMatch(/refused/)
  })
})

describe("budget allocation", () => {
  const b = (id: string, scopeType: string, s: string, e: string, status = "ACTIVE") => ({ id, scopeType, periodStart: s, periodEnd: e, status, amount: 100 })
  const all = [b("d", "DEPARTMENT", "2026-03-01", "2027-02-28"), b("p", "PROJECT", "2026-03-01", "2027-02-28"), b("old", "DEPARTMENT", "2025-03-01", "2026-02-28"), b("x", "DEPARTMENT", "2026-03-01", "2027-02-28", "CLOSED")]
  it("offers only active, same-or-broader budgets that cover the period", () => {
    const ids = (t: string, s: string, e: string) => parentChoices(all, { scopeType: t, periodStart: s, periodEnd: e }).map(x => x.id)
    expect(ids("PROJECT", "2026-04-01", "2026-09-30")).toEqual(["d", "p"])
    expect(ids("DEPARTMENT", "2026-04-01", "2026-09-30")).toEqual(["d"])
    expect(ids("PROJECT", "2026-04-01", "2027-03-31")).toEqual([])
    expect(ids("PROJECT", "", "")).toEqual([])
  })
  it("summarises how much of a parent is split", () => {
    expect(allocationText({ amount: 1000, allocated: 0, unallocated: 1000, childCount: 0 })).toBe("Not split")
    expect(allocationText({ amount: 1000, allocated: 750, unallocated: 250, childCount: 1 })).toBe("75% allocated to 1 sub-budget")
    expect(allocationText({ amount: 1000, allocated: 1000, unallocated: 0, childCount: 2 })).toBe("100% allocated to 2 sub-budgets")
  })
  it("flattens a tree with depth", () => {
    const t = [{ id: 1, children: [{ id: 2, children: [{ id: 3, children: [] }] }] }, { id: 4, children: [] }]
    expect(flattenTree(t).map(x => `${x.node.id}:${x.depth}`)).toEqual(["1:0", "2:1", "3:2", "4:0"])
  })
})

describe("supplier portal", () => {
  it("validates invites and registration", () => {
    expect(inviteProblem("a@b.co")).toBeNull(); expect(inviteProblem("nope")).toMatch(/valid email/)
    expect(registerProblem("longenough1", "longenough1", "Sam")).toBeNull()
    expect(registerProblem("short", "short", "Sam")).toMatch(/10 characters/)
    expect(registerProblem("longenough1", "different11", "Sam")).toMatch(/do not match/)
    expect(registerProblem("longenough1", "longenough1", " ")).toMatch(/name/)
  })
  it("words the state of an RFQ for the supplier", () => {
    expect(quoteStatusText("RESPONDED", "SUBMITTED", true)).toMatch(/revise/)
    expect(quoteStatusText("RESPONDED", "SUBMITTED", false)).toBe("Quoted")
    expect(quoteStatusText("DECLINED", null, true)).toBe("You declined")
    expect(quoteStatusText("INVITED", null, true)).toMatch(/Waiting/)
    expect(quoteStatusText("INVITED", null, false)).toBe("Closed")
    expect(grantStatusTone("ACTIVE")).toBe("ok"); expect(grantStatusTone("PENDING")).toBe("warn"); expect(grantStatusTone("REVOKED")).toBe("neutral")
  })
})
