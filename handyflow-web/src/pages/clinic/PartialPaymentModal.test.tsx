import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import PartialPaymentModal from "./PartialPaymentModal"

afterEach(cleanup)
const show = (onConfirm = vi.fn(), onClose = vi.fn()) => ({ onConfirm, onClose, ...render(<PartialPaymentModal gross={520} onConfirm={onConfirm} onClose={onClose} />) })

describe("PartialPaymentModal", () => {
  it("has no default amount and cannot be confirmed empty", () => {
    const { onConfirm } = show()
    expect((screen.getByLabelText("Amount the scheme paid") as HTMLInputElement).value).toBe("")
    const btn = screen.getByText("Record payment") as HTMLButtonElement
    expect(btn.disabled).toBe(true)
    fireEvent.click(btn)
    expect(onConfirm).not.toHaveBeenCalled()
  })
  it("explains an amount that is too high and does not confirm it", () => {
    show()
    fireEvent.change(screen.getByLabelText("Amount the scheme paid"), { target: { value: "520" } })
    expect(screen.getByRole("alert").textContent).toMatch(/less than the claim total/)
    expect((screen.getByText("Record payment") as HTMLButtonElement).disabled).toBe(true)
  })
  it("confirms a valid amount as a number", () => {
    const { onConfirm } = show()
    fireEvent.change(screen.getByLabelText("Amount the scheme paid"), { target: { value: "300,50" } })
    fireEvent.click(screen.getByText("Record payment"))
    expect(onConfirm).toHaveBeenCalledWith(300.5)
  })
  it("closes on cancel", () => {
    const { onClose } = show()
    fireEvent.click(screen.getByText("Cancel"))
    expect(onClose).toHaveBeenCalled()
  })
})
