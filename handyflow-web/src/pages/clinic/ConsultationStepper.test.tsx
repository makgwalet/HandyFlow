import { afterEach, describe, it, expect } from "vitest"
import { vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
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
  it("jumps to the part of the screen for a step", () => {
    const jump = vi.fn()
    render(<ConsultationStepper steps={consultSteps(notes, [])} onJump={jump} />)
    fireEvent.click(screen.getByText("2. Examination"))
    fireEvent.click(screen.getByText("5. Sign"))
    expect(jump).toHaveBeenNthCalledWith(1, "consult-vitals")
    expect(jump).toHaveBeenNthCalledWith(2, "consult-complete")
  })
})
