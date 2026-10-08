import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter, Route, Routes } from "react-router-dom"

const get = vi.fn(); const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
vi.mock("./ConsultationSession", () => ({
  default: (p: any) => <div>
    <div>SESSION for {p.patient.fullName} / {p.appointment.id}</div>
    <button onClick={() => p.onComplete("c1", { consultationId: "c1", diagnosis: "URTI", rxCount: 1, followUpDays: "7", durationMinutes: 9 })}>finish</button>
    <button onClick={p.onCancel}>discard</button>
  </div>,
}))
import ConsultationWorkspacePage from "./ConsultationWorkspacePage"

const appt = (status: string) => ({ id: "a1", patientId: "p1", patientName: "Liam Botha", practitionerId: "d1", status, appointmentType: "CONSULTATION", reason: "Cough", scheduledAt: "2026-10-08T08:00:00Z" })
const serve = (status: string) => get.mockImplementation((url: string) => {
  if (url === "/api/v1/clinic/appointments/a1") return Promise.resolve({ data: { data: appt(status) } })
  if (url === "/api/v1/clinic/patients/p1") return Promise.resolve({ data: { data: { id: "p1", fullName: "Liam Botha" } } })
  if (url.includes("/appointments/range")) return Promise.resolve({ data: { data: [{ id: "a2", patientId: "p2", patientName: "Jane Dlamini", practitionerId: "d1", scheduledAt: "2026-10-08T08:30:00Z", status: "CHECKED_IN", reason: "BP" }] } })
  return Promise.resolve({ data: { data: [] } })
})
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
  <MemoryRouter initialEntries={["/clinic/consult/a1"]}><Routes>
    <Route path="/clinic/consult/:appointmentId" element={<ConsultationWorkspacePage />} />
    <Route path="/clinic/dashboard" element={<div>TODAY</div>} />
    <Route path="/clinic/patients" element={<div>PATIENT FILE</div>} />
  </Routes></MemoryRouter></QueryClientProvider>)
beforeEach(() => { get.mockReset(); post.mockReset(); post.mockResolvedValue({ data: { data: {} } }) })
afterEach(cleanup)

describe("ConsultationWorkspacePage", () => {
  it("checks a scheduled patient in, starts the appointment, then opens the workspace", async () => {
    serve("SCHEDULED"); show()
    expect(await screen.findByText("SESSION for Liam Botha / a1")).toBeTruthy()
    expect(post.mock.calls.map(c => c[0])).toEqual(["/api/v1/clinic/appointments/a1/check_in", "/api/v1/clinic/appointments/a1/start"])
  })
  it("a checked-in patient only needs starting; one already in progress needs nothing", async () => {
    serve("CHECKED_IN"); show()
    await screen.findByText(/SESSION for/)
    expect(post.mock.calls.map(c => c[0])).toEqual(["/api/v1/clinic/appointments/a1/start"])
    cleanup(); post.mockClear(); serve("IN_PROGRESS"); show()
    await screen.findByText(/SESSION for/)
    expect(post).not.toHaveBeenCalled()
  })
  it("will not reopen a finished appointment", async () => {
    serve("COMPLETED"); show()
    expect(await screen.findByText("This consultation is already complete.")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole("button", { name: "Back to Today" }))
    expect(await screen.findByText("TODAY")).toBeTruthy()
  })
  it("says so when the appointment does not exist, or cannot be started", async () => {
    get.mockRejectedValue({ response: { status: 404 } }); show()
    expect(await screen.findByText("This appointment could not be found.")).toBeTruthy()
    cleanup(); serve("CONFIRMED"); post.mockRejectedValue({ response: { data: { message: "Practitioner is on leave" } } }); show()
    expect(await screen.findByText("Practitioner is on leave")).toBeTruthy()
  })
  it("after signing shows the result and the next waiting patient", async () => {
    serve("IN_PROGRESS"); show()
    fireEvent.click(await screen.findByRole("button", { name: "finish" }))
    expect(await screen.findByText("Consultation signed")).toBeTruthy()
    expect(await screen.findByText("Jane Dlamini")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Back to Today" }))
    expect(await screen.findByText("TODAY")).toBeTruthy()
  })
  it("discarding returns to Today", async () => {
    serve("IN_PROGRESS"); show()
    fireEvent.click(await screen.findByRole("button", { name: "discard" }))
    await waitFor(() => expect(screen.getByText("TODAY")).toBeTruthy())
  })
})
