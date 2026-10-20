import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import FleetListTab from "./FleetListTab"
import { documentBadge, fleetRows, fuelBar, serviceBar, viewFleet, type ListVehicle } from "./fleetBoards.logic"
import type { FuelRow } from "./fuelAnalytics.logic"

const get = vi.fn()
type Args = unknown[]
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))
vi.mock("./VehicleDetail", () => ({ default: ({ vehicleId }: { vehicleId: string }) => <div data-testid="detail">detail of {vehicleId}</div> }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const TODAY = new Date(2026, 9, 20, 22, 0)
const on = (n: number) => { const d = new Date(2026, 9, 20 + n); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}` }
const v = (o: Partial<ListVehicle> & { id: string; registration: string }): ListVehicle => ({ make: "Toyota", model: "Hilux", year: 2022, status: "AVAILABLE", assignedDriverName: null, currentOdometer: 50000, lastServiceKm: 45000, serviceIntervalKm: 15000, licenceDiscExpiry: on(200), roadworthyExpiry: on(200), insuranceExpiry: on(200), ...o })
const fuel = (vehicleId: string, l100: number | null, dev: number | null): FuelRow => ({ vehicleId, registration: vehicleId, summary: { fillups: 3, totalLitres: 100, totalSpend: 2000, measuredKm: 900, litresPer100Km: l100, kmPerLitre: null, costPerKm: null, flaggedSegments: 0, indicators: 0 }, latestDeviationPercent: dev })
const vehicles = [
  v({ id: "a", registration: "AAA1", assignedDriverName: "John Mokoena" }),
  v({ id: "b", registration: "BBB2", make: "Isuzu", model: "NPR", status: "MAINTENANCE", currentOdometer: 62000, lastServiceKm: 45000, licenceDiscExpiry: on(-3) }),
  v({ id: "c", registration: "CCC3", make: "VW", model: "Polo", serviceIntervalKm: null, insuranceExpiry: on(10) }),
  v({ id: "d", registration: "DDD4", licenceDiscExpiry: null, roadworthyExpiry: null, insuranceExpiry: null, currentOdometer: 48000 }),
]
const fuelRows = [fuel("a", 10, 5), fuel("b", 20, 30), fuel("d", null, null)]
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><FleetListTab /></QueryClientProvider>)
beforeEach(() => { get.mockReset(); get.mockImplementation((url: string) => (url.includes("/fuel-analytics/vehicles") ? ok(fuelRows) : url.includes("/vehicles") ? ok({ content: vehicles }) : ok([]))) })
afterEach(cleanup)

describe("fleet list rules", () => {
  it("scales the fuel bar to the thirstiest vehicle and colours it by distance from usual", () => {
    expect(fuelBar(10, 5, 20)).toEqual({ fraction: 0.5, tone: "ok", text: "10.0 L/100 km · 5% thirstier than usual" }); expect(fuelBar(20, 30, 20).tone).toBe("bad"); expect(fuelBar(15, 15, 20).tone).toBe("warn")
    expect(fuelBar(null, null, 20)).toEqual({ fraction: 0, tone: "none", text: "No fuel data" }); expect(fuelBar(12, null, 0).fraction).toBe(0); expect(fuelBar(12, null, 24).text).toBe("12.0 L/100 km")
  })
  it("shows the service bar as distance since service over the interval", () => {
    expect(serviceBar({ currentOdometer: 50000, lastServiceKm: 45000, serviceIntervalKm: 15000 })).toMatchObject({ tone: "ok", text: expect.stringMatching(/^10.000 km to go$/) })
    expect(serviceBar({ currentOdometer: 58000, lastServiceKm: 45000, serviceIntervalKm: 15000 }).tone).toBe("warn")
    expect(serviceBar({ currentOdometer: 62000, lastServiceKm: 45000, serviceIntervalKm: 15000 })).toMatchObject({ tone: "bad", text: expect.stringMatching(/^Overdue by 2.000 km$/) })
    expect(serviceBar({ currentOdometer: 1, lastServiceKm: 0, serviceIntervalKm: null })).toMatchObject({ tone: "none", text: "No service interval set" })
  })
  it("badges documents: expired beats expiring beats clear, and no dates is its own state", () => {
    const d = (o: object) => documentBadge({ licenceDiscExpiry: on(200), roadworthyExpiry: on(200), insuranceExpiry: on(200), ...o }, TODAY)
    expect(d({ licenceDiscExpiry: on(-1), insuranceExpiry: on(5) })).toMatchObject({ tone: "bad", text: "1 expired", expiring: 1 }); expect(d({ roadworthyExpiry: on(0) })).toMatchObject({ tone: "warn", text: "1 expiring" })
    expect(d({})).toMatchObject({ tone: "ok", text: "Clear" }); expect(d({ licenceDiscExpiry: null, roadworthyExpiry: null, insuranceExpiry: null })).toMatchObject({ tone: "none", text: "No dates" })
  })
  it("searches every word across registration, make, model and driver, and sorts with gaps last", () => {
    const rows = fleetRows(vehicles, fuelRows, TODAY)
    expect(viewFleet(rows, "isuzu npr", "ALL", "registration").map(r => r.vehicle.id)).toEqual(["b"]); expect(viewFleet(rows, "mokoena", "ALL", "registration").map(r => r.vehicle.id)).toEqual(["a"])
    expect(viewFleet(rows, "", "MAINTENANCE", "registration").map(r => r.vehicle.id)).toEqual(["b"]); expect(viewFleet(rows, "toyota hilux", "ALL", "registration").map(r => r.vehicle.id)).toEqual(["a", "d"])
    expect(viewFleet(rows, "", "ALL", "fuel").map(r => r.vehicle.id)).toEqual(["b", "a", "c", "d"]); expect(viewFleet(rows, "", "ALL", "service").map(r => r.vehicle.id)).toEqual(["b", "a", "d", "c"])
    expect(viewFleet(rows, "", "ALL", "documents").map(r => r.vehicle.id)).toEqual(["b", "c", "a", "d"])
  })
})

describe("FleetListTab", () => {
  it("shows a fuel bar and a service bar for every vehicle", async () => {
    wrap(); const row = await screen.findByLabelText("BBB2")
    expect(within(row).getByLabelText(/^Fuel: 20.0 L\/100 km · 30% thirstier/)).toBeTruthy(); expect(within(row).getByLabelText(/^Service: Overdue by 2.000 km/)).toBeTruthy(); expect(row.textContent).toContain("1 expired")
    expect(within(row).getByLabelText(/^Fuel:/).querySelector("[data-fraction]")?.getAttribute("data-fraction")).toBe("100"); expect(within(screen.getByLabelText("AAA1")).getByLabelText(/^Fuel:/).querySelector("[data-fraction]")?.getAttribute("data-fraction")).toBe("50")
    expect(within(screen.getByLabelText("DDD4")).getByLabelText(/^Fuel: No fuel data/)).toBeTruthy(); expect(screen.getByLabelText("Result count").textContent).toBe("4 of 4 vehicles")
  })
  it("narrows by search and status and says when nothing matches", async () => {
    wrap(); await screen.findByLabelText("AAA1"); fireEvent.change(screen.getByLabelText("Search fleet"), { target: { value: "polo" } })
    expect(screen.queryByLabelText("AAA1")).toBeNull(); expect(screen.getByLabelText("CCC3")).toBeTruthy(); expect(screen.getByLabelText("Result count").textContent).toBe("1 of 4 vehicles")
    fireEvent.change(screen.getByLabelText("Status"), { target: { value: "BREAKDOWN" } }); expect(screen.getByText("No vehicles match.")).toBeTruthy()
  })
  it("reorders and opens a vehicle's panel under its row", async () => {
    wrap(); await screen.findByLabelText("AAA1"); fireEvent.change(screen.getByLabelText("Sort fleet"), { target: { value: "fuel" } })
    expect(screen.getAllByRole("row").slice(1).map(r => r.getAttribute("aria-label"))).toEqual(["BBB2", "AAA1", "CCC3", "DDD4"])
    fireEvent.click(screen.getByLabelText("CCC3")); expect((await screen.findByTestId("detail")).textContent).toBe("detail of c"); fireEvent.click(screen.getByLabelText("CCC3")); expect(screen.queryByTestId("detail")).toBeNull()
  })
  it("keeps the list when fuel figures fail, and says so", async () => {
    get.mockImplementation((url: string) => (url.includes("/fuel-analytics/vehicles") ? Promise.reject(new Error("x")) : url.includes("/vehicles") ? ok({ content: vehicles }) : ok([])))
    wrap(); expect(await screen.findByLabelText("AAA1")).toBeTruthy(); expect((await screen.findAllByRole("alert"))[0].textContent).toMatch(/Fuel figures couldn't be loaded/); expect(within(screen.getByLabelText("BBB2")).getByLabelText(/^Fuel: No fuel data/)).toBeTruthy()
  })
})
