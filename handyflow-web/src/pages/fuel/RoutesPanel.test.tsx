import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import RoutesPanel from "./RoutesPanel"
import type { PlanLoad, Tanker, RouteView } from "./transportOps"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn(); const del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))

const tanker: Tanker = { id: "t1", registration: "GP 12 AB", name: null, active: true, totalCapacity: 16000, openLoads: 0, compartments: [{ id: "c1", number: 1, capacityLitres: 10000 }, { id: "c2", number: 2, capacityLitres: 6000 }] } as any
const plan = (o: any = {}): PlanLoad => ({ deliveryId: "d9", status: "SCHEDULED", scheduledAt: "2026-11-13T09:00:00Z", customerId: "c", customerName: "Held Farm", fuelType: "DIESEL", litres: 12000, tankerId: null, tankerRegistration: null, driverEmployeeId: null, driverName: null, allocated: false, ...o })
const stop = (id: string, seq: number, status = "SCHEDULED") => ({ deliveryId: id, sequence: seq, customerName: `Cust ${id}`, fuelType: "DIESEL", litres: 3000, status, scheduledAt: "2026-11-13T08:00:00Z", windowStart: null, windowEnd: null, failedAttempts: 0 })
const route = (o: any = {}): RouteView => ({ id: "r1", routeDate: "2026-11-13", label: "Morning run", status: "PLANNED", state: "PLANNED", tankerId: "t1", tankerRegistration: "GP 12 AB", driverEmployeeId: "e1", driverName: "Moses Sithole",
  capacityLitres: 16000, plannedLitres: 6000, stopCount: 2, deliveredCount: 0, cancelReason: null, stops: [stop("a", 1), stop("b", 2)], ...o })
let routes: RouteView[]; let canManage = true; let loads: PlanLoad[]
const onChanged = vi.fn()

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><RoutesPanel date="2026-11-13" loads={loads} tankers={[tanker]} canManage={canManage} onChanged={onChanged} /></QueryClientProvider>)
}

describe("RoutesPanel", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset(); onChanged.mockReset(); canManage = true; loads = [plan()]; routes = [route()]
    get.mockImplementation(async (u: string) => u.endsWith("/drivers") ? { data: { data: [{ id: "e1", name: "Moses Sithole", jobTitle: "Driver", phone: null }] } } : { data: { data: routes } })
    post.mockResolvedValue({ data: { data: {} } }); put.mockResolvedValue({ data: { data: {} } }); del.mockResolvedValue({ data: { data: {} } })
  })
  afterEach(() => cleanup())

  it("shows a route with its stops in order, litres against capacity and progress", async () => {
    mount()
    await waitFor(() => screen.getByText("Morning run"))
    expect(screen.getByText("Cust a")).toBeTruthy(); expect(screen.getByText("Cust b")).toBeTruthy()
    expect(screen.getByText(/0 of 2 delivered/)).toBeTruthy()
  })

  it("says so when there are no routes", async () => {
    routes = []; mount()
    await waitFor(() => screen.getByText(/No routes for this day/))
  })

  it("moves a stop down by sending the full new order", async () => {
    mount()
    await waitFor(() => screen.getByLabelText("Move stop 1 down"))
    fireEvent.click(screen.getByLabelText("Move stop 1 down"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/routes/r1/stop-order", { deliveryIds: ["b", "a"] }))
  })

  it("does not let a delivered stop move or be removed", async () => {
    routes = [route({ stops: [stop("a", 1, "DELIVERED"), stop("b", 2)] })]; mount()
    await waitFor(() => screen.getByLabelText("Move stop 1 down"))
    expect((screen.getByLabelText("Move stop 1 down") as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByLabelText("Remove stop 1") as HTMLButtonElement).disabled).toBe(true)
    expect(screen.queryByText("Cancel route")).toBeNull()
  })

  it("removes a stop", async () => {
    mount()
    await waitFor(() => screen.getByLabelText("Remove stop 2"))
    fireEvent.click(screen.getByLabelText("Remove stop 2"))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/fuel/routes/r1/stops/b"))
  })

  it("adds a stop with an automatic compartment split, and refuses a split that does not add up", async () => {
    mount()
    await waitFor(() => screen.getByText("Add stop"))
    fireEvent.click(screen.getByText("Add stop"))
    fireEvent.change(screen.getByLabelText("Load"), { target: { value: "d9" } })
    expect((screen.getByLabelText("Compartment 1") as HTMLInputElement).value).toBe("10000")
    expect((screen.getByLabelText("Compartment 2") as HTMLInputElement).value).toBe("2000")
    fireEvent.change(screen.getByLabelText("Compartment 2"), { target: { value: "1000" } })
    fireEvent.click(screen.getAllByText("Add stop").pop()!)
    expect(screen.getByRole("alert").textContent).toMatch(/add up/)
    fireEvent.change(screen.getByLabelText("Compartment 2"), { target: { value: "2000" } })
    fireEvent.click(screen.getAllByText("Add stop").pop()!)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/routes/r1/stops", { deliveryId: "d9", loads: [{ compartmentId: "c1", litres: 10000 }, { compartmentId: "c2", litres: 2000 }] }))
  })

  it("shows the server's refusal when a stop does not fit", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "Compartment 1 holds 10000 L and other stops on this route already use 4000 L of it." } } })
    mount()
    await waitFor(() => screen.getByText("Add stop"))
    fireEvent.click(screen.getByText("Add stop"))
    fireEvent.change(screen.getByLabelText("Load"), { target: { value: "d9" } })
    fireEvent.click(screen.getAllByText("Add stop").pop()!)
    await waitFor(() => expect(screen.getByRole("alert").textContent).toMatch(/already use/))
  })

  it("creates a route for the day with a tanker and driver", async () => {
    routes = []; mount()
    await waitFor(() => screen.getByText("New route"))
    fireEvent.click(screen.getByText("New route"))
    fireEvent.click(screen.getByText("Create route"))
    expect(screen.getByRole("alert").textContent).toMatch(/tanker and a driver/)
    await waitFor(() => screen.getByText("Moses Sithole"))
    fireEvent.change(screen.getByLabelText("Route tanker"), { target: { value: "t1" } })
    fireEvent.change(screen.getByLabelText("Route driver"), { target: { value: "e1" } })
    fireEvent.change(screen.getByLabelText("Route name"), { target: { value: "Morning run" } })
    fireEvent.click(screen.getByText("Create route"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/routes", { routeDate: "2026-11-13", label: "Morning run", tankerId: "t1", driverEmployeeId: "e1" }))
  })

  it("cancels a route only with a reason", async () => {
    mount()
    await waitFor(() => screen.getByText("Cancel route"))
    fireEvent.click(screen.getByText("Cancel route"))
    fireEvent.click(screen.getAllByText("Cancel route").pop()!)
    expect(screen.getByRole("alert").textContent).toMatch(/reason/)
    fireEvent.change(screen.getByLabelText("Cancel reason"), { target: { value: "Customer closed" } })
    fireEvent.click(screen.getAllByText("Cancel route").pop()!)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/routes/r1/cancel", { reason: "Customer closed" }))
  })

  it("hides every change control without the manage permission, and shows a cancelled route's reason", async () => {
    canManage = false; routes = [route(), route({ id: "r2", label: "Old run", state: "CANCELLED", status: "CANCELLED", cancelReason: "Fuel shortage", stops: [] })]; mount()
    await waitFor(() => screen.getByText("Morning run"))
    expect(screen.queryByText("New route")).toBeNull(); expect(screen.queryByText("Add stop")).toBeNull(); expect(screen.queryByLabelText("Move stop 1 down")).toBeNull()
    expect(screen.getByText(/Cancelled: Fuel shortage/)).toBeTruthy()
  })
  const suggestion = (o: any = {}) => ({ routeId: "r1", method: "STRAIGHT_LINE", changed: true, currentKm: 60, suggestedKm: 48.5, savingKm: 11.5, sitesWithoutPoint: [], note: "Straight-line distances, so shorter than by road and blind to traffic.",
    stops: [{ deliveryId: "b", sequence: 1, previousSequence: 2, customerName: "Cust b", status: "SCHEDULED", movable: true, moved: true, windowStart: null, windowEnd: null, hasPoint: true },
            { deliveryId: "a", sequence: 2, previousSequence: 1, customerName: "Cust a", status: "SCHEDULED", movable: true, moved: true, windowStart: null, windowEnd: null, hasPoint: true }], ...o })
  const withSuggestion = (o: any = {}) => get.mockImplementation(async (u: string) => u.endsWith("/drivers") ? { data: { data: [] } } : u.endsWith("/suggested-order") ? { data: { data: suggestion(o) } } : { data: { data: routes } })

  it("shows a suggested order and applies it with the normal stop-order call", async () => {
    withSuggestion(); mount()
    await waitFor(() => screen.getByText("Suggest order"))
    fireEvent.click(screen.getByText("Suggest order"))
    await waitFor(() => screen.getByText(/11\.5 km shorter/))
    expect(get.mock.calls.some(c => c[0] === "/api/v1/fuel/routes/r1/suggested-order")).toBe(true)
    fireEvent.click(screen.getByText(/Use this order \(2 moved\)/))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/routes/r1/stop-order", { deliveryIds: ["b", "a"] }))
  })

  it("keeping my order saves nothing", async () => {
    withSuggestion(); mount()
    await waitFor(() => screen.getByText("Suggest order"))
    fireEvent.click(screen.getByText("Suggest order"))
    await waitFor(() => screen.getByText(/km shorter/))
    fireEvent.click(screen.getByText("Keep my order"))
    expect(put).not.toHaveBeenCalled()
  })

  it("names the sites with no map position and cannot apply an unchanged order", async () => {
    withSuggestion({ method: "WINDOWS_AND_DISTANCE", changed: false, savingKm: null, currentKm: null, suggestedKm: null, sitesWithoutPoint: ["Cust a"], note: "Some sites have no map position." }); mount()
    await waitFor(() => screen.getByText("Suggest order"))
    fireEvent.click(screen.getByText("Suggest order"))
    await waitFor(() => screen.getByText(/No map position for: Cust a/))
    expect((screen.getByText(/Use this order/) as HTMLButtonElement).disabled).toBe(true)
  })

  it("offers no suggestion when fewer than two stops can still move, or without the manage permission", async () => {
    routes = [route({ stops: [stop("a", 1, "DELIVERED"), stop("b", 2)] })]; withSuggestion(); mount()
    await waitFor(() => screen.getByText(/Morning run/))
    expect(screen.queryByText("Suggest order")).toBeNull()
    cleanup(); routes = [route()]; canManage = false; withSuggestion(); mount()
    await waitFor(() => screen.getByText(/Morning run/))
    expect(screen.queryByText("Suggest order")).toBeNull()
  })
})
