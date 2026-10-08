import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import SchemePaymentModal from "./SchemePaymentModal"

const get = vi.fn(), post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))

const claim = (id: string, day: string, owes: number) => ({ id, patientName: "P" + id, referenceNumber: "R" + id, schemeName: "Discovery", status: "ACCEPTED", schemePortion: owes, schemeOutstanding: owes, createdAt: `2026-09-${day}T08:00:00Z` })
const show = () => render(<QueryClientProvider client={new QueryClient()}><SchemePaymentModal schemes={["Discovery"]} onClose={() => {}} /></QueryClientProvider>)

beforeEach(() => {
  get.mockReset(); post.mockReset()
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("ACCEPTED") ? [claim("b", "20", 400), claim("a", "10", 800)] : [] } }))
})
afterEach(cleanup)

describe("SchemePaymentModal", () => {
  it("cannot preview without an amount", () => {
    show()
    expect((screen.getByText("Preview") as HTMLButtonElement).disabled).toBe(true)
  })

  it("previews oldest first and records only after a preview", async () => {
    post.mockResolvedValueOnce({ data: { data: { recorded: false, method: "OLDEST_FIRST", amount: 1000, shares: [
      { claimId: "a", patientName: "Pa", claimReference: "Ra", outstandingBefore: 800, amount: 800, outstandingAfter: 0 },
      { claimId: "b", patientName: "Pb", claimReference: "Rb", outstandingBefore: 400, amount: 200, outstandingAfter: 200 }] } } })
    show()
    fireEvent.change(screen.getByLabelText("Amount received"), { target: { value: "1000" } })
    expect((screen.getByText("Record payment") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.click(screen.getByText("Preview"))
    await screen.findByLabelText("Allocation preview")
    expect(post.mock.calls[0][1]).toMatchObject({ schemeName: "Discovery", amount: 1000, preview: true })
    expect(post.mock.calls[0][1].shares).toBeUndefined()

    post.mockResolvedValueOnce({ data: { data: { recorded: true, method: "OLDEST_FIRST", amount: 1000, shares: [{ claimId: "a", outstandingBefore: 800, amount: 800, outstandingAfter: 0 }] } } })
    fireEvent.click(screen.getByText("Record payment"))
    await waitFor(() => expect(post.mock.calls[1][1]).toMatchObject({ preview: false }))
    expect((await screen.findByRole("status")).textContent).toMatch(/Recorded/)
  })

  it("a manual split sends the chosen shares and the reason", async () => {
    post.mockResolvedValue({ data: { data: { recorded: false, method: "MANUAL", amount: 500, shares: [] } } })
    show()
    fireEvent.change(screen.getByLabelText("Amount received"), { target: { value: "500" } })
    fireEvent.click(screen.getByLabelText(/Choose the split myself/))
    fireEvent.change(await screen.findByLabelText("Amount for Pa"), { target: { value: "100" } })
    fireEvent.change(screen.getByLabelText("Amount for Pb"), { target: { value: "400" } })
    expect((screen.getByText("Preview") as HTMLButtonElement).disabled).toBe(true)   // no reason yet
    fireEvent.change(screen.getByLabelText("Reason for the split"), { target: { value: "Remittance names these two" } })
    fireEvent.click(screen.getByText("Preview"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1]).toMatchObject({ overrideReason: "Remittance names these two", shares: [{ claimId: "a", amount: 100 }, { claimId: "b", amount: 400 }] })
  })

  it("shows the server's refusal and records nothing", async () => {
    post.mockRejectedValue({ response: { data: { message: "R 100.00 of the payment is more than the scheme owes on the open claims. Nothing was recorded" } } })
    show()
    fireEvent.change(screen.getByLabelText("Amount received"), { target: { value: "5000" } })
    fireEvent.click(screen.getByText("Preview"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/Nothing was recorded/)
  })
})
