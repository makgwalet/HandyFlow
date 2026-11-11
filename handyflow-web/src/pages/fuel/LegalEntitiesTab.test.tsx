import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import LegalEntitiesTab from "./LegalEntitiesTab"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn(); const del = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))
const rows = [{ id: "e1", name: "Acme Fuels", registrationNo: "2020/1", vatNo: "123", status: "ACTIVE", depotCount: 2 }]
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><LegalEntitiesTab /></QueryClientProvider>)
}

describe("LegalEntitiesTab", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset(); perms = ["FUEL_CONFIGURATION_MANAGE"]; get.mockResolvedValue({ data: { data: rows } }); post.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} }) })
  afterEach(cleanup)

  it("lists entities with depot counts", async () => {
    mount()
    expect(await screen.findByText("Acme Fuels")).toBeTruthy()
    expect(screen.getByText(/2 depots/)).toBeTruthy()
  })
  it("requires a name and then creates", async () => {
    mount()
    fireEvent.click(await screen.findByText("Add legal entity"))
    fireEvent.click(screen.getByText("Save"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/name/)
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Name *"), { target: { value: "Beta Oil" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/legal-entities", expect.objectContaining({ name: "Beta Oil" })))
  })
  it("shows the server's reason when removal is refused", async () => {
    del.mockRejectedValue({ response: { data: { message: "2 depots still use it" } } })
    mount()
    fireEvent.click(await screen.findByLabelText("Remove Acme Fuels"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/2 depots/)
  })
  it("is read-only without the manage permission", async () => {
    perms = ["FUEL_CONFIGURATION_READ"]
    mount()
    await screen.findByText("Acme Fuels")
    expect(screen.queryByText("Add legal entity")).toBeNull()
    expect(screen.queryByLabelText("Remove Acme Fuels")).toBeNull()
  })
})
