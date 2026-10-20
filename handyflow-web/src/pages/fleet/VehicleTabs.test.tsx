import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import VehicleDetail from "./VehicleDetail"
import { LocationTab, MaintenanceTab, FuelUsageTab, HistoryTab } from "./VehicleTabs"
import { buildTimeline, filterTimeline, inRange, serviceDue, serviceSpend, topSpeed, trackLine, trackSummary, trackWindow, tripTotals, type Trip } from "./vehicleTabs.logic"

const get = vi.fn()
type Args = unknown[]
const mapProps = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))
vi.mock("./FleetMap", () => ({ default: (p: { positions: unknown[]; track: unknown[] }) => { mapProps(p); return <div data-testid="map">{p.positions.length} vehicle, {p.track.length} points</div> } }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const NOW = new Date("2026-10-20T12:00:00Z")
const iso = (d: Date) => d.toISOString()
const today = iso(new Date())
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const urls = () => get.mock.calls.map(c => String(c[0]))

const live = [{ vehicleId: "v1", registration: "ABC123MP", make: "Toyota", model: "Hilux", latitude: -26.2, longitude: 28.04, speedKph: 62, heading: 90, ignitionOn: true, recordedAt: today, ageMinutes: 3, status: "MOVING" }, { vehicleId: "v9", registration: "OTHER1", latitude: -25, longitude: 28, speedKph: 0, ageMinutes: 5, status: "PARKED" }]
const track = { totalPoints: 3, returnedPoints: 3, distanceKm: 12.4, points: [{ recordedAt: today, latitude: -26.2, longitude: 28.0, speedKph: 40 }, { recordedAt: today, latitude: 0, longitude: 0, speedKph: 200 }, { recordedAt: today, latitude: -26.21, longitude: 28.05, speedKph: 95 }] }
const events = [{ id: "e1", type: "SPEEDING", occurredAt: "2026-10-20T08:00:00Z", value: 112, limitValue: 80, durationSeconds: null, geofenceName: null, driverName: "John Mokoena", acknowledged: false }]
const services = [
  { id: "s1", type: "FULL", description: "Major service", serviceDate: "2026-09-01", odometerAtService: 118000, nextServiceKm: 128000, cost: 4200, supplier: "Toyota Rosslyn" },
  { id: "s2", type: "OIL", description: null, serviceDate: "2025-01-10", odometerAtService: 100000, nextServiceKm: 110000, cost: 900, supplier: null },
]
const orders = [
  { id: "w1", referenceNumber: "WO-2026-0007", type: "REPAIR", title: "Replace clutch", priority: "HIGH", status: "IN_PROGRESS", assignedTechnicianName: "Sipho Dlamini", createdAt: "2026-10-10T08:00:00Z", completedAt: null, actualTotal: null, quotedAmount: 6500 },
  { id: "w2", referenceNumber: "WO-2026-0003", type: "SERVICE", title: "120k service", priority: "NORMAL", status: "COMPLETED", assignedTechnicianName: null, createdAt: "2026-09-01T08:00:00Z", completedAt: "2026-09-02T15:00:00Z", actualTotal: 4200, quotedAmount: 4000 },
]
const tyres = [{ id: "t1", position: "FL", serialNumber: "T-1", brand: "Bridgestone", size: "265/65R17", status: "FITTED", lastTreadDepthMm: 2.1, treadStatus: "REPLACE_NOW" }]
const batteries = [{ id: "b1", position: "ENGINE", serialNumber: "B-1", brand: "Exide", model: "N70", status: "INSTALLED", lastVoltage: 11.8, lastTestResult: "WEAK", warrantyExpiry: "2027-01-01", inWarranty: true }]
const analytics = {
  summary: { fillups: 6, totalLitres: 310.5, totalSpend: 6200, measuredKm: 3400, litresPer100Km: 11.2, kmPerLitre: 8.9, costPerKm: 1.82, flaggedSegments: 1 }, settings: { tolerancePercent: 20 },
  segments: [{ startDate: "2026-10-01", endDate: "2026-10-08", km: 640, litres: 80.5, litresPer100Km: 12.6, deviationPercent: 28, status: "HIGH_CONSUMPTION" }, { startDate: "2026-09-20", endDate: "2026-10-01", km: 700, litres: 70, litresPer100Km: 10, deviationPercent: null, status: "NO_BASELINE" }],
  indicators: [{ type: "OVERFILL", severity: "ALERT", date: "2026-10-08", message: "82 L into a 70 L tank", fillupId: "f1" }],
}
const cost = { totalServiceCost: 5100, totalFuelCost: 6200, totalCost: 11300, totalKm: 18000, costPerKm: 0.63 }
const fills = [{ id: "f1", filledAt: "2026-10-08", litres: 82, totalCost: 1640, odometerAtFillup: 118640, station: "Engen Midrand", fullTank: true }]
const trips = [
  { id: "p1", status: "COMPLETED", tripType: "BUSINESS", distanceKm: 300, startAt: today, endAt: today, startLocation: "Sandton", endLocation: "Durban", purpose: null, driverName: "John Mokoena" },
  { id: "p2", status: "COMPLETED", tripType: "PRIVATE", distanceKm: 100, startAt: today, endAt: today, startLocation: "Home", endLocation: "Shops", purpose: null, driverName: null },
  { id: "p3", status: "COMPLETED", tripType: "BUSINESS", distanceKm: 999, startAt: "2020-01-01T08:00:00Z", endAt: null, startLocation: null, endLocation: null, purpose: null, driverName: null },
  { id: "p4", status: "ACTIVE", tripType: "BUSINESS", distanceKm: null, startAt: today, endAt: null, startLocation: "Depot", endLocation: null, purpose: null, driverName: null },
]
const lifecycle = { lifecycleStatus: "ACTIVE", operationalStatus: "AVAILABLE", events: [{ id: "l1", eventType: "THEFT_REPORTED", toStatus: "STOLEN", occurredAt: "2026-06-01T08:00:00Z", reason: "Hijacked", disposal: null }, { id: "l2", eventType: "RECOVERED", toStatus: "ACTIVE", occurredAt: "2026-06-05T08:00:00Z", reason: null, disposal: null }] }
const accidents = [{ id: "a1", referenceNumber: "ACC-2026-00021", occurredAt: "2026-10-01T08:00:00Z", description: "Rear-ended", status: "IN_REPAIR", vehicleDrivable: false }]
const inspections = [{ id: "i1", inspectionType: "PRE_TRIP", status: "COMPLETED", verdict: "FAIL", failedCount: 2, startedAt: "2026-10-15T06:00:00Z", completedAt: "2026-10-15T06:20:00Z" }]

beforeEach(() => {
  mapProps.mockReset(); get.mockReset()
  get.mockImplementation((url: string) =>
    url.includes("/telematics/live") ? ok(live) : url.includes("/telematics/track") ? ok(track) : url.includes("/telematics/events") ? ok({ content: events })
    : url.includes("/services") ? ok({ content: services }) : url.includes("/work-orders") ? ok({ content: orders }) : url.includes("/tyres") ? ok(tyres) : url.includes("/batteries") ? ok(batteries)
    : url.includes("/fuel-analytics") ? ok(analytics) : url.includes("/cost-summary") ? ok(cost) : url.includes("/fuel") ? ok({ content: fills }) : url.includes("/trips") ? ok({ content: trips })
    : url.includes("/lifecycle") ? ok(lifecycle) : url.includes("/accidents") ? ok({ content: accidents }) : url.includes("/inspections") ? ok({ content: inspections }) : ok([]))
})
afterEach(cleanup)

describe("tab rules", () => {
  it("builds the route window and summarises a track, ignoring empty fixes", () => {
    expect(trackWindow("24h", NOW)).toEqual({ from: "2026-10-19T12:00:00.000Z", to: "2026-10-20T12:00:00.000Z" }); expect(trackWindow("7d", NOW).from).toBe("2026-10-13T12:00:00.000Z")
    expect(trackLine(track)).toEqual([[-26.2, 28.0], [-26.21, 28.05]]); expect(topSpeed(track)).toBe(200); expect(topSpeed({ ...track, points: [] })).toBeNull()
    expect(trackSummary(track)).toMatch(/12\.4 km by GPS.*not the odometer.*3 fixes/); expect(trackSummary({ ...track, totalPoints: 0 })).toMatch(/No movement/); expect(trackSummary(undefined)).toMatch(/No movement/)
  })
  it("works out the service due, preferring the booked next service", () => {
    expect(serviceDue({ currentOdometer: 118640, latestNextServiceKm: 128000 })).toMatchObject({ dueKm: 128000, remainingKm: 9360, tone: "ok" })
    expect(serviceDue({ currentOdometer: 118640, lastServiceKm: 100000, serviceIntervalKm: 15000 })).toMatchObject({ dueKm: 115000, remainingKm: -3640, tone: "bad" })
    expect(serviceDue({ currentOdometer: 127500, latestNextServiceKm: 128000 }).tone).toBe("warn"); expect(serviceDue({ currentOdometer: 5000 })).toMatchObject({ tone: "none", text: "No service interval set." })
    expect(serviceDue({ currentOdometer: 118640, lastServiceKm: 100000, serviceIntervalKm: 15000 }).text).toMatch(/^Overdue by 3.640 km \(was due at 115.000 km\)$/)
  })
  it("adds up twelve months of service spend", () => { expect(serviceSpend(services, NOW)).toBe(4200) })
  it("splits completed trips into business and private km", () => {
    expect(tripTotals(trips as Trip[])).toMatchObject({ count: 3, businessKm: 1299, privateKm: 100, totalKm: 1399, businessPercent: 93 }); expect(tripTotals([]).businessPercent).toBeNull()
    expect(inRange("2026-10-05T08:00:00Z", "2026-10-01", "2026-10-31")).toBe(true); expect(inRange(null, "2026-10-01", "2026-10-31")).toBe(false)
  })
  it("merges everything into one newest-first timeline and filters by kind", () => {
    const t = buildTimeline({ lifecycle: lifecycle.events, services, orders, accidents, inspections })
    expect(t.map(i => i.key)).toEqual(["Ii1", "Ww1", "Aa1", "Ww2", "Ss1", "Ll2", "Ll1", "Ss2"])
    expect(t[0]).toMatchObject({ kind: "INSPECTION", tone: "bad", detail: "Failed · 2 failed items" })
    expect(t.find(i => i.key === "Aa1")).toMatchObject({ tone: "bad" }); expect(t.find(i => i.key === "Aa1")!.detail).toContain("cannot be driven")
    expect(t.find(i => i.key === "Ww1")).toMatchObject({ tone: "warn" }); expect(t.find(i => i.key === "Ww2")).toMatchObject({ tone: "ok", at: "2026-09-02T15:00:00Z" })
    expect(t.find(i => i.key === "Ll1")).toMatchObject({ title: "Theft reported", tone: "bad" }); expect(t.find(i => i.key === "Ll2")).toMatchObject({ tone: "ok" })
    expect(t.map(i => i.at)).toEqual([...t.map(i => i.at)].sort().reverse())
    expect(filterTimeline(t, ["SERVICE"]).map(i => i.kind)).toEqual(["SERVICE", "SERVICE"]); expect(filterTimeline(t, []).length).toBe(t.length)
  })
})

describe("Location", () => {
  it("shows this vehicle's position, its route and recent driving events", async () => {
    wrap(<LocationTab id="v1" />)
    const pos = await screen.findByLabelText("Current position"); expect(pos.textContent).toContain("Moving"); expect(pos.textContent).toContain("62 km/h"); expect(pos.textContent).toContain("3 min ago"); expect(pos.textContent).toContain("-26.2000, 28.0400")
    expect((await screen.findByLabelText("Route summary")).textContent).toMatch(/12\.4 km by GPS.*top speed 200 km\/h/)
    expect((await screen.findByTestId("map")).textContent).toBe("1 vehicle, 2 points")
    expect((await screen.findByLabelText(/Speeding: 112 km\/h in a 80 km\/h limit/)).textContent).toContain("John Mokoena")
  })
  it("asks for the longer route when the period changes", async () => {
    wrap(<LocationTab id="v1" />); await screen.findByLabelText("Current position")
    fireEvent.change(screen.getByLabelText("Route period"), { target: { value: "7d" } })
    await waitFor(() => expect(urls().filter(u => u.includes("/telematics/track")).length).toBe(2))
    const [a, b] = urls().filter(u => u.includes("/telematics/track")).map(u => decodeURIComponent(u)); const span = (u: string) => (Date.parse(u.match(/to=([^&]+)/)![1]) - Date.parse(u.match(/from=([^&]+)/)![1])) / 3600000
    expect(Math.round(span(a))).toBe(24); expect(Math.round(span(b))).toBe(168)
  })
  it("says so when the vehicle has never reported", async () => {
    get.mockImplementation((url: string) => url.includes("/telematics/live") ? ok([live[1]]) : url.includes("/telematics/track") ? ok({ ...track, totalPoints: 0, points: [] }) : ok([]))
    wrap(<LocationTab id="v1" />); expect((await screen.findByRole("status")).textContent).toContain("No position yet"); expect(screen.queryByTestId("map")).toBeNull(); expect((await screen.findByLabelText("Route summary")).textContent).toMatch(/No movement/)
  })
})

describe("Maintenance", () => {
  it("shows service due, open work orders, tyres, batteries and service history", async () => {
    wrap(<MaintenanceTab id="v1" facts={{ currentOdometer: 127500, lastServiceKm: 118000, serviceIntervalKm: 10000 }} />)
    expect((await screen.findByLabelText("Service due")).textContent).toContain("Due in 500 km"); expect(screen.getByLabelText("Next service").textContent).toContain("500")
    const open = await screen.findByLabelText("WO-2026-0007"); expect(open.textContent).toContain("Replace clutch"); expect(open.textContent).toContain("Sipho Dlamini"); expect(screen.queryByLabelText("WO-2026-0003")).toBeNull()
    expect(screen.getByLabelText("Open work orders").textContent).toMatch(/^1/)
    const tyre = await screen.findByLabelText("Tyre FL"); expect(tyre.textContent).toContain("2.1 mm"); expect(tyre.textContent).toContain("Replace now")
    const bat = await screen.findByLabelText("Battery ENGINE"); expect(bat.textContent).toContain("Weak · 11.8 V")
    expect((await screen.findAllByLabelText(/^Service \d/))[0].textContent).toContain("Toyota Rosslyn")
  })
  it("flags an overdue service and a vehicle with nothing fitted", async () => {
    get.mockImplementation((url: string) => url.includes("/tyres") || url.includes("/batteries") || url.includes("/work-orders") ? ok([]) : url.includes("/services") ? ok({ content: [] }) : ok([]))
    wrap(<MaintenanceTab id="v1" facts={{ currentOdometer: 130000, lastServiceKm: 100000, serviceIntervalKm: 20000 }} />)
    expect((await screen.findByLabelText("Service due")).textContent).toMatch(/Overdue by 10.000 km/); expect(await screen.findByText("No tyres fitted to this vehicle.")).toBeTruthy()
    expect(screen.getByText("No battery installed on this vehicle.")).toBeTruthy(); expect(screen.getByText("Nothing open for this vehicle.")).toBeTruthy(); expect(screen.getByText("No services recorded.")).toBeTruthy()
  })
})

describe("Fuel & Usage", () => {
  it("shows consumption, cost per km, warnings, tank-to-tank stretches, fill-ups and the logbook split", async () => {
    wrap(<FuelUsageTab id="v1" />)
    await waitFor(() => expect(screen.getByLabelText("Consumption").textContent).toContain("11.2 L/100 km")); expect(screen.getByLabelText("Fuel cost per km").textContent).toContain("R 1.82/km")
    expect(screen.getByLabelText("Whole-life cost per km").textContent).toContain("R 0.63/km"); expect((await screen.findByLabelText("Whole-life cost")).textContent).toMatch(/11.300.*18.000 km/)
    expect((await screen.findByLabelText("Fuel warnings")).textContent).toContain("Fill above tank size"); expect(screen.getByLabelText("Fuel warnings").textContent).toContain("82 L into a 70 L tank")
    const seg = await screen.findAllByLabelText(/^Stretch ending /); expect(seg[0].textContent).toContain("28% thirstier than usual"); expect(seg[1].textContent).toContain("No baseline")
    expect((await screen.findAllByLabelText(/^Fill-up /))[0].textContent).toContain("Engen Midrand")
    expect(screen.getByLabelText("Trips").textContent).toMatch(/^2/); expect(screen.getByLabelText("Business km").textContent).toContain("300"); expect(screen.getByLabelText("Private km").textContent).toContain("100"); expect(screen.getByLabelText("Business share").textContent).toContain("75%")
    const rows = screen.getAllByLabelText(/^Trip /); expect(rows.length).toBe(4); expect(rows[0].textContent).toContain("Sandton → Durban"); expect(rows[3].textContent).toContain("Depot → under way")
  })
  it("asks the server for the chosen period", async () => {
    wrap(<FuelUsageTab id="v1" />); await screen.findByLabelText("Consumption"); fireEvent.change(screen.getByLabelText("Fuel period"), { target: { value: "90" } })
    await waitFor(() => expect(urls().filter(u => u.includes("/fuel-analytics")).length).toBe(2))
    const [a, b] = urls().filter(u => u.includes("/fuel-analytics")).map(u => u.match(/from=([\d-]+)&to=([\d-]+)/)!); const days = (m: RegExpMatchArray) => Math.round((Date.parse(m[2]) - Date.parse(m[1])) / 86400000)
    expect(days(a)).toBe(30); expect(days(b)).toBe(90)
  })
  it("shows an error rather than blank figures when the analytics fail", async () => {
    get.mockImplementation((url: string) => url.includes("/fuel-analytics") ? Promise.reject(new Error("x")) : ok([])); wrap(<FuelUsageTab id="v1" />)
    expect((await screen.findByRole("alert")).textContent).toMatch(/Could not load/)
  })
})

describe("History and the tab bar", () => {
  it("lists everything newest first and narrows by kind", async () => {
    wrap(<HistoryTab id="v1" />)
    const inspection = await screen.findByLabelText("Pre trip inspection"); expect(inspection.textContent).toContain("Failed · 2 failed items")
    expect(screen.getByLabelText("Accident ACC-2026-00021").textContent).toContain("cannot be driven"); expect(screen.getByLabelText("Theft reported").textContent).toContain("Hijacked")
    const order = screen.getAllByRole("listitem").map(li => li.getAttribute("aria-label")); expect(order[0]).toBe("Pre trip inspection"); expect(order.indexOf("Accident ACC-2026-00021")).toBeLessThan(order.indexOf("Theft reported"))
    fireEvent.click(screen.getByRole("button", { name: /Services \(2\)/ })); expect(screen.getAllByRole("listitem").length).toBe(2); expect(screen.getByRole("button", { name: /Services/ }).getAttribute("aria-pressed")).toBe("true")
    fireEvent.click(screen.getByText("Show all")); expect(screen.getAllByRole("listitem").length).toBe(8)
  })
  it("says so for a vehicle with no history", async () => {
    get.mockImplementation((url: string) => (url.includes("/lifecycle") ? ok({ events: [] }) : ok([]))); wrap(<HistoryTab id="v1" />)
    expect(await screen.findByText("Nothing has been recorded for this vehicle yet.")).toBeTruthy()
  })
  it("adds the four tabs to the vehicle panel and passes the odometer to Maintenance", async () => {
    wrap(<VehicleDetail vehicleId="v1" identity={{ currentOdometer: 127500, lastServiceKm: 118000, serviceIntervalKm: 10000 }} />)
    const tabs = screen.getAllByRole("tab").map(t => t.textContent); expect(tabs).toEqual(["Overview", "Location", "Maintenance", "Fuel & Usage", "Compliance", "Documents", "Insurance", "Accidents", "Lifecycle", "History"])
    fireEvent.click(screen.getByRole("tab", { name: "Maintenance" })); expect((await screen.findByLabelText("Service due")).textContent).toContain("Due in 500 km")
    fireEvent.click(screen.getByRole("tab", { name: "History" })); expect(await screen.findByLabelText("Pre trip inspection")).toBeTruthy()
    expect(within(screen.getByRole("tablist")).getByRole("tab", { name: "History" }).getAttribute("aria-selected")).toBe("true")
  })
})
