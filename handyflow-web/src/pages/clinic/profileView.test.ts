import { describe, expect, it } from "vitest"
import { describeCorrection, EMPTY_PROFILE, firstIncomplete, missing, postalProblem, profileSummary, sectionProgress, toForm, toRequest, type Completeness } from "./profileView"

const comp = (done: string[]): Completeness => {
  const items = [["name", "identity"], ["phone", "contact"], ["address", "address"], ["emergency", "emergency"], ["consent", "consent"]]
    .map(([key, section]) => ({ key, label: `Item ${key}`, section, done: done.includes(key) }))
  const n = items.filter(i => i.done).length
  return { done: n, total: items.length, percent: Math.round(n * 100 / items.length), items }
}

describe("profile view", () => {
  it("turns nulls into empty strings and back", () => {
    const f = toForm({ title: "Mr", city: null })
    expect(f.title).toBe("Mr"); expect(f.city).toBe("")
    expect(toForm(null)).toEqual(EMPTY_PROFILE)
    const r = toRequest({ ...f, suburb: "  Sandton " })
    expect(r.suburb).toBe("Sandton"); expect(r.city).toBeNull()
  })
  it("counts progress per section", () => {
    const c = comp(["name", "phone"])
    expect(sectionProgress(c, "identity")).toEqual({ done: 1, total: 1 })
    expect(sectionProgress(c, "address")).toEqual({ done: 0, total: 1 })
    expect(sectionProgress(undefined, "address")).toEqual({ done: 0, total: 0 })
  })
  it("opens at the first section with something missing", () => {
    expect(firstIncomplete(comp(["name"]))).toBe("contact")
    expect(firstIncomplete(comp(["name", "phone", "address", "emergency", "consent"]))).toBe("identity")
    expect(missing(comp(["name"])).length).toBe(4)
  })
  it("says what is missing in a line", () => {
    expect(profileSummary(comp(["name"]))).toBe("4 things missing: item phone, item address, item emergency…")
    expect(profileSummary(comp(["name", "phone", "address", "emergency", "consent"]))).toBe("Profile complete")
  })
  it("checks postal codes", () => {
    expect(postalProblem("2196")).toBeNull(); expect(postalProblem("")).toBeNull(); expect(postalProblem("21A6")).not.toBeNull()
  })
})

describe("describeCorrection", () => {
  it("says what changed in plain words", () => {
    expect(describeCorrection({ field: "LAST_NAME", oldValue: "Botha", newValue: "Bothma" })).toBe("Last name: Botha to Bothma")
    expect(describeCorrection({ field: "ID_NUMBER", oldValue: null, newValue: "A01234567" })).toBe("ID number: added A01234567")
    expect(describeCorrection({ field: "DATE_OF_BIRTH", oldValue: "2019-01-15", newValue: null })).toBe("Date of birth: 2019-01-15 removed")
  })
})
