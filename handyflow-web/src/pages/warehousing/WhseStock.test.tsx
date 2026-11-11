import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseStockTab from "./WhseStockTab"
import { hasNext, listParams, movementText, rangeText, signedQty, statusText } from "./stockView"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (u: string, cfg?: unknown) => get(u, cfg) } }))
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><WhseStockTab /></QueryClientProvider>)

const two = [{ id: "w1", code: "CPT", name: "Cape Town", active: true, isDefault: true }, { id: "w2", code: "JHB", name: "Johannesburg", active: true, isDefault: false }]
const stockRow = { inventoryId: "i1", clientName: "Acme", sku: "MILK-1L", description: "Milk 1L", uom: "EA", locationCode: "A-01", zone: "Cold", status: "QUARANTINE", qtyOnHand: 40, qtyAllocated: 10, available: 0, lotNumber: "L7", expiryDate: "2026-12-01" }
const moveRow = { id: "m1", at: "2026-11-10T08:00:00Z", clientName: "Acme", sku: "MILK-1L", locationCode: "A-01", movementType: "PICK", status: "AVAILABLE", qtyChange: -5, qtyBefore: 45, qtyAfter: 40, referenceNumber: "ORD-9", notes: null }
const serve = (warehouses: unknown[]) => get.mockImplementation(async (url: string) => {
  if (url.endsWith("/warehouses")) return { data: warehouses }
  if (url.endsWith("/clients/all")) return { data: [{ id: "c1", tradingName: "Acme" }] }
  if (url.endsWith("/operations/inventory")) return { data: { content: [stockRow], total: 120, page: 0, size: 50 } }
  if (url.endsWith("/operations/movements")) return { data: { content: [moveRow], total: 1, page: 0, size: 50 } }
  return { data: null }
})
const lastParams = (suffix: string) => {
  const calls = get.mock.calls.filter(([u]) => String(u).endsWith(suffix))
  return (calls[calls.length - 1]?.[1] as { params?: Record<string, unknown> } | undefined)?.params
}

beforeEach(() => { get.mockReset() })
afterEach(cleanup)

describe("stock helpers", () => {
  it("words and signs", () => {
    expect(movementText("COUNT_VARIANCE")).toBe("Count variance")
    expect(movementText("SOMETHING_NEW")).toBe("SOMETHING_NEW")
    expect(statusText("QUARANTINE")).toBe("Quarantine")
    expect(signedQty(5)).toMatch(/^\+5$/)
    expect(signedQty(-3)).toBe("−3")
    expect(signedQty(0)).toBe("0")
    expect(signedQty(null)).toBe("–")
  })
  it("ranges and paging", () => {
    expect(rangeText({ total: 0, page: 0, size: 50 })).toBe("0 results")
    expect(rangeText({ total: 120, page: 2, size: 50 })).toMatch(/^101.120 of 120$/)
    expect(hasNext({ total: 120, page: 1, size: 50 })).toBe(true)
    expect(hasNext({ total: 120, page: 2, size: 50 })).toBe(false)
  })
  it("sends only the filters that are set", () => {
    expect(listParams({ clientId: "", q: "  milk ", includeEmpty: false, warehouseId: undefined, page: 0, size: 50 })).toEqual({ q: "milk", page: 0, size: 50 })
    expect(listParams({ includeEmpty: true, q: "   " })).toEqual({ includeEmpty: true })
  })
})

describe("Stock tab", () => {
  it("lists stock with its status, lot and counts, and pages", async () => {
    serve([]); wrap()
    await screen.findByText(/MILK-1L · Milk 1L/)
    expect(screen.getAllByText("Quarantine").some(e => e.tagName === "SPAN")).toBe(true)
    expect(screen.getByText(/lot L7/)).toBeTruthy()
    expect(screen.getByText(/1.50 of 120/)).toBeTruthy()
    expect(screen.queryByLabelText("Warehouse")).toBeNull()
    expect(lastParams("/operations/inventory")?.warehouseId).toBeUndefined()
    fireEvent.click(screen.getByText("Next"))
    await waitFor(() => expect(lastParams("/operations/inventory")?.page).toBe(1))
  })

  it("searching and choosing a client go to the server and return to page one", async () => {
    serve([]); wrap()
    await screen.findByText(/MILK-1L/)
    fireEvent.click(screen.getByText("Next"))
    await waitFor(() => expect(lastParams("/operations/inventory")?.page).toBe(1))
    fireEvent.change(screen.getByLabelText("Search stock"), { target: { value: "milk" } })
    await waitFor(() => expect(lastParams("/operations/inventory")?.q).toBe("milk"))
    expect(lastParams("/operations/inventory")?.page).toBe(0)
    await screen.findByText("Acme", { selector: "option" })
    fireEvent.change(screen.getByLabelText("Client"), { target: { value: "c1" } })
    await waitFor(() => expect(lastParams("/operations/inventory")?.clientId).toBe("c1"))
  })

  it("with two warehouses the figures follow the chosen warehouse, for stock and for movements", async () => {
    serve(two); wrap()
    await screen.findByLabelText("Warehouse")
    await waitFor(() => expect(lastParams("/operations/inventory")?.warehouseId).toBe("w1"))
    fireEvent.change(screen.getByLabelText("Warehouse"), { target: { value: "w2" } })
    await waitFor(() => expect(lastParams("/operations/inventory")?.warehouseId).toBe("w2"))
    fireEvent.click(screen.getByRole("tab", { name: "Movements" }))
    await screen.findByText(/Pick · MILK-1L/)
    expect(lastParams("/operations/movements")?.warehouseId).toBe("w2")
  })

  it("shows movements with the signed quantity and the before and after", async () => {
    serve([]); wrap()
    fireEvent.click(screen.getByRole("tab", { name: "Movements" }))
    await screen.findByText(/Pick · MILK-1L/)
    expect(screen.getByText("−5")).toBeTruthy()
    expect(screen.getByText(/45 → 40/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Movement type"), { target: { value: "PICK" } })
    await waitFor(() => expect(lastParams("/operations/movements")?.type).toBe("PICK"))
    fireEvent.change(screen.getByLabelText("From date"), { target: { value: "2026-11-01" } })
    await waitFor(() => expect(lastParams("/operations/movements")?.from).toBe("2026-11-01"))
  })
})
