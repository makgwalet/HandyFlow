import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import RouteCostsPanel from "./RouteCostsPanel"

const get = vi.fn(); const post = vi.fn()
let canRecord = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => canRecord }))

const row = (o: any = {}) => ({ routeId: "r1", routeDate: "2026-11-10", label: "Morning run", tankerRegistration: "GP 12 AB", driverName: "Moses Sithole", state: "COMPLETED", stops: 2, deliveredCount: 2, litresDelivered: 8000,
  estimateVehicle: 1000, estimateDriver: 600, estimateTotal: 1600, actualVehicle: 1300, actualDriver: null, actualTolls: 250, actualOther: null, actualTotal: 1550, variance: 300, variancePct: 30, actualCostPerLitre: 0.19375, reconciled: true, flags: [], ...o })
const rollup = (o: any = {}) => ({ key: "k", label: "GP 12 AB", routes: 1, reconciledRoutes: 1, litres: 8000, estimate: 1000, comparableActual: 1300, variance: 300, actualTotal: 1550, actualCostPerLitre: 0.19, ...o })
let report: any
let entries: any[]

function mount(days = 30) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><RouteCostsPanel days={days} /></QueryClientProvider>)
}

describe("RouteCostsPanel", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); canRecord = true
    report = { from: "2026-10-14", to: "2026-11-12", ratesSet: true, unreconciledRoutes: 0, routes: [row()], byTanker: [rollup()], byDepot: [rollup({ key: "d", label: "North depot", reconciledRoutes: 0 })] }
    entries = [{ id: "e1", routeId: "r1", category: "VEHICLE", amount: 1300, quantity: 250, unit: "km", note: "odometer", status: "ACTIVE", voidReason: null, recordedAt: "2026-11-10T10:00:00Z" }]
    get.mockImplementation(async (u: string) => ({ data: { data: u.endsWith("/entries") ? entries : report } }))
    post.mockResolvedValue({ data: { data: {} } })
  })
  afterEach(() => cleanup())

  it("asks for the period ending today and shows estimate, actual and the difference for each route", async () => {
    mount()
    await waitFor(() => screen.getByText(/Morning run/))
    expect(get.mock.calls[0][0]).toBe("/api/v1/fuel/cost-actuals")
    expect(get.mock.calls[0][1].params.to).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(screen.getAllByText(/^\+.*300.*\(\+30\.0%\)$/).length).toBeGreaterThan(0)
    expect(screen.getAllByText("North depot").length).toBeGreaterThan(0)
    expect(screen.getByText(/1 not reconciled/)).toBeTruthy()
  })

  it("shows what is missing instead of inventing a figure", async () => {
    report = { ...report, unreconciledRoutes: 1, routes: [row({ estimateTotal: null, actualTotal: null, variance: null, variancePct: null, actualCostPerLitre: null, reconciled: false, flags: ["RATES_NOT_SET", "NO_ACTUALS"] })] }
    mount()
    await waitFor(() => screen.getByText(/Cost rates not set in Settings/))
    expect(screen.getByText(/No actual costs recorded/)).toBeTruthy()
    expect(screen.getByText(/1 route has no actual vehicle or driver cost/)).toBeTruthy()
  })

  it("says so when there are no routes, and when the report cannot load", async () => {
    report = { ...report, routes: [] }; mount()
    await waitFor(() => screen.getByText(/No routes with deliveries/))
    cleanup(); get.mockRejectedValue(new Error("boom")); mount()
    await waitFor(() => screen.getByText(/Could not load route costs/))
  })

  it("records a cost with validation, then sends the amount and type", async () => {
    mount()
    await waitFor(() => screen.getByText("Costs"))
    fireEvent.click(screen.getByText("Costs"))
    await waitFor(() => screen.getByText(/odometer/))
    fireEvent.click(screen.getByText("Record cost"))
    expect(screen.getByRole("alert").textContent).toMatch(/type/)
    fireEvent.change(screen.getByLabelText("Cost type"), { target: { value: "OTHER" } })
    fireEvent.change(screen.getByLabelText("Cost amount"), { target: { value: "75" } })
    fireEvent.click(screen.getByText("Record cost"))
    expect(screen.getByRole("alert").textContent).toMatch(/what the cost was for/)
    fireEvent.change(screen.getByLabelText("Cost note"), { target: { value: "Ferry" } })
    fireEvent.click(screen.getByText("Record cost"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/cost-actuals/routes/r1/entries", { category: "OTHER", amount: 75, quantity: null, unit: null, note: "Ferry" }))
  })

  it("voids an entry only with a reason", async () => {
    mount()
    await waitFor(() => screen.getByText("Costs"))
    fireEvent.click(screen.getByText("Costs"))
    await waitFor(() => screen.getByLabelText("Void Vehicle 1300"))
    fireEvent.click(screen.getByLabelText("Void Vehicle 1300"))
    fireEvent.click(screen.getByText("Void entry"))
    expect(screen.getByRole("alert").textContent).toMatch(/reason/)
    fireEvent.change(screen.getByLabelText("Void reason"), { target: { value: "Typed wrongly" } })
    fireEvent.click(screen.getByText("Void entry"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/cost-actuals/entries/e1/void", { reason: "Typed wrongly" }))
  })

  it("without the manage permission the figures show but nothing can be recorded or voided", async () => {
    canRecord = false; mount()
    await waitFor(() => screen.getByText(/Morning run/))
    expect(screen.queryByText("Costs")).toBeNull()
  })
})
