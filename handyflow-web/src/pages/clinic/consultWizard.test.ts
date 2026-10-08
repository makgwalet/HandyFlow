import { describe, it, expect } from "vitest"
import { nextStep, prevStep, stepNumber, vitalsLine, WIZARD_ORDER } from "./consultWizard"

describe("consultation wizard", () => {
  it("walks the five steps in order and stops at the ends", () => {
    expect(WIZARD_ORDER).toEqual(["symptoms", "examination", "diagnose", "plan", "sign"])
    expect(nextStep("symptoms")).toBe("examination")
    expect(nextStep("plan")).toBe("sign")
    expect(nextStep("sign")).toBeNull()
    expect(prevStep("symptoms")).toBeNull()
    expect(prevStep("sign")).toBe("plan")
    expect(stepNumber("diagnose")).toBe(3)
  })
  it("summarises only the vitals that were filled in", () => {
    const none = { weightKg: "", heightCm: "", bloodPressure: "", pulseBpm: "", temperatureC: "", oxygenSatPct: "" }
    expect(vitalsLine(none)).toBe("")
    expect(vitalsLine({ ...none, bloodPressure: "120/80", pulseBpm: " 72 ", temperatureC: "36.6" })).toBe("BP 120/80 · Pulse 72 bpm · Temp 36.6°C")
  })
})
