import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ForecourtMatchingSection from "./ForecourtMatchingSection"
import { differenceText, isFcMatching, receiptChoices } from "./forecourtSupplyOps"

const get = vi.fn(); const post = vi.fn(); const del = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const rep = (o: any = {}) => ({
  from: "2026-10-19", to: "2026-11-17", forecourts: [{
    depotId: "f1", name: "Forecourt One", dispatchedLitres: 15000, receivedLitres: 9900,
    matched: [{ dispatchId: "d1", dispatchedAt: "2026-11-10T08:00:00Z", fromTank: "Terminal diesel", fuelType: "DIESEL", litresDispatched: 10000, litresReceived: 9900, difference: 100, receipts: [{ matchId: "m1", receiptId: "r1", receivedAt: "2026-11-10T11:00:00Z", litres: 9900, deliveryNote: null }] }],
    openDispatches: [{ dispatchId: "d2", dispatchedAt: "2026-11-12T08:00:00Z", fromTank: "Terminal diesel", fuelType: "DIESEL", litres: 5000 }],
    openReceipts: [{ receiptId: "r2", receivedAt: "2026-11-12T12:00:00Z", tank: "FC diesel", fuelType: "DIESEL", litres: 4980, deliveryNote: "DN 88" }, { receiptId: "r3", receivedAt: "2026-11-12T12:30:00Z", tank: "FC petrol", fuelType: "PETROL", litres: 100, deliveryNote: null }],
  }], ...o })
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={qc}><ForecourtMatchingSection days={30} /></QueryClientProvider>)
}

describe("ForecourtMatchingSection", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); del.mockReset(); perms = ["FUEL_MANAGE"]; get.mockResolvedValue({ data: { data: rep() } }); post.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} }) })
  afterEach(cleanup)

  it("shows linked dispatches with the gap, and dispatches nothing is linked to", async () => {
    mount()
    expect(await screen.findByText(/Forecourt One/)).toBeTruthy()
    expect(screen.getByText(/less received than dispatched/)).toBeTruthy()
    expect(screen.getByText("No receipt linked")).toBeTruthy()
    expect(get.mock.calls[0][0]).toBe("/api/v1/fuel/forecourt/supply-report/dispatch-matching?days=30")
  })

  it("offers only open receipts of the same fuel and links the chosen one", async () => {
    mount()
    const sel = await screen.findByLabelText("Receipt for dispatch d2") as HTMLSelectElement
    expect(Array.from(sel.options).map(o => o.value)).toEqual(["", "r2"]) // the petrol receipt is not offered
    fireEvent.change(sel, { target: { value: "r2" } })
    fireEvent.click(screen.getByRole("button", { name: "Link" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/forecourt/supply-report/dispatch-matching", { dispatchId: "d2", receiptId: "r2" }))
  })

  it("unlinks a receipt", async () => {
    mount()
    fireEvent.click(await screen.findByRole("button", { name: /Unlink/ }))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/fuel/forecourt/supply-report/dispatch-matching/m1"))
  })

  it("is read-only without the manage permission", async () => {
    perms = []
    mount()
    await screen.findByText(/Forecourt One/)
    expect(screen.queryByRole("button", { name: "Link" })).toBeNull()
    expect(screen.queryByRole("button", { name: /Unlink/ })).toBeNull()
  })

  it("renders nothing when refused, empty, or not a report", async () => {
    get.mockRejectedValue({ response: { status: 403 } })
    mount(); await new Promise(r => setTimeout(r, 30))
    expect(screen.queryByLabelText("Forecourt dispatches and receipts")).toBeNull()
    expect(isFcMatching({ forecourts: [{ matched: 1 }] })).toBe(false)
  })

  it("words the gap plainly", () => {
    const m: any = { difference: 0 }
    expect(differenceText(m)).toBe("Received in full")
    expect(differenceText({ difference: -50 } as any)).toMatch(/more received/)
    expect(receiptChoices(rep().forecourts[0] as any, rep().forecourts[0].openDispatches[0] as any).length).toBe(1)
  })
})
