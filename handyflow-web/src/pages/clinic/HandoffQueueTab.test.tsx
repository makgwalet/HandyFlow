import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter, Route, Routes } from "react-router-dom"

const get = vi.fn(); const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))
vi.mock("./QuestionForm", () => ({ default: () => null }))
vi.mock("./QuestionAnswersReadOnly", () => ({ default: () => null }))
import HandoffQueueTab from "./HandoffQueueTab"

const item = (o: any) => ({ id: "c1", patientId: "p1", patientName: "Liam Botha", appointmentId: "a1", chiefComplaint: "Cough", status: "READY_FOR_DOCTOR",
  bloodPressure: "120/80", temperatureC: 37.8, oxygenSatPct: 96, createdAt: "2026-10-08T07:00:00Z", ...o })
const show = (rows: any[]) => {
  get.mockResolvedValue({ data: { data: rows } })
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter initialEntries={["/queue"]}><Routes><Route path="/queue" element={<HandoffQueueTab />} /><Route path="/clinic/consult/:id" element={<div>WORKSPACE</div>} /></Routes></MemoryRouter></QueryClientProvider>)
}
beforeEach(() => { get.mockReset(); post.mockReset(); post.mockResolvedValue({ data: { data: {} } }) })
afterEach(cleanup)

describe("HandoffQueueTab", () => {
  it("shows what the nurse collected, and Review & start accepts then opens the consultation", async () => {
    show([item({})])
    expect(await screen.findByText(/Nurse intake: complaint ✓ · BP 120\/80 · Temp 37.8°C · SpO₂ 96%/)).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Review & start" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/consultations/c1/accept", {}))
    expect(await screen.findByText("WORKSPACE")).toBeTruthy()
  })
  it("a consultation under review is reopened; a finished review goes to Review & sign", async () => {
    show([item({ status: "DOCTOR_REVIEWING" })])
    fireEvent.click(await screen.findByRole("button", { name: "Open consultation" }))
    expect(await screen.findByText("WORKSPACE")).toBeTruthy()
    cleanup(); show([item({ status: "DOCTOR_COMPLETED" })])
    expect(await screen.findByRole("button", { name: "Review & sign" })).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Sign" })).toBeNull()
  })
  it("without an appointment it still accepts in place", async () => {
    show([item({ appointmentId: null })])
    fireEvent.click(await screen.findByRole("button", { name: "Accept" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/consultations/c1/accept", {}))
    expect(screen.queryByText("WORKSPACE")).toBeNull()
  })
  it("says so when nobody is waiting", async () => {
    show([])
    expect(await screen.findByText("Nothing waiting for a doctor.")).toBeTruthy()
  })
})
