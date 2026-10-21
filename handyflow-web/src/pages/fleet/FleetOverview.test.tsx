import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import FleetDashboard from "./FleetDashboard"
import VehiclesTab from "./VehiclesTab"
import { StatusDonut, CostliestVehicles } from "./FleetCharts"
import { costBars, donutSlices, filterVehicles, statusCounts } from "./fleet.logic"

const get = vi.fn()
type Args = unknown[]
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), post: vi.fn(), patch: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const v = (o: object) => ({ id: "x", registration: "ABC123MP", make: "Toyota", model: "Hilux", year: 2020, colour: null, vehicleType: "BAKKIE", status: "AVAILABLE", fuelType: "DIESEL",
  currentOdometer: 1000, lastServiceKm: 0, serviceIntervalKm: 10000, dueForService: false, licenceExpiringSoon: false, roadworthyExpiringSoon: false, dailyRate: null, assignedDriverName: null, assignedDriverId: null, notes: null, createdAt: "2026-01-01", ...o })
const fleet = [v({ id: "1", registration: "ABC123MP", assignedDriverName: "John Mokoena", engineNumber: "2GD-1234" }), v({ id: "2", registration: "XYZ999GP", make: "Isuzu", model: "D-Max", status: "ON_TRIP" }), v({ id: "3", registration: "KLM555NW", status: "BREAKDOWN" })]
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

beforeEach(() => {
  get.mockReset()
  get.mockImplementation((url: string) => url.includes("/accidents") ? ok({ content: [{ registration: "KLM555NW", status: "IN_REPAIR", vehicleDrivable: false }, { registration: "ABC123MP", status: "CLOSED", vehicleDrivable: false }] })
    : url.includes("/cost-summary") ? ok([{ vehicleId: "1", registration: "ABC123MP", make: "", model: "", totalServiceCost: 0, totalFuelCost: 0, totalCost: 5000, totalKm: 100, costPerKm: 50 }])
    : url.includes("/fuel-analytics") ? ok([])
    : url.includes("/vehicles") ? ok({ content: fleet }) : ok({ content: [] }))
})
afterEach(cleanup)

describe("fleet logic", () => {
  it("counts every status including empty ones", () => {
    expect(statusCounts(fleet)).toEqual([{ status: "AVAILABLE", count: 1 }, { status: "ON_TRIP", count: 1 }, { status: "MAINTENANCE", count: 0 }, { status: "BREAKDOWN", count: 1 }, { status: "RETIRED", count: 0 }])
  })
  it("draws slices that add up to the whole circle and skips empty statuses", () => {
    const s = donutSlices(statusCounts(fleet))
    expect(s.map(x => x.status)).toEqual(["AVAILABLE", "ON_TRIP", "BREAKDOWN"])
    expect(s.reduce((t, x) => t + x.length, 0)).toBeCloseTo(1); expect(s[1].start).toBeCloseTo(1 / 3)
    expect(donutSlices(statusCounts([]))).toEqual([])
  })
  it("searches every word across registration, make, driver and engine number", () => {
    expect(filterVehicles(fleet, "isuzu", "ALL").map(x => x.id)).toEqual(["2"])
    expect(filterVehicles(fleet, "john hilux", "ALL").map(x => x.id)).toEqual(["1"])
    expect(filterVehicles(fleet, "2gd", "ALL").map(x => x.id)).toEqual(["1"])
    expect(filterVehicles(fleet, "", "BREAKDOWN").map(x => x.id)).toEqual(["3"])
    expect(filterVehicles(fleet, "isuzu", "BREAKDOWN")).toEqual([])
  })
  it("ranks costs as a share of the largest and ignores zero cost", () => {
    const b = costBars([{ registration: "A", totalCost: 100 }, { registration: "B", totalCost: 400 }, { registration: "C", totalCost: 0 }])
    expect(b.map(x => x.registration)).toEqual(["B", "A"]); expect(b.map(x => x.share)).toEqual([1, 0.25])
  })
})

describe("charts", () => {
  it("renders a slice per used status and the total", () => {
    wrap(<StatusDonut vehicles={fleet} />)
    expect(screen.getByLabelText("Fleet status: 3 vehicles")).toBeTruthy(); expect(screen.getByTestId("slice-ON_TRIP")).toBeTruthy(); expect(screen.queryByTestId("slice-RETIRED")).toBeNull()
  })
  it("says so when no costs are recorded", () => {
    wrap(<CostliestVehicles rows={[]} />); expect(screen.getByText(/No service or fuel costs/)).toBeTruthy()
  })
})

describe("screens", () => {
  it("dashboard names only vehicles with an open case that cannot be driven", async () => {
    wrap(<FleetDashboard onNavigate={() => {}} />)
    const banner = await screen.findByRole("alert")
    expect(banner.textContent).toContain("1 vehicle cannot be driven after an accident: KLM555NW")
    expect(banner.textContent).not.toContain("ABC123MP")
  })
  it("fleet list search narrows the table", async () => {
    wrap(<VehiclesTab />)
    await screen.findByText("XYZ999GP")
    fireEvent.change(screen.getByLabelText("Search fleet"), { target: { value: "isuzu" } })
    expect(screen.queryByText("ABC123MP")).toBeNull(); expect(screen.getByText("XYZ999GP")).toBeTruthy()
  })
})
