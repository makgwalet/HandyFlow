import { describe, it, expect } from "vitest"
import { myPractitionerId } from "./currentPractitioner"

const docs = [
  { id: "a", email: "Priya@Clinic.co.za", active: true },
  { id: "b", email: "other@clinic.co.za", active: true },
  { id: "c", email: null },
]
describe("myPractitionerId", () => {
  it("finds the practitioner by e-mail, ignoring case and spaces", () => {
    expect(myPractitionerId(docs, " priya@clinic.co.za ")).toBe("a")
  })
  it("returns nothing for a user who is not a practitioner or has no e-mail", () => {
    expect(myPractitionerId(docs, "reception@clinic.co.za")).toBe("")
    expect(myPractitionerId(docs, undefined)).toBe("")
    expect(myPractitionerId(docs, "")).toBe("")
  })
  it("ignores inactive practitioners", () => {
    expect(myPractitionerId([{ id: "x", email: "a@b.c", active: false }], "a@b.c")).toBe("")
  })
  it("will not guess when the e-mail matches two records", () => {
    expect(myPractitionerId([{ id: "1", email: "a@b.c" }, { id: "2", email: "a@b.c" }], "a@b.c")).toBe("")
  })
})
