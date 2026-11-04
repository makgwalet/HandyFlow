import { describe, it, expect, vi, beforeEach } from "vitest"
import { render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { AssetsPanel, ReportsPanel, TrackModal } from "./FmPortalExtras"

vi.mock("../../api/fmPortal.api", () => ({ fmPortalApi: {
  progress: vi.fn(), photos: vi.fn(), photoBlob: vi.fn(), assets: vi.fn(), sites: vi.fn(), ordersAll: vi.fn(), invoices: vi.fn(), credits: vi.fn(), invoicePdf: vi.fn(), creditPdf: vi.fn(),
} }))
import { fmPortalApi } from "../../api/fmPortal.api"
const api = fmPortalApi as unknown as Record<string, ReturnType<typeof vi.fn>>
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

beforeEach(() => { vi.clearAllMocks(); URL.createObjectURL = vi.fn(() => "blob:x"); URL.revokeObjectURL = vi.fn()
  api.sites.mockResolvedValue({ content: [{ id: "s1", name: "Head Office" }], totalElements: 1, totalPages: 1, number: 0 }) })

describe("TrackModal", () => {
  it("shows the steps with the current one marked, and photos before/after", async () => {
    api.progress.mockResolvedValue({ status: "ASSIGNED", steps: [
      { key: "REPORTED", label: "Reported", state: "DONE", at: "2026-11-01T08:00:00Z", note: null },
      { key: "ACCEPTED", label: "Accepted by the team", state: "DONE", at: "2026-11-01T09:00:00Z", note: null },
      { key: "BOOKED", label: "Booked", state: "CURRENT", at: null, note: null },
      { key: "IN_PROGRESS", label: "Work under way", state: "PENDING", at: null, note: null }] })
    api.photos.mockResolvedValue([{ id: "p2", fileName: "after.jpg", kind: "AFTER_PHOTO", createdAt: "2026-11-02T00:00:00Z" }, { id: "p1", fileName: "before.jpg", kind: "BEFORE_PHOTO", createdAt: "2026-11-01T00:00:00Z" }])
    api.photoBlob.mockResolvedValue(new Blob(["x"]))
    wrap(<TrackModal cid="c1" woId="w1" title="WO-1 · progress" onClose={() => {}} />)
    expect(await screen.findByText("Booked")).toBeTruthy()
    expect(screen.getByRole("list", { name: "Progress" }).querySelector("[aria-current=step]")?.textContent).toContain("Booked")
    const imgs = await screen.findAllByRole("img")
    expect(imgs.map(i => i.getAttribute("alt"))).toEqual(["Before: before.jpg", "After: after.jpg"])
  })
  it("says so when there are no photos", async () => {
    api.progress.mockResolvedValue({ status: "OPEN", steps: [{ key: "REPORTED", label: "Reported", state: "DONE", at: null, note: null }] })
    api.photos.mockResolvedValue([])
    wrap(<TrackModal cid="c1" woId="w1" title="t" onClose={() => {}} />)
    expect(await screen.findByText(/No photos yet/)).toBeTruthy()
  })
})

describe("AssetsPanel", () => {
  it("lists equipment with its site and warranty, and explains an empty list", async () => {
    api.assets.mockResolvedValue([{ id: "a1", siteId: "s1", assetTag: "AC-01", name: "Aircon unit", assetType: "HVAC", location: "Roof", manufacturer: "Daikin", model: "X1", status: "DOWN", installDate: null, warrantyExpiryDate: "2020-01-01" }])
    wrap(<AssetsPanel cid="c1" />)
    expect(await screen.findByText("Aircon unit")).toBeTruthy()
    expect(screen.getAllByText("Head Office").length).toBe(2); expect(screen.getByText("Down")).toBeTruthy(); expect(screen.getByText(/Ended/)).toBeTruthy()
  })
  it("shows an empty message", async () => {
    api.assets.mockResolvedValue([]); wrap(<AssetsPanel cid="c1" />)
    expect(await screen.findByText("No equipment is listed yet.")).toBeTruthy()
  })
})

describe("ReportsPanel", () => {
  it("summarises the service and lists documents", async () => {
    api.ordersAll.mockResolvedValue({ content: [{ id: "w1", workOrderNumber: "WO-1", siteId: "s1", assetId: null, category: "CORRECTIVE", priority: "N", status: "COMPLETED", description: "d", reportedBy: null, scheduledDate: null, completedAt: new Date().toISOString(), createdAt: new Date(Date.now() - 2 * 86400000).toISOString(), declineMessage: null, resolutionDueAt: null, resolutionSla: "MET" }], totalElements: 1, totalPages: 1, number: 0 })
    api.invoices.mockResolvedValue([{ id: "i1", invoiceNumber: "INV-9", periodStart: "2026-10-01", periodEnd: "2026-10-31", issueDate: "2026-11-01", dueDate: null, subtotal: 1, vatAmount: 0, total: 100, amountPaid: 0, creditedAmount: null, balance: 100, status: "SENT" }])
    api.credits.mockResolvedValue([])
    wrap(<ReportsPanel cid="c1" />)
    await waitFor(() => expect(screen.getByText("100%")).toBeTruthy())
    expect(screen.getByText("2 days")).toBeTruthy()
    expect(await screen.findByText("INV-9")).toBeTruthy()
  })
  it("explains when there is nothing yet", async () => {
    api.ordersAll.mockResolvedValue({ content: [], totalElements: 0, totalPages: 0, number: 0 }); api.invoices.mockResolvedValue([]); api.credits.mockResolvedValue([])
    wrap(<ReportsPanel cid="c1" />)
    expect(await screen.findByText(/nothing to report yet/)).toBeTruthy(); expect(await screen.findByText(/No documents yet/)).toBeTruthy()
  })
})
