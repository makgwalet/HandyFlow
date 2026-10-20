import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import LiveMapTab from "./LiveMapTab"
import { ageText, eventText, hasFix, liveCounts, speedText, visiblePositions, type LivePosition } from "./liveMap.logic"

const get = vi.fn(), post = vi.fn()
type Args = unknown[]
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: Args) => get(...a), post: (...a: Args) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
// Leaflet needs a real browser; the map component is replaced by a stub that shows what it was given.
vi.mock("./FleetMap", () => ({ default: ({ positions, selectedId, track }: { positions: LivePosition[]; selectedId: string | null; track: unknown[] }) => (
  <div data-testid="map">{positions.map(p => p.registration).join(",")}|{selectedId ?? "none"}|{track.length}</div>) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const pos = (o: Partial<LivePosition> & { registration: string }): LivePosition => ({ vehicleId: o.registration, make: "Toyota", model: "Hilux", latitude: -26.1, longitude: 28.0, speedKph: 0, heading: 0, ignitionOn: false, recordedAt: "2026-10-20T10:00:00Z", ageMinutes: 5, status: "PARKED", ...o })
const fleet = [pos({ registration: "PARK1" }), pos({ registration: "MOVE1", status: "MOVING", speedKph: 82 }), pos({ registration: "OFF1", status: "OFFLINE", ageMinutes: 3000 })]
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><LiveMapTab /></QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["FLEET_READ", "FLEET_MANAGE"]); get.mockReset(); post.mockReset(); post.mockResolvedValue({ data: { data: {} } })
  get.mockImplementation((url: string) => url.endsWith("/telematics/live") ? ok(fleet)
    : url.includes("/telematics/track") ? ok({ distanceKm: 42.3, totalPoints: 120, points: [{ latitude: -26, longitude: 28 }, { latitude: -26.2, longitude: 28.1 }] })
    : url.includes("/telematics/events") ? ok({ content: [{ id: "e1", registration: "MOVE1", driverName: "John", type: "SPEEDING", severity: "WARNING", occurredAt: "2026-10-20T09:00:00Z", value: 112, limitValue: 80, durationSeconds: null, geofenceName: null, acknowledged: false }] })
    : ok([]))
})
afterEach(cleanup)

describe("live map rules", () => {
  it("counts every status and orders moving vehicles first", () => {
    expect(liveCounts(fleet).map(c => c.count)).toEqual([1, 0, 0, 1, 1])
    expect(visiblePositions(fleet, "ALL", "").map(p => p.registration)).toEqual(["MOVE1", "PARK1", "OFF1"])
    expect(visiblePositions(fleet, "PARKED", "").map(p => p.registration)).toEqual(["PARK1"]); expect(visiblePositions(fleet, "ALL", "move hilux").map(p => p.registration)).toEqual(["MOVE1"])
  })
  it("words age, speed and fixes", () => {
    expect([0.5, 12, 180, 3000].map(ageText)).toEqual(["Just now", "12 min ago", "3 h ago", "2 days ago"]); expect([null, 82.4].map(speedText)).toEqual(["—", "82 km/h"])
    expect([hasFix({ latitude: -26, longitude: 28 }), hasFix({ latitude: 0, longitude: 0 }), hasFix({ latitude: NaN, longitude: 1 })]).toEqual([true, false, false])
  })
  it("describes events", () => {
    expect(eventText({ type: "SPEEDING", value: 112, limitValue: 80, geofenceName: null, durationSeconds: null })).toBe("Speeding: 112 km/h in a 80 km/h limit")
    expect(eventText({ type: "EXCESSIVE_IDLING", value: null, limitValue: null, geofenceName: null, durationSeconds: 900 })).toBe("Excessive idling: 15 min")
    expect(eventText({ type: "GEOFENCE_EXIT", value: null, limitValue: null, geofenceName: "Depot", durationSeconds: null })).toBe("Left zone: Depot")
  })
})

describe("LiveMapTab", () => {
  it("shows every tracked vehicle on the map and in the list", async () => {
    show(); expect((await screen.findByTestId("map")).textContent).toBe("MOVE1,PARK1,OFF1|none|0"); expect(screen.getByRole("button", { name: "All (3)" })).toBeTruthy()
  })
  it("filters by status", async () => {
    show(); await screen.findByTestId("map"); fireEvent.click(screen.getByRole("button", { name: /Moving \(1\)/ }))
    expect(screen.getByTestId("map").textContent).toBe("MOVE1|none|0"); expect(screen.queryByLabelText("Show PARK1")).toBeNull()
  })
  it("selects a vehicle, loads its 24 h track and says it is an estimate", async () => {
    show(); fireEvent.click(await screen.findByLabelText("Show MOVE1"))
    expect(await screen.findByText(/about 42.3 km/)).toBeTruthy(); expect(screen.getByText(/not the odometer/)).toBeTruthy(); expect(screen.getByTestId("map").textContent).toBe("MOVE1,PARK1,OFF1|MOVE1|2")
  })
  it("lists events to review and acknowledges one", async () => {
    show(); expect(await screen.findByText("Speeding: 112 km/h in a 80 km/h limit")).toBeTruthy()
    fireEvent.click(screen.getByText("Acknowledge")); await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fleet/telematics/events/e1/acknowledge"))
  })
  it("read-only users cannot acknowledge", async () => {
    perms = new Set(["FLEET_READ"]); show(); await screen.findByText("Speeding: 112 km/h in a 80 km/h limit"); expect(screen.queryByText("Acknowledge")).toBeNull()
  })
  it("explains an empty fleet", async () => {
    get.mockImplementation((url: string) => url.endsWith("/telematics/live") ? ok([]) : ok({ content: [] }))
    show(); expect(await screen.findByText(/No tracked vehicles yet/)).toBeTruthy()
  })
})
