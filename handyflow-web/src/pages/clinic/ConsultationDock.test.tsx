import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter, Route, Routes } from "react-router-dom"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a) } }))
import ConsultationDock from "./ConsultationDock"

const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
  <MemoryRouter initialEntries={["/clinic/dashboard"]}><Routes>
    <Route path="/clinic/dashboard" element={<ConsultationDock />} />
    <Route path="/clinic/consult/:id" element={<div>WORKSPACE</div>} />
  </Routes></MemoryRouter></QueryClientProvider>)
beforeEach(() => get.mockReset())
afterEach(cleanup)

describe("ConsultationDock", () => {
  it("says exactly where a consultation left off and resumes it", async () => {
    get.mockResolvedValue({ data: { data: [{ id: "c1", appointmentId: "a1", patientId: "p1", patientName: "Liam Botha", status: "DRAFT", chiefComplaint: "Cough", history: "3 days", pulseBpm: 80, updatedAt: "2026-10-08T09:00:00Z" }] } })
    show()
    expect(await screen.findByText("Consultation in progress · Liam Botha")).toBeTruthy()
    expect(screen.getByText("Symptoms ✓ · Exam ✓ · Assessment — · Plan —")).toBeTruthy()
    expect(get).toHaveBeenCalledWith("/api/v1/clinic/consultations/drafts", { params: { mine: true } })
    fireEvent.click(screen.getByRole("button", { name: "Resume" }))
    expect(await screen.findByText("WORKSPACE")).toBeTruthy()
  })
  it("shows nothing when there is nothing open, or nothing that can be reopened", async () => {
    get.mockResolvedValue({ data: { data: [{ id: "c2", appointmentId: null, patientId: "p1", status: "DRAFT" }] } })
    const { container } = show()
    await new Promise(r => setTimeout(r, 20))
    expect(container.textContent).toBe("")
  })
})
