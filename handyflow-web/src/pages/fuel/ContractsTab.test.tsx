import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ContractsTab from "./ContractsTab"

const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
let can = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => can }))

const contract = (o: any = {}) => ({ id: "c1", accountId: "a1", customerName: "Acme Mining", contractNumber: "C-001", title: "Mine supply 2026", startDate: "2026-10-01", endDate: "2026-12-31", fuelType: "DIESEL",
  committedLitres: 10000, terms: "Net 30", status: "ACTIVE", cancelledReason: null, deliveredLitres: 4500, percentOfCommitment: 45, litresRemaining: 5500, daysLeft: 49, linkedPrices: 0, prices: [], createdAt: "x", ...o })
function mount() { render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ContractsTab /></QueryClientProvider>) }

describe("ContractsTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset(); can = true
    get.mockImplementation(async (u: string) => ({ data: { data: u.endsWith("/contracts") ? [contract()] : u.includes("customer-accounts") ? [{ id: "a1", customerName: "Acme Mining" }]
      : [{ id: "p1", accountId: "a1", fuelType: "DIESEL", pricePerLitre: 21.5, validFrom: "2026-10-01" }, { id: "p2", accountId: "other", fuelType: "DIESEL", pricePerLitre: 20, validFrom: "2026-10-01" }] } }))
    post.mockResolvedValue({ data: { data: {} } }); put.mockResolvedValue({ data: { data: {} } }); del.mockResolvedValue({ data: { data: {} } })
  })
  afterEach(cleanup)

  it("lists contracts with status, days left and litres against the commitment, and says it holds no prices", async () => {
    mount()
    expect(await screen.findByText("C-001")).toBeTruthy()
    expect(screen.getByText("Active")).toBeTruthy(); expect(screen.getByText("49 days left")).toBeTruthy()
    expect(screen.getByText(/45\.0%/)).toBeTruthy(); expect(screen.getByText(/holds no prices of its own/)).toBeTruthy()
  })
  it("creates a contract with the account, number and dates", async () => {
    mount()
    fireEvent.click(await screen.findByText("New contract"))
    await waitFor(() => expect((screen.getByLabelText(/Customer account/) as HTMLSelectElement).options.length).toBe(2))
    fireEvent.change(screen.getByLabelText(/Customer account/), { target: { value: "a1" } })
    fireEvent.change(screen.getByLabelText(/Contract number/), { target: { value: "C-002" } })
    fireEvent.change(screen.getByLabelText(/Title/), { target: { value: "Haulage" } })
    fireEvent.change(screen.getByLabelText(/Starts/), { target: { value: "2026-11-01" } })
    fireEvent.change(screen.getByLabelText(/Ends/), { target: { value: "2027-01-31" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/fuel/contracts"); expect(post.mock.calls[0][1]).toMatchObject({ accountId: "a1", contractNumber: "C-002", title: "Haulage", committedLitres: null })
  })
  it("will not save with the end before the start", async () => {
    mount()
    fireEvent.click(await screen.findByText("New contract"))
    await waitFor(() => expect((screen.getByLabelText(/Customer account/) as HTMLSelectElement).options.length).toBe(2))
    fireEvent.change(screen.getByLabelText(/Customer account/), { target: { value: "a1" } })
    fireEvent.change(screen.getByLabelText(/Contract number/), { target: { value: "C-002" } })
    fireEvent.change(screen.getByLabelText(/Title/), { target: { value: "x" } })
    fireEvent.change(screen.getByLabelText(/Starts/), { target: { value: "2026-11-01" } })
    fireEvent.change(screen.getByLabelText(/Ends/), { target: { value: "2026-10-01" } })
    fireEvent.click(screen.getByText("Save"))
    expect(await screen.findByText(/before the start/)).toBeTruthy(); expect(post).not.toHaveBeenCalled()
  })
  it("offers only this customer's unlinked prices to link, and links the one chosen", async () => {
    mount()
    fireEvent.click(await screen.findByText("C-001"))
    const sel = await screen.findByLabelText("Link a price")
    await waitFor(() => expect(sel.querySelectorAll("option").length).toBe(2))   // the prompt and p1, not the other account's p2
    fireEvent.change(sel, { target: { value: "p1" } })
    fireEvent.click(screen.getByText("Link"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/fuel/contracts/c1/prices/p1"))
  })
  it("cancelling needs a reason", async () => {
    mount()
    fireEvent.click(await screen.findByText("Cancel"))
    fireEvent.click(screen.getByText("Cancel contract"))
    expect(await screen.findByText(/Give a reason/)).toBeTruthy(); expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "Customer left" } })
    fireEvent.click(screen.getByText("Cancel contract"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/contracts/c1/cancel", { reason: "Customer left" }))
  })
  it("people who can only read see no buttons", async () => {
    can = false; mount()
    await screen.findByText("C-001")
    expect(screen.queryByText("New contract")).toBeNull(); expect(screen.queryByText("Edit")).toBeNull()
  })
})
