import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import FuelAnalyticsTab from "./FuelAnalyticsTab"
import AlertsTab from "./AlertsTab"
import { buildAlerts, consumptionText, costPerKmText, deviationText, deviationTone, fleetTotals, periodRange, premiumText, type FuelRow } from "./fuelAnalytics.logic"

const get = vi.fn()
type Args = unknown[]
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a) } }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const row = (o: Partial<FuelRow> & { registration: string }): FuelRow => ({ vehicleId: o.registration, make: "Toyota", model: "Hilux", latestDeviationPercent: null, summary: { fillups: 4, totalLitres: 200, totalSpend: 4000, measuredKm: 2000, litresPer100Km: 10, kmPerLitre: 10, costPerKm: 2, flaggedSegments: 0, indicators: 0 }, ...o })
const rows = [row({ registration: "ABC123MP", latestDeviationPercent: 35, summary: { fillups: 5, totalLitres: 300, totalSpend: 6000, measuredKm: 2000, litresPer100Km: 15, kmPerLitre: 6.7, costPerKm: 3, flaggedSegments: 1, indicators: 1 } }), row({ registration: "XYZ999GP", latestDeviationPercent: -8 })]
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

beforeEach(() => {
  get.mockReset()
  get.mockImplementation((url: string) => url.includes("/fuel-analytics/vehicles") ? ok(rows)
    : url.includes("/fuel-analytics/alerts") ? ok([{ kind: "INDICATOR", type: "OVERFILL", severity: "ALERT", registration: "ABC123MP", date: "2026-10-10", message: "Filled 95 L into an 80 L tank" }])
    : url.includes("/fuel-analytics/stations") ? ok([{ station: "Engen Sandton", fuelType: "DIESEL", fillups: 6, litres: 400, totalSpend: 8400, avgPricePerLitre: 21, premiumPercent: 6.4 }])
    : url.includes("/accidents") ? ok({ content: [{ id: "a1", registration: "KLM555NW", status: "IN_REPAIR", vehicleDrivable: false, referenceNumber: "ACC-1" }, { id: "a2", registration: "OLD111", status: "CLOSED", vehicleDrivable: false, referenceNumber: "ACC-0" }] })
    : url.includes("/vehicles") ? ok({ content: [{ id: "v1", registration: "ABC123MP", status: "AVAILABLE", dueForService: true, licenceExpiringSoon: true, roadworthyExpiringSoon: false }, { id: "v2", registration: "RET1", status: "RETIRED", dueForService: true, licenceExpiringSoon: true, roadworthyExpiringSoon: true }] })
    : ok([]))
})
afterEach(cleanup)

describe("fuel logic", () => {
  it("words consumption, cost and deviation", () => {
    expect(consumptionText(9.456)).toBe("9.5 L/100 km"); expect(consumptionText(null)).toBe("—"); expect(costPerKmText(2.5)).toBe("R 2.50/km")
    expect([35, -8, 0.2, null].map(deviationText)).toEqual(["35% thirstier than usual", "8% lighter than usual", "As usual", "—"])
    expect([35, 15, 3, -10, null].map(d => deviationTone(d))).toEqual(["bad", "warn", "ok", "ok", "none"])
    expect([6.4, -3, 0.1, null].map(premiumText)).toEqual(["6% dearer than average", "3% cheaper than average", "At the average", "—"])
  })
  it("weights fleet consumption by km", () => {
    const t = fleetTotals([row({ registration: "A" }), row({ registration: "B", summary: { fillups: 1, totalLitres: 300, totalSpend: 6000, measuredKm: 1000, litresPer100Km: 30, kmPerLitre: 3.3, costPerKm: 6, flaggedSegments: 1, indicators: 2 } })])
    expect(t.litres).toBe(500); expect(t.km).toBe(3000); expect(t.l100).toBeCloseTo(16.67, 1); expect(t.flagged).toBe(3)
    expect(fleetTotals([]).l100).toBeNull()
  })
  it("builds periods", () => {
    const d = new Date(2026, 9, 20)
    expect(periodRange("month", d)).toEqual({ from: "2026-10-01", to: "2026-10-20" }); expect(periodRange("30", d)).toEqual({ from: "2026-09-20", to: "2026-10-20" })
  })
  it("merges alerts, urgent first, skipping retired vehicles and closed cases", () => {
    const a = buildAlerts({ fuel: [{ kind: "INDICATOR", type: "OVERFILL", severity: "ALERT", registration: "ABC123MP", date: "2026-10-10", message: "Overfill" }],
      accidents: [{ id: "a1", registration: "KLM555NW", status: "IN_REPAIR", vehicleDrivable: false, referenceNumber: "ACC-1" }, { id: "a2", registration: "OLD", status: "CLOSED", vehicleDrivable: false, referenceNumber: "ACC-0" }],
      vehicles: [{ id: "v1", registration: "ABC123MP", status: "AVAILABLE", dueForService: true, licenceExpiringSoon: true, roadworthyExpiringSoon: false }, { id: "v2", registration: "RET1", status: "RETIRED", dueForService: true, licenceExpiringSoon: true, roadworthyExpiringSoon: true }] })
    expect(a.map(x => `${x.severity}:${x.source}:${x.registration}`)).toEqual(["high:Accident:KLM555NW", "high:Fuel:ABC123MP", "medium:Compliance:ABC123MP", "medium:Service:ABC123MP"])
  })
})

describe("expired papers", () => {
  it("are high severity and say expired, not expires soon", () => {
    const v = (d: string) => ({ id: "v", registration: "ABC", status: "AVAILABLE", dueForService: false, licenceExpiringSoon: true, roadworthyExpiringSoon: false, licenceDiscExpiry: d })
    const now = new Date(2026, 9, 20)
    expect(buildAlerts({ fuel: [], accidents: [], vehicles: [v("2026-10-01")] }, now)[0]).toMatchObject({ severity: "high", message: "Licence disc has expired." })
    expect(buildAlerts({ fuel: [], accidents: [], vehicles: [v("2026-11-05")] }, now)[0]).toMatchObject({ severity: "medium", message: "Licence disc expires soon." })
  })
})

describe("FuelAnalyticsTab", () => {
  it("shows fleet totals and each vehicle against its own usual", async () => {
    wrap(<FuelAnalyticsTab />); await screen.findByRole("row", { name: "ABC123MP" })
    expect(screen.getByLabelText("Fleet consumption").textContent).toBe("12.5 L/100 km"); expect(screen.getByLabelText("Flags").textContent).toBe("2")
    expect(screen.getByRole("row", { name: "ABC123MP" }).textContent).toContain("35% thirstier than usual"); expect(screen.getByRole("row", { name: "XYZ999GP" }).textContent).toContain("8% lighter than usual")
  })
  it("asks for the chosen period", async () => {
    wrap(<FuelAnalyticsTab />); await screen.findByRole("row", { name: "ABC123MP" }); fireEvent.change(screen.getByLabelText("Period"), { target: { value: "90" } })
    await waitFor(() => expect(get.mock.calls.some(c => String(c[0]).includes("/fuel-analytics/vehicles") && String(c[0]).includes("from="))).toBe(true))
  })
  it("lists alerts with the not-proof reminder", async () => {
    wrap(<FuelAnalyticsTab />); fireEvent.click(await screen.findByRole("button", { name: /Alerts/ }))
    expect(await screen.findByText("Filled 95 L into an 80 L tank")).toBeTruthy(); expect(screen.getByText(/not proof of theft/)).toBeTruthy()
  })
  it("compares stations with the average price", async () => {
    wrap(<FuelAnalyticsTab />); fireEvent.click(await screen.findByRole("button", { name: "Stations" }))
    expect((await screen.findByLabelText("Engen Sandton DIESEL")).textContent).toContain("6% dearer than average")
  })
})

describe("AlertsTab", () => {
  it("shows one urgent-first feed and opens the right screen", async () => {
    const go = vi.fn(); wrap(<AlertsTab onNavigate={go} />)
    const first = await screen.findByLabelText("KLM555NW Cannot be driven after accident ACC-1; blocked from trips.")
    expect(first.textContent).toContain("Urgent"); expect(screen.queryByText("RET1")).toBeNull(); expect(screen.queryByText(/OLD/)).toBeNull()
    fireEvent.click(first.querySelector("button") as HTMLElement); expect(go).toHaveBeenCalledWith("accidents")
  })
  it("says so when nothing needs attention", async () => {
    get.mockImplementation(() => ok([])); wrap(<AlertsTab onNavigate={() => {}} />)
    expect(await screen.findByText("Nothing needs attention.")).toBeTruthy()
  })
})
