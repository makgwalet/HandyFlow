import { describe, it, expect } from "vitest"
import { CAN, holds, type Capability } from "./clinicAccess"

describe("clinic capabilities", () => {
  it("a person with the new permission holds the capability", () => {
    expect(holds(["CLINIC_CONSULTATION_SIGN"], "signConsultation")).toBe(true)
  })
  it("the coarse permission it replaced still counts until they sign in again", () => {
    expect(holds(["CLINIC_CLINICAL_SIGN"], "signConsultation")).toBe(true)
  })
  it("neither means no", () => {
    expect(holds(["CLINIC_CLINICAL_WRITE", "CLINIC_NOTE_CREATE"], "signConsultation")).toBe(false)
    expect(holds([], "checkIn")).toBe(false)
  })
  it("signing is not implied by writing", () => {
    expect(holds(["CLINIC_CONSULTATION_UPDATE", "CLINIC_CLINICAL_WRITE"], "signConsultation")).toBe(false)
    expect(holds(["CLINIC_NURSE_HANDOFF"], "acceptHandoff")).toBe(false)
  })
  it("every capability pairs a catalogue-style permission with a coarse one", () => {
    for (const k of Object.keys(CAN) as Capability[]) {
      expect(CAN[k].fine).toMatch(/^CLINIC_[A-Z_]+$/)
      expect(CAN[k].legacy).toMatch(/^CLINIC_(READ|WRITE|CLINICAL_WRITE|CLINICAL_SIGN|PRESCRIPTION_WRITE|LAB_WRITE|BILLING_READ|BILLING_WRITE|ADMIN)$/)
      expect(CAN[k].fine).not.toBe(CAN[k].legacy)
    }
  })
})
