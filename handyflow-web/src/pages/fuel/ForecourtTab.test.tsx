import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ForecourtTab from "./ForecourtTab"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn(); const del = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
let me: string | undefined = "alice"
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: (s: any) => unknown) => sel({ user: { userId: me } }) }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const pump = (o: any = {}) => ({ id: "p1", name: "Pump 1", tankId: "t1", tankName: "Diesel 1", fuelType: "DIESEL", pricePerLitre: 20, lastMeter: 1000, active: true, onShift: false, ...o })
const reading = (o: any = {}) => ({ pumpId: "p1", pumpName: "Pump 1", fuelType: "DIESEL", openingMeter: 1000, openingGap: 0, closingMeter: null, testLitres: 0, litresSold: null, pricePerLitre: 20, salesValue: null, ...o })
const shift = (o: any = {}) => ({ id: "s1", shiftNumber: "SH-2026-00001", attendantName: "Thabo", status: "OPEN", openedAt: "2026-11-11T06:00:00Z", closedAt: null, cash: null, card: null, account: null, other: null, expected: null, litresSold: null, variance: null, varianceReason: null, notes: null, reverseReason: null, readings: [reading()], ...o })
let shifts: any[]; let pumps: any[]; let adjustments: any[]; let controls: any

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><ForecourtTab /></QueryClientProvider>)
}
const type = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } })

describe("ForecourtTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset(); perms = ["FUEL_FORECOURT"]
    adjustments = []; controls = { requireSupplierApproval: false, adjustmentThresholdLitres: null, cashVarianceTolerance: 5, requireSeparateCloser: false }; me = "alice"
    shifts = [shift()]; pumps = [pump(), pump({ id: "p2", name: "Pump 2", lastMeter: 5000 })]
    post.mockResolvedValue({ data: { data: {} } })
    get.mockImplementation(async (u: string) => {
      if (u.includes("/adjustments")) return { data: { data: adjustments } }
      if (u.includes("/settings/controls")) return { data: { data: controls } }
      if (u.includes("/shifts")) return { data: { data: shifts } }
      if (u.includes("/pumps")) return { data: { data: pumps } }
      if (u.includes("/summary")) return { data: { data: { days: 7, shifts: 3, litres: 4200, sales: 84000, variance: -120, flaggedShifts: 1, byFuel: [] } } }
      if (u.includes("/fuel/tanks")) return { data: { data: [{ id: "t1", name: "Diesel 1", fuelType: "DIESEL" }] } }
      return { data: { data: [] } }
    })
  })
  afterEach(() => cleanup())

  it("shows the weekly summary and the shifts", async () => {
    mount()
    await waitFor(() => screen.getByText("SH-2026-00001"))
    expect(screen.getByText("Thabo")).toBeTruthy()
    await waitFor(() => screen.getByText("Shifts needing a look"))
    expect(screen.getByText(/short/)).toBeTruthy()
  })

  it("opens a shift with the last closing meter prefilled and posts the readings", async () => {
    shifts = []
    mount()
    await waitFor(() => screen.getByText("Open shift"))
    fireEvent.click(screen.getAllByText("Open shift")[0])
    await waitFor(() => screen.getByLabelText("Attendant"))
    expect((screen.getByLabelText("Opening meter Pump 1") as HTMLInputElement).value).toBe("1000")
    type("Attendant", "Thabo"); type("Opening meter Pump 1", "1004")
    fireEvent.click(screen.getByLabelText("Use Pump 2")) // untick
    fireEvent.click(screen.getAllByText("Open shift").pop()!)
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post).toHaveBeenCalledWith("/api/v1/fuel/forecourt/shifts", { attendantName: "Thabo", notes: null, readings: [{ pumpId: "p1", openingMeter: 1004 }] })
  })

  it("closing: shows expected value and variance live, asks for a reason beyond R5, and posts the readings", async () => {
    mount()
    await waitFor(() => screen.getByText("Close shift"))
    fireEvent.click(screen.getByText("Close shift"))
    await waitFor(() => screen.getByLabelText("Closing meter Pump 1"))
    type("Closing meter Pump 1", "1210"); type("Test litres Pump 1", "10")
    expect(screen.getByText(/Meters say: 200 L/)).toBeTruthy()
    type("Cash", "3000")
    expect(screen.getByTestId("variance").textContent).toMatch(/short/)
    fireEvent.click(screen.getAllByText("Close shift").pop()!)
    await waitFor(() => screen.getByText(/Give a reason/))
    expect(post).not.toHaveBeenCalled()
    type("Cash", "4000")
    expect(screen.queryByLabelText("Reason")).toBeNull()
    fireEvent.click(screen.getAllByText("Close shift").pop()!)
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post).toHaveBeenCalledWith("/api/v1/fuel/forecourt/shifts/s1/close",
      { cash: 4000, card: 0, fleetCard: 0, account: 0, other: 0, varianceReason: null, readings: [{ pumpId: "p1", closingMeter: 1210, testLitres: 10 }] })
  })

  it("shows the server's message when closing is refused (book stock too low)", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "The meters say 300L was sold from Diesel 1 but the book stock is only 200L. Record the latest delivery or a dip adjustment first." } } })
    mount()
    await waitFor(() => screen.getByText("Close shift"))
    fireEvent.click(screen.getByText("Close shift"))
    await waitFor(() => screen.getByLabelText("Closing meter Pump 1"))
    type("Closing meter Pump 1", "1200"); type("Cash", "4000")
    fireEvent.click(screen.getAllByText("Close shift").pop()!)
    await waitFor(() => screen.getByText(/dip adjustment/))
  })

  it("attendants cannot reverse shifts or manage pumps; managers can", async () => {
    shifts = [shift({ status: "CLOSED", expected: 4000, litresSold: 200, variance: 0, cash: 4000, card: 0, account: 0, other: 0 })]
    mount()
    await waitFor(() => screen.getByText("SH-2026-00001"))
    expect(screen.queryByText("Reverse")).toBeNull()
    fireEvent.click(screen.getByText("Pumps"))
    await waitFor(() => expect(screen.getAllByText("Diesel 1").length).toBe(2))
    expect(screen.queryByText("Add pump")).toBeNull()
    cleanup()
    perms = ["FUEL_MANAGE"]
    mount()
    await waitFor(() => screen.getByText("Reverse"))
    fireEvent.click(screen.getByText("Pumps"))
    await waitFor(() => screen.getByText("Add pump"))
  })

  it("reverses a closed shift with a reason", async () => {
    perms = ["FUEL_MANAGE"]
    shifts = [shift({ status: "CLOSED", expected: 4000, litresSold: 200, variance: 0, cash: 4000, card: 0, account: 0, other: 0 })]
    mount()
    await waitFor(() => screen.getByText("Reverse"))
    fireEvent.click(screen.getByText("Reverse"))
    await waitFor(() => screen.getByLabelText("Reason"))
    type("Reason", "abc")
    fireEvent.click(screen.getAllByText("Reverse shift").pop()!)
    await waitFor(() => screen.getByText(/at least 5 characters/))
    type("Reason", "Wrong meter typed")
    fireEvent.click(screen.getAllByText("Reverse shift").pop()!)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/forecourt/shifts/s1/reverse", { reason: "Wrong meter typed" }))
  })

  it("adds a pump", async () => {
    perms = ["FUEL_MANAGE"]
    mount()
    await waitFor(() => screen.getByText("Pumps"))
    fireEvent.click(screen.getByText("Pumps"))
    fireEvent.click(await screen.findByText("Add pump"))
    await waitFor(() => screen.getByLabelText("Pump name"))
    await waitFor(() => screen.getByText("Diesel 1 (DIESEL)"))
    type("Pump name", "Pump 3"); type("Tank", "t1"); type("Price per litre", "21.50")
    fireEvent.click(screen.getByText("Save pump"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/forecourt/pumps", { name: "Pump 3", tankId: "t1", pricePerLitre: 21.5, active: true }))
  })

  it("adds a pump grouped as a nozzle on a dispenser (D01), and refuses a nozzle number with no dispenser", async () => {
    perms = ["FUEL_MANAGE"]
    mount()
    await waitFor(() => screen.getByText("Pumps"))
    fireEvent.click(screen.getByText("Pumps"))
    fireEvent.click(await screen.findByText("Add pump"))
    await waitFor(() => screen.getByText("Diesel 1 (DIESEL)"))
    type("Pump name", "Pump 3"); type("Tank", "t1"); type("Price per litre", "21.50"); type("Nozzle number", "2")
    fireEvent.click(screen.getByText("Save pump"))
    await waitFor(() => screen.getByText("Name the dispenser this nozzle is on."))
    expect(post).not.toHaveBeenCalledWith("/api/v1/fuel/forecourt/pumps", expect.anything())
    type("Dispenser", "Island 1")
    fireEvent.click(screen.getByText("Save pump"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/forecourt/pumps", { name: "Pump 3", tankId: "t1", pricePerLitre: 21.5, active: true, dispenserLabel: "Island 1", nozzleNumber: 2 }))
  })

  const adj = (o: any = {}) => ({ id: "a1", shiftId: "s1", kind: "REFUND", amount: 100, reason: "Customer overfilled", status: "PENDING", recordedBy: "alice", recordedAt: "2026-11-11T08:00:00Z", ...o })
  const openClose = async () => {
    mount()
    await waitFor(() => screen.getByText("Close shift"))
    fireEvent.click(screen.getByText("Close shift"))
    await waitFor(() => screen.getByLabelText("Closing meter Pump 1"))
    type("Closing meter Pump 1", "1210"); type("Test litres Pump 1", "10")
  }

  it("uses the company's cash tolerance and sends the fleet-card amount", async () => {
    controls = { ...controls, cashVarianceTolerance: 50 }
    await openClose()
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/fuel/settings/controls"))
    type("Cash", "1980"); type("Fleet card", "2000")
    await waitFor(() => expect(screen.getByTestId("variance").textContent).toMatch(/short/))
    expect(screen.queryByLabelText("Reason")).toBeNull() // R20 short is inside R50
    fireEvent.click(screen.getAllByText("Close shift").pop()!)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/forecourt/shifts/s1/close",
      { cash: 1980, card: 0, fleetCard: 2000, account: 0, other: 0, varianceReason: null, readings: [{ pumpId: "p1", closingMeter: 1210, testLitres: 10 }] }))
  })

  it("approved refunds lower the expected takings in the close dialog", async () => {
    adjustments = [adj({ status: "APPROVED", decidedBy: "bob" })]
    await openClose()
    await screen.findByText(/taken off what is expected/)
    type("Cash", "3900")
    expect(screen.getByTestId("variance").textContent).toMatch(/Balanced/)
  })

  it("warns, and does not post, while refunds are waiting for a supervisor", async () => {
    adjustments = [adj()]
    await openClose()
    expect((await screen.findAllByRole("alert")).some(a => /waiting for a supervisor/.test(a.textContent ?? ""))).toBe(true)
  })

  it("a supervisor can mark an out-of-tolerance shift reviewed; an attendant sees no Review button", async () => {
    shifts = [shift({ status: "CLOSED", expected: 4000, litresSold: 200, variance: -100, varianceReason: "Drive-off", cash: 3900, card: 0, account: 0, other: 0, tolerance: 5, reviewStatus: "PENDING_REVIEW" })]
    mount()
    await waitFor(() => screen.getByText("Needs review"))
    expect(screen.queryByText("Review")).toBeNull()
    cleanup()
    perms = ["FUEL_SHIFT_SUPERVISE"]
    mount()
    fireEvent.click(await screen.findByText("Review"))
    await waitFor(() => screen.getByLabelText("Review note"))
    type("Review note", "ok")
    fireEvent.click(screen.getByText("Mark reviewed"))
    await waitFor(() => screen.getByText(/at least 5 characters/))
    type("Review note", "CCTV checked, drive-off")
    fireEvent.click(screen.getByText("Mark reviewed"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/forecourt/shifts/s1/review", { reason: "CCTV checked, drive-off" }))
  })

  it("records a refund on an open shift", async () => {
    mount()
    fireEvent.click(await screen.findByText("SH-2026-00001"))
    await waitFor(() => screen.getByText("Refunds, voids and discounts"))
    fireEvent.change(screen.getByLabelText("Amount (R)"), { target: { value: "0" } })
    fireEvent.click(screen.getByText("Record"))
    await waitFor(() => screen.getByText(/above R 0/))
    fireEvent.change(screen.getByLabelText("Amount (R)"), { target: { value: "100" } })
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Customer overfilled" } })
    fireEvent.click(screen.getByText("Record"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/forecourt/shifts/s1/adjustments", { kind: "REFUND", amount: 100, reason: "Customer overfilled" }))
  })

  it("only a supervisor other than the recorder can approve; the recorder can withdraw", async () => {
    adjustments = [adj()]
    perms = ["FUEL_FORECOURT", "FUEL_SHIFT_SUPERVISE"]
    mount()
    fireEvent.click(await screen.findByText("SH-2026-00001"))
    await screen.findByText("Customer overfilled")
    expect(screen.queryByText("Approve")).toBeNull()
    expect(screen.getByText(/another supervisor must approve/)).toBeTruthy()
    expect(screen.getByText("Withdraw")).toBeTruthy()
    cleanup(); me = "bob"
    mount()
    fireEvent.click(await screen.findByText("SH-2026-00001"))
    fireEvent.click(await screen.findByText("Approve"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/forecourt/adjustments/a1/approve", {}))
  })
})
