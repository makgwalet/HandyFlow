import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a) } }))
import TimelineTab, { groupByDay } from "./TimelineTab"

const ev = (kind: string, id: string, at: string, title: string, status: string | null = null, detail: string | null = null) =>
  ({ kind, id, at, title, status, detail })
const events = [
  ev("CONSULTATION", "c1", "2026-10-05T09:00:00Z", "Cough", "SIGNED", "Dr Mokoena"),
  ev("PRESCRIPTION", "r1", "2026-10-05T09:30:00Z", "Amoxicillin 500mg"),
  ev("APPOINTMENT", "a1", "2026-09-01T08:00:00Z", "Follow-up", "COMPLETED"),
]
const show = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <TimelineTab patientId="p1" />
  </QueryClientProvider>)

beforeEach(() => { get.mockReset(); get.mockResolvedValue({ data: { data: events } }) })
afterEach(cleanup)

describe("TimelineTab", () => {
  it("lists events and offers a filter only for kinds present", async () => {
    show()
    expect(await screen.findByText("Cough")).toBeTruthy()
    expect(screen.getByText("Amoxicillin 500mg")).toBeTruthy()
    expect(screen.getByText("Follow-up")).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Lab results" })).toBeNull()
    expect(screen.queryByRole("button", { name: "Claims" })).toBeNull()
  })
  it("hides a kind when its chip is turned off, and says so when none are left", async () => {
    show()
    await screen.findByText("Cough")
    fireEvent.click(screen.getByRole("button", { name: "Prescriptions" }))
    expect(screen.queryByText("Amoxicillin 500mg")).toBeNull()
    fireEvent.click(screen.getByRole("button", { name: "Consultations" }))
    fireEvent.click(screen.getByRole("button", { name: "Appointments" }))
    expect(screen.getByText(/Every kind of event is hidden/)).toBeTruthy()
  })
  it("shows billing chips only when the server sent billing events", async () => {
    get.mockResolvedValue({ data: { data: [...events, ev("CLAIM", "k1", "2026-10-05T10:00:00Z", "Claim CL-1", "SUBMITTED")] } })
    show()
    expect(await screen.findByText("Claim CL-1")).toBeTruthy()
    expect(screen.getByRole("button", { name: "Claims" })).toBeTruthy()
  })
  it("says so when there is nothing on record", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    show()
    expect(await screen.findByText(/Nothing on record/)).toBeTruthy()
  })
})

describe("groupByDay", () => {
  it("groups consecutive events by calendar day, keeping order", () => {
    const g = groupByDay(events)
    expect(g.map(x => x.events.length)).toEqual([2, 1])
  })
  it("shows the visit summary and who was involved on a consultation", async () => {
    get.mockResolvedValue({ data: { data: [{ ...events[0], summary: ["Reason: Cough", "Diagnosis: Acute bronchitis (J20.9)", "1 medicine prescribed"],
      people: ["Prepared by Sister Zodwa Nkosi", "Signed by Dr Andile Dlamini"] }, events[1]] } })
    show()
    expect(await screen.findByText("Diagnosis: Acute bronchitis (J20.9)")).toBeTruthy()
    expect(screen.getByText(/Prepared by Sister Zodwa Nkosi · Signed by Dr Andile Dlamini/)).toBeTruthy()
    expect(screen.getAllByText("People:").length).toBe(1)
  })
})
