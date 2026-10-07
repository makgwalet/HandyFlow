import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a) } }))
vi.mock("./MyDayPanel", () => ({ default: () => null }))
import ClinicDashboard, { dayOf } from "./ClinicDashboard"

const item = (over: object) => ({ id: "x", patientName: "Ann One", practitionerName: "Lee", scheduledAt: "2026-10-07T06:00:00Z",
  durationMinutes: 15, appointmentType: "CONSULTATION", status: "SCHEDULED", ...over })
const SUMMARY = { date: "2026-10-07", todayTotal: 120, awaiting: 40, inProgress: 2, completed: 70, cancelled: 5, noShow: 3,
  totalPatients: 5432, today: [item({ id: "1", patientName: "Ann One" }), item({ id: "2", patientName: "Bob Two", status: "COMPLETED" })],
  next: item({ id: "3", patientName: "Cara Three", scheduledAt: "2026-10-08T06:00:00Z" }) }

const show = (nav = vi.fn()) => ({ nav, ...render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <ClinicDashboard onNavigate={nav} /></QueryClientProvider>) })

beforeEach(() => { get.mockReset()
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("dashboard/summary") ? SUMMARY : [] } })) })
afterEach(cleanup)

describe("dayOf", () => {
  it("uses the South African day, not the UTC day", () => {
    expect(dayOf("2026-10-07T22:30:00Z")).toBe("2026-10-08")
    expect(dayOf("2026-10-07T21:30:00Z")).toBe("2026-10-07")
  })
})

describe("ClinicDashboard", () => {
  it("shows the counts from the server, not the number of rows it received", async () => {
    show()
    expect(await screen.findByText("120")).toBeTruthy()
    expect(screen.getByText("40")).toBeTruthy()
    expect(screen.getByText("70")).toBeTruthy()
    expect(screen.getByText("5432")).toBeTruthy()
    expect(screen.getByText("Ann One")).toBeTruthy()
  })

  it("asks for the summary and no longer pages through appointments", async () => {
    show()
    await screen.findByText("120")
    const urls = get.mock.calls.map(c => String(c[0]))
    expect(urls.some(u => u.includes("/api/v1/clinic/dashboard/summary"))).toBe(true)
    expect(urls.some(u => u.includes("/appointments"))).toBe(false)
  })

  it("shows the date on the next-up card when it is not today", async () => {
    show()
    await screen.findByText("Cara Three")
    expect(screen.getByText("2026-10-08")).toBeTruthy()
  })

  it("sends Awaiting today to the waiting room", async () => {
    const { nav } = show()
    fireEvent.click((await screen.findByText("Awaiting today")).closest("button") ?? screen.getByText("Awaiting today"))
    expect(nav).toHaveBeenCalledWith("waiting-room")
  })

  it("shows dashes while the summary has not loaded", () => {
    get.mockImplementation(() => new Promise(() => {}))
    show()
    expect(screen.getAllByText("—").length).toBeGreaterThanOrEqual(4)
  })
})
