import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import FuelEnergyBoard from "./FuelEnergyBoard"
import { cheapestStations, fleetCostPerKm, monthRanges, spendBars, thirstiestBars, topFuelAlerts, type StationRow } from "./fleetBoards.logic"
import type { FuelRow } from "./fuelAnalytics.logic"

const get = vi.fn()
type Args = unknown[]
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a) } }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const fr = (id: string, o: { spend: number; litres: number; km: number; l100: number | null; cpk: number | null; dev?: number | null; flagged?: number }): FuelRow => ({ vehicleId: id, registration: id, summary: { fillups: 4, totalLitres: o.litres, totalSpend: o.spend, measuredKm: o.km, litresPer100Km: o.l100, kmPerLitre: null, costPerKm: o.cpk, flaggedSegments: o.flagged ?? 0, indicators: 0 }, latestDeviationPercent: o.dev ?? null })
const rows = [fr("AAA1", { spend: 3000, litres: 150, km: 1000, l100: 15, cpk: 3, dev: 30, flagged: 1 }), fr("BBB2", { spend: 1000, litres: 60, km: 1000, l100: 6, cpk: 1, dev: 2 }), fr("CCC3", { spend: 0, litres: 0, km: 0, l100: null, cpk: null }), fr("DDD4", { spend: 500, litres: 30, km: 0, l100: null, cpk: null })]
const stations: StationRow[] = [{ station: "Engen Midrand", fuelType: "DIESEL", fillups: 5, litres: 300, totalSpend: 6000, avgPricePerLitre: 20.5, premiumPercent: -3 }, { station: "Shell Sandton", fuelType: "DIESEL", fillups: 2, litres: 100, totalSpend: 2200, avgPricePerLitre: 22, premiumPercent: 4 }, { station: "BP Rosebank", fuelType: "DIESEL", fillups: 1, litres: 50, totalSpend: 1000, avgPricePerLitre: 20.5, premiumPercent: -3 }]
const alerts = [{ kind: "INDICATOR", type: "OVERFILL", severity: "WARNING", registration: "BBB2", date: "2026-10-08", message: "82 L into a 70 L tank" }, { kind: "SEGMENT", type: "HIGH", severity: "ALERT", registration: "AAA1", date: "2026-10-02", message: "30% thirstier than usual" }, { kind: "INDICATOR", type: "OVERFILL", severity: "ALERT", registration: "CCC3", date: "2026-10-12", message: "Odometer went backwards" }]
const wrap = (go?: (s: string) => void) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><FuelEnergyBoard onNavigate={go} /></QueryClientProvider>)
beforeEach(() => { get.mockReset(); get.mockImplementation((url: string) => (url.includes("/fuel-analytics/alerts") ? ok(alerts) : url.includes("/fuel-analytics/stations") ? ok(stations) : url.includes("/fuel-analytics/vehicles") ? ok(rows) : ok([]))) })
afterEach(cleanup)

describe("fuel board rules", () => {
  it("builds six calendar months ending this month, which stops at today", () => {
    const m = monthRanges(new Date(2026, 9, 20), 6); expect(m.map(x => x.key)).toEqual(["2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10"])
    expect(m[0]).toMatchObject({ from: "2026-05-01", to: "2026-05-31" }); expect(m[4]).toMatchObject({ from: "2026-09-01", to: "2026-09-30" }); expect(m[5]).toMatchObject({ from: "2026-10-01", to: "2026-10-20" })
    expect(monthRanges(new Date(2026, 1, 28), 2).map(x => x.to)).toEqual(["2026-01-31", "2026-02-28"])
  })
  it("weights fleet cost per km by distance measured, ignoring vehicles without one", () => { expect(fleetCostPerKm(rows)).toBe(2); expect(fleetCostPerKm([rows[2]])).toBeNull() })
  it("ranks the biggest spenders and the thirstiest, leaving out vehicles with nothing to show", () => {
    expect(spendBars(rows).map(b => [b.registration, b.fraction])).toEqual([["AAA1", 1], ["DDD4", 500 / 3000], ["BBB2", 1000 / 3000]].sort((a, b) => (b[1] as number) - (a[1] as number))); expect(spendBars(rows, 1).length).toBe(1)
    const th = thirstiestBars(rows); expect(th.map(t => t.registration)).toEqual(["AAA1", "BBB2"]); expect(th[0].bar).toMatchObject({ fraction: 1, tone: "bad" }); expect(th[1].bar.fraction).toBeCloseTo(0.4)
  })
  it("lists the cheapest stations, ties by name, and the most serious warnings first", () => {
    expect(cheapestStations(stations).map(s => s.station)).toEqual(["BP Rosebank", "Engen Midrand", "Shell Sandton"]); expect(cheapestStations(stations, 1).length).toBe(1)
    expect(topFuelAlerts(alerts).map(a => a.registration)).toEqual(["CCC3", "AAA1", "BBB2"]); expect(topFuelAlerts(alerts, 2).length).toBe(2)
  })
})

describe("FuelEnergyBoard", () => {
  it("shows the headline figures for the period", async () => {
    wrap(); await waitFor(() => expect(screen.getByLabelText("Fuel spend").textContent).toMatch(/4.500/))
    expect(screen.getByLabelText("Litres").textContent).toMatch(/240 L/); expect(screen.getByLabelText("Fleet consumption").textContent).toContain("10.5 L/100 km"); expect(screen.getByLabelText("Fuel cost per km").textContent).toContain("R 2.00/km")
    expect(screen.getByLabelText("Flags to review").textContent).toMatch(/^1/)
  })
  it("draws six months of spend and litres from one request per month", async () => {
    wrap(); expect((await screen.findAllByLabelText(/^Oct: R /))[0]).toBeTruthy(); await waitFor(() => expect(screen.getAllByLabelText(/: R 4.500$/).length).toBe(6))
    const monthly = get.mock.calls.map(c => String(c[0])).filter(u => /fuel-analytics\/vehicles\?from=\d{4}-\d{2}-01&to=/.test(u)); expect(new Set(monthly).size).toBeGreaterThanOrEqual(6)
    expect(screen.getByLabelText("Litres by month")).toBeTruthy(); expect(screen.getByLabelText("Fuel spend by month")).toBeTruthy()
  })
  it("ranks spenders, thirsty vehicles, warnings and stations", async () => {
    wrap(); const spenders = await screen.findAllByLabelText("AAA1"); expect(spenders.length).toBeGreaterThanOrEqual(2)
    expect(within(spenders.find(e => e.getAttribute("data-fraction") === null && e.querySelector("[role=img]")) as HTMLElement).getByLabelText(/^Fuel: 15.0 L\/100 km · 30% thirstier/)).toBeTruthy()
    expect((await screen.findByLabelText("Warning CCC3")).textContent).toContain("Odometer went backwards"); expect((await screen.findByLabelText("Engen Midrand")).textContent).toMatch(/R 20.50\/L/); expect(screen.getByLabelText("Engen Midrand").textContent).toContain("3% cheaper than average")
    expect(screen.getByText(/Electric charging is not tracked yet/)).toBeTruthy()
  })
  it("asks for the chosen period and links through to the detail screens", async () => {
    const go = vi.fn(); wrap(go); await screen.findByLabelText("Warning CCC3"); fireEvent.change(screen.getByLabelText("Period"), { target: { value: "90" } })
    await waitFor(() => expect(get.mock.calls.map(c => String(c[0])).filter(u => u.includes("/fuel-analytics/alerts")).length).toBe(2))
    fireEvent.click(screen.getByText("Fuel log")); expect(go).toHaveBeenCalledWith("fuel"); fireEvent.click(screen.getByText("Fleet list")); expect(go).toHaveBeenCalledWith("fleet-list")
  })
  it("says so when there is no fuel in the period, or the figures fail", async () => {
    get.mockImplementation((url: string) => (url.includes("/fuel-analytics/vehicles") ? ok([]) : ok([]))); wrap()
    expect(await screen.findByText("No fuel recorded in this period.")).toBeTruthy(); expect(screen.getByText(/needs two full-tank fills/)).toBeTruthy(); cleanup()
    get.mockImplementation((url: string) => (url.includes("/fuel-analytics/vehicles") ? Promise.reject(new Error("x")) : ok([]))); wrap(); expect((await screen.findAllByRole("alert"))[0].textContent).toMatch(/Couldn't load fuel analytics/)
  })
})
