import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a) } }))
import NextPatientPanel from "./NextPatientPanel"

const summary = { consultationId: "c1", diagnosis: "Acute URTI", rxCount: 2, followUpDays: "7", durationMinutes: 12 }
const appt = (id: string, name: string, status: string, at: string, doc = "d1") => ({ id, patientId: "p" + id, patientName: name, practitionerId: doc, scheduledAt: `2026-10-08T${at}:00Z`, status, reason: "Review" })
const show = (props: Partial<React.ComponentProps<typeof NextPatientPanel>> = {}) => {
  const fns = { onOpenNext: vi.fn(), onToday: vi.fn(), onOpenFile: vi.fn() }
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <NextPatientPanel patientName="Liam Botha" summary={summary} appointmentId="a0" practitionerId="d1" {...fns} {...props} /></QueryClientProvider>)
  return fns
}
beforeEach(() => get.mockReset())
afterEach(cleanup)

describe("NextPatientPanel", () => {
  it("shows what the visit produced and offers the next waiting patient", async () => {
    get.mockResolvedValue({ data: { data: [appt("a0", "Liam Botha", "COMPLETED", "08:00"), appt("a1", "Jane Dlamini", "CHECKED_IN", "08:30"), appt("a2", "Sipho Nkosi", "TRIAGED", "08:45")] } })
    const f = show()
    expect(screen.getByText("Consultation signed")).toBeTruthy()
    expect(screen.getByText("Acute URTI")).toBeTruthy()
    expect(screen.getByText("in 7 days")).toBeTruthy()
    expect(await screen.findByText("Sipho Nkosi")).toBeTruthy()           // triaged first
    expect(screen.getByText(/1 more waiting after them/)).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Open next patient" }))
    expect(f.onOpenNext).toHaveBeenCalledWith(expect.objectContaining({ id: "a2" }))
  })
  it("says so when nobody is waiting, naming who is expected", async () => {
    get.mockResolvedValue({ data: { data: [appt("a3", "Thandi Mokoena", "CONFIRMED", "09:30")] } })
    show()
    expect(await screen.findByText(/Nobody is waiting\. Next expected: Thandi Mokoena/)).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Open next patient" })).toBeNull()
  })
  it("does not offer the patient just seen, and goes back to Today", async () => {
    get.mockResolvedValue({ data: { data: [appt("a0", "Liam Botha", "CHECKED_IN", "08:00")] } })
    const f = show()
    expect(await screen.findByText(/Nobody is waiting/)).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Back to Today" }))
    expect(f.onToday).toHaveBeenCalled()
  })
  it("says no follow-up was set when there is none", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    show({ summary: { ...summary, followUpDays: "", rxCount: 0 } })
    expect(await screen.findByText("none set")).toBeTruthy()
    expect(screen.queryByRole("button", { name: /Prescription \(PDF\)/ })).toBeNull()
  })
})
