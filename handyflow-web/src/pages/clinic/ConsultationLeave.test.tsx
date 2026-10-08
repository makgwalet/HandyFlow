import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn(); const post = vi.fn(); const patch = vi.fn(); const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), patch: (...a: any[]) => patch(...a), put: (...a: any[]) => put(...a), delete: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))
vi.mock("./QuestionForm", () => ({ default: () => <div /> }))
vi.mock("./PatientNotes", () => ({ PatientAlertBanner: () => null }))
vi.mock("./PrescriptionAllergyCheck", () => ({ missingReasons: () => [], useAllergyChecks: () => ({}) }))
import ConsultationSession from "./ConsultationSession"

const patient: any = { id: "p1", fullName: "Ada Lovelace", allergies: [] }
const appt: any = { id: "a1", appointmentType: "CONSULTATION", reason: "Cough", scheduledAt: "2026-10-08T08:00:00Z", status: "IN_PROGRESS" }
const onMinimise = vi.fn(); const onCancel = vi.fn()
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
  <ConsultationSession patient={patient} appointment={appt} onComplete={() => {}} onMinimise={onMinimise} onCancel={onCancel} /></QueryClientProvider>)

beforeEach(() => {
  for (const m of [get, post, patch, put, onMinimise, onCancel]) m.mockReset()
  get.mockResolvedValue({ data: { data: [] } }); post.mockResolvedValue({ data: { data: { id: "d1" } } }); patch.mockResolvedValue({ data: {} }); put.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

const ready = async () => { await screen.findByRole("heading", { name: "1. Symptoms" }); await screen.findByText("Auto-saved") }
const confirmDiscard = async () => {
  fireEvent.click(screen.getByLabelText("Discard draft"))
  const dialog = await screen.findByRole("dialog")
  fireEvent.click(within(dialog).getByText("Discard draft", { selector: "button" }))
}

describe("Save for later and Discard", () => {
  it("Save for later writes the notes first, then leaves", async () => {
    show(); await ready()
    fireEvent.change(document.getElementById("soap-history")!, { target: { value: "cough for 3 days" } })
    patch.mockClear()
    fireEvent.click(screen.getByRole("button", { name: "Save for later" }))
    await waitFor(() => expect(onMinimise).toHaveBeenCalled())
    expect(patch).toHaveBeenCalled()
    expect(JSON.stringify(patch.mock.calls[0][1])).toContain("cough for 3 days")
  })
  it("stays on the screen and says so when the notes cannot be saved", async () => {
    show(); await ready()
    patch.mockRejectedValue({ response: { data: { message: "Server down" } } })
    fireEvent.click(screen.getByRole("button", { name: "Save for later" }))
    expect(await screen.findByText("Server down")).toBeTruthy()
    expect(onMinimise).not.toHaveBeenCalled()
  })
  it("Discard abandons the draft on the server, then leaves", async () => {
    show(); await ready()
    await confirmDiscard()
    await waitFor(() => expect(onCancel).toHaveBeenCalled())
    expect(post).toHaveBeenCalledWith("/api/v1/clinic/consultations/d1/abandon")
  })
  it("Discard that fails does not pretend: it stays and shows the reason", async () => {
    show(); await ready()
    post.mockRejectedValueOnce({ response: { data: { message: "This consultation cannot be abandoned from READY_FOR_DOCTOR." } } })
    await confirmDiscard()
    expect(await screen.findByText(/cannot be abandoned/)).toBeTruthy()
    expect(onCancel).not.toHaveBeenCalled()
  })
})
