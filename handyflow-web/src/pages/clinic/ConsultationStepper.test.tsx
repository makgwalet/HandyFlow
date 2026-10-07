import { afterEach, describe, it, expect } from "vitest"
import { cleanup, render, screen } from "@testing-library/react"
import ConsultationStepper from "./ConsultationStepper"
import { consultSteps } from "./consultSteps"

afterEach(cleanup)
const notes: any = { chiefComplaint: "Cough", history: "3 days", examination: "", diagnosis: "URTI", treatmentPlan: "", followUpDays: "",
  weightKg: "", heightCm: "", bloodPressure: "", pulseBpm: "", temperatureC: "", oxygenSatPct: "" }

describe("ConsultationStepper", () => {
  it("shows the five steps with their state", () => {
    render(<ConsultationStepper steps={consultSteps(notes, [{ medicationName: "X", dosage: "", frequency: "", duration: "", quantity: 1 }])} />)
    const items = screen.getAllByRole("listitem")
    expect(items).toHaveLength(5)
    expect(items.map(i => i.getAttribute("data-state"))).toEqual(["done", "todo", "attention", "todo", "todo"])
    expect(screen.getByText("1. Symptoms")).toBeTruthy()
    expect(screen.getByText("1 prescription needs dosage details")).toBeTruthy()
  })
})
