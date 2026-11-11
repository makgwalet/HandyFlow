import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import AdjustmentRequestsPanel from "./AdjustmentRequestsPanel"

const get = vi.fn(); const post = vi.fn()
let perms: string[] = []; let me = "bob"
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: (s: any) => unknown) => sel({ user: { userId: me } }) }))

const rows = [{ id: "r1", tankId: "t1", tankName: "Diesel 1", litres: -800, reason: "Stocktake loss", status: "PENDING", requestedBy: "alice", requestedAt: "2026-11-11T10:00:00Z" }]
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><AdjustmentRequestsPanel /></QueryClientProvider>)
}

describe("AdjustmentRequestsPanel", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); perms = ["FUEL_STOCK_ADJUST_APPROVE", "FUEL_STOCK_ADJUST"]; me = "bob"; get.mockResolvedValue({ data: { data: rows } }); post.mockResolvedValue({ data: {} }) })
  afterEach(cleanup)

  it("lets a second person approve", async () => {
    mount()
    fireEvent.click(await screen.findByLabelText("Approve adjustment for Diesel 1"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/adjustment-requests/r1/approve"))
  })
  it("does not offer the requester approval, only withdrawal", async () => {
    me = "alice"
    mount()
    await screen.findByText("Diesel 1")
    expect(screen.queryByLabelText("Approve adjustment for Diesel 1")).toBeNull()
    expect(screen.getByText(/someone else must approve/)).toBeTruthy()
    fireEvent.click(screen.getByLabelText("Withdraw adjustment for Diesel 1"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/adjustment-requests/r1/withdraw"))
  })
  it("needs a reason to reject", async () => {
    mount()
    fireEvent.click(await screen.findByLabelText("Reject adjustment for Diesel 1"))
    fireEvent.change(screen.getByLabelText("Reason *"), { target: { value: "Count was wrong" } })
    fireEvent.click(screen.getAllByText("Reject").find(b => (b as HTMLElement).style.flex !== "")!)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/adjustment-requests/r1/reject", { reason: "Count was wrong" }))
  })
  it("shows the server's refusal", async () => {
    post.mockRejectedValue({ response: { data: { message: "Stock is reserved" } } })
    mount()
    fireEvent.click(await screen.findByLabelText("Approve adjustment for Diesel 1"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/reserved/)
  })
  it("renders nothing when nothing is waiting, and does not call without a right to see it", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    mount()
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(screen.queryByText(/waiting for a second person/)).toBeNull()
    cleanup(); get.mockClear(); perms = []
    mount()
    expect(get).not.toHaveBeenCalled()
  })
})
