import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ReceiptsTab from "./ReceiptsTab"

const get = vi.fn(), post = vi.fn()
let can = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => can }))

const receipt = { id: "r1", tankId: "t1", litresReceived: 5000, pricePerLitre: 20, totalCost: 100000, receivedAt: "2026-11-01T08:00:00Z", reversedAt: null, freightAmount: null }
let history: any
function mount() { render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ReceiptsTab /></QueryClientProvider>) }

describe("ReceiptsTab late freight", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); can = true
    history = { receiptId: "r1", freightTotal: null, landedPricePerLitre: 20, adjustments: [] }
    get.mockImplementation(async (u: string) => ({ data: { data: u.includes("/freight") ? history : u.includes("/tanks") ? [{ id: "t1", name: "Diesel 1", fuelType: "DIESEL" }] : { content: [receipt] } } }))
  })
  afterEach(cleanup)

  it("adds freight with a note and refreshes", async () => {
    post.mockResolvedValue({ data: { data: history } })
    mount()
    fireEvent.click(await screen.findByText("Freight"))
    fireEvent.change(await screen.findByLabelText(/Freight \(R\)/), { target: { value: "1500" } })
    fireEvent.change(screen.getByLabelText(/What it is for/), { target: { value: "Carrier invoice 1042" } })
    fireEvent.click(screen.getByText("Add freight"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/receipts/r1/freight", { amount: 1500, note: "Carrier invoice 1042" }))
  })
  it("will not send without an amount or a note", async () => {
    mount()
    fireEvent.click(await screen.findByText("Freight"))
    fireEvent.click(await screen.findByText("Add freight"))
    expect(await screen.findByText(/more than zero/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })
  it("shows earlier additions and what each did to the tank's cost", async () => {
    history = { ...history, freightTotal: 1000, adjustments: [{ id: "a1", amount: 1000, note: "Inv 7", litresInTank: 2000, absorbedAmount: 400, unabsorbedAmount: 600, costBefore: 20, costAfter: 20.2, createdAt: "2026-11-10T10:00:00Z" }] }
    mount()
    fireEvent.click(await screen.findByText("Freight"))
    expect(await screen.findByText(/belongs to fuel already sold/)).toBeTruthy()
  })
  it("hides the Freight button without permission", async () => {
    can = false
    mount()
    await screen.findByText("Reverse")
    expect(screen.queryByText("Freight")).toBeNull()
  })
})
