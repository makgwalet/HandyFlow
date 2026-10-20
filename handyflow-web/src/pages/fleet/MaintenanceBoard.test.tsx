import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import MaintenanceBoard from "./MaintenanceBoard"
import { batteryAttention, orderSummary, serviceQueue, spendByMonth, statusBars, tyreAttention, type BoardOrder, type ListVehicle } from "./fleetBoards.logic"

const get = vi.fn()
type Args = unknown[]
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a) } }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const NOW = new Date(2026, 9, 20, 22, 0)
const on = (n: number) => { const d = new Date(2026, 9, 20 + n); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}` }
const order = (o: Partial<BoardOrder> & { id: string }): BoardOrder => ({ referenceNumber: `WO-${o.id}`, registration: "AAA1", title: "Job", priority: "NORMAL", status: "OPEN", scheduledFor: null, createdAt: "2026-10-01T08:00:00Z", completedAt: null, actualTotal: null, ...o })
const orders = [
  order({ id: "1", status: "OPEN", scheduledFor: on(-2) }), order({ id: "2", status: "QUOTED" }), order({ id: "3", status: "IN_PROGRESS", scheduledFor: on(3) }), order({ id: "4", status: "ON_HOLD" }), order({ id: "5", status: "IN_PROGRESS" }),
  order({ id: "6", status: "COMPLETED", createdAt: "2026-10-10T08:00:00Z", completedAt: "2026-10-14T08:00:00Z", actualTotal: 4200 }),
  order({ id: "7", status: "COMPLETED", createdAt: "2026-09-01T08:00:00Z", completedAt: "2026-09-03T08:00:00Z", actualTotal: 1800 }),
  order({ id: "8", status: "COMPLETED", createdAt: "2025-01-01T08:00:00Z", completedAt: "2025-01-20T08:00:00Z", actualTotal: 9999 }),
  order({ id: "9", status: "CANCELLED", scheduledFor: on(-9) }),
]
const veh = (o: Partial<ListVehicle> & { id: string; registration: string }): ListVehicle => ({ make: "Toyota", model: "Hilux", year: 2022, status: "AVAILABLE", assignedDriverName: null, currentOdometer: 50000, lastServiceKm: 45000, serviceIntervalKm: 15000, licenceDiscExpiry: null, roadworthyExpiry: null, insuranceExpiry: null, ...o })
const vehicles = [veh({ id: "a", registration: "AAA1" }), veh({ id: "b", registration: "BBB2", currentOdometer: 62000 }), veh({ id: "c", registration: "CCC3", currentOdometer: 59500 }), veh({ id: "d", registration: "DDD4", serviceIntervalKm: null }), veh({ id: "e", registration: "EEE5", status: "RETIRED", currentOdometer: 90000 })]
const tyres = [
  { id: "t1", registration: "AAA1", position: "FL", brand: "Bridgestone", size: "265/65R17", lastTreadDepthMm: 3.1, treadStatus: "REPLACE_SOON" }, { id: "t2", registration: "BBB2", position: "RR", brand: "Michelin", size: "225/70R15", lastTreadDepthMm: 1.6, treadStatus: "REPLACE_NOW" },
  { id: "t3", registration: "CCC3", position: "FR", brand: "Goodyear", size: "205/55R16", lastTreadDepthMm: 7, treadStatus: "OK" },
]
const batteries = [{ id: "b1", registration: "AAA1", position: "ENGINE", brand: "Exide", status: "INSTALLED", lastTestResult: "WEAK", lastVoltage: 11.8 }, { id: "b2", registration: "BBB2", position: "ENGINE", brand: "Willard", status: "INSTALLED", lastTestResult: "FAILED", lastVoltage: 9.4 }, { id: "b3", registration: "CCC3", position: "ENGINE", brand: "Exide", status: "INSTALLED", lastTestResult: "GOOD", lastVoltage: 12.6 }]
const wrap = (go?: (s: string) => void) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MaintenanceBoard onNavigate={go} /></QueryClientProvider>)
beforeEach(() => { get.mockReset(); get.mockImplementation((url: string) => (url.includes("/work-orders") ? ok({ content: orders }) : url.includes("/tyres") ? ok(tyres) : url.includes("/batteries") ? ok(batteries) : url.includes("/vehicles") ? ok({ content: vehicles }) : ok([]))) })
afterEach(cleanup)

describe("maintenance board rules", () => {
  it("summarises work in flight", () => {
    expect(orderSummary(orders, NOW)).toEqual({ open: 5, awaitingApproval: 1, overdue: 1, avgDaysToComplete: 3 }); expect(orderSummary([], NOW)).toEqual({ open: 0, awaitingApproval: 0, overdue: 0, avgDaysToComplete: null })
  })
  it("scales the status bars to the busiest status and leaves out finished work", () => {
    const bars = statusBars(orders); expect(bars.map(b => b.status)).toEqual(["OPEN", "QUOTED", "APPROVED", "IN_PROGRESS", "ON_HOLD"]); expect(bars.find(b => b.status === "IN_PROGRESS")).toMatchObject({ count: 2, fraction: 1 }); expect(bars.find(b => b.status === "APPROVED")).toMatchObject({ count: 0, fraction: 0 })
  })
  it("totals completed work by the month it was finished, over a rolling six months", () => {
    const m = spendByMonth(orders, NOW); expect(m.map(x => x.key)).toEqual(["2026-05", "2026-06", "2026-07", "2026-08", "2026-09", "2026-10"]); expect(m.map(x => x.value)).toEqual([0, 0, 0, 0, 1800, 4200])
  })
  it("queues vehicles overdue or within 2 000 km, most overdue first, leaving out retired and unscheduled ones", () => {
    expect(serviceQueue(vehicles).map(q => [q.vehicle.registration, q.remainingKm])).toEqual([["BBB2", -2000], ["CCC3", 500]]); expect(serviceQueue(vehicles, 1).length).toBe(1)
  })
  it("picks out the tyres and batteries that need attention, worst first", () => {
    expect(tyreAttention(tyres).map(t => t.id)).toEqual(["t2", "t1"]); expect(batteryAttention(batteries).map(b => b.id)).toEqual(["b2", "b1"])
    expect(batteryAttention([{ id: "x", registration: null, position: null, brand: "A", status: "FAULTY", lastTestResult: null, lastVoltage: null }]).length).toBe(1)
  })
})

describe("MaintenanceBoard", () => {
  it("shows the tiles and sends each to where the work is done", async () => {
    const go = vi.fn(); wrap(go); await screen.findByLabelText("BBB2")
    expect(screen.getByLabelText("Open work orders").textContent).toMatch(/^5/); expect(screen.getByRole("button", { name: "Awaiting approval" }).textContent).toMatch(/^1/); expect(screen.getByLabelText("Past scheduled date").textContent).toMatch(/^1/)
    expect(screen.getByLabelText("Services overdue").textContent).toMatch(/^1/); expect(screen.getByLabelText("Average days to complete (90 days)").textContent).toMatch(/^3/)
    fireEvent.click(screen.getByLabelText("Open work orders")); expect(go).toHaveBeenCalledWith("workshop"); fireEvent.click(screen.getByLabelText("Services overdue")); expect(go).toHaveBeenCalledWith("fleet-list")
  })
  it("lists the service queue, workshop bars, spend columns, tyres and batteries", async () => {
    wrap(); const q = await screen.findByLabelText("BBB2"); expect(within(q).getByLabelText(/^Service: Overdue by 2.000 km/)).toBeTruthy(); expect(within(screen.getByLabelText("CCC3")).getByLabelText(/^Service: 500 km to go/)).toBeTruthy()
    expect(screen.queryByLabelText("EEE5")).toBeNull(); expect(screen.queryByLabelText("DDD4")).toBeNull()
    expect(screen.getByLabelText("In progress").textContent).toContain("2"); expect(screen.getByLabelText("On hold").textContent).toContain("1")
    expect((await screen.findAllByLabelText(/^Oct: R /))[0].textContent).toMatch(/4.200/); expect((await screen.findAllByLabelText(/^Sep.*: R /))[0].textContent).toMatch(/1.800/)
    expect((await screen.findByLabelText("Tyre BBB2 RR")).textContent).toContain("Replace now"); expect(screen.getByLabelText("Tyre AAA1 FL").textContent).toContain("Replace soon"); expect(screen.queryByLabelText("Tyre CCC3 FR")).toBeNull()
    expect((await screen.findByLabelText("Battery BBB2 ENGINE")).textContent).toContain("Failed"); expect(screen.getByLabelText("Battery AAA1 ENGINE").textContent).toContain("Weak"); expect(screen.queryByLabelText("Battery CCC3 ENGINE")).toBeNull()
  })
})
