import { describe, it, expect } from "vitest"
import { actionsFor, ageRange, measureLabel, order, sexLabel, statusLabel, type RefSet } from "./growthReferenceView"

const set = (o: Partial<RefSet>): RefSet => ({ id: "1", measure: "WEIGHT_FOR_AGE", sex: "F", title: "t", source: "WHO", sourceVersion: "2006", minAgeMonths: 0, maxAgeMonths: 60, status: "DRAFT", pointCount: 61, ...o })

describe("growth reference review", () => {
  it("offers each step only at its stage and only to the right person", () => {
    const both = { admin: true, approve: true }
    expect(actionsFor(set({ status: "DRAFT" }), both)).toEqual(["submit"])
    expect(actionsFor(set({ status: "DRAFT" }), { admin: false, approve: true })).toEqual([])
    expect(actionsFor(set({ status: "CLINICAL_REVIEW" }), both)).toEqual(["approve", "send-back"])
    expect(actionsFor(set({ status: "CLINICAL_REVIEW" }), { admin: true, approve: false })).toEqual([])
    expect(actionsFor(set({ status: "APPROVED" }), both)).toEqual(["send-back", "activate"])
    expect(actionsFor(set({ status: "ACTIVE" }), both)).toEqual(["retire"])
    expect(actionsFor(set({ status: "RETIRED" }), both)).toEqual([])
  })

  it("puts what needs a decision first and the active and retired sets last", () => {
    const sorted = order([set({ id: "a", status: "ACTIVE" }), set({ id: "b", status: "DRAFT" }), set({ id: "c", status: "CLINICAL_REVIEW" }), set({ id: "d", status: "RETIRED" }), set({ id: "e", status: "APPROVED" })])
    expect(sorted.map(s => s.id)).toEqual(["c", "e", "b", "a", "d"])
  })

  it("reads in plain words", () => {
    expect(statusLabel("CLINICAL_REVIEW")).toBe("In clinical review")
    expect(statusLabel("APPROVED")).toMatch(/not yet active/)
    expect(measureLabel("HEAD_CIRC_FOR_AGE")).toBe("Head circumference for age")
    expect(measureLabel("SOMETHING_NEW")).toBe("Something new")
    expect(sexLabel("M")).toBe("Boys")
    expect(sexLabel("F")).toBe("Girls")
    expect(ageRange({ minAgeMonths: 0, maxAgeMonths: 60 })).toBe("0 months to 5 years")
    expect(ageRange({ minAgeMonths: 6, maxAgeMonths: 18 })).toBe("6 months to 18 months")
  })
})
