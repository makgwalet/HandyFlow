import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { bmi, SoapFields, VitalsPanel, type NotesState } from "./ConsultationNotesPanels"

const blank: NotesState = {
  chiefComplaint: "", history: "", examination: "", diagnosis: "", icd10Codes: "", treatmentPlan: "", followUpDays: "",
  weightKg: "", heightCm: "", bloodPressure: "", pulseBpm: "", temperatureC: "", oxygenSatPct: "",
}
afterEach(cleanup)

describe("bmi", () => {
  it("works out one decimal", () => expect(bmi("82", "175")).toBe("26.8"))
  it("accepts a decimal point", () => expect(bmi("70.5", "170.5")).toBe("24.3"))
  it("is null for missing, text, zero or negative input", () => {
    for (const [w, h] of [["", "175"], ["82", ""], ["abc", "175"], ["82", "x"], ["0", "175"], ["82", "0"], ["-5", "175"]])
      expect(bmi(w, h)).toBeNull()
  })
})

describe("VitalsPanel", () => {
  it("shows the BMI only for real numbers", () => {
    const { rerender } = render(<VitalsPanel soap={{ ...blank, weightKg: "82", heightCm: "175" }} sf={vi.fn()} />)
    expect(screen.getByText("BMI: 26.8")).toBeTruthy()
    rerender(<VitalsPanel soap={{ ...blank, weightKg: "abc", heightCm: "175" }} sf={vi.fn()} />)
    expect(screen.queryByText(/BMI/)).toBeNull()
  })
  it("reports typing with the field name", () => {
    const sf = vi.fn()
    render(<VitalsPanel soap={blank} sf={sf} />)
    fireEvent.change(screen.getByPlaceholderText("120/80"), { target: { value: "130/85" } })
    expect(sf).toHaveBeenCalledWith("bloodPressure", "130/85")
  })
})

describe("SoapFields", () => {
  it("shows the note fields and reports typing", () => {
    const sf = vi.fn()
    render(<SoapFields soap={blank} sf={sf} />)
    expect(screen.getByText("Chief complaint *")).toBeTruthy()
    fireEvent.change(screen.getByPlaceholderText("Plan — management and treatment"), { target: { value: "Rest" } })
    expect(sf).toHaveBeenCalledWith("treatmentPlan", "Rest")
    fireEvent.change(screen.getByPlaceholderText("J06.9, Z00.0"), { target: { value: "J06.9" } })
    expect(sf).toHaveBeenCalledWith("icd10Codes", "J06.9")
  })
})
