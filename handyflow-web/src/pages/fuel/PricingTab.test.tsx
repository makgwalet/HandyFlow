import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import PricingTab from "./PricingTab"

const get = vi.fn(); const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const row = (o: any = {}) => ({ id: "p1", fuelType: "DIESEL", accountId: null, customerName: null, pricePerLitre: 22.5, minLitres: 0, validFrom: "2026-11-01", validTo: null, note: null, live: true, ...o })
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><PricingTab /></QueryClientProvider>)
}

describe("PricingTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); perms = ["FUEL_MANAGE"]
    post.mockResolvedValue({ data: { data: {} } })
    get.mockImplementation(async (u: string) => {
      if (u.endsWith("/prices")) return { data: { data: [row(), row({ id: "p2", accountId: "a1", customerName: "Gold Mine Ltd", pricePerLitre: 21.9, minLitres: 10000 })] } }
      if (u.includes("customer-accounts")) return { data: { data: [{ id: "a1", customerName: "Gold Mine Ltd" }] } }
      return { data: { data: [] } }
    })
  })
  afterEach(cleanup)

  it("shows standard and contract prices", async () => {
    mount()
    expect(await screen.findByText("Standard list")).toBeTruthy()
    expect(screen.getByText("Gold Mine Ltd")).toBeTruthy()
  })

  it("will not save a price without a number and says why", async () => {
    mount()
    await screen.findByText("Standard list")
    fireEvent.click(screen.getByText("Add price"))
    fireEvent.click(screen.getByText("Save price"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/price per litre/)
    expect(post).not.toHaveBeenCalled()
  })

  it("saves a contract price with a volume break", async () => {
    mount()
    await screen.findByText("Standard list")
    fireEvent.click(screen.getByText("Add price"))
    await screen.findByText("Contract: Gold Mine Ltd")
    fireEvent.change(screen.getByLabelText("Price for"), { target: { value: "a1" } })
    fireEvent.change(screen.getByLabelText("Price per litre"), { target: { value: "21.5" } })
    fireEvent.change(screen.getByLabelText("Volume break"), { target: { value: "20000" } })
    fireEvent.click(screen.getByText("Save price"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1]).toMatchObject({ accountId: "a1", pricePerLitre: 21.5, minLitres: 20000, fuelType: "DIESEL" })
  })

  it("shows the server's message when a price overlaps", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "A standard price for DIESEL already covers those dates." } } })
    mount()
    await screen.findByText("Standard list")
    fireEvent.click(screen.getByText("Add price"))
    fireEvent.change(screen.getByLabelText("Price per litre"), { target: { value: "22" } })
    fireEvent.click(screen.getByText("Save price"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/already covers/)
  })

  it("hides Add price without the manage permission", async () => {
    perms = ["FUEL_READ"]
    mount()
    await screen.findByText("Standard list")
    expect(screen.queryByText("Add price")).toBeNull()
  })
})
