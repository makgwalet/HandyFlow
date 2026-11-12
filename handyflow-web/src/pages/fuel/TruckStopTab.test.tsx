import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TruckStopTab from "./TruckStopTab"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const offering = (o: any = {}) => ({ id: "o1", bookingsServiceId: "s1", serviceName: "Shower", kind: "SHOWER", depotId: null, active: true, durationMinutes: 30, price: 40, resources: [], serviceMissing: false, ...o })
const reservation = (o: any = {}) => ({ id: "r1", offeringId: "o1", kind: "SHOWER", serviceName: "Shower", bookingId: "b1", bookingNumber: "BK-1", vehicleRegistration: "CY 1 GP", driverName: "Moses", driverPhone: null, resourceName: "Bay 1", date: "2026-11-14", start: "09:00:00", end: "09:30:00", status: "CONFIRMED", price: 40, ...o })
let offs: any[]; let res: any[]

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><TruckStopTab /></QueryClientProvider>)
}

describe("TruckStopTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset(); perms = ["FUEL_MANAGE"]
    offs = [offering()]; res = [reservation()]
    post.mockResolvedValue({ data: { data: {} } }); put.mockResolvedValue({ data: { data: {} } })
    get.mockImplementation(async (u: string) => {
      if (u.endsWith("/offerings")) return { data: { data: offs } }
      if (u.endsWith("/reservations")) return { data: { data: res } }
      if (u.endsWith("/slots")) return { data: { data: [{ start: "09:00:00", end: "09:30:00", label: "09:00 – 09:30" }] } }
      if (u.endsWith("/available-services")) return { data: { data: [{ id: "s2", name: "Truck wash", durationMinutes: 45, price: 120 }] } }
      return { data: { data: [] } }
    })
  })
  afterEach(() => cleanup())

  it("shows the services and the reservations with their truck, driver, bay and status", async () => {
    mount()
    await waitFor(() => screen.getByText("CY 1 GP"))
    expect(screen.getAllByText("Shower").length).toBeGreaterThan(0)
    expect(screen.getByText("Bay 1")).toBeTruthy()
    expect(screen.getByText("Confirmed")).toBeTruthy()
  })

  it("reserves a slot for a truck", async () => {
    mount()
    await waitFor(() => expect((screen.getByText("Reserve") as HTMLButtonElement).disabled).toBe(false))
    fireEvent.click(screen.getByText("Reserve"))
    await waitFor(() => screen.getByText("09:00 – 09:30"))
    fireEvent.click(screen.getAllByText("Reserve").pop()!)
    expect(screen.getByRole("alert").textContent).toMatch(/time slot|registration/)
    fireEvent.change(screen.getByLabelText("Time"), { target: { value: "09:00:00" } })
    fireEvent.change(screen.getByLabelText("Vehicle registration"), { target: { value: "CY 2 GP" } })
    fireEvent.click(screen.getAllByText("Reserve").pop()!)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/reservations", expect.objectContaining({ offeringId: "o1", start: "09:00:00", vehicleRegistration: "CY 2 GP", resourceId: null })))
  })

  it("a bay must be chosen when the service has bays", async () => {
    offs = [offering({ resources: [{ id: "b1", name: "Bay 1" }] })]
    mount()
    await waitFor(() => expect((screen.getByText("Reserve") as HTMLButtonElement).disabled).toBe(false))
    fireEvent.click(screen.getByText("Reserve"))
    await waitFor(() => screen.getByLabelText("Bay"))
    fireEvent.change(screen.getByLabelText("Vehicle registration"), { target: { value: "CY 2 GP" } })
    fireEvent.click(screen.getAllByText("Reserve").pop()!)
    expect(screen.getByRole("alert").textContent).toMatch(/bay/i)
    expect(post).not.toHaveBeenCalled()
  })

  it("cancels a live reservation, and offers nothing for a finished one", async () => {
    res = [reservation(), reservation({ id: "r2", vehicleRegistration: "DONE 1", status: "COMPLETED" })]
    mount()
    await waitFor(() => screen.getByText("DONE 1"))
    expect(screen.getAllByText("Cancel")).toHaveLength(1)
    fireEvent.click(screen.getByText("Cancel"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/reservations/r1/cancel", {}))
  })

  it("adds a Bookings service as a truck stop service", async () => {
    mount()
    await waitFor(() => screen.getByText("Add service"))
    fireEvent.click(screen.getByText("Add service"))
    await waitFor(() => screen.getByText(/Truck wash/))
    fireEvent.click(screen.getAllByText("Add service").pop()!)
    expect(screen.getByRole("alert").textContent).toMatch(/Bookings service/)
    fireEvent.change(screen.getByLabelText("Bookings service"), { target: { value: "s2" } })
    fireEvent.change(screen.getByLabelText("Kind"), { target: { value: "WASH" } })
    fireEvent.click(screen.getAllByText("Add service").pop()!)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/offerings", { bookingsServiceId: "s2", kind: "WASH" }))
  })

  it("a read-only user sees reservations but no buttons", async () => {
    perms = ["FUEL_READ"]
    mount()
    await waitFor(() => screen.getByText("CY 1 GP"))
    expect(screen.queryByText("Reserve")).toBeNull()
    expect(screen.queryByText("Cancel")).toBeNull()
    expect(screen.queryByText("Add service")).toBeNull()
  })
})
