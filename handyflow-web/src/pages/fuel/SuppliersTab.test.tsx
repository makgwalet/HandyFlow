import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import SuppliersTab from "./SuppliersTab"

const get = vi.fn(); const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: vi.fn() } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const suppliers = [
  { id: "s1", name: "Acme Fuels", approvalStatus: "PENDING_APPROVAL", createdAt: "2026-11-11T10:00:00Z" },
  { id: "s2", name: "Beta Oil", approvalStatus: "APPROVED", createdAt: "2026-11-11T10:00:00Z" },
  { id: "s3", name: "Gamma", approvalStatus: "SUSPENDED", statusReason: "Licence expired", createdAt: "2026-11-11T10:00:00Z" },
]
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><SuppliersTab /></QueryClientProvider>)
}

describe("SuppliersTab approval", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); perms = ["FUEL_SUPPLIER_APPROVE"]; get.mockResolvedValue({ data: suppliers }); post.mockResolvedValue({ data: {} }) })
  afterEach(cleanup)

  it("shows who cannot be ordered from and why", async () => {
    mount()
    await screen.findByText("Acme Fuels")
    expect(screen.getByText(/Waiting for approval/)).toBeTruthy()
    expect(screen.getByText(/Suspended: Licence expired/)).toBeTruthy()
  })
  it("approves a pending supplier and reinstates a suspended one", async () => {
    mount()
    fireEvent.click(await screen.findByLabelText("Approve Acme Fuels"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/suppliers/s1/approve"))
    fireEvent.click(screen.getByLabelText("Approve Gamma"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/suppliers/s3/approve"))
  })
  it("suspends only with a reason", async () => {
    mount()
    fireEvent.click(await screen.findByLabelText("Suspend Beta Oil"))
    fireEvent.change(screen.getByLabelText("Reason *"), { target: { value: "Failed an audit" } })
    fireEvent.click(screen.getAllByText("Suspend").find(b => (b as HTMLElement).style.flex !== "")!)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/suppliers/s2/suspend", { reason: "Failed an audit" }))
  })
  it("shows the server's refusal, for example approving your own supplier", async () => {
    post.mockRejectedValue({ response: { data: { message: "A supplier cannot be approved by the person who added it." } } })
    mount()
    fireEvent.click(await screen.findByLabelText("Approve Acme Fuels"))
    expect(await screen.findByText(/cannot be approved by the person who added it/)).toBeTruthy()
  })
  it("hides approval controls without the right", async () => {
    perms = []
    mount()
    await screen.findByText("Acme Fuels")
    expect(screen.queryByLabelText("Approve Acme Fuels")).toBeNull()
    expect(screen.queryByLabelText("Suspend Beta Oil")).toBeNull()
  })
})
