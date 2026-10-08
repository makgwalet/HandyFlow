import { describe, it, expect } from "vitest"
import { cleanIdInput, idProblem, autofillsFromId } from "./idEntry"

describe("ID entry", () => {
  it("an SA ID keeps digits only, 13 at most", () => {
    expect(cleanIdInput("SA_ID", "850101 5026-083999")).toBe("8501015026083")
    expect(cleanIdInput("SA_ID", "A1B2")).toBe("12")
  })
  it("a passport keeps letters and digits, upper case, 20 at most", () => {
    expect(cleanIdInput("PASSPORT", "a01 234-567")).toBe("A01234567")
    expect(cleanIdInput("OTHER", "x".repeat(30))).toHaveLength(20)
  })
  it("blank is fine; an SA ID must be 13 digits with a real date", () => {
    expect(idProblem("SA_ID", "")).toBeNull()
    expect(idProblem("SA_ID", "85010")).toMatch(/13 digits/)
    expect(idProblem("SA_ID", "8513015026083")).toMatch(/not a valid/)       // month 13
    expect(idProblem("SA_ID", "8501015026083")).toBeNull()
  })
  it("a passport is not held to the SA rules, only to a sensible length", () => {
    expect(idProblem("PASSPORT", "A01234567")).toBeNull()
    expect(idProblem("PASSPORT", "A1")).toMatch(/at least 5/)
  })
  it("only an SA ID fills in the birth date and sex", () => {
    expect(autofillsFromId("SA_ID")).toBe(true)
    expect(autofillsFromId("PASSPORT")).toBe(false)
  })
})
