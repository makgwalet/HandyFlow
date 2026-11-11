import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseDockTab, { type Appointment } from "./WhseDockTab"
import { dayBounds, lateLabel, nextStep, slotProblem, toInstant, localDay } from "./dock"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))

const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const appt = (over: Partial<Appointment> = {}): Appointment => ({ id: "A1", direction: "INBOUND", dockLocationId: "D1", dockCode: "DOCK-1", startsAt: "2026-11-12T08:00:00Z", endsAt: "2026-11-12T09:00:00Z",
  carrier: "Acme Freight", vehicleRegistration: "CA 123", driverName: null, shipmentReference: "PO-7", manifestNumber: null, notes: null, status: "SCHEDULED", arrivedAt: null,
  cancelReason: null, lateMinutes: 0, ...over })

function serve(list: Appointment[]) {
  get.mockImplementation(async (url: string) => {
    if (url.includes("/dock-appointments")) return { data: list }
    if (url.endsWith("/warehousing/locations")) return { data: [{ id: "D1", code: "DOCK-1", active: true, locationType: "DOCK" }, { id: "B1", code: "A-01", active: true, locationType: "BIN" }] }
    return { data: [] }
  })
}

beforeEach(() => { get.mockReset(); post.mockReset(); post.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("dock helpers", () => {
  it("a day covers 24 hours of local time", () => {
    const { from, to } = dayBounds("2026-11-12")
    expect(new Date(to).getTime() - new Date(from).getTime()).toBe(24 * 3600 * 1000)
    expect(new Date(from).getHours()).toBe(0)
  })
  it("reads a local date-time input, and rejects blank or junk", () => {
    expect(toInstant("2026-11-12T08:00")).toBe(new Date("2026-11-12T08:00").toISOString())
    expect(toInstant("")).toBeNull()
    expect(toInstant("nonsense")).toBeNull()
    expect(localDay(new Date(2026, 10, 5))).toBe("2026-11-05")
  })
  it("a slot must be 5 minutes to 12 hours", () => {
    expect(slotProblem("", "")).toMatch(/starts and ends/)
    expect(slotProblem("2026-11-12T09:00", "2026-11-12T08:00")).toMatch(/end after/)
    expect(slotProblem("2026-11-12T08:00", "2026-11-12T08:03")).toMatch(/at least 5/)
    expect(slotProblem("2026-11-12T08:00", "2026-11-12T21:00")).toMatch(/at most 12/)
    expect(slotProblem("2026-11-12T08:00", "2026-11-12T09:00")).toBeNull()
  })
  it("the next step follows the day, and a finished appointment has none", () => {
    expect(nextStep("SCHEDULED")?.path).toBe("arrived")
    expect(nextStep("ARRIVED")?.path).toBe("at-dock")
    expect(nextStep("AT_DOCK")?.path).toBe("complete")
    expect(nextStep("COMPLETED")).toBeNull()
    expect(nextStep("NO_SHOW")).toBeNull()
  })
  it("says how late a vehicle was", () => {
    expect(lateLabel(0)).toBeNull()
    expect(lateLabel(25)).toBe("25 min late")
    expect(lateLabel(95)).toBe("1 h 35 min late")
  })
})

describe("dock tab", () => {
  it("lists the day's appointments with their carrier details", async () => {
    serve([appt(), appt({ id: "A2", status: "ARRIVED", dockCode: "DOCK-2", lateMinutes: 20 })])
    wrap(<WhseDockTab />)
    await waitFor(() => expect(screen.getAllByText(/Acme Freight/).length).toBe(2))
    expect(screen.getByText("Scheduled")).toBeTruthy()
    expect(screen.getByText("20 min late")).toBeTruthy()
    expect(screen.getByText(/2 appointments · 2 still to do/)).toBeTruthy()
  })

  it("walks an appointment on to the next step", async () => {
    serve([appt()])
    wrap(<WhseDockTab />)
    await waitFor(() => expect(screen.getByText("Vehicle arrived")).toBeTruthy())
    fireEvent.click(screen.getByText("Vehicle arrived"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/dock-appointments/A1/arrived"))
  })

  it("a finished appointment has no actions", async () => {
    serve([appt({ status: "COMPLETED" })])
    wrap(<WhseDockTab />)
    await waitFor(() => expect(screen.getByText("Completed")).toBeTruthy())
    expect(screen.queryByText("Cancel")).toBeNull()
    expect(screen.queryByText("No-show")).toBeNull()
  })

  it("books a slot on a dock door only", async () => {
    serve([])
    wrap(<WhseDockTab />)
    await waitFor(() => expect(screen.getByLabelText("Dock door")).toBeTruthy())
    await waitFor(() => expect(screen.getByText("DOCK-1")).toBeTruthy())
    expect(screen.queryByText("A-01")).toBeNull()
    expect((screen.getByText("Book") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Dock door"), { target: { value: "D1" } })
    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2026-11-12T08:00" } })
    fireEvent.change(screen.getByLabelText("To"), { target: { value: "2026-11-12T09:00" } })
    fireEvent.change(screen.getByLabelText("Carrier"), { target: { value: "Acme" } })
    fireEvent.click(screen.getByText("Book"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/dock-appointments", {
      direction: "INBOUND", dockLocationId: "D1", startsAt: new Date("2026-11-12T08:00").toISOString(), endsAt: new Date("2026-11-12T09:00").toISOString(),
      carrier: "Acme", vehicleRegistration: null, driverName: null, inboundShipmentId: null, manifestId: null }))
  })

  it("explains when there are no dock doors", async () => {
    get.mockImplementation(async (url: string) => (url.includes("/dock-appointments") ? { data: [] } : { data: [{ id: "B1", code: "A-01", active: true, locationType: "BIN" }] }))
    wrap(<WhseDockTab />)
    await waitFor(() => expect(screen.getByText(/no dock doors yet/)).toBeTruthy())
  })
})
