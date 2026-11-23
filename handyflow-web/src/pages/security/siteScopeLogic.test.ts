import { describe, it, expect } from "vitest"
import {
  accessErrorText, editBlock, limitedBanner, overLimit, regionsOf, sameIds, saveWarning, scopeBody, scopeChanged, scopeErrorText, scopeSummary, toggleId,
  type ScopeView,
} from "./siteScopeLogic"

const view = (o: Partial<ScopeView> = {}): ScopeView => ({ restricted: false, branches: [], clients: [], sites: [], visibleSites: -1, ...o })
const limited = (o: Partial<ScopeView> = {}) => view({ restricted: true, visibleSites: 3, sites: [{ id: "s1", name: "Mall" }], ...o })
const err = (status: number, errorCode?: string, message?: string) => ({ response: { status, data: { errorCode, message } } })

describe("summary", () => {
  it("says whole company when there is no limit", () => expect(scopeSummary(view())).toBe("Whole company: every site, current and future."))
  it("counts the sites and where they come from", () => {
    expect(scopeSummary(limited())).toBe("Limited to 3 sites (from 1 named site).")
    expect(scopeSummary(limited({ visibleSites: 1, sites: [], clients: [{ id: "c", name: "A" }, { id: "d", name: "B" }], branches: [{ id: "b", name: "JHB", role: "MANAGER" }] })))
      .toBe("Limited to 1 site (from 1 branch, 2 clients).")
    expect(scopeSummary(limited({ visibleSites: 0, sites: [] }))).toBe("Limited to 0 sites.")
  })
})

describe("banner for the signed-in user", () => {
  it("shows nothing for an unrestricted user or before it loads", () => { expect(limitedBanner(undefined)).toBeNull(); expect(limitedBanner(view())).toBeNull() })
  it("tells a limited user how many sites they see", () => expect(limitedBanner(limited())).toBe("Your access is limited to 3 sites. Company-wide pages and figures are not shown to you."))
  it("says so when a limited user has no sites at all", () => expect(limitedBanner(limited({ visibleSites: 0 }))).toMatch(/covers no sites yet/))
  it("uses the singular", () => expect(limitedBanner(limited({ visibleSites: 1 }))).toMatch(/limited to 1 site\./))
})

describe("choices", () => {
  it("toggles an id on and off without touching the others", () => {
    expect(toggleId(["a"], "b")).toEqual(["a", "b"])
    expect(toggleId(["a", "b"], "a")).toEqual(["b"])
  })
  it("builds the request", () => expect(scopeBody(["c"], ["s"])).toEqual({ clientIds: ["c"], siteIds: ["s"] }))
  it("compares as sets", () => { expect(sameIds(["a", "b"], ["b", "a"])).toBe(true); expect(sameIds(["a"], ["a", "b"])).toBe(false); expect(sameIds(["a", "a"], ["a", "b"])).toBe(false) })
  it("sees a change in sites or clients, and none when the same", () => {
    const v = limited({ clients: [{ id: "c1", name: "A" }] })
    expect(scopeChanged(v, ["c1"], ["s1"])).toBe(false)
    expect(scopeChanged(v, ["c1"], [])).toBe(true)
    expect(scopeChanged(v, [], ["s1"])).toBe(true)
    expect(scopeChanged(v, ["c1"], ["s1", "s2"])).toBe(true)
  })
  it("caps the lists at the server's limit", () => {
    expect(overLimit(Array(200).fill("x"), [])).toBeNull()
    expect(overLimit(Array(201).fill("x"), [])).toMatch(/at most 200 clients/)
    expect(overLimit([], Array(201).fill("x"))).toMatch(/at most 200 sites/)
  })
})

describe("who may change access", () => {
  it("never yourself", () => expect(editBlock("u1", "u1", view())).toMatch(/your own site access/))
  it("never a limited administrator", () => expect(editBlock("u1", "u2", limited())).toMatch(/whole company/))
  it("an unrestricted administrator may change someone else", () => expect(editBlock("u1", "u2", view())).toBeNull())
  it("waits for the administrator's own scope before allowing", () => expect(editBlock("u1", "u2", undefined)).toBeNull())
  it("checks self before restriction", () => expect(editBlock("u1", "u1", limited())).toMatch(/own site access/))
})

describe("warnings before saving", () => {
  it("warns when a whole-company user is about to be limited", () => expect(saveWarning(view(), [], ["s1"])).toMatch(/can see every site now/))
  it("warns when the last limit is removed", () => expect(saveWarning(limited(), [], [])).toMatch(/see the whole company/))
  it("says branches keep limiting the user", () => expect(saveWarning(limited({ branches: [{ id: "b", name: "J", role: "VIEWER" }] }), [], [])).toMatch(/stays limited to their branches/))
  it("says nothing for an ordinary edit or no change", () => { expect(saveWarning(limited(), [], ["s1", "s2"])).toBeNull(); expect(saveWarning(view(), [], [])).toBeNull() })
})

describe("scope errors", () => {
  it("words the two limited-user refusals, using the server's message when there is one", () => {
    expect(scopeErrorText(err(400, "SITE_REQUIRED"))).toMatch(/Choose a site first/)
    expect(scopeErrorText(err(400, "SITE_REQUIRED", "Choose a site."))).toBe("Choose a site.")
    expect(scopeErrorText(err(403, "SITE_SCOPE_LIMITED"))).toMatch(/whole company/)
    expect(scopeErrorText(err(404, "SITE_NOT_FOUND"))).toBe("That site is not available to you.")
  })
  it("leaves every other error alone", () => {
    expect(scopeErrorText(err(409, "DRAFT_EXISTS", "x"))).toBeNull()
    expect(scopeErrorText(new Error("n"))).toBeNull()
    expect(scopeErrorText(null)).toBeNull()
  })
  it("accessErrorText falls back to the server's message, then the fallback", () => {
    expect(accessErrorText(err(400, "SITE_REQUIRED"), "x")).toMatch(/Choose a site/)
    expect(accessErrorText(err(409, "SCOPE_SITE_UNKNOWN", "A chosen site was not found."), "x")).toBe("A chosen site was not found.")
    expect(accessErrorText(err(500), "Could not save")).toBe("Could not save")
    expect(accessErrorText(undefined, "F")).toBe("F")
  })
})

describe("regions in a user's access", () => {
  const rv = (o: Partial<ScopeView> = {}): ScopeView => ({ restricted: true, branches: [], clients: [], sites: [], visibleSites: 2, regions: [{ id: "r1", name: "KZN" }], ...o })
  it("treats a server without regions as none", () => { expect(regionsOf({})).toEqual([]); expect(regionsOf({ regions: [{ id: "r1", name: "KZN" }] })).toHaveLength(1) })
  it("counts regions in the summary, first", () => {
    expect(scopeSummary(rv())).toBe("Limited to 2 sites (from 1 region).")
    expect(scopeSummary(rv({ regions: [{ id: "a", name: "A" }, { id: "b", name: "B" }], branches: [{ id: "b", name: "J", role: "VIEWER" }], sites: [{ id: "s", name: "S" }] }))).toBe("Limited to 2 sites (from 2 regions, 1 branch, 1 named site).")
  })
  it("never says the whole company to a user who still holds a region", () => {
    expect(saveWarning(rv(), [], [])).toBe("Saving removes the clients and sites chosen here. The user stays limited to their regions.")
    expect(saveWarning(rv({ branches: [{ id: "b", name: "J", role: "VIEWER" }] }), [], [])).toMatch(/stays limited to their branches and regions\./)
    expect(saveWarning(rv(), [], [])).not.toMatch(/whole company/)
  })
  it("still warns about the last limit when no region is held", () => { expect(saveWarning(rv({ regions: [] }), [], [])).toMatch(/see the whole company/) })
})
