import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseStockTab from "./WhseStockTab"
import { alertKindText, hasNext, levelsText, listParams, movementText, rangeText, recommendationText, signedQty, statusText, stockBreakdown, thresholdProblem } from "./stockView"

const get = vi.fn(), post = vi.fn(), put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (u: string, cfg?: unknown) => get(u, cfg), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => p === "WAREHOUSING_MANAGE" }))
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><WhseStockTab /></QueryClientProvider>)

const two = [{ id: "w1", code: "CPT", name: "Cape Town", active: true, isDefault: true }, { id: "w2", code: "JHB", name: "Johannesburg", active: true, isDefault: false }]
const stockRow = { inventoryId: "i1", clientName: "Acme", sku: "MILK-1L", description: "Milk 1L", uom: "EA", locationCode: "A-01", zone: "Cold", status: "QUARANTINE", qtyOnHand: 40, qtyAllocated: 10, available: 0, lotNumber: "L7", expiryDate: "2026-12-01" }
const moveRow = { id: "m1", at: "2026-11-10T08:00:00Z", clientName: "Acme", sku: "MILK-1L", locationCode: "A-01", movementType: "PICK", status: "AVAILABLE", qtyChange: -5, qtyBefore: 45, qtyAfter: 40, referenceNumber: "ORD-9", notes: null }
const alertRow = { id: "a1", warehouseId: "w1", warehouseCode: "JHB", clientName: "Acme", itemId: "it1", sku: "FILTER", description: "Diesel Filter", kind: "BELOW_MIN", status: "OPEN", physical: 115, unavailable: 15, reserved: 80, available: 20, incoming: 20, minQty: 100, minSource: "WAREHOUSE", maxQty: 500, maxSource: "ITEM", recommendedOrder: 460, acknowledgedAt: null, resolution: null, resolutionNote: null }
const serve = (warehouses: unknown[]) => get.mockImplementation(async (url: string) => {
  if (url.endsWith("/stock-alerts")) return { data: [alertRow] }
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

beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); post.mockResolvedValue({ data: null }); put.mockResolvedValue({ data: null }) })
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

describe("alert helpers", () => {
  it("keeps the kinds of stock apart", () => {
    expect(stockBreakdown(alertRow)).toBe("20 available · 115 physical · 80 reserved · 15 quarantined, damaged or blocked · 20 incoming")
    expect(stockBreakdown({ physical: null, available: 5, reserved: 0, unavailable: 0, incoming: null })).toBe("5 available")
  })
  it("says where each level comes from", () => {
    expect(levelsText(alertRow)).toBe("min 100 · max 500 (item default)")
    expect(levelsText({ minQty: null, minSource: null, maxQty: 200, maxSource: "WAREHOUSE" })).toBe("max 200")
  })
  it("recommends an order, or nothing when stock is on its way, or a look for overstock", () => {
    expect(recommendationText(alertRow)).toBe("Reorder 460.")
    expect(recommendationText({ kind: "BELOW_MIN", recommendedOrder: 0, incoming: 500 })).toBe("Incoming stock already covers this.")
    expect(recommendationText({ kind: "ABOVE_MAX", recommendedOrder: null, incoming: 0 })).toMatch(/excess/)
    expect(recommendationText({ kind: "BELOW_MIN", recommendedOrder: null, incoming: 0 })).toBeNull()
    expect(alertKindText("OUT_OF_STOCK")).toBe("Out of stock")
  })
  it("checks levels the way the server does", () => {
    expect(thresholdProblem("", "")).toMatch(/Enter a minimum/)
    expect(thresholdProblem("30", "150")).toBeNull()
    expect(thresholdProblem("", "150")).toBeNull()
    expect(thresholdProblem("150", "150")).toMatch(/more than the minimum/)
    expect(thresholdProblem("-1", "")).toMatch(/negative/)
    expect(thresholdProblem("", "0")).toMatch(/above zero/)
    expect(thresholdProblem("abc", "")).toMatch(/numbers/)
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

  it("shows alerts, lets a manager acknowledge one, and resolves with a note", async () => {
    serve([]); wrap()
    fireEvent.click(screen.getByRole("tab", { name: "Alerts" }))
    await screen.findByText(/FILTER · Diesel Filter/)
    expect(screen.getAllByText("Below minimum").some(e => e.tagName === "SPAN")).toBe(true)
    expect(screen.getByText(/20 available · 115 physical/)).toBeTruthy()
    expect(screen.getByText("Reorder 460.")).toBeTruthy()
    fireEvent.click(screen.getByText("Acknowledge"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/stock-alerts/a1/acknowledge"))
    fireEvent.click(screen.getByText("Resolve"))
    const note = await screen.findByLabelText("Reason *")
    fireEvent.change(note, { target: { value: "stock on its way" } })
    fireEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Resolve" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/stock-alerts/a1/resolve", { note: "stock on its way" }))
  })

  it("sets a warehouse's own levels, leaving a blank level to the item's default", async () => {
    serve([]); wrap()
    fireEvent.click(screen.getByRole("tab", { name: "Alerts" }))
    await screen.findByText(/FILTER · Diesel Filter/)
    fireEvent.click(screen.getByText("Set levels"))
    fireEvent.change(await screen.findByLabelText("Minimum"), { target: { value: "30" } })
    expect(screen.getByLabelText("Maximum")).toHaveProperty("value", "")
    fireEvent.click(screen.getByText("Save levels"))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/warehousing/stock-alerts/thresholds", { warehouseId: "w1", itemId: "it1", minQty: 30, maxQty: null }))
  })

  it("the alert list follows the chosen warehouse and can show resolved alerts", async () => {
    serve(two); wrap()
    await screen.findByLabelText("Warehouse")
    fireEvent.click(screen.getByRole("tab", { name: "Alerts" }))
    await waitFor(() => expect(lastParams("/stock-alerts")?.warehouseId).toBe("w1"))
    fireEvent.click(screen.getByLabelText("Show resolved"))
    await waitFor(() => expect(lastParams("/stock-alerts")?.status).toBe("RESOLVED"))
  })
})
