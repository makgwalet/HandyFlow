import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EventsDashboardTab from "./EventsDashboardTab"
import EventAnalytics from "./EventAnalytics"
import EventTicketsTab from "./EventTicketsTab"

const get = vi.fn(); const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

beforeEach(() => { get.mockReset(); post.mockReset(); perms = []; post.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

const overview = { statusCounts: { DRAFT: 2, PUBLISHED: 3, LIVE: 1 }, attention: [
  { type: "NEAR_CAPACITY", severity: "INFO", message: "95% full", eventId: "e2", eventNumber: "EVT-2" },
  { type: "NOT_LIVE_ON_TIME", severity: "WARNING", message: "Started 2 hours ago", eventId: "e1", eventNumber: "EVT-1" }],
  upcoming: [{ id: "e1", eventNumber: "EVT-1", title: "Summit", status: "PUBLISHED", startDatetime: "2026-11-12T09:00:00", daysUntil: 1, venueCapacity: 200, registered: 120, fillPercent: 60, budgetStatus: null }] }

describe("events dashboard", () => {
  it("is closed to people without the reports permission and fetches nothing", () => {
    wrap(<EventsDashboardTab onOpenEvent={() => {}} />); expect(screen.getByText(/do not have access/)).toBeTruthy(); expect(get).not.toHaveBeenCalled()
  })
  it("plain read access is enough, as on the server", async () => {
    perms = ["EVENTS_READ"]; get.mockResolvedValue({ data: { data: overview } }); wrap(<EventsDashboardTab onOpenEvent={() => {}} />)
    await waitFor(() => screen.getByText("Summit"))
  })
  it("shows counts, warnings before info, and opens an event", async () => {
    perms = ["EVENTS_REPORTS_READ"]; get.mockResolvedValue({ data: { data: overview } }); const open = vi.fn()
    wrap(<EventsDashboardTab onOpenEvent={open} />)
    await waitFor(() => screen.getByText("Summit"))
    const titles = screen.getAllByText(/Should be live|Nearly full/).map(e => e.textContent)
    expect(titles).toEqual(["Should be live", "Nearly full"])
    expect(screen.getByText("120 of 200 (60%)")).toBeTruthy(); expect(screen.getByText(/Tomorrow/)).toBeTruthy(); expect(screen.getByText(/Budget: No budget/)).toBeTruthy()
    expect(screen.getByRole("progressbar").getAttribute("aria-valuenow")).toBe("60")
    fireEvent.click(screen.getByText("Open guests")); expect(open).toHaveBeenCalledWith("e1", "Summit")
  })
})

const reg = { status: "LIVE", registered: 120, venueCapacity: 200, fillPercent: 60, seatsRemaining: 80, waitlisted: 4, cancelled: 2, transferred: 1, checkedIn: 90, bySource: { STAFF: 100, WALK_IN: 20 },
  amountRecorded: 15000, paymentsPending: 3, tiers: [{ id: "t1", name: "General", price: 100, quantity: 150, sold: 100, available: 50, checkedIn: 70, faceValueSold: 10000 }], registrationsByDay: [] }
const att = { expected: 120, checkedIn: 90, remaining: 30, checkInRatePercent: 75, walkIns: 20, scanResults: { SUCCESS: 85, PAYMENT_PENDING: 3, NOT_FOUND: 1 }, byTier: [], arrivalsByQuarterHour: [] }

describe("event analytics", () => {
  it("uses the server's registration and door numbers and names every scan result", async () => {
    perms = ["EVENTS_MANAGE"]
    get.mockImplementation(async (u: string) => ({ data: { data: u.endsWith("/registration") ? reg : att } }))
    wrap(<EventAnalytics eventId="e1" />)
    await waitFor(() => screen.getByText("At the door (live)"))
    expect(screen.getByText("120 of 200 (60%)")).toBeTruthy(); expect(screen.getByText("Payments still pending")).toBeTruthy()
    expect(screen.getByText("Payment not confirmed")).toBeTruthy(); expect(screen.getByText("Added by staff")).toBeTruthy(); expect(screen.getByText("General (100/150)")).toBeTruthy()
  })
  it("a draft event has no door section and no attendance call", async () => {
    perms = ["EVENTS_MANAGE"]; get.mockResolvedValue({ data: { data: { ...reg, status: "DRAFT" } } })
    wrap(<EventAnalytics eventId="e1" />); await waitFor(() => screen.getByText("Ticket types"))
    expect(screen.queryByText(/At the door/)).toBeNull(); expect(get.mock.calls.some(c => String(c[0]).endsWith("/attendance"))).toBe(false)
  })
})

describe("ticket types screen", () => {
  const tiers = [{ id: "t1", name: "VIP", description: null, price: 250, quantity: 50, quantitySold: 50, quantityCheckedIn: 0, available: 0, saleStart: null, saleEnd: null, active: true }]
  it("lists types with their state and hides Add from a reader", async () => {
    get.mockResolvedValue({ data: { data: tiers } }); wrap(<EventTicketsTab eventId="e1" eventTitle="Summit" onChangeEvent={() => {}} />)
    await waitFor(() => screen.getByText("VIP")); expect(screen.getByText("Sold out")).toBeTruthy(); expect(screen.queryByText("Add ticket type")).toBeNull()
  })
  it("adds a free type with defaults and refreshes", async () => {
    perms = ["EVENTS_TICKETING_MANAGE"]; get.mockResolvedValue({ data: { data: tiers } }); wrap(<EventTicketsTab eventId="e1" eventTitle="Summit" onChangeEvent={() => {}} />)
    await waitFor(() => screen.getByText("VIP")); fireEvent.click(screen.getByText("Add ticket type"))
    fireEvent.change(screen.getByLabelText(/Name/), { target: { value: "Student" } }); fireEvent.change(screen.getByLabelText(/Places/), { target: { value: "30" } })
    const btns = screen.getAllByRole("button", { name: "Add ticket type" }); fireEvent.click(btns[btns.length - 1])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/tiers", { name: "Student", description: null, price: 0, quantity: 30, saleStart: null, saleEnd: null }))
  })
  it("shows the server's refusal", async () => {
    perms = ["EVENTS_MANAGE"]; get.mockResolvedValue({ data: { data: [] } }); post.mockRejectedValue({ response: { data: { message: "Event is closed" } } })
    wrap(<EventTicketsTab eventId="e1" eventTitle="Summit" onChangeEvent={() => {}} />)
    await waitFor(() => screen.getByText("No ticket types yet.")); fireEvent.click(screen.getByText("Add ticket type"))
    fireEvent.change(screen.getByLabelText(/Name/), { target: { value: "X" } }); fireEvent.change(screen.getByLabelText(/Places/), { target: { value: "5" } })
    const btns = screen.getAllByRole("button", { name: "Add ticket type" }); fireEvent.click(btns[btns.length - 1])
    await waitFor(() => expect(screen.getByRole("alert").textContent).toBe("Event is closed"))
  })
})
