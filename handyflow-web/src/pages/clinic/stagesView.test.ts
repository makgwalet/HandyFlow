import { describe, it, expect } from "vitest"
import { fromApi, lessThanDefault, sameSelection, sourceNote, toRequest, visitTypeLabel } from "./stagesView"

describe("stagesView", () => {
  it("reads the server answer as one tick per stage", () => {
    const s = fromApi({ stages: [{ stage: "SYMPTOMS", required: true }, { stage: "EXAMINATION", required: true }, { stage: "DIAGNOSIS", required: false }, { stage: "PLAN", required: false }] })
    expect(s).toEqual({ SYMPTOMS: true, EXAMINATION: true, DIAGNOSIS: false, PLAN: false })
  })
  it("treats an empty answer as the default: Symptoms and Diagnosis", () => {
    expect(fromApi({ stages: [] })).toEqual({ SYMPTOMS: true, EXAMINATION: false, DIAGNOSIS: true, PLAN: false })
    expect(fromApi(null)).toEqual({ SYMPTOMS: true, EXAMINATION: false, DIAGNOSIS: true, PLAN: false })
  })
  it("sends all four stages in clinical order", () => {
    const r = toRequest({ SYMPTOMS: true, EXAMINATION: false, DIAGNOSIS: true, PLAN: true })
    expect(r.stages.map(s => s.stage)).toEqual(["SYMPTOMS", "EXAMINATION", "DIAGNOSIS", "PLAN"])
    expect(r.stages.map(s => s.required)).toEqual([true, false, true, true])
  })
  it("sees a change", () => {
    const a = { SYMPTOMS: true, EXAMINATION: false, DIAGNOSIS: true, PLAN: false }
    expect(sameSelection(a, { ...a })).toBe(true)
    expect(sameSelection(a, { ...a, PLAN: true })).toBe(false)
  })
  it("says where the setting comes from", () => {
    expect(sourceNote("TENANT")).toMatch(/its own setting/)
    expect(sourceNote("PLATFORM")).toMatch(/platform/)
    expect(sourceNote("DEFAULT")).toMatch(/Symptoms and Diagnosis/)
    expect(sourceNote(undefined)).toMatch(/Nothing is set/)
  })
  it("warns, without blocking, when less than the default is required", () => {
    expect(lessThanDefault({ SYMPTOMS: true, EXAMINATION: false, DIAGNOSIS: true, PLAN: false })).toBeNull()
    expect(lessThanDefault({ SYMPTOMS: false, EXAMINATION: false, DIAGNOSIS: true, PLAN: false })).toMatch(/without Symptoms\./)
    expect(lessThanDefault({ SYMPTOMS: false, EXAMINATION: false, DIAGNOSIS: false, PLAN: false })).toMatch(/Symptoms and Diagnosis/)
  })
  it("labels visit types", () => { expect(visitTypeLabel("RESULTS_REVIEW")).toBe("Results review") })
})
