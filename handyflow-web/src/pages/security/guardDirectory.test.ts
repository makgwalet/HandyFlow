import { describe, it, expect } from "vitest"
import { DEFAULT_DIR_QUERY as D, directoryParams, nextSort, withFilter, pageInfo, complianceFlags, activityLabel, guardsCsv, bulkSummary } from "./guardDirectory.logic"

describe("guard directory query", () => {
  it("leaves unset filters out and always sends sort and paging", () => {
    expect(directoryParams(D)).toBe("?sort=name&dir=asc&page=0&size=25")
    const p = new URLSearchParams(directoryParams({ ...D, search: " Zulu ", status: "ACTIVE", grade: "B", psira: "ATTENTION", screening: "FLAGGED", page: 2 }))
    expect(p.get("search")).toBe("Zulu"); expect(p.get("status")).toBe("ACTIVE"); expect(p.get("grade")).toBe("B")
    expect(p.get("psira")).toBe("ATTENTION"); expect(p.get("screening")).toBe("FLAGGED"); expect(p.get("page")).toBe("2")
  })
  it("a heading click flips the same column and starts a new one ascending, back on page one", () => {
    expect(nextSort({ ...D, page: 3 }, "name")).toMatchObject({ sort: "name", dir: "desc", page: 0 })
    expect(nextSort(D, "grade")).toMatchObject({ sort: "grade", dir: "asc", page: 0 })
    expect(nextSort(D, "activity")).toMatchObject({ sort: "activity", dir: "desc" })
  })
  it("changing a filter returns to the first page", () => {
    expect(withFilter({ ...D, page: 4 }, { grade: "C" })).toMatchObject({ grade: "C", page: 0 })
  })
  it("paging numbers", () => {
    expect(pageInfo(0, 0, 25)).toEqual({ pages: 1, from: 0, to: 0, hasPrev: false, hasNext: false })
    expect(pageInfo(60, 0, 25)).toEqual({ pages: 3, from: 1, to: 25, hasPrev: false, hasNext: true })
    expect(pageInfo(60, 2, 25)).toEqual({ pages: 3, from: 51, to: 60, hasPrev: true, hasNext: false })
  })
})

describe("row flags", () => {
  it("a compliant guard has none", () => {
    expect(complianceFlags({ psiraState: "VALID", psiraDaysLeft: 200, screeningStatus: "CLEARED" })).toEqual([])
  })
  it("reports PSiRA and screening separately", () => {
    expect(complianceFlags({ psiraState: "EXPIRED", psiraDaysLeft: -10, screeningStatus: "FLAGGED" }).map(f => f.label)).toEqual(["PSiRA expired", "Screening flagged"])
    expect(complianceFlags({ psiraState: "EXPIRING", psiraDaysLeft: 15, screeningStatus: "PENDING" }).map(f => f.label)).toEqual(["PSiRA 15d left", "Screening pending"])
    expect(complianceFlags({ psiraState: "NONE", psiraDaysLeft: null, screeningStatus: "UNSCREENED" }).map(f => f.label)).toEqual(["No PSiRA expiry on file", "Not screened"])
  })
})

describe("last activity wording", () => {
  const now = new Date("2026-10-07T12:00:00Z")
  it("is relative for the first week and a date after", () => {
    expect(activityLabel(null, now)).toBe("No activity yet")
    expect(activityLabel("2026-10-07T11:59:40Z", now)).toBe("Just now")
    expect(activityLabel("2026-10-07T11:25:00Z", now)).toBe("35 min ago")
    expect(activityLabel("2026-10-07T09:00:00Z", now)).toBe("3 h ago")
    expect(activityLabel("2026-10-04T12:00:00Z", now)).toBe("3 d ago")
    expect(activityLabel("2026-09-01T12:00:00Z", now)).toContain("2026")
  })
})

describe("export and bulk results", () => {
  it("quotes awkward text and leaves out ID and bank details", () => {
    const csv = guardsCsv([{ guard: { fullName: 'Zulu, Ayanda "A"', employeeCode: "G7", grade: "C", status: null, psiraNumber: "P1", psiraExpiryDate: "2027-01-01", phone: "082", idNumber: "9001015800086", bankAccountNumber: "123" }, psiraState: "VALID", psiraDaysLeft: 90, screeningStatus: "CLEARED", lastActivityAt: null }])
    expect(csv.split("\r\n")[1]).toBe('"Zulu, Ayanda ""A""",G7,C,ACTIVE,P1,2027-01-01,VALID,CLEARED,082')
    expect(csv).not.toContain("9001015800086"); expect(csv).not.toContain("123,")
  })
  it("summarises a bulk change, naming the guards that were refused", () => {
    expect(bulkSummary([{ id: "1", name: "A", ok: true }, { id: "2", name: "B", ok: true }]).text).toBe("Updated 2 guards.")
    const s = bulkSummary([{ id: "1", name: "A", ok: true }, { id: "2", name: "B", ok: false, error: "terminated" }])
    expect(s.text).toBe("Updated 1 of 2. Not updated: B (terminated).")
  })
})
