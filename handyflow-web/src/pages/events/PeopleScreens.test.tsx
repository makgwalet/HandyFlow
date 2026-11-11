import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EventStaffTab from "./EventStaffTab"
import EventIncidentsTab from "./EventIncidentsTab"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn(); const del = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))
const dlg = () => within(screen.getByRole("dialog"))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const noop = () => {}

const inc = (o: object = {}) => ({ id: "i1", reference: "INC-001", title: "Guest fainted", description: "Near stage", category: "MEDICAL", severity: "HIGH", status: "OPEN", location: "Hall A", reportedByName: "Thabo", assignedToName: null, escalationLevel: 0, acknowledgedAt: null, resolvedAt: null, resolution: null, createdAt: "2026-12-01T10:00:00", minutesOpen: 12, acknowledgementOverdue: true, resolutionOverdue: false, timeline: [{ id: "t1", kind: "NOTE", note: "First aider called", authorName: "Thabo", createdAt: "2026-12-01T10:02:00" }], ...o })
let staff: any[]; let cov: any[]; let incidents: any[]; let contacts: any[]
beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset(); perms = []
  post.mockResolvedValue({ data: { data: {} } }); put.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} })
  staff = [{ id: "s1", userId: null, fullName: "Sipho Ndlovu", kind: "STAFF", role: "STEWARD", phone: "082 123 4567", email: null, post: "Gate 1", shiftStart: "2026-12-01T08:00:00", shiftEnd: "2026-12-01T16:00:00", status: "ASSIGNED", arrivedAt: null, notes: null }]
  cov = [{ role: "STEWARD", rostered: 3, confirmed: 1, arrived: 1, noShow: 0 }]
  incidents = [inc()]; contacts = [{ id: "c1", contactType: "MEDICAL", name: "Netcare 911", organisation: null, phone: "082 911", alternatePhone: null, email: null, notes: null }]
  get.mockImplementation(async (u: string) => {
    const d = u.endsWith("/staff/coverage") ? cov : u.endsWith("/staff") ? staff : u.endsWith("/incidents/summary") ? { total: 1, unresolved: 1, resolved: 0, overdue: 1, unresolvedBySeverity: {} }
      : u.endsWith("/incidents") ? { content: incidents } : u.includes("/incidents/") ? incidents[0] : u.endsWith("/contacts") ? contacts : {}
    return { data: { data: d } }
  })
})
afterEach(cleanup)

describe("staff roster", () => {
  it("is hidden without ops access because it holds phone numbers", () => {
    wrap(<EventStaffTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    expect(screen.getByText(/do not have access/)).toBeTruthy(); expect(get).not.toHaveBeenCalled()
  })
  it("a reader sees the roster and the coverage gap but no buttons", async () => {
    perms = ["EVENTS_OPS_READ"]; wrap(<EventStaffTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText("Sipho Ndlovu")); expect(screen.getByRole("note").textContent).toMatch(/2 steward/)
    expect(screen.queryByText("Arrived")).toBeNull(); expect(screen.queryByText("Add to roster")).toBeNull()
  })
  it("marks someone arrived", async () => {
    perms = ["EVENTS_OPS_MANAGE"]; wrap(<EventStaffTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText("Arrived")); fireEvent.click(screen.getByText("Arrived"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/staff/s1/arrive"))
  })
  it("adds a shift and shows the server's refusal", async () => {
    perms = ["EVENTS_OPS_MANAGE"]; post.mockRejectedValueOnce({ response: { data: { message: "Sipho already has a shift at that time" } } })
    wrap(<EventStaffTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText("Add to roster")); fireEvent.click(screen.getByText("Add to roster"))
    fireEvent.change(dlg().getByLabelText(/Full name/), { target: { value: "Zanele" } }); fireEvent.change(dlg().getByLabelText(/Role/), { target: { value: "SECURITY" } })
    fireEvent.change(dlg().getByLabelText(/Shift starts/), { target: { value: "2026-12-01T08:00" } }); fireEvent.change(dlg().getByLabelText(/Shift ends/), { target: { value: "2026-12-01T12:00" } })
    fireEvent.click(dlg().getByText("Save shift"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/staff", expect.objectContaining({ fullName: "Zanele", role: "SECURITY", userId: null, phone: null, shiftStart: "2026-12-01T08:00:00" })))
    await waitFor(() => screen.getByText(/already has a shift/))
  })
})

describe("incidents", () => {
  it("lets a reporter with no read access report, without seeing the list", async () => {
    perms = ["EVENTS_INCIDENT_REPORT"]; wrap(<EventIncidentsTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    expect(screen.queryByText("Guest fainted")).toBeNull(); expect(get).not.toHaveBeenCalled()
    fireEvent.click(screen.getByText("Report incident"))
    fireEvent.change(dlg().getByLabelText(/What happened/), { target: { value: "Spill" } }); fireEvent.change(dlg().getByLabelText(/Category/), { target: { value: "FACILITY" } }); fireEvent.change(dlg().getByLabelText(/How serious/), { target: { value: "LOW" } })
    fireEvent.click(dlg().getByText("Report incident"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/incidents", { title: "Spill", description: null, category: "FACILITY", severity: "LOW", location: null }))
  })
  it("shows an overdue incident with an alert and history", async () => {
    perms = ["EVENTS_OPS_READ"]; wrap(<EventIncidentsTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText(/Guest fainted/)); expect(screen.getByRole("alert").textContent).toMatch(/Nobody has taken this yet/)
    expect(screen.queryByText("Take it")).toBeNull(); await waitFor(() => screen.getByText(/Netcare 911/))
    fireEvent.click(screen.getByText("History")); await waitFor(() => screen.getByText(/First aider called/))
  })
  it("takes, escalates with a raised severity and resolves", async () => {
    perms = ["EVENTS_OPS_MANAGE"]; wrap(<EventIncidentsTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText("Take it")); fireEvent.click(screen.getByText("Take it"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/incidents/i1/acknowledge"))
    fireEvent.click(screen.getByText("Escalate")); fireEvent.change(dlg().getByLabelText(/Reason/), { target: { value: "Needs paramedics" } }); fireEvent.change(dlg().getByLabelText(/Also raise severity/), { target: { value: "CRITICAL" } })
    fireEvent.click(dlg().getByText("Escalate"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/incidents/i1/escalate", { reason: "Needs paramedics", raiseSeverityTo: "CRITICAL" }))
    await waitFor(() => expect(screen.queryByRole("dialog")).toBeNull())
    fireEvent.click(screen.getByText("Resolve")); expect((dlg().getByText("Mark resolved") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(dlg().getByLabelText(/How was it resolved/), { target: { value: "Taken to clinic" } }); fireEvent.click(dlg().getByText("Mark resolved"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/incidents/i1/resolve", { resolution: "Taken to clinic" }))
  })
  it("a resolved incident can be closed or reopened, not escalated", async () => {
    perms = ["EVENTS_OPS_MANAGE"]; incidents = [inc({ status: "RESOLVED", acknowledgementOverdue: false, resolution: "Done" })]
    wrap(<EventIncidentsTab eventId="e1" eventTitle="Summit" onChangeEvent={noop} />)
    await waitFor(() => screen.getByText("Close")); expect(screen.getByText("Reopen")).toBeTruthy(); expect(screen.queryByText("Escalate")).toBeNull(); expect(screen.queryByRole("alert")).toBeNull()
  })
})
