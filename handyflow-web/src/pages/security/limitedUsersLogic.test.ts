import { describe, it, expect } from "vitest"
import { filterUsers, limitText, reachText, seesNothing, statusNote, summariseUsers, type LimitedUser } from "./limitedUsersLogic"

const u = (o: Partial<LimitedUser> = {}): LimitedUser => ({ userId: "u1", name: "Thandi Nkosi", email: "thandi@acme.co.za", status: "ACTIVE", branches: 0, clients: 0, sites: 0, visibleSites: 3, ...o })

describe("limitText", () => {
  it("names each part with the right plural", () => {
    expect(limitText({ branches: 2, clients: 1, sites: 3 })).toBe("2 branches, 1 client, 3 named sites")
    expect(limitText({ branches: 1, clients: 0, sites: 1 })).toBe("1 branch, 1 named site")
    expect(limitText({ branches: 0, clients: 0, sites: 0 })).toBe("")
  })
})

describe("reachText and seesNothing", () => {
  it("says so plainly when the user is limited to no sites at all", () => {
    expect(reachText({ visibleSites: 0 })).toBe("Sees no sites"); expect(seesNothing({ visibleSites: 0 })).toBe(true)
    expect(reachText({ visibleSites: 1 })).toBe("1 site"); expect(reachText({ visibleSites: 12 })).toBe("12 sites"); expect(seesNothing({ visibleSites: 1 })).toBe(false)
  })
})

describe("filterUsers", () => {
  const list = [u(), u({ userId: "u2", name: "Pieter van Wyk", email: "pieter@acme.co.za" })]
  it("matches name or email, ignoring case and spaces", () => {
    expect(filterUsers(list, " THANDI ").map(x => x.userId)).toEqual(["u1"])
    expect(filterUsers(list, "wyk").map(x => x.userId)).toEqual(["u2"])
    expect(filterUsers(list, "@acme").map(x => x.userId)).toEqual(["u1", "u2"])
  })
  it("keeps everyone for empty text, and nobody for no match", () => {
    expect(filterUsers(list, "  ")).toHaveLength(2); expect(filterUsers(list, "zzz")).toHaveLength(0)
  })
})

describe("summariseUsers and statusNote", () => {
  it("counts the people who see nothing", () => expect(summariseUsers([u(), u({ visibleSites: 0 }), u({ visibleSites: 0 })])).toEqual({ total: 3, seeingNothing: 2 }))
  it("says nothing for an active user and names any other status", () => {
    expect(statusNote("ACTIVE")).toBeNull(); expect(statusNote("SUSPENDED")).toBe("Suspended"); expect(statusNote("INVITED")).toBe("Invited")
  })
})

describe("limitText with regions", () => {
  it("puts regions first", () => {
    expect(limitText({ regions: 2, branches: 1, clients: 0, sites: 0 })).toBe("2 regions, 1 branch")
    expect(limitText({ regions: 1, branches: 0, clients: 0, sites: 0 })).toBe("1 region")
  })
  it("treats a missing count as none", () => { expect(limitText({ branches: 1, clients: 0, sites: 0 })).toBe("1 branch") })
})
