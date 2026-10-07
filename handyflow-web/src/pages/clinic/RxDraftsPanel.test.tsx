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
    expect((screen.getByRole("spinbutton") as HTMLInputElement).value).toBe("")
  })
  it("adds a blank prescription on request", () => {
    const add = vi.fn()
    show([], { add })
    fireEvent.click(screen.getByText("Add prescription manually"))
    expect(add).toHaveBeenCalled()
  })
})
