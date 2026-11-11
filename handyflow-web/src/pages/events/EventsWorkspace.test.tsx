import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EventsListTab from "./EventsListTab"
import EventAuditTab from "./EventAuditTab"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({
  usePermission: (p: string) => perms.includes(p),
  usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)),
}))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

const ev = (over: object = {}) => ({ id: "e1", title: "Summit", eventNumber: "EVT-1", eventType: "CONFERENCE", status: "PUBLISHED", isFree: true, isPrivate: false,
  startDatetime: "2026-12-01T09:00:00", endDatetime: "2026-12-01T17:00:00", venueCapacity: 100, ...over })

beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); perms = []; post.mockResolvedValue({ data: {} }); put.mockResolvedValue({ data: {} }) })
afterEach(cleanup)

describe("event list actions", () => {
  it("shows no write buttons to someone who can only read", async () => {
    get.mockResolvedValue({ data: { data: { content: [ev()] } } })
    wrap(<EventsListTab onSelectEvent={() => {}} />)
    await waitFor(() => screen.getByText("Summit"))
    for (const t of ["New Event", "Edit", "Postpone", "Copy", "Cancel", "Go Live"]) expect(screen.queryByText(t)).toBeNull()
  })

  it("offers actions that fit the status for a manager", async () => {
    perms = ["EVENTS_MANAGE"]
    get.mockResolvedValue({ data: { data: { content: [ev({ status: "LIVE" })] } } })
    wrap(<EventsListTab onSelectEvent={() => {}} />)
    await waitFor(() => screen.getByText("Summit"))
    expect(screen.getByText("Complete")).toBeTruthy()
    expect(screen.getByText("Edit")).toBeTruthy()
    expect(screen.queryByText("Postpone")).toBeNull()   // a live event cannot be moved
    expect(screen.queryByText("Publish")).toBeNull()
  })

  it("cancelling needs a reason and sends it with the notify choice", async () => {
    perms = ["EVENTS_CANCEL"]
    get.mockResolvedValue({ data: { data: { content: [ev()] } } })
    wrap(<EventsListTab onSelectEvent={() => {}} />)
    await waitFor(() => screen.getByText("Summit"))
    fireEvent.click(screen.getByText("Cancel"))
    const confirm = screen.getByRole("button", { name: "Cancel event" }) as HTMLButtonElement
    expect(confirm.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "venue lost" } })
    expect(confirm.disabled).toBe(false)
    fireEvent.click(confirm)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/cancel", { reason: "venue lost", notifyGuests: true }))
  })

  it("shows the server's message when postponing is refused", async () => {
    perms = ["EVENTS_WRITE"]
    get.mockResolvedValue({ data: { data: { content: [ev()] } } })
    post.mockRejectedValue({ response: { data: { message: "Only DRAFT, PUBLISHED or SOLD_OUT events can be rescheduled" } } })
    wrap(<EventsListTab onSelectEvent={() => {}} />)
    await waitFor(() => screen.getByText("Summit"))
    fireEvent.click(screen.getByText("Postpone"))
    fireEvent.change(screen.getByLabelText(/New start/), { target: { value: "2026-12-08T09:00" } })
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "venue flooded" } })
    fireEvent.click(screen.getByRole("button", { name: "Postpone event" }))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toMatch(/can be rescheduled/))
    expect(post).toHaveBeenCalledWith("/api/v1/events/e1/postpone", expect.objectContaining({ newStartDatetime: "2026-12-08T09:00:00", newEndDatetime: "2026-12-08T17:00:00", reason: "venue flooded" }))
  })

  it("editing sends a PUT with the changed title", async () => {
    perms = ["EVENTS_WRITE"]
    get.mockResolvedValue({ data: { data: { content: [ev()] } } })
    wrap(<EventsListTab onSelectEvent={() => {}} />)
    await waitFor(() => screen.getByText("Summit"))
    fireEvent.click(screen.getByText("Edit"))
    fireEvent.change(screen.getByLabelText(/Event title/), { target: { value: "Summit 2" } })
    fireEvent.click(screen.getByRole("button", { name: "Save changes" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/events/e1", expect.objectContaining({ title: "Summit 2", venueCapacity: 100 })))
  })

  it("a copy needs the new start and posts it", async () => {
    perms = ["EVENTS_WRITE"]
    get.mockResolvedValue({ data: { data: { content: [ev()] } } })
    wrap(<EventsListTab onSelectEvent={() => {}} />)
    await waitFor(() => screen.getByText("Summit"))
    fireEvent.click(screen.getByText("Copy"))
    expect((screen.getByRole("button", { name: "Create copy" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/New start/), { target: { value: "2027-01-10T09:00" } })
    fireEvent.click(screen.getByRole("button", { name: "Create copy" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/duplicate", { newStartDatetime: "2027-01-10T09:00:00", title: "Copy of Summit" }))
  })
})

describe("audit trail", () => {
  it("explains itself without the permission and does not call the server", async () => {
    wrap(<EventAuditTab eventId="e1" eventTitle="Summit" onChangeEvent={() => {}} />)
    expect(screen.getByText(/do not have access/)).toBeTruthy()
    expect(get).not.toHaveBeenCalled()
  })
  it("lists entries with who and what changed", async () => {
    perms = ["EVENTS_AUDIT_READ"]
    get.mockResolvedValue({ data: { data: { content: [{ id: "a1", action: "EVENT_POSTPONED", summary: "Event EVT-1 postponed", details: "start: 1 Dec → 8 Dec", actorName: "Thabo M", occurredAt: "2026-11-11T10:00:00Z" }], totalPages: 1, number: 0 } } })
    wrap(<EventAuditTab eventId="e1" eventTitle="Summit" onChangeEvent={() => {}} />)
    await waitFor(() => screen.getByText("Event postponed"))
    expect(screen.getByText(/Thabo M/)).toBeTruthy()
    expect(screen.getByText(/1 Dec → 8 Dec/)).toBeTruthy()
  })
})
