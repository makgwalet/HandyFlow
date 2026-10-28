import { describe, it, expect } from "vitest"
import { EMPTY_FOREIGN, EXPIRING_FILTERS, anyForeign, endingBadge, expiringParam, expiryState, expiryText, foreignBody, foreignProblem, fromServer, permitLabel } from "./foreignIdentity.logic"
import { identityLine } from "./eti.logic"
import { certificateFileName, certificateTitle } from "./myCertificates.logic"

const f = (o: Partial<typeof EMPTY_FOREIGN>) => ({ ...EMPTY_FOREIGN, ...o })

describe("foreignProblem", () => {
  it("accepts an empty block", () => expect(foreignProblem(EMPTY_FOREIGN)).toBeNull())
  it("needs the number and the country together", () => {
    expect(foreignProblem(f({ passportCountry: "ZW" }))).toMatch(/passport number/)
    expect(foreignProblem(f({ passportNumber: "AB123456" }))).toMatch(/country/)
    expect(foreignProblem(f({ passportNumber: "AB123456", passportCountry: "ZIM" }))).toMatch(/two-letter/)
    expect(foreignProblem(f({ passportNumber: "ab 123-456", passportCountry: "zw" }))).toBeNull()
  })
  it("limits the passport length and characters", () => {
    expect(foreignProblem(f({ passportNumber: "AB1", passportCountry: "ZW" }))).toMatch(/5 to 20/)
    expect(foreignProblem(f({ passportNumber: "AB12#456", passportCountry: "ZW" }))).toMatch(/letters and digits/)
  })
  it("needs a number and an end date for a permit, except permanent residence", () => {
    expect(foreignProblem(f({ workPermitNumber: "X1" }))).toMatch(/kind of work permit/)
    expect(foreignProblem(f({ workPermitType: "GENERAL_WORK" }))).toMatch(/number/)
    expect(foreignProblem(f({ workPermitType: "GENERAL_WORK", workPermitNumber: "X1" }))).toMatch(/expires/)
    expect(foreignProblem(f({ workPermitType: "PERMANENT_RESIDENCE", workPermitNumber: "X1" }))).toBeNull()
  })
  it("refuses a permit that ends before the start date", () => {
    const p = f({ workPermitType: "GENERAL_WORK", workPermitNumber: "X1", workPermitExpiry: "2026-01-31" })
    expect(foreignProblem(p, "2026-03-01")).toMatch(/before the start/)
    expect(foreignProblem(p, "2026-01-31")).toBeNull()
  })
})

describe("foreignBody", () => {
  it("cleans and nulls blanks", () => {
    expect(foreignBody(EMPTY_FOREIGN)).toEqual({ passportNumber: null, passportCountry: null, passportExpiry: null, workPermitType: null, workPermitNumber: null, workPermitExpiry: null })
    const b = foreignBody(f({ passportNumber: "ab 123", passportCountry: "zw", passportExpiry: "2030-01-01", workPermitType: "CORPORATE", workPermitNumber: " P9 ", workPermitExpiry: "2028-02-02" }))
    expect(b.passportNumber).toBe("AB123"); expect(b.passportCountry).toBe("ZW"); expect(b.workPermitNumber).toBe("P9")
  })
  it("drops the rest of a block whose key field is gone", () => {
    const b = foreignBody(f({ passportCountry: "ZW", passportExpiry: "2030-01-01", workPermitNumber: "X", workPermitExpiry: "2028-01-01" }))
    expect(b.passportCountry).toBeNull(); expect(b.passportExpiry).toBeNull(); expect(b.workPermitNumber).toBeNull(); expect(b.workPermitExpiry).toBeNull()
  })
  it("round trips through the server shape", () => {
    const form = f({ passportNumber: "AB123456", passportCountry: "ZW", workPermitType: "GENERAL_WORK", workPermitNumber: "X1", workPermitExpiry: "2028-02-02" })
    expect(fromServer(foreignBody(form))).toEqual({ ...form, passportExpiry: "" })
    expect(fromServer(null)).toEqual(EMPTY_FOREIGN)
  })
  it("knows when anything was typed", () => { expect(anyForeign(EMPTY_FOREIGN)).toBe(false); expect(anyForeign(f({ workPermitType: "OTHER" }))).toBe(true) })
})

describe("expiry", () => {
  it("states and words", () => {
    expect(expiryState(null, "2026-10-28")).toBeNull()
    expect(expiryState("2026-10-27", "2026-10-28")).toBe("expired")
    expect(expiryState("2026-10-28", "2026-10-28")).toBe("soon")
    expect(expiryState("2027-01-26", "2026-10-28")).toBe("soon")
    expect(expiryState("2027-01-27", "2026-10-28")).toBe("ok")
    expect(expiryText("2026-11-27", "2026-10-28")).toBe("Expires in 30 days")
    expect(expiryText("2026-10-29", "2026-10-28")).toBe("Expires tomorrow")
    expect(expiryText("2026-10-28", "2026-10-28")).toBe("Expires today")
    expect(expiryText("2026-10-27", "2026-10-28")).toBe("Expired 1 day ago")
    expect(expiryText("2026-10-25", "2026-10-28")).toBe("Expired 3 days ago")
    expect(expiryText("", "2026-10-28")).toBe("")
  })
  it("labels permits", () => { expect(permitLabel("CRITICAL_SKILLS")).toBe("Critical skills work visa"); expect(permitLabel(null)).toBe("—") })
})

describe("certificate wording", () => {
  it("titles and file names", () => {
    expect(certificateTitle({ certificateType: "IT3A", taxYear: 2027 })).toBe("IT3(a) for the 2027 tax year")
    expect(certificateTitle({ certificateType: "IRP5", taxYear: 2027 })).toBe("IRP5 for the 2027 tax year")
    expect(certificateFileName({ certificateType: "IRP5", taxYear: 2027, certificateNumber: "7123/2027 01" })).toBe("IRP5-2027-7123_2027_01.pdf")
  })
})

describe("employee list filter", () => {
  it("offers any, ended, 30, 60 and 90 days, and sends only values the server accepts", () => {
    expect(EXPIRING_FILTERS.map(o => o.value)).toEqual(["", "0", "30", "60", "90"])
    expect(expiringParam("")).toBeNull()
    expect(expiringParam("0")).toBe("0")
    expect(expiringParam("90")).toBe("90")
    expect(expiringParam("367")).toBeNull()
    expect(expiringParam("-1")).toBeNull()
    expect(expiringParam("abc")).toBeNull()
  })

  it("shows the document nearest its end on a row", () => {
    const today = "2026-10-28"
    expect(endingBadge(null, today)).toBeNull()
    expect(endingBadge({}, today)).toBeNull()
    expect(endingBadge({ workPermitExpiry: "2027-06-01", passportExpiry: "2028-01-01" }, today)).toBeNull()
    const b = endingBadge({ workPermitExpiry: "2026-11-10", passportExpiry: "2026-10-20" }, today)
    expect(b?.state).toBe("expired")
    expect(b?.text.startsWith("Passport")).toBe(true)
    expect(endingBadge({ workPermitExpiry: "2026-11-10" }, today)?.state).toBe("soon")
  })
})

describe("ETI papers line", () => {
  it("is empty until the server says, then gives the reason or says they qualify", () => {
    expect(identityLine(undefined)).toBe("")
    expect(identityLine({ identityProblem: undefined } as never)).toBe("")
    expect(identityLine({ identityProblem: null } as never)).toMatch(/they qualify/)
    expect(identityLine({ identityProblem: "a passport or work visa does not qualify" } as never)).toBe("Papers on record: a passport or work visa does not qualify.")
  })
})
