import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ReportsTab from "./ReportsTab"
import FuelAnalyticsTab from "./FuelAnalyticsTab"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const costRow = (i: number) => ({ vehicleId: `v${i}`, registration: `REG${String(i).padStart(3, "0")}`, make: "Toyota", model: "Hilux", totalServiceCost: 100, totalFuelCost: 200, totalCost: 300, totalKm: 1000, costPerKm: 0.3 })
const fuelRow = (i: number) => ({ vehicleId: `v${i}`, registration: `FUEL${String(i).padStart(3, "0")}`, make: "Isuzu", model: "D-Max", latestDeviationPercent: null, summary: { fillups: 4, totalLitres: 200, totalSpend: 4000, measuredKm: 2000, litresPer100Km: 10, kmPerLitre: 10, costPerKm: 2, flaggedSegments: 0, indicators: 0 } })
beforeEach(() => { get.mockReset() })
afterEach(cleanup)

describe("reports at scale", () => {
  it("pages and searches a long report, and reads every page of completed work orders", async () => {
    get.mockImplementation((url: string) => url.includes("/cost-summary") ? ok(Array.from({ length: 60 }, (_, i) => costRow(i))) : ok([]))
    wrap(<ReportsTab />)
    await waitFor(() => expect(screen.getByLabelText("Showing").textContent).toBe("Showing 1–25 of 60"))
    fireEvent.change(screen.getByLabelText("Search report"), { target: { value: "reg042" } }); expect(screen.getByLabelText("Showing").textContent).toBe("Showing 1–1 of 1")
    fireEvent.change(screen.getByLabelText("Search report"), { target: { value: "zzz" } }); expect(screen.getByText("No rows match.")).toBeTruthy()
  })
  it("asks for the maintenance report page by page rather than a fixed 500", async () => {
    get.mockImplementation(() => ok({ content: [], totalPages: 1 }))
    wrap(<ReportsTab />); fireEvent.click(await screen.findByRole("button", { name: /Maintenance cost/ }))
    await waitFor(() => expect(get.mock.calls.some(c => String(c[0]).includes("work-orders?status=COMPLETED&page=0&size=200"))).toBe(true))
    expect(get.mock.calls.some(c => String(c[0]).includes("size=500"))).toBe(false)
  })
})
describe("fuel analytics at scale", () => {
  it("pages and searches the vehicle board", async () => {
    get.mockImplementation((url: string) => url.includes("/fuel-analytics/vehicles") ? ok(Array.from({ length: 40 }, (_, i) => fuelRow(i))) : ok([]))
    wrap(<FuelAnalyticsTab />)
    await waitFor(() => expect(screen.getByLabelText("Showing").textContent).toBe("Showing 1–25 of 40"))
    fireEvent.change(screen.getByLabelText("Search vehicles"), { target: { value: "fuel031" } }); expect(screen.getByLabelText("Showing").textContent).toBe("Showing 1–1 of 1")
  })
})

describe("fuel analytics dashboard", () => {
  const station = (name: string, type: string, spend: number) => ({ station: name, fuelType: type, fillups: 3, litres: 100, totalSpend: spend, avgPricePerLitre: 21, premiumPercent: null })
  it("shows the trend, station shares and top consumers, and a vehicle filter narrows the tiles", async () => {
    const rows = [fuelRow(1), { ...fuelRow(2), summary: { ...fuelRow(2).summary, totalSpend: 1000, totalLitres: 50, litresPer100Km: 14 } }]
    get.mockImplementation((url: string) => url.includes("/fuel-analytics/vehicles") ? ok(rows)
      : url.includes("/fuel-analytics/stations") ? ok([station("Engen", "DIESEL", 6000), station("Engen", "PETROL", 2000), station("Shell", "DIESEL", 2000)]) : ok([]))
    wrap(<FuelAnalyticsTab />)
    expect(await screen.findByLabelText("Engen: 80%")).toBeTruthy(); expect(screen.getByLabelText("Shell: 20%")).toBeTruthy()
    expect(screen.getByLabelText("Fuel cost trend")).toBeTruthy(); expect(screen.getByLabelText("Top consumers").textContent).toContain("FUEL002")
    await waitFor(() => expect(screen.getByLabelText("Total spend").textContent).toMatch(/5\s000/))
    fireEvent.change(screen.getByLabelText("Vehicle"), { target: { value: "v2" } })
    expect(screen.getByLabelText("Total spend").textContent).toMatch(/1\s000/); expect(screen.queryByRole("row", { name: "FUEL001" })).toBeNull()
  })
})
