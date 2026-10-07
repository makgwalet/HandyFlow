import { describe, it, expect } from "vitest"
import { filterOnSite, longestOnSite, onSiteFor, typeLabel, typesPresent, type OnSiteRow } from "./gate.logic"

const NOW = new Date("2026-10-07T12:00:00Z")
const r = (over: Partial<OnSiteRow> = {}): OnSiteRow => ({ id: "e", siteId: "s", siteName: "Centurion Mall", accessPointName: "Main gate", entryType: "VISITOR", personName: "Ann Botha",
  company: "Acme", hostName: "Pieter", vehicleRegistration: "CA 123-456", loggedInAt: "2026-10-07T11:00:00Z", status: "ON_SITE", ...over })

describe("onSiteFor", () => {
  it("reads minutes, hours and days", () => {
    expect(onSiteFor("2026-10-07T11:59:30Z", NOW)).toBe("just now")
    expect(onSiteFor("2026-10-07T11:15:00Z", NOW)).toBe("45 min")
    expect(onSiteFor("2026-10-07T08:55:00Z", NOW)).toBe("3 h 05 min")
    expect(onSiteFor("2026-10-05T08:00:00Z", NOW)).toBe("2 d 4 h")
  })
  it("never goes negative or NaN", () => {
    expect(onSiteFor("2026-10-07T12:30:00Z", NOW)).toBe("just now")
    expect(onSiteFor("not a date", NOW)).toBe("just now")
  })
})

describe("filterOnSite", () => {
  const rows = [r({ id: "a" }), r({ id: "b", entryType: "CONTRACTOR", personName: "Sipho", company: "Fixit", status: "OVERSTAYED", vehicleRegistration: null, hostName: null })]
  it("filters by type, overstay and free text across name, company, host, vehicle and site", () => {
    expect(filterOnSite(rows, { type: "CONTRACTOR", search: "", overstayedOnly: false }).map(x => x.id)).toEqual(["b"])
    expect(filterOnSite(rows, { type: "", search: "", overstayedOnly: true }).map(x => x.id)).toEqual(["b"])
    expect(filterOnSite(rows, { type: "", search: "fixit", overstayedOnly: false }).map(x => x.id)).toEqual(["b"])
    expect(filterOnSite(rows, { type: "", search: "ca 123", overstayedOnly: false }).map(x => x.id)).toEqual(["a"])
    expect(filterOnSite(rows, { type: "", search: "centurion", overstayedOnly: false }).length).toBe(2)
  })
})

describe("types and longest stay", () => {
  it("lists the types present, standard ones first", () => {
    expect(typesPresent({ DELIVERY: 2, VISITOR: 1, ODD_TYPE: 1, CONTRACTOR: 0 })).toEqual(["VISITOR", "DELIVERY", "ODD_TYPE"])
    expect(typeLabel("STAFF_VEHICLE")).toBe("Staff vehicle"); expect(typeLabel("ODD_TYPE")).toBe("Odd type")
  })
  it("picks the longest-staying entry, which the server sends first", () => {
    expect(longestOnSite([r({ id: "x" }), r({ id: "y" })])?.id).toBe("x"); expect(longestOnSite([])).toBeNull()
  })
})
