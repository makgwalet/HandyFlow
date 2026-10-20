import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ReportsTab from "./ReportsTab"
import { cellText, complianceReport, costPerKmReport, csvField, csvFileName, driverScoresReport, fuelReport, maintenanceReport, reportPeriod, serviceDueReport, toCsv } from "./reports.logic"
import type { ListVehicle } from "./fleetBoards.logic"
import type { FuelRow } from "./fuelAnalytics.logic"

const get = vi.fn()
type Args = unknown[]
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a) } }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const TODAY = new Date(2026, 9, 20, 22, 0)
const on = (n: number) => { const d = new Date(2026, 9, 20 + n); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}` }
const cost = [
  { registration: "AAA1", make: "Toyota", model: "Hilux", totalServiceCost: 5000, totalFuelCost: 15000, totalCost: 20000, totalKm: 10000, costPerKm: 2 }, { registration: "BBB2", make: "Isuzu", model: "NPR", totalServiceCost: 9000, totalFuelCost: 21000, totalCost: 30000, totalKm: 6000, costPerKm: 5 },
  { registration: "CCC3", make: null, model: null, totalServiceCost: 0, totalFuelCost: 0, totalCost: 0, totalKm: 0, costPerKm: null },
]
const fr = (id: string, spend: number, litres: number, km: number, l100: number | null): FuelRow => ({ vehicleId: id, registration: id, summary: { fillups: 3, totalLitres: litres, totalSpend: spend, measuredKm: km, litresPer100Km: l100, kmPerLitre: null, costPerKm: km ? spend / km : null, flaggedSegments: 0, indicators: 1 }, latestDeviationPercent: l100 && l100 > 12 ? 30 : null })
const fuel = [fr("AAA1", 3000, 150, 1000, 15), fr("BBB2", 1000, 60, 1000, 6), fr("CCC3", 500, 30, 0, null)]
const veh = (o: Partial<ListVehicle> & { id: string; registration: string }): ListVehicle => ({ make: "Toyota", model: "Hilux", year: 2022, status: "AVAILABLE", assignedDriverName: null, currentOdometer: 50000, lastServiceKm: 45000, serviceIntervalKm: 15000, licenceDiscExpiry: on(200), roadworthyExpiry: on(200), insuranceExpiry: on(200), ...o })
const vehicles = [veh({ id: "a", registration: "AAA1" }), veh({ id: "b", registration: "BBB2", currentOdometer: 62000, licenceDiscExpiry: on(-3) }), veh({ id: "c", registration: "CCC3", insuranceExpiry: on(10), serviceIntervalKm: null }), veh({ id: "d", registration: "DDD4", status: "RETIRED", licenceDiscExpiry: on(-500) })]
const orders = [
  { referenceNumber: "WO-1", registration: "AAA1", type: "ACCIDENT_REPAIR", title: "Bumper", status: "COMPLETED", completedAt: `${on(-5)}T10:00:00Z`, quotedAmount: 4000, actualTotal: 4600 },
  { referenceNumber: "WO-2", registration: "BBB2", type: "SERVICE", title: "120k service", status: "COMPLETED", completedAt: `${on(-2)}T10:00:00Z`, quotedAmount: null, actualTotal: 2000 },
  { referenceNumber: "WO-3", registration: "CCC3", type: "REPAIR", title: "Old job", status: "COMPLETED", completedAt: "2024-01-05T10:00:00Z", quotedAmount: 1000, actualTotal: 1000 },
  { referenceNumber: "WO-4", registration: "AAA1", type: "REPAIR", title: "Not finished", status: "IN_PROGRESS", completedAt: null, quotedAmount: 500, actualTotal: null },
]
const scores = [{ driverName: "New Starter", overall: null, band: null, coverage: 0, reliable: false, tripCount: 0 }, { driverName: "Thandi Nkosi", overall: 61, band: "FAIR", coverage: 30, reliable: false, tripCount: 3 }, { driverName: "John Mokoena", overall: 92.3, band: "EXCELLENT", coverage: 100, reliable: true, tripCount: 40 }, { driverName: "Ann Bell", overall: 70, band: "GOOD", coverage: 90, reliable: true, tripCount: 20 }]
const cell = (t: { rows: Record<string, unknown>[] }, key: string) => t.rows.map(r => r[key])

describe("report tables", () => {
  it("cost per km: dearest first, vehicles without distance last, totals recomputed from the sums", () => {
    const t = costPerKmReport(cost); expect(cell(t, "vehicle")).toEqual(["BBB2", "AAA1", "CCC3"]); expect(t.totals).toMatchObject({ vehicle: "Fleet", service: 14000, fuel: 36000, total: 50000, km: 16000, perKm: 3.13 })
    expect(costPerKmReport([]).totals?.perKm).toBeNull()
  })
  it("fuel: thirstiest first, flags counted, fleet consumption from distance-weighted totals", () => {
    const t = fuelReport(fuel); expect(cell(t, "vehicle")).toEqual(["AAA1", "BBB2", "CCC3"]); expect(t.rows[0]).toMatchObject({ l100: 15, usual: "30% thirstier than usual", flags: 1 }); expect(t.totals).toMatchObject({ fills: 9, litres: 240, spend: 4500, km: 2000, l100: 10.5, flags: 3 })
  })
  it("maintenance: only work completed in the period, newest first, with the variance against the quote", () => {
    const t = maintenanceReport(orders, on(-30), on(0)); expect(cell(t, "ref")).toEqual(["WO-2", "WO-1"]); expect(t.rows[1]).toMatchObject({ type: "Accident repair", completed: on(-5), quoted: 4000, actual: 4600, variance: 600 }); expect(t.rows[0].variance).toBeNull()
    expect(t.totals).toMatchObject({ ref: "2 completed", quoted: 4000, actual: 6600 }); expect(maintenanceReport(orders, on(-1), on(0)).rows).toEqual([])
  })
  it("compliance: most urgent first, retired vehicles left out, dates and wording together", () => {
    const t = complianceReport(vehicles, TODAY); expect(cell(t, "vehicle")).toEqual(["BBB2", "CCC3", "AAA1"]); expect(t.rows[0].licence).toBe(`${on(-3)} · Expired 3 days ago`); expect(t.rows[1].insurance).toBe(`${on(10)} · 10 days left`)
    expect(complianceReport([veh({ id: "x", registration: "X", insuranceExpiry: null })], TODAY).rows[0].insurance).toBe("Not set")
  })
  it("service due: most overdue first, with no-interval and retired vehicles left out", () => {
    const t = serviceDueReport(vehicles); expect(cell(t, "vehicle")).toEqual(["BBB2", "AAA1"]); expect(t.rows[0]).toMatchObject({ remaining: -2000, dueAt: 60000, state: "Overdue" }); expect(t.rows[1]).toMatchObject({ remaining: 10000, state: "OK" })
  })
  it("driver scores: rated, then not rated, then no data; a thin score is never a rating", () => {
    const t = driverScoresReport(scores); expect(cell(t, "driver")).toEqual(["John Mokoena", "Ann Bell", "Thandi Nkosi", "New Starter"]); expect(cell(t, "rating")).toEqual(["Excellent", "Good", "Not rated", "No data"]); expect(t.rows[2].score).toBe("61.0%")
  })
})

describe("period and CSV", () => {
  it("covers this year as well as the usual periods", () => { expect(reportPeriod("year", TODAY)).toEqual({ from: "2026-01-01", to: "2026-10-20" }); expect(reportPeriod("30", TODAY).to).toBe("2026-10-20"); expect(reportPeriod("month", TODAY).from).toBe("2026-10-01") })
  it("writes numbers raw and text readable", () => {
    expect(cellText("money", 1234.5)).toMatch(/^R 1.234,50$|^R 1.234\.50$/); expect(cellText("number", 12345)).toMatch(/^12.345$/); expect(cellText("decimal", 10.54)).toMatch(/^10[.,]5$/); expect(cellText("money", null)).toBe("—"); expect(cellText("text", "x")).toBe("x")
  })
  it("quotes fields that need it and makes formulas inert", () => {
    expect(csvField('Say "hi", please')).toBe('"Say ""hi"", please"'); expect(csvField("a\nb")).toBe('"a\nb"'); expect(csvField(null)).toBe(""); expect(csvField(12.5)).toBe("12.5"); expect(csvField(-3)).toBe("-3")
    expect(csvField("=HYPERLINK(\"x\")")).toBe('"\'=HYPERLINK(""x"")"'); expect(csvField("+27 82 111 0001")).toBe("'+27 82 111 0001"); expect(csvField("@sum")).toBe("'@sum")
  })
  it("writes a header, the rows and the totals row", () => {
    const csv = toCsv(costPerKmReport(cost.slice(0, 1))); expect(csv.split("\r\n")).toEqual(["Vehicle,Make and model,Service cost,Fuel cost,Total cost,Km driven,Cost per km", "AAA1,Toyota Hilux,5000,15000,20000,10000,2", "Fleet,,5000,15000,20000,10000,2", ""])
  })
  it("names the file for the report and its period", () => { expect(csvFileName("fuel", { from: "2026-10-01", to: "2026-10-20" })).toBe("fleet-fuel-2026-10-01_to_2026-10-20.csv"); expect(csvFileName("compliance", null, TODAY)).toBe("fleet-compliance-2026-10-20.csv") })
})

describe("ReportsTab", () => {
  const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ReportsTab /></QueryClientProvider>)
  const urls = () => get.mock.calls.map(c => String(c[0]))
  beforeEach(() => {
    get.mockReset(); get.mockImplementation((url: string) => (url.includes("/cost-summary") ? ok(cost) : url.includes("/fuel-analytics/vehicles") ? ok(fuel) : url.includes("/work-orders") ? ok({ content: orders }) : url.includes("/driver-scores") ? ok(scores) : url.includes("/vehicles") ? ok({ content: vehicles }) : ok([])))
  })
  afterEach(cleanup)

  it("opens on cost per km with a totals row and a row count", async () => {
    wrap(); const table = await screen.findByLabelText("Cost per km", { selector: "table" }); expect(within(table).getAllByRole("row").length).toBe(5)
    expect(screen.getByLabelText("Row count").textContent).toBe("3 rows"); expect(within(screen.getByLabelText("Totals")).getByText("Fleet")).toBeTruthy(); expect(screen.queryByLabelText("Report period")).toBeNull()
  })
  it("switches reports, shows the period only where it applies, and asks the server for that period", async () => {
    wrap(); await screen.findByLabelText("Row count"); fireEvent.click(screen.getByRole("button", { name: /Fuel efficiency/ }))
    await screen.findByLabelText("Fuel efficiency", { selector: "table" }); fireEvent.change(screen.getByLabelText("Report period"), { target: { value: "year" } })
    await waitFor(() => expect(urls().filter(u => u.includes("/fuel-analytics/vehicles")).length).toBe(2)); expect(urls().filter(u => u.includes("/fuel-analytics/vehicles"))[1]).toContain("from=" + new Date().getFullYear() + "-01-01")
    expect(screen.getByLabelText("Period dates").textContent).toContain(`${new Date().getFullYear()}-01-01 to`)
  })
  it("says so when a report is empty and keeps the download off", async () => {
    wrap(); await screen.findByLabelText("Row count"); fireEvent.click(screen.getByRole("button", { name: /Maintenance cost/ })); fireEvent.change(await screen.findByLabelText("Report period"), { target: { value: "month" } })
    get.mockImplementation(() => ok({ content: [] })); fireEvent.change(screen.getByLabelText("Report period"), { target: { value: "90" } })
    expect(await screen.findByText("Nothing to report for this period.")).toBeTruthy(); expect((screen.getByText("Download CSV") as HTMLButtonElement).disabled).toBe(true)
  })
  it("downloads the CSV under a name that says what and when", async () => {
    const created: Blob[] = [], names: string[] = []
    URL.createObjectURL = vi.fn((b: Blob) => { created.push(b); return "blob:x" }); URL.revokeObjectURL = vi.fn()
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) { names.push(this.download) })
    wrap(); await screen.findByLabelText("Row count"); fireEvent.click(screen.getByRole("button", { name: /Compliance expiry/ })); await screen.findByLabelText("Compliance expiry", { selector: "table" })
    fireEvent.click(screen.getByText("Download CSV")); expect(names.length).toBe(1); expect(names[0]).toMatch(/^fleet-compliance-\d{4}-\d{2}-\d{2}\.csv$/); expect(created[0].type).toBe("text/csv;charset=utf-8"); expect(created[0].size).toBeGreaterThan(50); click.mockRestore()
  })
  it("offers another try when a report fails", async () => {
    get.mockImplementation(() => Promise.reject(new Error("x"))); wrap(); expect((await screen.findByRole("alert")).textContent).toMatch(/Couldn't build this report/)
    get.mockImplementation((url: string) => (url.includes("/cost-summary") ? ok(cost) : ok([]))); fireEvent.click(screen.getByText("Try again")); expect(await screen.findByLabelText("Row count")).toBeTruthy(); await waitFor(() => expect(screen.getByLabelText("Row count").textContent).toBe("3 rows"))
  })
})
