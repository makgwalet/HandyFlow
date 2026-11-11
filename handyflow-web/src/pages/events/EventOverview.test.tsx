import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EventOverviewTab from "./EventOverviewTab"
import { failedReadiness, nextStep, quickActions, shortcuts, capacityLine, type OverviewPerms } from "./overviewOps"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))
const none: OverviewPerms = { write: false, approve: false, cancel: false, ops: false, incident: false, budget: false, payments: false, exportPii: false }
const full: OverviewPerms = { write: true, approve: true, cancel: true, ops: true, incident: true, budget: true, payments: true, exportPii: true }

describe("overviewOps", () => {
  it("offers only the actions the status and permissions allow", () => {
    expect(quickActions("DRAFT", none)).toEqual([]); expect(quickActions("DRAFT", full).map(a => a.id)).toEqual(["publish", "edit", "postpone", "copy", "cancel"])
    expect(quickActions("PUBLISHED", full).map(a => a.id)).toContain("go-live"); expect(quickActions("LIVE", full).map(a => a.id)).toEqual(["complete", "edit", "copy", "cancel"])
    expect(quickActions("COMPLETED", full).map(a => a.id)).toEqual(["copy"]); expect(quickActions("CANCELLED", { ...none, approve: true })).toEqual([])
  })
  it("gives shortcuts by permission", () => {
    expect(shortcuts("PUBLISHED", none).map(s => s.id)).toEqual(["guests", "tickets", "programme", "checklist", "vendors"])
    expect(shortcuts("LIVE", full).find(s => s.id === "guests")?.label).toBe("Check in guests"); expect(shortcuts("LIVE", { ...none, incident: true }).map(s => s.id)).toContain("incidents"); expect(shortcuts("LIVE", { ...none, incident: true }).map(s => s.id)).not.toContain("staff")
  })
  it("words the next step", () => {
    expect(nextStep("DRAFT", 2)).toMatch(/2 setup items/); expect(nextStep("DRAFT", 1)).toMatch(/1 setup item to/); expect(nextStep("DRAFT", 0)).toMatch(/Publish/); expect(nextStep("COMPLETED", 0)).toMatch(/close-out/); expect(nextStep("X", 0)).toBe("")
    expect(failedReadiness([{ code: "a", label: "A", passed: true, detail: null }, { code: "b", label: "B", passed: false, detail: "d" }])).toHaveLength(1); expect(failedReadiness(undefined)).toEqual([])
    expect(capacityLine(40, 100, 60)).toBe("40 of 100 registered · 60 left"); expect(capacityLine(40, null, null)).toMatch(/no capacity limit/)
  })
})

describe("overview screen", () => {
  let status = "DRAFT"
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset(); perms = []; status = "DRAFT"; post.mockResolvedValue({ data: { data: {} } })
    get.mockImplementation(async (u: string) => ({ data: { data:
      u.endsWith("/stats") ? { totalRegistered: 40, totalCheckedIn: 0, totalCancelled: 1, totalVendors: 3, confirmedVendors: 2 }
      : u.endsWith("/dashboard/registration") ? { registered: 40, venueCapacity: 100, seatsRemaining: 60, waitlisted: 4, paymentsPending: 2 }
      : u.endsWith("/readiness") ? { ready: false, checks: [{ code: "V", label: "Venue confirmed", passed: true, detail: null }, { code: "B", label: "Budget approved", passed: false, detail: "Still a draft" }] }
      : { id: "e1", eventNumber: "EV-9", title: "Summit", status, isFree: false, isPrivate: false, venueName: "Hall", venueAddress: null, startDatetime: "2026-12-01T08:00:00", endDatetime: "2026-12-01T17:00:00" } } }))
  })
  afterEach(cleanup)
  const mount = (goTo = vi.fn()) => { render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><EventOverviewTab eventId="e1" onChangeEvent={() => {}} goTo={goTo} /></QueryClientProvider>); return goTo }

  it("shows where the event stands, the figures and what is outstanding", async () => {
    perms = ["EVENTS_READ"]; mount(); await waitFor(() => screen.getByText("Summit"))
    await waitFor(() => screen.getByText("40 of 100 registered · 60 left")); expect(screen.getByText("2 of 3")).toBeTruthy(); expect(screen.getByRole("status").textContent).toMatch(/1 setup item/)
    expect(within(screen.getByLabelText("Outstanding")).getByText(/Budget approved/)).toBeTruthy(); expect(screen.queryByLabelText("Quick actions")).toBeNull()
  })
  it("publishes from the quick actions", async () => {
    perms = ["EVENTS_APPROVE"]; mount(); await waitFor(() => screen.getByText("Publish")); fireEvent.click(screen.getByText("Publish"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/publish"))
  })
  it("shows the server's refusal", async () => {
    perms = ["EVENTS_APPROVE"]; post.mockRejectedValueOnce({ response: { data: { message: "Add a ticket type before publishing" } } }); mount(); await waitFor(() => screen.getByText("Publish")); fireEvent.click(screen.getByText("Publish"))
    await waitFor(() => screen.getByText(/Add a ticket type before publishing/))
  })
  it("opens the edit dialog and jumps to a screen", async () => {
    perms = ["EVENTS_WRITE"]; const goTo = mount(); await waitFor(() => screen.getByText("Edit details")); fireEvent.click(screen.getByText("Edit details")); await waitFor(() => screen.getByRole("dialog"))
    fireEvent.click(within(screen.getByRole("dialog")).getByText("Back")); fireEvent.click(screen.getByText("Add guests")); expect(goTo).toHaveBeenCalledWith("add-guests")
  })
  it("offers go-live on a published event and nothing on a cancelled one", async () => {
    perms = ["EVENTS_APPROVE", "EVENTS_CANCEL", "EVENTS_WRITE"]; status = "PUBLISHED"; mount(); await waitFor(() => screen.getByText("Go live")); cleanup(); status = "CANCELLED"; mount(); await waitFor(() => screen.getByText("Summit")); expect(screen.queryByText("Go live")).toBeNull(); expect(screen.queryByText("Cancel event")).toBeNull()
  })
})
