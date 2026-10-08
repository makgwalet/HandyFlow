import { describe, it, expect, vi, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup } from "@testing-library/react"
import ClaimAdjustmentModal from "./ClaimAdjustmentModal"

afterEach(cleanup)

describe("ClaimAdjustmentModal", () => {
  it("a write-off needs an amount within the balance and a reason before it can be sent", () => {
    const onConfirm = vi.fn()
    render(<ClaimAdjustmentModal kind="WRITE_OFF" outstanding={300} onConfirm={onConfirm} onClose={() => {}} />)
    const go = screen.getByText("Write off", { selector: "button" }) as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Amount"), { target: { value: "300.01" } })
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Scheme short-paid the tariff" } })
    expect(go.disabled).toBe(true)
    expect(screen.getByRole("alert").textContent).toMatch(/more than/)
    fireEvent.change(screen.getByLabelText("Amount"), { target: { value: "250,50" } })
    expect(go.disabled).toBe(false)
    fireEvent.click(go)
    expect(onConfirm).toHaveBeenCalledWith({ amount: 250.5, reason: "Scheme short-paid the tariff" })
  })

  it("a void asks only for a reason", () => {
    const onConfirm = vi.fn()
    render(<ClaimAdjustmentModal kind="VOID" outstanding={0} onConfirm={onConfirm} onClose={() => {}} />)
    expect(screen.queryByLabelText("Amount")).toBeNull()
    const go = screen.getByText("Void claim", { selector: "button" }) as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "  Wrong member number entered " } })
    fireEvent.click(go)
    expect(onConfirm).toHaveBeenCalledWith({ amount: undefined, reason: "Wrong member number entered" })
  })

  it("shows the server's refusal", () => {
    render(<ClaimAdjustmentModal kind="CREDIT_NOTE" outstanding={100} error="A claim that is PAID cannot be credited" onConfirm={() => {}} onClose={() => {}} />)
    expect(screen.getByRole("alert").textContent).toMatch(/PAID/)
  })
})
