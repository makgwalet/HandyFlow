import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
vi.mock("./companyZone", async orig => ({ ...(await orig<typeof import("./companyZone")>()), useCompanyZone: () => ({ zone: "Africa/Johannesburg", ready: true }) }))
import SiteBillingCard from "./SiteBillingCard"
import { dateTimeIn } from "./companyZone"
import { useAuthStore } from "../../store/auth.store"

const sast = (iso: string) => dateTimeIn("Africa/Johannesburg", iso)
const wrap = (data: unknown) => ({ data: { data } })
const draft = (o = {}) => ({ id: "d1", siteId: "s1", month: "2026-09", basis: "SCHEDULED", currency: "ZAR", status: "DRAFT", totalCents: 1850000, inProgress: false, unpricedHours: 0, mixedVat: false,
  note: null, createdAt: "2026-10-02T08:00:00Z", approvedAt: null, voidedAt: null, voidReason: null, ...o })
const snap = (o = {}) => ({ siteId: "s1", siteName: "Mall", month: "2026-09", lines: [{ rateId: "r1", type: "HOURLY", rateCents: 18500, vatInclusive: false, hours: 100, daysInForce: 30, amountCents: 1850000 }],
  billedHours: 100, unpricedHours: 0, mixedVat: false, totalCents: 1850000, warnings: ["VAT is not calculated. Amounts are as set on each rate."], ...o })

let drafts: unknown[] = [], detailBody: unknown
function route() {
  api.get.mockImplementation((url: string) => {
    if (url.endsWith("/rates")) return Promise.resolve(wrap([]))
    if (url.endsWith("/billing-preview")) return Promise.resolve(wrap({ siteId: "s1", siteName: "Mall", month: "2026-10", from: "2026-10-01", to: "2026-10-31", inProgress: false, basis: "SCHEDULED", currency: "ZAR", excludesVat: true, lines: [], billedHours: 0, unpricedHours: 0, mixedVat: false, totalCents: 0, warnings: [] }))
    if (url.endsWith("/invoice-drafts")) return Promise.resolve(wrap(drafts))
    if (url.includes("/invoice-drafts/")) return detailBody instanceof Error ? Promise.reject(detailBody) : Promise.resolve(wrap(detailBody))
    return Promise.reject(new Error("unexpected " + url))
  })
}
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><SiteBillingCard siteId="s1" /></QueryClientProvider>)

describe("a draft opened on the site's billing card", () => {
  beforeEach(() => { api.get.mockReset(); drafts = [draft()]; detailBody = { draft: draft(), snapshot: snap() }; route(); useAuthStore.setState({ user: { permissions: ["SECURITY_ADMIN"] } } as never) })
  afterEach(() => { cleanup(); useAuthStore.setState({ user: null } as never) })

  it("loads nothing about a draft until it is opened", async () => {
    show()
    await screen.findByText("Details")
    expect(api.get.mock.calls.some(c => String(c[0]).includes("/invoice-drafts/"))).toBe(false)
  })

  it("shows the frozen lines, the total and when it was saved, from the draft's own site", async () => {
    show()
    fireEvent.click(await screen.findByText("Details"))
    expect(await screen.findByTestId("snapshot-total")).toHaveProperty("textContent", "R18 500.00")
    expect(api.get.mock.calls.some(c => c[0] === "/api/v1/security/sites/s1/invoice-drafts/d1")).toBe(true)
    expect(screen.getByText(`Saved ${sast("2026-10-02T08:00:00Z")}`)).toBeTruthy()
    expect(sast("2026-10-02T08:00:00Z")).toMatch(/10:00/) // 08:00 UTC is 10:00 in the company zone
    expect(screen.getByText("R185.00 per hour")).toBeTruthy()
    expect(within(screen.getByLabelText("Draft detail")).getByText(/VAT is not calculated/)).toBeTruthy()
    expect(screen.getByText("Figures as they were when this was saved (scheduled hours)")).toBeTruthy()
  })

  it("closes again", async () => {
    show()
    fireEvent.click(await screen.findByText("Details"))
    await screen.findByTestId("snapshot-total")
    fireEvent.click(screen.getByText("Hide details"))
    expect(screen.queryByTestId("snapshot-total")).toBeNull()
  })

  it("shows when it was approved or voided, and why", async () => {
    detailBody = { draft: draft({ status: "VOID", approvedAt: "2026-10-03T08:00:00Z", voidedAt: "2026-10-04T08:00:00Z", voidReason: "Wrong rate" }), snapshot: snap() }
    drafts = [draft({ status: "VOID", voidReason: "Wrong rate", voidedAt: "2026-10-04T08:00:00Z" })]
    show()
    fireEvent.click(await screen.findByText("Details"))
    expect(await screen.findByText(`Approved ${sast("2026-10-03T08:00:00Z")}`)).toBeTruthy()
    expect(screen.getByText(`Voided ${sast("2026-10-04T08:00:00Z")}: Wrong rate`)).toBeTruthy()
  })

  it("names who saved, approved and voided it", async () => {
    detailBody = { draft: draft({ status: "VOID", createdBy: "u1", approvedBy: "u2", voidedBy: "u3", approvedAt: "2026-10-03T08:00:00Z", voidedAt: "2026-10-04T08:00:00Z", voidReason: "Wrong rate" }), snapshot: snap(),
      people: { u1: "Thandi Nkosi", u2: "Sipho Dlamini" } }
    drafts = [draft({ status: "VOID", voidReason: "Wrong rate", voidedAt: "2026-10-04T08:00:00Z" })]
    show()
    fireEvent.click(await screen.findByText("Details"))
    expect(await screen.findByText(`Saved by Thandi Nkosi, ${sast("2026-10-02T08:00:00Z")}`)).toBeTruthy()
    expect(screen.getByText(`Approved by Sipho Dlamini, ${sast("2026-10-03T08:00:00Z")}`)).toBeTruthy()
    expect(screen.getByText(`Voided by a user who is no longer listed, ${sast("2026-10-04T08:00:00Z")}: Wrong rate`)).toBeTruthy()
  })

  it("keeps the old wording when the server names nobody", async () => {
    detailBody = { draft: draft({ createdBy: "u1" }), snapshot: snap() }
    show()
    fireEvent.click(await screen.findByText("Details"))
    expect(await screen.findByText(`Saved ${sast("2026-10-02T08:00:00Z")}`)).toBeTruthy()
  })

  it("warns, but still shows the copy, when the saved total disagrees with the draft", async () => {
    detailBody = { draft: draft({ totalCents: 1000 }), snapshot: snap() }
    show()
    fireEvent.click(await screen.findByText("Details"))
    const alert = await screen.findByText(/Do not approve this one/)
    expect(alert.getAttribute("role")).toBe("alert")
    expect(screen.getByTestId("snapshot-total")).toBeTruthy()
  })

  it("says so when the saved copy cannot be read", async () => {
    detailBody = { draft: draft(), snapshot: null }
    show()
    fireEvent.click(await screen.findByText("Details"))
    expect(await screen.findByText(/could not be read/)).toBeTruthy()
    expect(screen.queryByTestId("snapshot-total")).toBeNull()
  })

  it("shows the server's words when the draft cannot be opened", async () => {
    detailBody = Object.assign(new Error("x"), { response: { status: 404, data: { message: "Draft not found" } } })
    show()
    fireEvent.click(await screen.findByText("Details"))
    expect(await screen.findByText("Draft not found")).toBeTruthy()
  })

  it("keeps Approve and Void working beside the details", async () => {
    api.post.mockResolvedValue(wrap({}))
    show()
    fireEvent.click(await screen.findByText("Details"))
    await screen.findByTestId("snapshot-total")
    fireEvent.click(screen.getByText("Approve"))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/security/sites/s1/invoice-drafts/d1/approve", { acknowledgeWarnings: false }))
  })
})
