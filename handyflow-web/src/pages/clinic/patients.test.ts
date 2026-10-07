import { describe, it, expect } from "vitest"
import { directoryUrl, toRow, visitsLabel, pagesOf, availableViews } from "./patients"

describe("patients directory helpers", () => {
  it("asks the server for the view, search and page", () => {
    const u = directoryUrl({ view: "RECENT", search: " ada ", includeArchived: true, practitionerId: "d1", page: 3 })
    expect(u).toContain("view=RECENT"); expect(u).toContain("search=ada"); expect(u).toContain("includeArchived=true"); expect(u).toContain("page=3")
    expect(u).not.toContain("practitionerId")
  })
  it("only sends the doctor for My patients", () => {
    expect(directoryUrl({ view: "MINE", search: "", includeArchived: false, practitionerId: "d1", page: 0 })).toContain("practitionerId=d1")
  })
  it("flattens an entry into a row", () => {
    const r = toRow({ patient: { id: "1", fullName: "A B" }, visitCount: 2, lastVisitAt: "2026-10-01T08:00:00Z", nextAppointmentAt: null, sameNameCount: 2, followUpDue: true })
    expect(r).toMatchObject({ id: "1", fullName: "A B", visitCount: 2, sameNameCount: 2, followUpDue: true, lastVisitAt: "2026-10-01T08:00:00Z" })
    expect(r.nextAppointmentAt).toBeUndefined()
  })
  it("labels visits and pages", () => {
    expect(visitsLabel(0)).toBe("No visits"); expect(visitsLabel(1)).toBe("1 visit"); expect(visitsLabel(5)).toBe("5 visits")
    expect(pagesOf(0)).toBe(0); expect(pagesOf(26)).toBe(2)
  })
  it("hides My patients without a practitioner record", () => {
    expect(availableViews("").map(v => v.id)).not.toContain("MINE")
    expect(availableViews("d1").map(v => v.id)).toContain("MINE")
  })
})
