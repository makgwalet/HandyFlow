import { describe, it, expect } from "vitest"
import { consultSteps, rxGaps, type StepNotes, type StepRx } from "./consultSteps"

const blank: StepNotes = { chiefComplaint: "", history: "", examination: "", diagnosis: "", treatmentPlan: "", followUpDays: "",
  weightKg: "", heightCm: "", bloodPressure: "", pulseBpm: "", temperatureC: "", oxygenSatPct: "" }
const rx = (o: Partial<StepRx> = {}): StepRx => ({ medicationName: "Amoxicillin", dosage: "500mg", frequency: "3x daily", duration: "7 days", quantity: 21, ...o })
const by = (n: StepNotes, r: StepRx[] = []) => Object.fromEntries(consultSteps(n, r).map(s => [s.id, s]))

describe("rxGaps", () => {
  it("is empty for a complete draft", () => expect(rxGaps(rx())).toEqual([]))
  it("lists what is missing", () => expect(rxGaps(rx({ dosage: " ", duration: "", quantity: NaN }))).toEqual(["dosage", "duration", "quantity"]))
  it("needs a medicine", () => expect(rxGaps(rx({ medicationName: "" }))).toEqual(["medicine"]))
})

describe("consultSteps", () => {
  it("starts with everything still to do, in order", () => {
    const s = consultSteps(blank, [])
    expect(s.map(x => x.id)).toEqual(["symptoms", "examination", "diagnose", "plan", "sign"])
    expect(s.every(x => x.state === "todo")).toBe(true)
  })
  it("symptoms need complaint and history", () => {
    expect(by({ ...blank, chiefComplaint: "Cough" }).symptoms.state).toBe("todo")
    expect(by({ ...blank, chiefComplaint: "Cough" }).symptoms.hint).toBe("Add the history")
    expect(by({ ...blank, chiefComplaint: "Cough", history: "3 days" }).symptoms.state).toBe("done")
  })
  it("examination is done by findings or any vital", () => {
    expect(by({ ...blank, examination: "Chest clear" }).examination.state).toBe("done")
    expect(by({ ...blank, pulseBpm: "72" }).examination.state).toBe("done")
    expect(by({ ...blank, pulseBpm: "  " }).examination.state).toBe("todo")
  })
  it("diagnose needs the diagnosis, then flags incomplete prescriptions", () => {
    expect(by(blank, [rx()]).diagnose.state).toBe("todo")
    const n = { ...blank, diagnosis: "URTI" }
    expect(by(n).diagnose.state).toBe("done")
    expect(by(n).diagnose.hint).toMatch(/no prescriptions/)
    expect(by(n, [rx()]).diagnose.state).toBe("done")
    const bad = by(n, [rx(), rx({ dosage: "" }), rx({ frequency: "" })]).diagnose
    expect(bad.state).toBe("attention")
    expect(bad.hint).toBe("2 prescriptions need dosage details")
    expect(by(n, [rx({ dosage: "" })]).diagnose.hint).toBe("1 prescription needs dosage details")
  })
  it("plan is done by a plan or a follow-up", () => {
    expect(by({ ...blank, treatmentPlan: "Rest" }).plan.state).toBe("done")
    expect(by({ ...blank, followUpDays: "7" }).plan.state).toBe("done")
  })
  it("sign is ready only when symptoms and diagnose are done; plan and examination do not block it", () => {
    expect(by({ ...blank, chiefComplaint: "Cough", history: "3 days" }).sign.hint).toBe("Finish first: Diagnose & prescribe")
    const ready = { ...blank, chiefComplaint: "Cough", history: "3 days", diagnosis: "URTI" }
    expect(by(ready).sign.state).toBe("done")
    expect(by(ready, [rx({ dosage: "" })]).sign.state).toBe("todo")
  })
})
