import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import FleetMapTab from "./FleetMapTab"
import { currentLoad, joinTankers } from "./fleetMapOps"
import type { LivePosition } from "../fleet/liveMap.logic"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
vi.mock("../fleet/FleetMap", () => ({ default: ({ positions, selectedId }: { positions: LivePosition[]; selectedId: string | null }) => <div data-testid="map">{positions.map(p => p.registration).join(",")}|{selectedId ?? "none"}</div> }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const tanker = (id: string, registration: string, o: any = {}) => ({ id, registration, name: null, active: true, totalCapacity: 20000, compartments: [], openLoads: 0, fleetVehicleId: null, ...o })
const pos = (vehicleId: string, o: any = {}): LivePosition => ({ vehicleId, registration: "FLEET-" + vehicleId, make: null, model: null, latitude: -26.1, longitude: 28, speedKph: 60, heading: 0, ignitionOn: true, recordedAt: "", ageMinutes: 2, status: "MOVING", ...o })
const load = (o: any = {}) => ({ deliveryId: "d1", status: "IN_TRANSIT", scheduledAt: "2026-11-18T08:00:00Z", customerId: "c", customerName: "Gold Mine Ltd", fuelType: "DIESEL", litres: 10000, tankerId: "t1", tankerRegistration: "AB 12 CD GP", driverEmployeeId: null, driverName: null, allocated: true, ...o })
const tankers = [tanker("t1", "AB 12 CD GP", { fleetVehicleId: "v1" }), tanker("t2", "EF 34 GH GP"), tanker("t3", "IJ 56 KL GP", { fleetVehicleId: "v3" }), tanker("t4", "OLD", { active: false })]

function mount() { render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><FleetMapTab /></QueryClientProvider>) }

describe("fleetMapOps", () => {
  it("says which tankers can be drawn and why the others cannot", () => {
    const rows = joinTankers(tankers as any, [pos("v1"), pos("vx")], [load()] as any)
    expect(rows.map(r => [r.tanker.registration, r.state])).toEqual([["AB 12 CD GP", "TRACKED"], ["EF 34 GH GP", "UNLINKED"], ["IJ 56 KL GP", "NOT_TRACKED"]])
    expect(joinTankers(tankers as any, [pos("v1", { latitude: 0, longitude: 0 })], [])[0].state).toBe("NO_FIX")
  })
  it("picks the load on the road, else the next one to leave", () => {
    const a = load({ deliveryId: "a", status: "SCHEDULED", scheduledAt: "2026-11-18T14:00:00Z" }), b = load({ deliveryId: "b", status: "SCHEDULED", scheduledAt: "2026-11-18T10:00:00Z" }), c = load({ deliveryId: "c", status: "IN_TRANSIT" })
    expect(currentLoad([a, b] as any)?.deliveryId).toBe("b")
    expect(currentLoad([a, b, c] as any)?.deliveryId).toBe("c")
    expect(currentLoad([load({ status: "DELIVERED" })] as any)).toBeNull()
  })
})

describe("FleetMapTab", () => {
  beforeEach(() => {
    get.mockReset()
    get.mockImplementation((u: string) => u.endsWith("/tankers") ? ok(tankers) : u.includes("/planning") ? ok({ date: "2026-11-18", loads: [load()], tankers: [] }) : ok([pos("v1")]))
  })
  afterEach(cleanup)

  it("draws tracked tankers by their own registration and shows today's load", async () => {
    mount()
    expect(await screen.findByTestId("map")).toBeTruthy()
    expect(screen.getByTestId("map").textContent).toBe("AB 12 CD GP|none")
    expect(screen.getByText(/Gold Mine Ltd/)).toBeTruthy()
    expect(screen.getByText("Not linked to a Fleet vehicle")).toBeTruthy()
    expect(screen.getByText("Linked, but not tracked")).toBeTruthy()
    expect(screen.queryByText("OLD")).toBeNull()
  })

  it("selects a tanker on the map from the list", async () => {
    mount()
    fireEvent.click(await screen.findByLabelText("Show AB 12 CD GP"))
    expect(screen.getByTestId("map").textContent).toBe("AB 12 CD GP|t1")
    expect((screen.getByLabelText("Show EF 34 GH GP") as HTMLButtonElement).disabled).toBe(true)
  })

  it("still lists tankers and loads when Fleet tracking cannot be read, and says why", async () => {
    get.mockImplementation((u: string) => u.endsWith("/tankers") ? ok(tankers) : u.includes("/planning") ? ok({ date: "x", loads: [load()], tankers: [] }) : Promise.reject({ response: { status: 403 } }))
    mount()
    expect(await screen.findByText(/Positions are not available/)).toBeTruthy()
    expect(screen.queryByTestId("map")).toBeNull()
    expect(screen.getByText(/Gold Mine Ltd/)).toBeTruthy()
  })
})
