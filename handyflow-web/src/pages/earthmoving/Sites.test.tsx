import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
const patch = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({
  apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a),
    patch: (...a: unknown[]) => patch(...a), delete: vi.fn() },
}))
vi.mock("../../store/auth.store", () => ({
  useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: perms } }),
}))

import SitesTab from "./SitesTab"

afterEach(cleanup)

const site = (over: Record<string, unknown> = {}) => ({
  id: "s1", name: "Mine A", address: "Rustenburg", latitude: null, longitude: null, customerId: "c1", customerName: "Anglo",
  projectId: null, projectName: null, supervisorUserId: "u2", notes: null, active: true, ...over,
})
const recon = (over: Record<string, unknown> = {}) => ({
  totalDeployments: 10, withSite: 4, withClientText: 8, withCustomer: 3, groups: 2,
  siteMatched: 1, siteAmbiguous: 1, siteUnmatched: 0, customerMatched: 1, customerAmbiguous: 0, customerUnmatched: 1,
  rows: [{ legacySite: "Mine B", legacyClient: "Twin", deployments: 3, siteState: "AMBIGUOUS", siteCandidates: [], customerState: "UNMATCHED", customerCandidates: [] }], ...over,
})
const mount = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><SitesTab /></QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  perms = []
  get.mockImplementation((url: string) => Promise.resolve(
    url.includes("/sites/reconciliation") ? { data: { data: recon() } }
      : url.includes("/earthmoving/sites") ? { data: { data: [site()] } }
      : url.includes("assignable-users") ? { data: [{ id: "u2", name: "Thandi Supervisor" }] }
      : url.includes("/crm/customers") ? { data: { data: { content: [{ id: "c1", name: "Anglo" }], last: true } } }
      : { data: { data: { content: [], last: true } } }))
  post.mockResolvedValue({ data: { data: site() } })
  put.mockResolvedValue({ data: { data: site() } })
  patch.mockResolvedValue({ data: { data: site({ active: false }) } })
})

describe("Sites", () => {
  it("shows each site with its customer and supervisor, and no edit buttons for a reader", async () => {
    perms = ["EARTHMOVING_READ"]
    mount()
    expect(await screen.findByText("Mine A")).toBeTruthy()
    expect(await screen.findByText(/Supervisor: Thandi Supervisor/)).toBeTruthy()
    expect(screen.getByText(/Customer: Anglo/)).toBeTruthy()
    expect(screen.queryByText("Add site")).toBeNull()
    expect(screen.queryByText("Edit")).toBeNull()
    expect(screen.queryByText("Links to review")).toBeNull()
  })

  it("adds a site with no project or customer", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE"]
    mount()
    fireEvent.click(await screen.findByText("Add site"))
    fireEvent.change(document.getElementById("site-name") as HTMLInputElement, { target: { value: "Quarry 2" } })
    fireEvent.click(screen.getByText("Save site"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const body = post.mock.calls[0][1]
    expect(body.name).toBe("Quarry 2")
    expect(body.projectId).toBeNull()
    expect(body.customerId).toBeNull()
  })

  it("refuses a latitude without a longitude before calling the server", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_WRITE"]
    mount()
    fireEvent.click(await screen.findByText("Add site"))
    fireEvent.change(document.getElementById("site-name") as HTMLInputElement, { target: { value: "Quarry 2" } })
    fireEvent.change(document.getElementById("site-lat") as HTMLInputElement, { target: { value: "-26.2" } })
    fireEvent.click(screen.getByText("Save site"))
    expect(await screen.findByText(/both latitude and longitude/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it("only an administrator sees the links to review, with counts that reconcile", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    mount()
    fireEvent.click(await screen.findByText("Links to review"))
    expect(await screen.findByText(/10 deployments in all/)).toBeTruthy()
    expect(screen.getByText(/1 ambiguous/)).toBeTruthy()
    expect(screen.getByText(/Mine B/)).toBeTruthy()
  })

  it("applies only the exact matches when asked", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    post.mockResolvedValue({ data: { data: { deploymentsLinkedToSite: 1, deploymentsLinkedToCustomer: 1, deploymentsLinkedToProject: 0, groupsStillUnresolved: 1 } } })
    mount()
    fireEvent.click(await screen.findByText("Links to review"))
    fireEvent.click(await screen.findByText("Apply exact matches"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toContain("/reconciliation/apply-verified")
    expect(await screen.findByText(/1 groups still need a person/)).toBeTruthy()
  })
})
