import { describe, it, expect } from "vitest"
import { EMPTY_DEPENDANT, ageText, applyIdNumber, dependantProblem, pregnancyApplies, profilePatch, sexFromSaId } from "./overviewView"

const NOW = new Date(2026, 9, 8)   // 8 Oct 2026

describe("ageText", () => {
  it("uses months under two years and years after", () => {
    expect(ageText("2026-03-08", NOW)).toBe("7 m"); expect(ageText("2026-03-09", NOW)).toBe("6 m")
    expect(ageText("2024-10-08", NOW)).toBe("2 y"); expect(ageText("1990-01-01", NOW)).toBe("36 y")
  })
  it("has nothing to say without a usable date of birth", () => {
    expect(ageText(null, NOW)).toBeNull(); expect(ageText("", NOW)).toBeNull(); expect(ageText("garbage", NOW)).toBeNull(); expect(ageText("2027-01-01", NOW)).toBeNull()
  })
})

describe("sex at birth", () => {
  it("suggests from a valid ID only", () => {
    expect(sexFromSaId("9001015800087")).toBe("MALE"); expect(sexFromSaId("9001014000087")).toBe("FEMALE")
    expect(sexFromSaId("9019994800087")).toBeNull(); expect(sexFromSaId("123")).toBeNull(); expect(sexFromSaId(null)).toBeNull()
  })
  it("pregnancy status applies to female only and is cleared otherwise", () => {
    expect(pregnancyApplies("FEMALE")).toBe(true); expect(pregnancyApplies("MALE")).toBe(false); expect(pregnancyApplies(null)).toBe(false)
    expect(profilePatch("FEMALE", "PREGNANT")).toEqual({ sexAtBirth: "FEMALE", pregnancyStatus: "PREGNANT" })
    expect(profilePatch("MALE", "PREGNANT")).toEqual({ sexAtBirth: "MALE", pregnancyStatus: null })
    expect(profilePatch("", "PREGNANT")).toEqual({ sexAtBirth: null, pregnancyStatus: null })
  })
})

describe("dependant form", () => {
  const ok = { ...EMPTY_DEPENDANT, firstName: "Alex", lastName: "Smith" }
  it("needs both names", () => {
    expect(dependantProblem({ ...ok, firstName: " " }, NOW)).toMatch(/required/)
    expect(dependantProblem(ok, NOW)).toBeNull()
  })
  it("refuses an ID that is not a real one but accepts no ID", () => {
    expect(dependantProblem({ ...ok, idNumber: "12345" }, NOW)).toMatch(/not a valid SA ID/)
    expect(dependantProblem({ ...ok, idNumber: "9019994800087" }, NOW)).toMatch(/not a valid SA ID/)
    expect(dependantProblem({ ...ok, idNumber: "9001014800087" }, NOW)).toBeNull()
  })
  it("refuses a date of birth in the future", () => {
    expect(dependantProblem({ ...ok, dateOfBirth: "2027-01-01" }, NOW)).toMatch(/future/)
    expect(dependantProblem({ ...ok, dateOfBirth: "2020-01-01" }, NOW)).toBeNull()
  })
  it("fills from a complete valid ID without overwriting a chosen sex at birth", () => {
    const f = applyIdNumber(ok, "9001014000087")
    expect(f).toMatchObject({ idNumber: "9001014000087", dateOfBirth: "1990-01-01", gender: "FEMALE", sexAtBirth: "FEMALE" })
    expect(applyIdNumber({ ...ok, sexAtBirth: "INTERSEX" }, "9001014000087").sexAtBirth).toBe("INTERSEX")
  })
  it("never invents a date from an impossible ID", () => {
    const f = applyIdNumber(ok, "9019994800087")
    expect(f.dateOfBirth).toBe(""); expect(f.idNumber).toBe("9019994800087")
  })
})
