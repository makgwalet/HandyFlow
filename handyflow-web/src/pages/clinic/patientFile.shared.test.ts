import { describe, it, expect } from "vitest"
import { saId, saIdDob, saIdParts } from "./patientFile.shared"

describe("SA ID birth date", () => {
  it("reads a real date out of the ID", () => {
    expect(saIdDob("8601015026087")).toBe("1986-01-01")
    expect(saIdParts("8601015026087")).toMatchObject({ year: 1986, month: 1, day: 1, male: true })
    expect(saId("8601015026087")).toMatchObject({ dob: "01 Jan 1986", gender: "Male" })
  })
  it("gives nothing, instead of 'undefined', when the digits are not a date", () => {
    // month 19: seen in real data as "01 undefined 2020"
    expect(saId("2019015026088")).toBeNull()
    expect(saIdDob("2019015026088")).toBeNull()
    expect(saIdDob("8602305026087")).toBeNull()   // 30 February
    expect(saIdDob("123")).toBeNull()
    expect(saIdDob(undefined)).toBeNull()
  })
})
