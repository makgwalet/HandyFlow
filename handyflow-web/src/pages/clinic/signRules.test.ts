import { describe, it, expect } from "vitest"
import { DEFAULT_REQUIRED, overrideProblem, requiredFromApi, signChecklist, signGaps, signVerdict, type ChecklistInput, type RequiredStages } from "./signRules"

const blank: ChecklistInput = { chiefComplaint: "", diagnosis: "", icd10Codes: "", examination: "", followUpDays: "",
  weightKg: "", heightCm: "", bloodPressure: "", pulseBpm: "", temperatureC: "", oxygenSatPct: "" }
const full: ChecklistInput = { ...blank, chiefComplaint: "Cough", diagnosis: "URTI", examination: "Chest clear", followUpDays: "7" }
const rx = { medicationName: "Amoxicillin", dosage: "500mg", frequency: "tds", duration: "5 days", quantity: 15 }
const byId = (items: ReturnType<typeof signChecklist>) => Object.fromEntries(items.map(i => [i.id, i]))

describe("signGaps (mirrors the server rule)", () => {
  it("needs a chief complaint and a diagnosis", () => {
    expect(signGaps(blank).map(g => g.step)).toEqual(["symptoms", "diagnose"])
    expect(signGaps(full)).toEqual([])
  })
  it("an ICD-10 code alone counts as a diagnosis; blanks do not", () => {
    expect(signGaps({ ...blank, chiefComplaint: "Cough", icd10Codes: "J06.9" })).toEqual([])
    expect(signGaps({ ...blank, chiefComplaint: "Cough", diagnosis: " ", icd10Codes: " , " }).map(g => g.step)).toEqual(["diagnose"])
  })
  it("history is not required", () => expect(signGaps({ ...blank, chiefComplaint: "Cough", diagnosis: "x" })).toEqual([]))
})

describe("signChecklist and verdict", () => {
  it("a complete record is all ok and can be signed", () => {
    const items = signChecklist(full, [rx], [])
    expect(items.map(i => i.state)).toEqual(["ok", "ok", "ok", "ok", "ok"])
    expect(signVerdict(items)).toEqual({ canSign: true, canOverride: false })
  })
  it("missing symptoms or diagnosis stop signing but can be overridden", () => {
    const items = signChecklist(blank, [], [])
    expect(byId(items).symptoms.state).toBe("required")
    expect(byId(items).diagnosis.state).toBe("required")
    expect(signVerdict(items)).toEqual({ canSign: false, canOverride: true })
  })
  it("no examination, no follow-up and incomplete prescriptions are only worth a look", () => {
    const items = signChecklist({ ...full, examination: "", followUpDays: "" }, [{ ...rx, dosage: "" }], [])
    expect(byId(items).examination.state).toBe("warn")
    expect(byId(items)["follow-up"].detail).toBe("Follow-up not specified")
    expect(byId(items).rx.detail).toMatch(/1 prescription needs dosage details/)
    expect(signVerdict(items).canSign).toBe(true)
  })
  it("vitals alone count as an examination", () => expect(byId(signChecklist({ ...full, examination: "", pulseBpm: "72" }, [], [])).examination.state).toBe("ok"))
  it("an allergy conflict blocks signing and cannot be overridden", () => {
    const items = signChecklist(blank, [rx], ["Amoxicillin"])
    expect(byId(items).allergy.state).toBe("blocked")
    expect(signVerdict(items)).toEqual({ canSign: false, canOverride: false })
  })
})

describe("overrideProblem", () => {
  it("needs a reason, not too long", () => {
    expect(overrideProblem("  ")).toMatch(/reason/)
    expect(overrideProblem("Results review")).toBeNull()
    expect(overrideProblem("x".repeat(501))).toMatch(/500/)
  })
})

const antenatal: RequiredStages = { symptoms: true, examination: true, diagnose: true, plan: true }
const resultsReview: RequiredStages = { symptoms: false, examination: false, diagnose: true, plan: true }

describe("visit-type required stages (CLINIC-DEC-012)", () => {
  it("antenatal also needs an examination and a plan", () => {
    const n = { ...blank, chiefComplaint: "Booking visit", diagnosis: "Normal pregnancy" }
    expect(signGaps(n, antenatal).map(g => g.step)).toEqual(["examination", "plan"])
    expect(signGaps({ ...n, hasVitals: true, hasPlan: true }, antenatal)).toEqual([])
  })
  it("a results review does not need symptoms", () => {
    expect(signGaps({ ...blank, diagnosis: "Anaemia", hasPlan: true }, resultsReview)).toEqual([])
    expect(signGaps({ ...blank, hasPlan: true }, resultsReview).map(g => g.step)).toEqual(["diagnose"])
  })
  it("the default is still Symptoms + Diagnosis", () => {
    expect(DEFAULT_REQUIRED).toEqual({ symptoms: true, examination: false, diagnose: true, plan: false })
    expect(signGaps(blank).map(g => g.step)).toEqual(["symptoms", "diagnose"])
  })
  it("checklist: required examination and plan stop the signature but can be overridden", () => {
    const items = signChecklist({ ...blank, chiefComplaint: "Booking", diagnosis: "Normal pregnancy" }, [], [], antenatal)
    const m = byId(items)
    expect(m.examination.state).toBe("required")
    expect(m.plan.state).toBe("required")
    expect(signVerdict(items)).toEqual({ canSign: false, canOverride: true })
  })
  it("checklist: vitals and a follow-up satisfy examination and plan", () => {
    const items = signChecklist({ ...blank, chiefComplaint: "Booking", diagnosis: "Normal pregnancy", pulseBpm: "80", followUpDays: "28" }, [], [], antenatal)
    expect(byId(items).examination.state).toBe("ok")
    expect(byId(items).plan.state).toBe("ok")
    expect(signVerdict(items).canSign).toBe(true)
  })
  it("checklist: symptoms not required shows ok without a complaint", () => {
    const items = signChecklist({ ...blank, diagnosis: "Anaemia", treatmentPlan: "Iron" }, [], [], resultsReview)
    expect(byId(items).symptoms.state).toBe("ok")
    expect(signVerdict(items).canSign).toBe(true)
  })
  it("reads the server's stages and falls back to the default for anything unusable", () => {
    const api = { visitType: "ANTENATAL", source: "PLATFORM", stages: [
      { stage: "SYMPTOMS", required: true }, { stage: "EXAMINATION", required: true }, { stage: "DIAGNOSIS", required: true }, { stage: "PLAN", required: false }] }
    expect(requiredFromApi(api)).toEqual({ symptoms: true, examination: true, diagnose: true, plan: false })
    for (const bad of [null, undefined, [], {}, { stages: [] }, "x"]) expect(requiredFromApi(bad)).toEqual(DEFAULT_REQUIRED)
  })
})
