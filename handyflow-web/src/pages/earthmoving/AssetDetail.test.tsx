import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() } }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: perms } }) }))

import AssetsTab from "./AssetsTab"
import { activityFeed } from "./shared/assetActivity"

afterEach(cleanup)
const asset = { id: "a1", name: "CAT D9", fleetNumber: "D9-001", assetType: "DOZER", make: "Caterpillar", model: "D9T", year: 2020, serialNumber: "S1", registration: "GP1",
  ownershipType: "OWN", hireSupplier: null, hireStartDate: null, hireEndDate: null, status: "AVAILABLE", currentSite: null, currentClient: null, dailyRate: 18500,
  hourlyRate: 2200, currentHours: 100, lastServiceHours: 0, serviceIntervalHours: 250, dueForService: false, notes: null, createdAt: "2026-01-01T00:00:00Z" }
const page = (content: unknown[]) => ({ data: { data: { content, last: true } } })
const list = (data: unknown) => ({ data: { data: data } })

beforeEach(() => {
  vi.clearAllMocks()
  perms = ["EARTHMOVING_READ"]
  get.mockImplementation((url: string) => Promise.resolve(
    url.includes("/maintenance-plans") ? list([{ id: "p1", assetId: "a1", name: "250 hr service", state: "OVERDUE", remaining: -20, unit: "hrs", active: true }])
    : url.includes("/a1/maintenance") ? page([{ id: "m1", assetId: "a1", type: "SERVICE", description: "Changed filters", performedAt: "2026-09-01T08:00:00Z", hoursAtService: 90, cost: 1500, supplier: "Dealer", invoiceRef: null, createdAt: "" }])
    : url.includes("/a1/deployments") ? list([{ id: "d1", assetId: "a1", siteName: "N1 Bridge", clientName: "Acme Civils", deployedAt: "2026-08-01T00:00:00Z", returnedAt: null }])
    : url.includes("/hire-charges") ? list([{ id: "h1", assetId: "a1", customerName: "Acme Civils", periodFrom: "2026-08-01", periodTo: "2026-08-31", amount: 5000, status: "PENDING", invoiceNumber: null },
        { id: "h2", assetId: "other", customerName: "Someone Else", periodFrom: "2026-08-01", periodTo: "2026-08-31", amount: 1, status: "PENDING", invoiceNumber: null }])
    : url.includes("/inspections") ? list([{ id: "i1", assetId: "a1", machine: "D9", inspectorUserId: null, performedAt: "2026-10-14T06:00:00Z", meterReading: 99, result: "DEFECTS", notes: null, inspectionType: "RETURN", items: [{ item: "Brakes", outcome: "DEFECT", comment: "Soft pedal", incidentId: null }] }])
    : url.includes("/status-history") ? list([{ id: "e1", assetId: "a1", fromStatus: "AVAILABLE", toStatus: "BREAKDOWN", reason: "Hose burst", happenedAt: "2026-10-10T08:00:00Z" }])
    : url.includes("/operator-logs") ? page([])
    : url.includes("/incidents") ? page([])
    : page([asset])))
})

const open = async () => {
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><AssetsTab /></QueryClientProvider>)
  fireEvent.click(await screen.findByLabelText("View profile"))
  await screen.findByLabelText("Machine detail")
}

describe("Machine detail page", () => {
  it("opens in place of the fleet list, shows the overview and goes back", async () => {
    await open()
    expect(screen.getByText("Service interval progress")).toBeTruthy()
    expect(await screen.findByText(/250 hr service: Overdue · 20 hrs overdue/)).toBeTruthy()
    expect(screen.queryByText("Total fleet")).toBeNull()
    expect(screen.queryByText("Edit details")).toBeNull()
    fireEvent.click(screen.getByText("Back to fleet"))
    expect(await screen.findByText("Total fleet")).toBeTruthy()
  })
  it("shows service plans and history under Maintenance", async () => {
    await open()
    fireEvent.click(screen.getByRole("tab", { name: "Maintenance" }))
    expect(await screen.findByText("Changed filters")).toBeTruthy()
    expect(screen.getByText("250 hr service")).toBeTruthy()
  })
  it("shows the machine's inspections with their type", async () => {
    await open()
    fireEvent.click(screen.getByRole("tab", { name: "Inspections" }))
    expect(await screen.findByText("Return from hire")).toBeTruthy()
    expect(screen.getByText(/Brakes: Soft pedal/)).toBeTruthy()
  })
  it("shows deployments and only this machine's hire charges", async () => {
    await open()
    fireEvent.click(screen.getByRole("tab", { name: "Hire history" }))
    expect(await screen.findByText("N1 Bridge")).toBeTruthy()
    expect(await screen.findByText(/Acme Civils · /)).toBeTruthy()
    expect(screen.queryByText(/Someone Else/)).toBeNull()
  })
  it("shows one timeline of what happened", async () => {
    await open()
    fireEvent.click(screen.getByRole("tab", { name: "Recent activity" }))
    expect(await screen.findByText(/Available → Breakdown: Hose burst/)).toBeTruthy()
    await waitFor(() => expect(screen.getByText(/Return inspection passed with defects/)).toBeTruthy())
  })
  it("gives edit buttons only to people who can edit", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE"]
    await open()
    expect(screen.getByText("Edit details")).toBeTruthy()
  })
})

describe("activityFeed", () => {
  it("merges everything newest first and caps the list", () => {
    const feed = activityFeed({
      events: [{ id: "e", assetId: "a", fromStatus: null, toStatus: "AVAILABLE", reason: null, happenedAt: "2026-10-01T00:00:00Z" }] as never,
      incidents: [{ id: "i", assetId: "a", type: "BREAKDOWN", severity: "CRITICAL", title: "Pump", status: "RESOLVED", reportedAt: "2026-10-03T00:00:00Z" }] as never,
      logs: [{ id: "l", assetId: "a", operatorName: "Sipho", siteName: "N1", startedAt: "2026-10-02T00:00:00Z", endedAt: null, hoursLogged: null }] as never,
      inspections: [],
    })
    expect(feed.map(f => f.kind)).toEqual(["INCIDENT", "SHIFT", "STATUS"])
    expect(feed[0].text).toContain("(resolved)")
    expect(feed[1].text).toContain("(in progress)")
    expect(activityFeed({ events: [], incidents: [], logs: [], inspections: [] })).toEqual([])
    const many = Array.from({ length: 40 }, (_, n) => ({ id: "x" + n, toStatus: "AVAILABLE", fromStatus: null, reason: null, happenedAt: `2026-09-${String(n % 28 + 1).padStart(2, "0")}T00:00:00Z` }))
    expect(activityFeed({ events: many as never, incidents: [], logs: [], inspections: [] }, 30).length).toBe(30)
  })
})
