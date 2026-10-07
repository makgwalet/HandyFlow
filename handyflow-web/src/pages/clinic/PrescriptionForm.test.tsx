import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"

const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { post: (...a: any[]) => post(...a) } }))
import PrescriptionForm, { EMPTY_RX, rxProblems, rxRequest } from "./PrescriptionForm"

beforeEach(() => { post.mockReset(); post.mockResolvedValue({ data: { data: { alerts: [], note: "" } } }) })
afterEach(cleanup)

describe("rxProblems / rxRequest", () => {
  it("needs a medicine, a whole quantity of at least 1, and whole non-negative repeats", () => {
    expect(rxProblems(EMPTY_RX)).toEqual(["Enter the medicine."])
    expect(rxProblems({ ...EMPTY_RX, medicationName: "A", quantity: "0" })).toHaveLength(1)
    expect(rxProblems({ ...EMPTY_RX, medicationName: "A", quantity: "2.5" })).toHaveLength(1)
    expect(rxProblems({ ...EMPTY_RX, medicationName: "A", repeats: "-1" })).toHaveLength(1)
    expect(rxProblems({ ...EMPTY_RX, medicationName: "A" })).toEqual([])
  })
  it("builds a trimmed request with numbers and nulls for blanks", () => {
    expect(rxRequest({ ...EMPTY_RX, medicationName: " Amox ", dosage: " 500mg ", quantity: "20", repeats: "2" }, " reason ")).toEqual({
      medicationName: "Amox", dosage: "500mg", frequency: null, duration: null, quantity: 20, repeats: 2, instructions: null, allergyOverrideReason: "reason",
    })
    expect(rxRequest({ ...EMPTY_RX, medicationName: "A" }, "  ").allergyOverrideReason).toBeNull()
  })
})

describe("PrescriptionForm", () => {
  it("sends the request and clears the form only after it is accepted", async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(<PrescriptionForm consultationId="c1" onSubmit={onSubmit} />)
    fireEvent.change(screen.getByLabelText("Medication *"), { target: { value: "Amoxicillin 500mg" } })
    fireEvent.click(screen.getByText("Add prescription", { selector: "button" }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    expect(onSubmit.mock.calls[0][0]).toMatchObject({ medicationName: "Amoxicillin 500mg", quantity: 30, repeats: 0 })
    await waitFor(() => expect((screen.getByLabelText("Medication *") as HTMLInputElement).value).toBe(""))
  })
  it("keeps what was typed when the server refuses", async () => {
    const onSubmit = vi.fn().mockRejectedValue(new Error("no"))
    render(<PrescriptionForm consultationId="c1" onSubmit={onSubmit} error="Recorded allergy matches this medicine" />)
    fireEvent.change(screen.getByLabelText("Medication *"), { target: { value: "Penicillin" } })
    fireEvent.click(screen.getByText("Add prescription", { selector: "button" }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    expect((screen.getByLabelText("Medication *") as HTMLInputElement).value).toBe("Penicillin")
    expect(screen.getByText(/Recorded allergy matches/)).toBeTruthy()
  })
  it("explains a bad quantity and sends nothing", async () => {
    const onSubmit = vi.fn()
    render(<PrescriptionForm consultationId="c1" onSubmit={onSubmit} />)
    fireEvent.change(screen.getByLabelText("Medication *"), { target: { value: "A" } })
    fireEvent.change(screen.getByLabelText("Quantity"), { target: { value: "0" } })
    fireEvent.click(screen.getByText("Add prescription", { selector: "button" }))
    expect(await screen.findByText(/Quantity must be/)).toBeTruthy()
    expect(onSubmit).not.toHaveBeenCalled()
  })
  it("asks for a reason when an allergy matches, and blocks until one is given", async () => {
    post.mockResolvedValue({ data: { data: { alerts: [{ allergen: "Penicillin", severity: "SEVERE" }], note: "n" } } })
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(<PrescriptionForm consultationId="c1" onSubmit={onSubmit} />)
    fireEvent.change(screen.getByLabelText("Medication *"), { target: { value: "Penicillin V" } })
    expect(await screen.findByText(/Recorded allergy matches/, {}, { timeout: 3000 })).toBeTruthy()
    expect((screen.getByText("Add prescription", { selector: "button" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason to prescribe anyway"), { target: { value: "Tolerated before, specialist advice" } })
    fireEvent.click(screen.getByText("Add prescription", { selector: "button" }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalled())
    expect(onSubmit.mock.calls[0][0].allergyOverrideReason).toBe("Tolerated before, specialist advice")
  })
})
