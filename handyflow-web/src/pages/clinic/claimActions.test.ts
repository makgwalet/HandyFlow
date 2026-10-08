import { describe, it, expect } from "vitest"
import { getClaimActions } from "./ClaimsTab"

const accepted = { status: "ACCEPTED", schemePortion: 1000, schemeOutstanding: 1000 } as any
const labels = (c: any, can = { writeOff: false, reverse: false }) => getClaimActions(c, can).map(b => b.action)

describe("getClaimActions", () => {
  it("an accepted claim offers Mark paid with the balance in the label, and a partial payment", () => {
    const b = getClaimActions(accepted, { writeOff: false, reverse: false })
    expect(b.map(x => x.action)).toEqual(["paid", "partial"])
    expect(b[0].label).toMatch(/Mark paid \(R/)
    expect(b[0].label).toMatch(/1[\s ]?000/)
  })
  it("a part-paid claim can be paid again (the balance), where before it offered nothing", () =>
    expect(labels({ ...accepted, status: "PARTIAL", schemePaid: 400, schemeOutstanding: 600 })).toEqual(["paid", "partial"]))
  it("write-off and credit/void only show to people who hold those permissions", () => {
    expect(labels(accepted, { writeOff: true, reverse: false })).toEqual(["paid", "partial", "writeOff"])
    expect(labels(accepted, { writeOff: false, reverse: true })).toEqual(["paid", "partial", "creditNote", "void"])
  })
  it("a claim with money on it cannot be voided", () =>
    expect(labels({ ...accepted, status: "PARTIAL", schemePaid: 1, schemeOutstanding: 999 }, { writeOff: true, reverse: true })).toEqual(["paid", "partial", "writeOff", "creditNote"]))
  it("a rejected claim can be resubmitted, credited or voided", () =>
    expect(labels({ ...accepted, status: "REJECTED" }, { writeOff: true, reverse: true })).toEqual(["submit", "creditNote", "void"]))
  it("finished claims offer nothing", () => {
    expect(labels({ ...accepted, status: "PAID", schemePaid: 1000, schemeOutstanding: 0 }, { writeOff: true, reverse: true })).toEqual([])
    expect(labels({ ...accepted, status: "VOIDED" }, { writeOff: true, reverse: true })).toEqual([])
  })
})
