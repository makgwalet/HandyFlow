import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() } }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: ["EARTHMOVING_READ"] } }) }))

import EarthDashboard from "./EarthDashboard"
import { statusCounts, upcomingPlans, remainingText } from "./shared/dashboard"

afterEach(cleanup)
const asset = (id: string, status: string, over: Record<string, unknown> = {}) => ({ id, name: "Machine " + id, fleetNumber: "F" + id, assetType: "DOZER", status, dailyRate: 100, dueForService: false, ...over })
const plan = (id: string, state: string, remaining: number, over: Record<string, unknown> = {}) => ({ id, assetId: "1", name: "Plan " + id, state, remaining, unit: "hrs", active: true, ...over })
const page = (content: unknown[]) => ({ data: { data: { content, last: true } } })
let util = { from: "2026-09-16", to: "2026-10-15", machines: 2, totalHours: 42.5, perDay: [0, 8, 0, 12.5] }
let plans: unknown[] = []

beforeEach(() => {
  vi.clearAllMocks()
  plans = []
  util = { from: "2026-09-16", to: "2026-10-15", machines: 2, totalHours: 42.5, perDay: [0, 8, 0, 12.5] }
  get.mockImplementation((url: string) => {
    if (url.includes("/reports/utilisation")) return Promise.resolve({ data: { data: util } })
    if (url.includes("/maintenance-plans")) return Promise.resolve({ data: { data: plans } })
    if (url.includes("/incidents")) return Promise.resolve(page([]))
    if (url.includes("/assets")) return Promise.resolve(page([asset("1", "AVAILABLE"), asset("2", "DEPLOYED"), asset("3", "DEPLOYED"), asset("4", "RETIRED")]))
    return Promise.resolve({ data: { data: [] } })
  })
})
const mount = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><EarthDashboard onNavigate={() => {}} /></QueryClientProvider>)

describe("dashboard helpers", () => {
  it("counts machines per status and leaves retired ones out", () => {
    expect(statusCounts([{ status: "DEPLOYED" }, { status: "DEPLOYED" }, { status: "AVAILABLE" }, { status: "RETIRED" }]))
      .toEqual([{ status: "AVAILABLE", count: 1 }, { status: "DEPLOYED", count: 2 }])
  })
  it("lists the most urgent plans first and skips fine or switched-off ones", () => {
    const list = upcomingPlans([plan("a", "OK", 200), plan("b", "DUE_SOON", 20), plan("c", "OVERDUE", -50), plan("d", "DUE", 0), plan("e", "OVERDUE", -10, { active: false }), plan("f", "OVERDUE", -5)] as never)
    expect(list.map(p => p.id)).toEqual(["c", "f", "d", "b"])
  })
  it("says how far a plan is from due", () => {
    expect(remainingText({ remaining: 12.34, unit: "hrs" })).toBe("12.3 hrs left")
    expect(remainingText({ remaining: -3, unit: "days" })).toBe("3 days overdue")
  })
})

describe("EarthDashboard panels", () => {
  it("shows the status mix, hours worked and what is due", async () => {
    plans = [plan("p1", "OVERDUE", -20, { name: "250 hr service" })]
    mount()
    const status = await screen.findByLabelText("Fleet status")
    await waitFor(() => expect(within(status).getByText("Deployed")).toBeTruthy())
    expect(within(status).queryByText("Retired")).toBeNull()
    expect(await screen.findByText("42.5 hrs")).toBeTruthy()
    expect(screen.getByText(/on 2 hour-metered machines/)).toBeTruthy()
    expect(await screen.findByText(/250 hr service/)).toBeTruthy()
    expect(screen.getByText(/Overdue · 20 hrs overdue/)).toBeTruthy()
  })
  it("asks for the period chosen", async () => {
    mount()
    fireEvent.change(await screen.findByLabelText("Chart period"), { target: { value: "90" } })
    await waitFor(() => expect(get.mock.calls.some(c => String(c[0]).includes("utilisation?days=90"))).toBe(true))
  })
  it("says so when hours cannot be loaded, and when nothing is due", async () => {
    get.mockImplementation((url: string) => url.includes("/reports/utilisation") ? Promise.reject(new Error("x")) : Promise.resolve(url.includes("/assets") || url.includes("/incidents") ? page([]) : { data: { data: [] } }))
    mount()
    expect(await screen.findByText("Hours worked could not be loaded")).toBeTruthy()
    expect(screen.getByText("Nothing due soon")).toBeTruthy()
  })
})
