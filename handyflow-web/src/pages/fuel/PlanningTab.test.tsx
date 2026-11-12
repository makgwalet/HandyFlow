import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import PlanningTab from "./PlanningTab"

const get = vi.fn(); const post = vi.fn(); const del = vi.fn(); const put = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), delete: (...a: unknown[]) => del(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const tanker = { id: "t1", registration: "GP 12 AB", name: null, active: true, totalCapacity: 16000, openLoads: 0, compartments: [{ id: "c1", number: 1, capacityLitres: 10000 }, { id: "c2", number: 2, capacityLitres: 6000 }] }
const load = (o: any = {}) => ({ deliveryId: "d1", status: "SCHEDULED", scheduledAt: "2026-11-12T08:00:00Z", customerId: "c", customerName: "Gold Mine Ltd", fuelType: "DIESEL", litres: 12000, tankerId: null, tankerRegistration: null, driverEmployeeId: null, driverName: null, allocated: false, ...o })
let plan: any
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><PlanningTab /></QueryClientProvider>)
}

describe("PlanningTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); del.mockReset(); put.mockReset(); put.mockResolvedValue({ data: { data: null } }); perms = ["FUEL_MANAGE"]
    plan = { date: "2026-11-12", loads: [load(), load({ deliveryId: "d2", customerName: "Held Farm", allocated: true, tankerRegistration: "GP 12 AB", driverName: "Moses Sithole" })], tankers: [{ tankerId: "t1", registration: "GP 12 AB", capacity: 16000, loads: 1, litres: 12000 }] }
    post.mockResolvedValue({ data: { data: {} } })
    get.mockImplementation(async (u: string) => {
      if (u.endsWith("/planning")) return { data: { data: plan } }
      if (u.endsWith("/tankers")) return { data: { data: [tanker] } }
      if (u.endsWith("/drivers")) return { data: { data: [{ id: "e1", name: "Moses Sithole", jobTitle: "Driver", phone: null }] } }
      return { data: { data: [] } }
    })
  })
  afterEach(() => cleanup())

  it("highlights unallocated loads and shows tanker utilisation", async () => {
    mount()
    await waitFor(() => screen.getByText("Gold Mine Ltd"))
    expect(screen.getByText("Unallocated")).toBeTruthy()
    expect(screen.getByText(/1 load need a tanker and driver/)).toBeTruthy()
    expect(screen.getByText(/capacity 16/)).toBeTruthy()
  })

  it("allocates: auto-splits across compartments and posts tanker, driver and split", async () => {
    mount()
    await waitFor(() => screen.getByText("Allocate"))
    fireEvent.click(screen.getByText("Allocate"))
    await waitFor(() => screen.getByLabelText("Tanker"))
    fireEvent.change(screen.getByLabelText("Tanker"), { target: { value: "t1" } })
    expect((screen.getByLabelText("Compartment 1") as HTMLInputElement).value).toBe("10000")
    expect((screen.getByLabelText("Compartment 2") as HTMLInputElement).value).toBe("2000")
    fireEvent.click(screen.getAllByText("Allocate").pop()!)
    expect(screen.getByRole("alert").textContent).toMatch(/driver/)
    await waitFor(() => screen.getByText(/Moses Sithole \(Driver\)/))
    fireEvent.change(screen.getByLabelText("Driver"), { target: { value: "e1" } })
    fireEvent.click(screen.getAllByText("Allocate").pop()!)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/deliveries/d1/allocate", {
      tankerId: "t1", driverEmployeeId: "e1", loads: [{ compartmentId: "c1", litres: 10000 }, { compartmentId: "c2", litres: 2000 }] }))
  })

  it("refuses a split that does not add up, and shows the server's double-booking message", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "GP 12 AB is already on another load within 4 hours of this one." } } })
    mount()
    await waitFor(() => screen.getByText("Allocate"))
    fireEvent.click(screen.getByText("Allocate"))
    await waitFor(() => screen.getByLabelText("Tanker"))
    fireEvent.change(screen.getByLabelText("Tanker"), { target: { value: "t1" } })
    await waitFor(() => screen.getByText(/Moses Sithole \(Driver\)/))
    fireEvent.change(screen.getByLabelText("Driver"), { target: { value: "e1" } })
    fireEvent.change(screen.getByLabelText("Compartment 2"), { target: { value: "1000" } })
    fireEvent.click(screen.getAllByText("Allocate").pop()!)
    expect(screen.getByRole("alert").textContent).toMatch(/add up/)
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Compartment 2"), { target: { value: "2000" } })
    fireEvent.click(screen.getAllByText("Allocate").pop()!)
    await waitFor(() => expect(screen.getByRole("alert").textContent).toMatch(/already on another load/))
  })

  it("adds a tanker from a list of compartment capacities", async () => {
    mount()
    await waitFor(() => screen.getByText("Add tanker"))
    fireEvent.click(screen.getByText("Add tanker"))
    fireEvent.click(screen.getByText("Save tanker"))
    expect(screen.getByRole("alert").textContent).toMatch(/registration/)
    fireEvent.change(screen.getByLabelText("Registration"), { target: { value: "GP 99 ZZ" } })
    fireEvent.change(screen.getByLabelText("Compartment capacities"), { target: { value: "8000, 8000" } })
    fireEvent.click(screen.getByText("Save tanker"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/tankers", { registration: "GP 99 ZZ", name: null, compartmentCapacities: [8000, 8000], fleetVehicleId: null }))
  })

  it("C03: choosing a Fleet vehicle sends its id and uses Fleet's registration", async () => {
    get.mockImplementation(async (u: string) => {
      if (u.endsWith("/planning")) return { data: { data: plan } }
      if (u.endsWith("/tankers/fleet-vehicles")) return { data: { data: [
        { id: "v1", registration: "CY 1", label: "CY 1 · Volvo FM", linkedTankerRegistration: null },
        { id: "v2", registration: "CY 2", label: "CY 2 · Scania", linkedTankerRegistration: "CY 2" }] } }
      if (u.endsWith("/tankers")) return { data: { data: [tanker] } }
      return { data: { data: [] } }
    })
    mount()
    await waitFor(() => screen.getByText("Add tanker"))
    fireEvent.click(screen.getByText("Add tanker"))
    const pick = await screen.findByLabelText("Fleet vehicle")
    expect((screen.getByText(/CY 2 · Scania/) as HTMLOptionElement).disabled).toBe(true)
    fireEvent.change(pick, { target: { value: "v1" } })
    fireEvent.change(screen.getByLabelText("Compartment capacities"), { target: { value: "8000" } })
    fireEvent.click(screen.getByText("Save tanker"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/tankers", { registration: "CY 1", name: null, compartmentCapacities: [8000], fleetVehicleId: "v1" }))
  })

  it("a read-only user cannot allocate or add tankers", async () => {
    perms = []; mount()
    await waitFor(() => screen.getByText("Gold Mine Ltd"))
    expect(screen.queryByText("Allocate")).toBeNull()
    expect(screen.queryByText("Add tanker")).toBeNull()
  })

  it("shows the window and failed attempts, and sets a window", async () => {
    plan = { ...plan, loads: [load({ windowStart: "2026-11-12T06:00:00Z", windowEnd: "2026-11-12T10:00:00Z", failedAttempts: 2 })] }
    mount()
    await waitFor(() => screen.getByText("Gold Mine Ltd"))
    expect(screen.getByText("2 failed attempts")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Window" }))
    fireEvent.change(screen.getByLabelText("Window from"), { target: { value: "2026-11-12T10:00" } })
    fireEvent.change(screen.getByLabelText("Window to"), { target: { value: "2026-11-12T09:00" } })
    fireEvent.click(screen.getByText("Save window"))
    expect(screen.getByRole("alert").textContent).toMatch(/end after/)
    fireEvent.change(screen.getByLabelText("Window to"), { target: { value: "2026-11-12T12:00" } })
    fireEvent.click(screen.getByText("Save window"))
    await waitFor(() => expect(put).toHaveBeenCalledTimes(1))
    expect(put.mock.calls[0][0]).toBe("/api/v1/fuel/deliveries/d1/window")
    expect(typeof put.mock.calls[0][1].windowStart).toBe("string")
  })

  it("records a failed delivery for a load on the road and not for a scheduled one", async () => {
    plan = { ...plan, loads: [load({ status: "IN_TRANSIT", allocated: true }), load({ deliveryId: "d9", customerName: "Quiet Farm" })] }
    mount()
    await waitFor(() => screen.getByText("Gold Mine Ltd"))
    expect(screen.getAllByText("Could not deliver")).toHaveLength(1)
    fireEvent.click(screen.getByText("Could not deliver"))
    fireEvent.click(screen.getByText("Record failed delivery"))
    expect(screen.getByRole("alert").textContent).toMatch(/reason/i)
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "REFUSED" } })
    fireEvent.click(screen.getByText("Record failed delivery"))
    await waitFor(() => expect(post).toHaveBeenCalledTimes(1))
    expect(post.mock.calls[0][0]).toBe("/api/v1/fuel/deliveries/d1/failed-attempt")
    expect(post.mock.calls[0][1]).toMatchObject({ reasonCode: "REFUSED", litresReturned: null })
  })
})
