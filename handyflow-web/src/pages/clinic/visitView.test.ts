import { describe, it, expect } from "vitest"
import { headline, isAmendable, isOpenVisit, resumeProblem, rxLine, statusOf, vitalLines, type Visit } from "./visitView"

const v = (o: Partial<Visit> = {}): Visit => ({ id: "v1", status: "SIGNED", consultedAt: "2026-10-08T08:00:00Z", team: [], billed: false, prescriptions: [], addenda: [], ...o })

describe("visit status", () => {
  it("names every handoff state, and a signed visit is amendable, not open", () => {
    expect(statusOf("READY_FOR_DOCTOR").label).toBe("Waiting for the doctor")
    expect(statusOf("SIGNED").label).toBe("Signed")
    expect(isOpenVisit("DRAFT")).toBe(true); expect(isOpenVisit("SIGNED")).toBe(false)
    expect(isAmendable("SIGNED")).toBe(true); expect(isAmendable("LOCKED")).toBe(true); expect(isAmendable("DRAFT")).toBe(false)
  })
  it("shows an unknown status in words instead of hiding it", () => { expect(statusOf("ON_HOLD").label).toBe("on hold") })
})

describe("visit headline and vitals", () => {
  it("prefers the diagnosis, then the complaint, then says nothing was recorded", () => {
    expect(headline(v({ diagnosis: "Acute bronchitis", chiefComplaint: "Cough" }))).toBe("Acute bronchitis")
    expect(headline(v({ chiefComplaint: "Cough" }))).toBe("Cough")
    expect(headline(v({ diagnosis: " " }))).toBe("No reason recorded")
  })
  it("lists only the vitals that were taken, with units, and a BMI when height and weight exist", () => {
    expect(vitalLines(v())).toEqual([])
    const lines = Object.fromEntries(vitalLines(v({ bloodPressure: "128/82", pulseBpm: 72, weightKg: 70, heightCm: 175 })))
    expect(lines).toMatchObject({ BP: "128/82", Pulse: "72 bpm", Weight: "70 kg", Height: "175 cm" })
    expect(lines.BMI).toBeTruthy(); expect(lines.Temp).toBeUndefined()
  })
})

describe("prescription line and resume", () => {
  it("joins dose, frequency, duration, quantity and repeats", () => {
    expect(rxLine({ id: "r", medicationName: "Amoxicillin", dosage: "500mg", frequency: "3x daily", duration: "7 days", quantity: 21, repeats: 1, dispensed: false }))
      .toBe("500mg · 3x daily · 7 days · Qty 21 · Repeats 1")
  })
  it("cannot resume an open visit that has no appointment", () => {
    expect(resumeProblem(v({ status: "DRAFT", appointmentId: null }))).toMatch(/no appointment/)
    expect(resumeProblem(v({ status: "DRAFT", appointmentId: "a1" }))).toBeNull()
    expect(resumeProblem(v({ status: "SIGNED" }))).toBeNull()
  })
})
