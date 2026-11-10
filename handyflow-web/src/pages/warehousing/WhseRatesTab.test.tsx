import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseRatesTab from "./WhseRatesTab"

const get = vi.fn()
const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.includes(p) }))

const day = (offset: number) => { const d = new Date(); d.setDate(d.getDate() + offset); return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}` }
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><WhseRatesTab clientId="c1" /></QueryClientProvider>)

beforeEach(() => {
  get.mockReset(); post.mockReset(); perms = []; post.mockResolvedValue({ data: { id: "x" } })
  get.mockImplementation(async (url: string) => {
    if (url.includes("storage-tariffs")) return { data: [
      { id: "t3", clientId: "c1", itemId: null, monthlyRate: 5, effectiveFrom: day(10), note: "renewal" },
      { id: "t2", clientId: "c1", itemId: null, monthlyRate: 4, effectiveFrom: day(-5), note: null },
      { id: "t1", clientId: "c1", itemId: null, monthlyRate: 3, effectiveFrom: day(-60), note: null },
      { id: "t0", clientId: null, itemId: null, monthlyRate: 2, effectiveFrom: day(-90), note: null }] }
    if (url.includes("billing-terms")) return { data: [{ id: "m1", clientId: "c1", minimumStorageCharge: 500, effectiveFrom: day(-1), note: null }] }
    return { data: [{ id: "i1", sku: "SKU-1" }] }
  })
})
afterEach(cleanup)

describe("rates and terms", () => {
  it("marks which version is in force, which is history and which starts later", async () => {
    wrap()
    await waitFor(() => expect(screen.getByText("STARTS LATER")).toBeTruthy())
    expect(screen.getAllByText("IN FORCE").length).toBe(3)       // client rate, warehouse default, minimum
    expect(screen.getAllByText("SUPERSEDED").length).toBe(1)      // the older client rate
    expect(screen.getByText(/All clients/)).toBeTruthy()
  })

  it("a user who is not a warehouse administrator cannot add anything", async () => {
    wrap()
    await waitFor(() => screen.getByText("STARTS LATER"))
    expect(screen.queryByText("Add rate")).toBeNull()
    expect(screen.queryByText("Set minimum")).toBeNull()
    expect(screen.getByText(/Only a warehouse administrator can add a rate/)).toBeTruthy()
  })

  it("an administrator adds a rate that starts on the chosen day", async () => {
    perms = ["WAREHOUSING_ADMIN"]
    wrap()
    const add = await screen.findByText("Add rate")
    fireEvent.change(screen.getByLabelText(/New monthly rate/), { target: { value: "6.5" } })
    fireEvent.change(screen.getByLabelText(/Starts \*/, { selector: "#rate-from" }), { target: { value: day(3) } })
    fireEvent.click(add)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/storage-tariffs",
      { clientId: "c1", itemId: null, monthlyRate: 6.5, effectiveFrom: day(3), note: null }))
  })

  it("an administrator sets a minimum charge", async () => {
    perms = ["WAREHOUSING_ADMIN"]
    wrap()
    const set = await screen.findByText("Set minimum")
    fireEvent.change(screen.getByLabelText(/Minimum per 30 days/), { target: { value: "750" } })
    fireEvent.click(set)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/billing-terms",
      { clientId: "c1", minimumStorageCharge: 750, effectiveFrom: day(0) }))
  })
})
