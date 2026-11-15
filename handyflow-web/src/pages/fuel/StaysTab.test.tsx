import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import StaysTab from "./StaysTab"

const get = vi.fn(), post = vi.fn(), put = vi.fn()
let can = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => can }))

const todayStr = () => new Date().toLocaleDateString("en-CA", { timeZone: "Africa/Johannesburg" })
const plus = (n: number) => { const d = new Date(todayStr() + "T12:00:00"); d.setDate(d.getDate() + n); return d.toLocaleDateString("en-CA") }
const stay = (o: any = {}) => ({ id: "s1", vehicleRegistration: "GP 12 AB", driverName: "Sipho", driverPhone: "0821234567", arrivalDate: todayStr(), departureDate: plus(2), actualDepartureDate: null, status: "BOOKED",
  ratePerNight: 150, nights: 2, amount: 300, note: null, cancelledReason: null, overstaying: false, checkedInAt: null, checkedOutAt: null, ...o })
let settingsNow: any
let capacityNow: any
function mount() { render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><StaysTab /></QueryClientProvider>) }

describe("StaysTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset(); can = true; settingsNow = { nightlyRate: 150, bays: 5 }
    capacityNow = { from: todayStr(), bays: 5, overstays: [], nights: [{ date: todayStr(), used: 2, arrivals: 1, departures: 0, free: 3 }, { date: plus(1), used: 5, arrivals: 0, departures: 0, free: 0 }] }
    get.mockImplementation(async (u: string) => ({ data: { data: u.includes("parking-settings") ? settingsNow : u.includes("occupancy") ? { date: todayStr(), inYard: 2, arrivals: 1, departures: 0, bays: 5, free: 3 } : u.includes("capacity") ? capacityNow : [stay()] } }))
    post.mockResolvedValue({ data: { data: {} } }); put.mockResolvedValue({ data: { data: {} } })
  })
  afterEach(cleanup)

  it("shows the yard today, the stay with its nights and amount, and says nothing is invoiced", async () => {
    mount()
    expect(await screen.findByText("GP 12 AB")).toBeTruthy()
    expect(screen.getByText(/in the yard today/)).toBeTruthy(); expect(screen.getByText(/of 5 bays free/)).toBeTruthy()
    expect(screen.getByText("2 nights")).toBeTruthy(); expect(screen.getByText(/Nothing is invoiced from here/)).toBeTruthy()
  })
  it("checks in a booked truck that is due", async () => {
    mount()
    fireEvent.click(await screen.findByText("Check in"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/stays/s1/check-in"))
  })
  it("books a stay with the dates and registration", async () => {
    mount()
    fireEvent.click(await screen.findByText("Book a stay"))
    fireEvent.change(screen.getByLabelText(/Truck registration/), { target: { value: "ND 5 GP" } })
    fireEvent.change(screen.getByLabelText(/Leaves/), { target: { value: plus(3) } })
    fireEvent.click(screen.getByText("Book stay"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/fuel/truck-stop/stays")
    expect(post.mock.calls[0][1]).toMatchObject({ vehicleRegistration: "ND 5 GP", arrivalDate: todayStr(), departureDate: plus(3) })
  })
  it("will not book before a rate is set", async () => {
    settingsNow = { nightlyRate: null, bays: null }
    mount()
    fireEvent.click(await screen.findByText("Book a stay"))
    fireEvent.change(screen.getByLabelText(/Truck registration/), { target: { value: "ND 5 GP" } })
    fireEvent.change(screen.getByLabelText(/Leaves/), { target: { value: plus(3) } })
    fireEvent.click(screen.getByText("Book stay"))
    expect(await screen.findByText(/Set the nightly rate first/)).toBeTruthy(); expect(post).not.toHaveBeenCalled()
  })
  it("saves the company's rate and bays", async () => {
    mount()
    const rate = await screen.findByLabelText(/Nightly rate/)
    await waitFor(() => expect((rate as HTMLInputElement).value).toBe("150"))
    fireEvent.change(rate, { target: { value: "175" } })
    fireEvent.click(screen.getByText("Save settings"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/parking-settings", { nightlyRate: 175, bays: 5 }))
  })
  it("cancelling needs a reason", async () => {
    mount()
    fireEvent.click(await screen.findByText("Cancel"))
    fireEvent.click(screen.getByText("Cancel stay"))
    expect(await screen.findByText(/Give a reason/)).toBeTruthy(); expect(post).not.toHaveBeenCalled()
  })
  it("flags a truck past its departure date, and people who can only read see no buttons", async () => {
    get.mockImplementation(async (u: string) => ({ data: { data: u.includes("parking-settings") ? settingsNow : u.includes("occupancy") ? { date: todayStr(), inYard: 1, arrivals: 0, departures: 0, bays: null, free: null } : [stay({ status: "CHECKED_IN", overstaying: true })] } }))
    can = false; mount()
    expect(await screen.findByText("Past its departure date")).toBeTruthy(); expect(screen.getByText("No bay limit set")).toBeTruthy()
    expect(screen.queryByText("Book a stay")).toBeNull(); expect(screen.queryByText("Check out")).toBeNull()
  })
  it("shows the next nights with the first full night in words, not only colour", async () => {
    mount()
    expect(await screen.findByText(/Yard, next 2 nights/)).toBeTruthy()
    expect(screen.getByText(new RegExp(`Every bay is taken on the night of ${plus(1)}`))).toBeTruthy()
    expect(screen.getByTestId(`night-${plus(1)}`).textContent).toContain("Full")
    expect(screen.getByTestId(`night-${todayStr()}`).textContent).toContain("Free")
  })
  it("lists trucks past their departure date", async () => {
    capacityNow = { ...capacityNow, overstays: [{ id: "s9", vehicleRegistration: "GP 99 ZZ", plannedDeparture: plus(-2), daysOver: 2 }] }
    mount()
    expect(await screen.findByText(/GP 99 ZZ \(2 days over\)/)).toBeTruthy()
  })
  it("says so when no bay limit is set", async () => {
    capacityNow = { ...capacityNow, bays: null, nights: [{ date: todayStr(), used: 1, arrivals: 0, departures: 0, free: null }] }
    mount()
    expect(await screen.findByText(/No bay limit is set, so nothing is capacity-limited/)).toBeTruthy()
  })
})
