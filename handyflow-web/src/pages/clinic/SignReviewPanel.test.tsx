import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import SignReviewPanel from "./SignReviewPanel"
import { signChecklist, signVerdict, type ChecklistInput } from "./signRules"

afterEach(cleanup)
const blank: ChecklistInput = { chiefComplaint: "", diagnosis: "", icd10Codes: "", examination: "", followUpDays: "", weightKg: "", heightCm: "", bloodPressure: "", pulseBpm: "", temperatureC: "", oxygenSatPct: "" }
const panel = (n: ChecklistInput, o: { blocks?: string[]; mode?: "sign" | "handoff" } = {}) => {
  const items = signChecklist(n, [], o.blocks ?? [])
  const fns = { onGoTo: vi.fn(), onSign: vi.fn(), onHandoff: vi.fn() }
  render(<SignReviewPanel mode={o.mode ?? "sign"} items={items} verdict={signVerdict(items)} busy={false} error="" {...fns} />)
  return fns
}

describe("SignReviewPanel", () => {
  it("offers Sign & complete when nothing is required", () => {
    const f = panel({ ...blank, chiefComplaint: "Cough", diagnosis: "URTI" })
    fireEvent.click(screen.getByRole("button", { name: /Sign & complete/ }))
    expect(f.onSign).toHaveBeenCalledWith()
    expect(screen.queryByText("Cannot sign yet")).toBeNull()
  })
  it("says what is missing, jumps there, and overrides only with a reason", () => {
    const f = panel(blank)
    expect(screen.getByText("Cannot sign yet")).toBeTruthy()
    expect(screen.getByText(/Symptoms and Diagnosis required/)).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Go to diagnosis" }))
    expect(f.onGoTo).toHaveBeenCalledWith("diagnose")
    fireEvent.click(screen.getByRole("button", { name: /Override requirement/ }))
    fireEvent.click(screen.getByRole("button", { name: /Confirm override and sign/ }))
    expect(f.onSign).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "  Results review only " } })
    fireEvent.click(screen.getByRole("button", { name: /Confirm override and sign/ }))
    expect(f.onSign).toHaveBeenCalledWith("Results review only")
  })
  it("an allergy conflict cannot be overridden", () => {
    panel({ ...blank, chiefComplaint: "Cough", diagnosis: "URTI" }, { blocks: ["Amoxicillin"] })
    expect(screen.getByText("Cannot sign yet")).toBeTruthy()
    expect(screen.queryByRole("button", { name: /Override requirement/ })).toBeNull()
  })
  it("a nurse hands over instead of signing", () => {
    const f = panel(blank, { mode: "handoff" })
    expect(screen.queryByRole("button", { name: /Sign/ })).toBeNull()
    fireEvent.click(screen.getByRole("button", { name: "Send to doctor" }))
    expect(f.onHandoff).toHaveBeenCalled()
  })
})
