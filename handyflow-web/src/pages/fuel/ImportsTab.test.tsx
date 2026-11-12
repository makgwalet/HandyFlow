import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ImportsTab from "./ImportsTab"
import { litres, totalLitres, totalTendered } from "./importOps"

const get = vi.fn(); const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const imp = (o: any = {}) => ({ id: "i1", source: "ACME-POS", externalId: "S-1", status: "PENDING", receivedAt: "2026-11-12T14:05:00Z", problems: [], decidedBy: null, decidedAt: null, decisionNote: null, shiftId: null,
  shift: { source: "ACME-POS", externalId: "S-1", attendantName: "Thabo", openedAt: null, closedAt: null, cash: 2000, card: 350, account: 0, other: 0, fleetCard: null, notes: null,
    readings: [{ pumpName: "Pump 1", openingMeter: 1000, closingMeter: 1100, testLitres: 5 }] }, ...o })
function routes(items: unknown[], keys: unknown[] = []) {
  get.mockImplementation((url: string) => url.includes("/keys") ? ok(keys) : ok(items))
}
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><ImportsTab /></QueryClientProvider>)
}

describe("ImportsTab", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); perms = ["FUEL_SHIFT_SUPERVISE", "FUEL_MANAGE"]; post.mockResolvedValue({ data: { data: {} } }) })
  afterEach(cleanup)

  it("lists waiting shifts with litres and takings, and asks for PENDING by default", async () => {
    routes([imp()])
    mount()
    expect(await screen.findByText("Thabo")).toBeTruthy()
    expect(screen.getByText(/ACME-POS · S-1/)).toBeTruthy()
    expect(get.mock.calls.some(c => String(c[0]).endsWith("imports?status=PENDING"))).toBe(true)
  })

  it("accepts a shift, sending the variance reason only if typed", async () => {
    routes([imp()])
    mount()
    fireEvent.click(await screen.findByLabelText("Details for S-1"))
    fireEvent.change(screen.getByLabelText("Reason for any variance"), { target: { value: "Short by R5" } })
    fireEvent.click(screen.getByText("Accept shift"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/forecourt/imports/i1/accept", { varianceReason: "Short by R5" }))
  })

  it("rejects with a reason", async () => {
    routes([imp()])
    mount()
    fireEvent.click(await screen.findByLabelText("Details for S-1"))
    fireEvent.click(screen.getByText("Reject"))
    fireEvent.change(await screen.findByLabelText("Reason *"), { target: { value: "Typed in by hand already" } })
    fireEvent.click(screen.getAllByText("Reject").slice(-1)[0])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/forecourt/imports/i1/reject", { reason: "Typed in by hand already" }))
  })

  it("shows problems that need fixing before accepting", async () => {
    routes([imp({ problems: ['Pump "Pump 9" is not set up in Fuel.'] })])
    mount()
    expect(await screen.findByText("1 to fix")).toBeTruthy()
    fireEvent.click(screen.getByLabelText("Details for S-1"))
    expect(screen.getByText(/Pump 9/)).toBeTruthy()
  })

  it("shows the server's refusal when accepting fails, and the shift stays in the list", async () => {
    routes([imp()])
    post.mockRejectedValue({ response: { data: { message: "Enter a reason for the variance." } } })
    mount()
    fireEvent.click(await screen.findByLabelText("Details for S-1"))
    fireEvent.click(screen.getByText("Accept shift"))
    expect(await screen.findByText(/Enter a reason for the variance/)).toBeTruthy()
    expect(screen.getByText("Thabo")).toBeTruthy()
  })

  it("hides accept and reject without the supervisor permission", async () => {
    perms = ["FUEL_MANAGE"]
    routes([imp()])
    mount()
    fireEvent.click(await screen.findByLabelText("Details for S-1"))
    expect(screen.queryByText("Accept shift")).toBeNull()
  })

  it("creates a key and shows it once", async () => {
    routes([])
    post.mockResolvedValue({ data: { data: { key: { id: "k1", name: "Site 2 POS", keyPrefix: "hf_fuel_abcd", createdAt: "2026-11-12T10:00:00Z", lastUsedAt: null, revokedAt: null }, apiKey: "hf_fuel_abcdSECRET" } } })
    mount()
    fireEvent.change(await screen.findByLabelText("Key name"), { target: { value: "Site 2 POS" } })
    fireEvent.click(screen.getByText("Create key"))
    expect(await screen.findByText(/hf_fuel_abcdSECRET/)).toBeTruthy()
    expect(post).toHaveBeenCalledWith("/api/v1/fuel/forecourt/imports/keys", { name: "Site 2 POS" })
  })

  it("lists keys without ever showing the key itself, and revokes after confirming", async () => {
    routes([], [{ id: "k1", name: "Site 2 POS", keyPrefix: "hf_fuel_abcd", createdAt: "2026-11-12T10:00:00Z", lastUsedAt: null, revokedAt: null }])
    vi.spyOn(window, "confirm").mockReturnValue(true)
    mount()
    expect(await screen.findByText("Not used yet")).toBeTruthy()
    fireEvent.click(screen.getByText("Revoke"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/forecourt/imports/keys/k1/revoke"))
  })

  it("does not ask for keys without the key-management permission", async () => {
    perms = ["FUEL_SHIFT_SUPERVISE"]
    routes([imp()])
    mount()
    await screen.findByText("Thabo")
    expect(get.mock.calls.some(c => String(c[0]).includes("/keys"))).toBe(false)
    expect(screen.queryByText("Create key")).toBeNull()
  })

  it("importOps: litres net of test litres, never negative; totals add up", () => {
    expect(litres({ pumpName: "p", openingMeter: 10, closingMeter: 110, testLitres: 5 })).toBe(95)
    expect(litres({ pumpName: "p", openingMeter: 10, closingMeter: 10, testLitres: 5 })).toBe(0)
    expect(totalLitres(imp().shift)).toBe(95)
    expect(totalTendered(imp().shift)).toBe(2350)
  })
})
