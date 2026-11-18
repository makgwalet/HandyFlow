import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import FuelDashboard from "./FuelDashboard"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))

const summary = { today: "2026-11-18", monthLitres: 1000, monthDispatches: 2, monthReceipts: 1, dailyLitres: [1, 2], negativeVarianceReadings: 0, openDeliveries: 0, inTransitDeliveries: 0, overdueDeliveries: 0, nextDeliveries: [] }
const retail = { days: 7, shifts: 3, litres: 4000, sales: 90000, variance: -20, flaggedShifts: 1, byFuel: [] }
const occupancy = { date: "2026-11-18", inYard: 5, arrivals: 2, departures: 1, bays: 8, free: 3 }
const capacity = { from: "2026-11-18", bays: 8, nights: [], overstays: [] }
const refused = (status: number) => Object.assign(new Error("refused"), { response: { status } })
const nav = vi.fn()

/** The shared client strips the {success,data} envelope, so the real shape is {data: payload}; older mocks kept the envelope. */
let stripped = false
const wrap = (v: unknown) => (stripped ? { data: v } : { data: { data: v } })

function reply(over: Record<string, () => unknown> = {}) {
  get.mockImplementation(async (u: string) => {
    for (const [k, f] of Object.entries(over)) if (u.includes(k)) { const v = f(); if (v instanceof Error) throw v; return wrap(v) }
    if (u.includes("dashboard-summary")) return wrap(summary)
    if (u.includes("/tanks")) return wrap([])
    if (u.includes("forecourt/summary")) return wrap(retail)
    if (u.includes("occupancy")) return wrap(occupancy)
    if (u.includes("capacity")) return wrap(capacity)
    return wrap(null)
  })
}
function mount() {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><FuelDashboard onNavigate={nav} /></QueryClientProvider>)
}

describe("FuelDashboard retail and yard tiles", () => {
  beforeEach(() => { get.mockReset(); nav.mockReset(); stripped = false })
  afterEach(cleanup)

  it("shows retail takings and the yard, and each opens its tab", async () => {
    reply(); mount()
    expect(await screen.findByText(/90.000/)).toBeTruthy()
    expect(screen.getByText(/1 flagged/)).toBeTruthy()
    expect(screen.getByText("5 in the yard")).toBeTruthy()
    expect(screen.getByText(/3 of 8 bays free/)).toBeTruthy()
    fireEvent.click(screen.getByText("Retail takings, last 7 days")); expect(nav).toHaveBeenCalledWith("forecourt")
    fireEvent.click(screen.getByText("Truck stop yard")); expect(nav).toHaveBeenCalledWith("truck-stop")
  })

  it("works with the real client, which has already removed the envelope", async () => {
    stripped = true
    reply(); mount()
    expect(await screen.findByText(/90.000/)).toBeTruthy()
    expect(screen.getByText("5 in the yard")).toBeTruthy()
  })

  it("leaves out a division the company does not run, without calling it an error", async () => {
    reply({ "forecourt/summary": () => refused(403), occupancy: () => refused(404) }); mount()
    expect(await screen.findByText("Total stock")).toBeTruthy()
    await new Promise(r => setTimeout(r, 30))
    expect(screen.queryByText("Retail takings, last 7 days")).toBeNull()
    expect(screen.queryByText("Truck stop yard")).toBeNull()
  })

  it("shows a real failure on the tile instead of hiding it", async () => {
    reply({ "forecourt/summary": () => refused(500) }); mount()
    expect(await screen.findByText("Could not load")).toBeTruthy()
    expect(screen.getByText("Retail takings, last 7 days")).toBeTruthy()
  })

  it("flags an overstaying truck in the yard tile", async () => {
    reply({ capacity: () => ({ ...capacity, overstays: [{ id: "x", vehicleRegistration: "GP 1", plannedDeparture: "2026-11-16", daysOver: 2 }] }) }); mount()
    expect(await screen.findByText(/1 overstaying/)).toBeTruthy()
  })

  it("still shows the yard when the capacity view fails", async () => {
    reply({ capacity: () => refused(500) }); mount()
    expect(await screen.findByText("5 in the yard")).toBeTruthy()
  })
})
