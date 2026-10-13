import { afterEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import RxDraftsPanel from "./RxDraftsPanel"

afterEach(cleanup)
const draft = (o: any = {}) => ({ id: "r1", medicationName: "Amoxicillin 500mg", dosage: "500mg", frequency: "3x daily", duration: "7 days", quantity: 21, instructions: "", fromBill: true, ...o })
const show = (drafts: any[], fns: any = {}) => render(
  <RxDraftsPanel rxDrafts={drafts} allergyResults={{}} updateRx={fns.update ?? vi.fn()} removeRx={fns.remove ?? vi.fn()} addBlankRx={fns.add ?? vi.fn()} />)

describe("RxDraftsPanel", () => {
  it("shows an empty message and the count", () => {
    show([])
    expect(screen.getByText("Prescriptions (0)")).toBeTruthy()
    expect(screen.getByText("Medications added during the consultation appear here.")).toBeTruthy()
  })
  it("lists a draft, marks the ones added from the bill, and reports edits and removal", () => {
    const update = vi.fn(), remove = vi.fn()
    show([draft()], { update, remove })
    expect(screen.getByText("Amoxicillin 500mg")).toBeTruthy()
    expect(screen.getByText("Added to bill")).toBeTruthy()
    fireEvent.change(screen.getByPlaceholderText("Take with food"), { target: { value: "After meals" } })
    expect(update).toHaveBeenCalledWith("r1", "instructions", "After meals")
    fireEvent.click(screen.getByText("Remove"))
    expect(remove).toHaveBeenCalledWith("r1")
  })
  it("a cleared quantity box is empty, not NaN", () => {
    show([draft({ quantity: NaN })])
    expect((screen.getAllByRole("spinbutton")[0] as HTMLInputElement).value).toBe("")
  })
  it("has a repeats box that reports a whole number, and shows 0 when none are set", () => {
    const update = vi.fn()
    show([draft({ repeats: 0 })], { update })
    const repeats = screen.getAllByRole("spinbutton")[1] as HTMLInputElement
    expect(repeats.value).toBe("0")
    fireEvent.change(repeats, { target: { value: "2" } })
    expect(update).toHaveBeenCalledWith("r1", "repeats", 2)
  })
  it("adds a blank prescription on request", () => {
    const add = vi.fn()
    show([], { add })
    fireEvent.click(screen.getByText("Add prescription manually"))
    expect(add).toHaveBeenCalled()
  })
})
