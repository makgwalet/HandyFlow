import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({
  apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}))
vi.mock("../../store/auth.store", () => ({
  useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "me", permissions: perms } }),
}))

import DisposalsTab from "./DisposalsTab"

afterEach(cleanup)

const disposal = (over: Record<string, unknown> = {}) => ({
  id: "r1", assetId: "a1", assetName: "CAT D9", fleetNumber: "D9-001", reason: "SCRAPPED", status: "REQUESTED", details: "Engine seized",
  counterparty: null, proceeds: null, requestedBy: "other", requestedAt: "2026-10-20T08:00:00Z", decidedBy: null, decidedAt: null, decisionNote: null,
  completedBy: null, completedAt: null, cancelledBy: null, cancelledAt: null, cancelReason: null, evidenceCount: 1, blockers: [], ...over,
})
let list: unknown[] = []
let evidence: unknown[] = []

const mount = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><DisposalsTab /></QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  perms = ["EARTHMOVING_READ"]
  list = [disposal()]
  evidence = [{ id: "d1", title: "Scrap certificate", current: { fileName: "scrap.pdf" } }]
  get.mockImplementation((url: string) => {
    if (url.includes("/disposals")) return Promise.resolve({ data: { data: list } })
    if (url.includes("/documents?")) return Promise.resolve({ data: { data: evidence } })
    if (url.includes("/assets")) return Promise.resolve({ data: { data: { content: [{ id: "a1", name: "CAT D9", fleetNumber: "D9-001", status: "AVAILABLE" }], totalPages: 1 } } })
    return Promise.resolve({ data: { data: [] } })
  })
  post.mockResolvedValue({ data: { data: disposal() } })
})

describe("Disposals", () => {
  it("lists requests with their reason, status and evidence", async () => {
    mount()
    await waitFor(() => expect(screen.getByText("Engine seized")).toBeTruthy())
    expect(screen.getByText(/Scrapped · Waiting for approval/)).toBeTruthy()
    await waitFor(() => expect(screen.getByText("Scrap certificate")).toBeTruthy())
  })

  it("warns when there is no evidence yet", async () => {
    evidence = []
    mount()
    await waitFor(() => expect(screen.getByText(/No evidence attached yet/)).toBeTruthy())
  })

  it("shows what blocks a live request", async () => {
    list = [disposal({ blockers: ["The machine is still out on a deployment or hire. Return it first."] })]
    mount()
    await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("still out on a deployment"))
  })

  it("hides every action from someone who can only read", async () => {
    mount()
    await waitFor(() => expect(screen.getByText("Engine seized")).toBeTruthy())
    expect(screen.queryByText("Dispose of a machine")).toBeNull()
    expect(screen.queryByText("Approve")).toBeNull()
    expect(screen.queryByText("Cancel request")).toBeNull()
  })

  it("lets an administrator approve someone else's request, with a note", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    mount()
    await waitFor(() => expect(screen.getByText("Approve")).toBeTruthy())
    fireEvent.click(screen.getByText("Approve"))
    fireEvent.change(screen.getByLabelText("Note (optional)"), { target: { value: "Go ahead" } })
    fireEvent.click(screen.getByRole("button", { name: "Approve disposal" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/earthmoving/disposals/r1/approve", { note: "Go ahead" }))
  })

  it("does not offer approval on your own request", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN", "EARTHMOVING_WRITE"]
    list = [disposal({ requestedBy: "me" })]
    mount()
    await waitFor(() => expect(screen.getByText("Engine seized")).toBeTruthy())
    expect(screen.queryByText("Approve")).toBeNull()
    expect(screen.getByText("Cancel request")).toBeTruthy()
  })

  it("makes the administrator say why when rejecting", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    mount()
    await waitFor(() => expect(screen.getByText("Reject")).toBeTruthy())
    fireEvent.click(screen.getByText("Reject"))
    fireEvent.click(screen.getByRole("button", { name: "Reject disposal" }))
    await waitFor(() => expect(screen.getByText(/Give a reason for rejecting/)).toBeTruthy())
    expect(post).not.toHaveBeenCalled()
  })

  it("completes an approved request, but not while something blocks it", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    list = [disposal({ status: "APPROVED", blockers: ["1 hire charge has not been invoiced yet. Invoice or void it first."] })]
    mount()
    await waitFor(() => expect(screen.getByText("Complete and retire")).toBeTruthy())
    expect((screen.getByText("Complete and retire") as HTMLButtonElement).disabled).toBe(true)
    cleanup()
    list = [disposal({ status: "APPROVED" })]
    mount()
    await waitFor(() => expect(screen.getByText("Complete and retire")).toBeTruthy())
    fireEvent.click(screen.getByText("Complete and retire"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/earthmoving/disposals/r1/complete"))
  })

  it("asks who a sold machine went to before sending the request", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE"]
    mount()
    await waitFor(() => expect(screen.getByText("Dispose of a machine")).toBeTruthy())
    fireEvent.click(screen.getByText("Dispose of a machine"))
    await waitFor(() => expect(screen.getByRole("option", { name: /D9-001/ })).toBeTruthy())
    fireEvent.change(screen.getByLabelText("Machine"), { target: { value: "a1" } })
    fireEvent.change(screen.getByLabelText("Details"), { target: { value: "Sold at auction" } })
    fireEvent.click(screen.getByRole("button", { name: "Submit request" }))
    await waitFor(() => expect(screen.getByText(/Say who the machine is sold to/)).toBeTruthy())
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Buyer"), { target: { value: "Acme Plant" } })
    fireEvent.click(screen.getByRole("button", { name: "Submit request" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/earthmoving/disposals",
      { assetId: "a1", reason: "SOLD", details: "Sold at auction", counterparty: "Acme Plant", proceeds: null }))
  })
})
