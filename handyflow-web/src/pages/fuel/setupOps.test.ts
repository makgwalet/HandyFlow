import { describe, it, expect } from "vitest"
import { EMPTY_WIZARD, emptyEntity, entityToForm, toEntityRequest, toWizardRequest, toggleDivision, validateEntity, validateStep, wizardFromProfile, type Profile } from "./setupOps"

const base: Profile = { status: "IN_PROGRESS", legacyMode: false, wizardStep: 1, wizardAnswers: JSON.stringify({ divisions: ["RETAIL_FORECOURT", "BOGUS"] }), businessSize: "SMALL", orgStructure: null, activeDivisions: [], divisions: [], checklist: [] }

describe("setupOps", () => {
  it("restores the wizard and drops unknown divisions", () => {
    expect(wizardFromProfile(base)).toEqual({ step: 1, divisions: ["RETAIL_FORECOURT"], businessSize: "SMALL", orgStructure: "" })
  })
  it("survives corrupt answers and out-of-range steps", () => {
    expect(wizardFromProfile({ ...base, wizardAnswers: "{not json", wizardStep: 99 })).toMatchObject({ divisions: [], step: 2 })
  })
  it("serialises progress", () => {
    const r = toWizardRequest({ step: 1, divisions: ["DISTRIBUTION"], businessSize: "", orgStructure: "ONE_ENTITY" })
    expect(r).toEqual({ businessSize: null, orgStructure: "ONE_ENTITY", wizardStep: 1, wizardAnswers: '{"divisions":["DISTRIBUTION"]}' })
  })
  it("toggles divisions", () => {
    expect(toggleDivision([], "DISTRIBUTION")).toEqual(["DISTRIBUTION"])
    expect(toggleDivision(["DISTRIBUTION"], "DISTRIBUTION")).toEqual([])
  })
  it("validates each step", () => {
    expect(validateStep(EMPTY_WIZARD)).toMatch(/at least one/)
    expect(validateStep({ ...EMPTY_WIZARD, step: 1, divisions: ["DISTRIBUTION"] })).toMatch(/size/)
    expect(validateStep({ ...EMPTY_WIZARD, step: 1, businessSize: "SMALL" })).toMatch(/structured/)
    expect(validateStep({ step: 1, divisions: [], businessSize: "SMALL", orgStructure: "ONE_ENTITY" })).toBeNull()
  })
  it("builds legal entity requests", () => {
    expect(validateEntity(emptyEntity())).toMatch(/name/)
    expect(toEntityRequest({ ...emptyEntity(), name: "  Acme  ", vatNo: " 4 " })).toEqual({ name: "Acme", registrationNo: null, taxNo: null, vatNo: "4", address: null })
    expect(entityToForm({ id: "1", name: "A", status: "ACTIVE", depotCount: 0 }).registrationNo).toBe("")
  })
})
