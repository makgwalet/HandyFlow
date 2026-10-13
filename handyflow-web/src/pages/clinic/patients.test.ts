import { describe, it, expect } from "vitest"
import { ageSexLine, directoryUrl, toRow, visitsLabel, pagesOf, availableViews } from "./patients"

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

describe("ageSexLine (0202: children without an ID number)", () => {
  const today = new Date(2026, 9, 13)
  it("uses the stored date of birth and gender when there is no ID", () =>
    expect(ageSexLine({ dateOfBirth: "2025-03-01", gender: "FEMALE", idNumber: null }, today)).toBe("19 mths · Female"))
  it("shows days for a newborn and years from two", () => {
    expect(ageSexLine({ dateOfBirth: "2026-10-03", gender: "MALE" }, today)).toBe("10 days · Male")
    expect(ageSexLine({ dateOfBirth: "2024-10-13", gender: "MALE" }, today)).toBe("2 yrs · Male")
  })
  it("falls back to the SA ID number", () =>
    expect(ageSexLine({ idNumber: "9001015009087" }, today)).toBe("36 yrs · Male"))
  it("prefers the stored gender over the ID digit, and leaves out 'prefer not to say'", () => {
    expect(ageSexLine({ dateOfBirth: "1990-01-01", gender: "FEMALE", idNumber: "9001015009087" }, today)).toBe("36 yrs · Female")
    expect(ageSexLine({ dateOfBirth: "1990-01-01", gender: "PREFER_NOT_TO_SAY" }, today)).toBe("36 yrs")
  })
  it("shows nothing when nothing is known, and no age for a future date", () => {
    expect(ageSexLine({}, today)).toBeNull()
    expect(ageSexLine({ dateOfBirth: "2027-01-01" }, today)).toBeNull()
  })
})
