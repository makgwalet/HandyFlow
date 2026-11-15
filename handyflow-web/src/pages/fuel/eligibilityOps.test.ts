import { describe, it, expect } from "vitest"
import { emptySiteForm, validateSiteForm, toSiteRequest, describeSiteRule, emptyDriverForm, validateDriverForm, toDriverRequest, validateSpecs, toSpecsRequest, parseProducts, validateChangeover, validateCleaning, stateBlocks } from "./eligibilityOps"

describe("site rule form", () => {
  it("an empty form restricts nothing and says so", () => { expect(describeSiteRule(emptySiteForm())).toBe("Nothing is restricted at this site."); expect(toSiteRequest(emptySiteForm())).toMatchObject({ maxCapacityLitres: null, approvedTypes: [], approvedTankerIds: [], requireApprovedDrivers: false }) })
  it("describes what it restricts and sends numbers, upper-cased types and trimmed training", () => {
    const f = { ...emptySiteForm(), cap: "18000", types: "rigid, tanker_trailer", tankerIds: ["t1"], requireDrivers: true, training: " Mine induction " }
    expect(describeSiteRule(f)).toContain("at most 18000 L"); expect(describeSiteRule(f)).toContain("approved drivers only")
    expect(toSiteRequest(f)).toMatchObject({ maxCapacityLitres: 18000, approvedTypes: ["RIGID", "TANKER_TRAILER"], approvedTankerIds: ["t1"], requiredTraining: "Mine induction" })
  })
  it("refuses limits that are not positive numbers, and training without approved drivers", () => {
    expect(validateSiteForm({ ...emptySiteForm(), cap: "0" })).toMatch(/above zero/); expect(validateSiteForm({ ...emptySiteForm(), len: "abc" })).toMatch(/Maximum length/)
    expect(validateSiteForm({ ...emptySiteForm(), gvm: "1.5" })).toMatch(/whole number/); expect(validateSiteForm({ ...emptySiteForm(), training: "X" })).toMatch(/approved drivers/)
    expect(validateSiteForm({ ...emptySiteForm(), cap: "18000" })).toBeNull()
  })
})
describe("driver approval form", () => {
  const ok = { ...emptyDriverForm(), employeeId: "e1", inductionDate: "2026-11-01" }
  it("needs a driver and an induction date, unless suspended", () => {
    expect(validateDriverForm(emptyDriverForm())).toMatch(/driver/); expect(validateDriverForm({ ...ok, inductionDate: "" })).toMatch(/induction date/)
    expect(validateDriverForm({ ...ok, status: "SUSPENDED", inductionDate: "", conditions: "Breach of rules" })).toBeNull(); expect(validateDriverForm(ok)).toBeNull()
  })
  it("a restriction or suspension must say why; expiry cannot precede the induction; a training expiry needs its reference", () => {
    expect(validateDriverForm({ ...ok, status: "RESTRICTED" })).toMatch(/restriction/); expect(validateDriverForm({ ...ok, status: "SUSPENDED" })).toMatch(/suspended/)
    expect(validateDriverForm({ ...ok, inductionExpiry: "2026-10-01" })).toMatch(/cannot expire before/); expect(validateDriverForm({ ...ok, trainingExpiry: "2027-01-01" })).toMatch(/training reference/)
  })
  it("sends empty dates as null", () => { expect(toDriverRequest(ok)).toMatchObject({ inductionDate: "2026-11-01", inductionExpiry: null, trainingRef: null, conditions: null }) })
  it("expired or missing items stop; current and restricted do not", () => { expect(["SUSPENDED", "EXPIRED", "TRAINING_MISSING", "TRAINING_EXPIRED"].every(stateBlocks)).toBe(true); expect(stateBlocks("OK")).toBe(false); expect(stateBlocks("RESTRICTED")).toBe(false) })
})
describe("specs, products, changeover and cleaning", () => {
  it("specs are optional numbers", () => {
    expect(validateSpecs({ type: "", gvm: "", len: "", wid: "", hgt: "" })).toBeNull(); expect(validateSpecs({ type: "", gvm: "-1", len: "", wid: "", hgt: "" })).toMatch(/Gross vehicle mass/)
    expect(toSpecsRequest({ type: " rigid ", gvm: "28000", len: "", wid: "", hgt: "3.9" })).toEqual({ tankerType: "RIGID", gvmKg: 28000, lengthM: null, widthM: null, heightM: 3.9 })
  })
  it("products are split, upper-cased and de-duplicated", () => { expect(parseProducts("diesel, Petrol  diesel")).toEqual(["DIESEL", "PETROL"]); expect(parseProducts("")).toEqual([]) })
  it("a changeover needs two different products", () => { expect(validateChangeover("diesel", "DIESEL")).toMatch(/different/); expect(validateChangeover("", "X")).toMatch(/both/); expect(validateChangeover("diesel", "paraffin")).toBeNull() })
  it("a cleaning needs a date that is not in the future, and evidence", () => {
    const now = new Date("2026-11-15T10:00:00Z")
    expect(validateCleaning("", "C1", "", now)).toMatch(/when/); expect(validateCleaning("2026-11-16T10:00", "C1", "", now)).toMatch(/future/)
    expect(validateCleaning("2026-11-14T10:00", "", " ", now)).toMatch(/certificate/); expect(validateCleaning("2026-11-14T10:00", "C1", "", now)).toBeNull(); expect(validateCleaning("2026-11-14T10:00", "", "Steam cleaned, inspected", now)).toBeNull()
  })
})
