import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import PurchaseOrdersTab from "./PurchaseOrdersTab"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const po = (o: any = {}) => ({ id: "p1", poNumber: "PO-2026-00001", supplierId: "s1", supplierName: "Sasol", fuelType: "DIESEL", litresOrdered: 10000, litresReceived: 0, litresOutstanding: 10000, pricePerLitre: 20, orderValue: 200000, status: "DRAFT", expectedDate: null, overdue: false, cancelReason: null, notes: null, ...o })
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><PurchaseOrdersTab /></QueryClientProvider>)
}

describe("PurchaseOrdersTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset(); perms = ["FUEL_MANAGE"]
    post.mockResolvedValue({ data: { data: {} } })
    get.mockImplementation(async (u: string) => {
      if (u.endsWith("/purchase-orders")) return { data: { data: [po(), po({ id: "p2", poNumber: "PO-2026-00002", status: "PART_RECEIVED", litresReceived: 4000, litresOutstanding: 6000, overdue: true, expectedDate: "2026-11-01" })] } }
      if (u.includes("/match")) return { data: { data: { order: po({ id: "p2", status: "PART_RECEIVED", litresReceived: 4000 }), receivedValue: 82000, valueVariance: -118000, litresVariance: -6000, receipts: [
        { receiptId: "r1", receivedAt: "2026-11-05T08:00:00Z", litres: 4000, pricePerLitre: 20.5, priceVariance: 0.5, totalCost: 82000, deliveryNote: "DN1", invoiceRef: null, reversed: false, priceDiffers: true }] } } }
      if (u.endsWith("/suppliers")) return { data: { data: [{ id: "s1", name: "Sasol" }] } }
      return { data: { data: [] } }
    })
  })
  afterEach(cleanup)

  it("shows orders, status and overdue", async () => {
    mount()
    expect(await screen.findByText("PO-2026-00001")).toBeTruthy()
    expect(screen.getByText("Part received")).toBeTruthy()
    expect(screen.getByText("overdue")).toBeTruthy()
  })

  it("only a draft can be placed or edited", async () => {
    mount()
    await screen.findByText("PO-2026-00001")
    expect(screen.getAllByText("Place order")).toHaveLength(1)
    expect(screen.getAllByText("Edit")).toHaveLength(1)
  })

  it("places a draft", async () => {
    mount()
    await screen.findByText("PO-2026-00001")
    fireEvent.click(screen.getByText("Place order"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toMatch(/p1\/place/)
  })

  it("the match view shows the short delivery and the price difference", async () => {
    mount()
    fireEvent.click(await screen.findByText("PO-2026-00002"))
    expect(await screen.findByText(/6.000 L short/)).toBeTruthy()
    expect(screen.getByText("+0.5000 per L")).toBeTruthy()
  })

  it("cancelling needs a reason", async () => {
    mount()
    await screen.findByText("PO-2026-00001")
    fireEvent.click(screen.getAllByText("Cancel")[0])
    fireEvent.click(screen.getByText("Cancel order"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/reason/)
    expect(post).not.toHaveBeenCalled()
  })

  it("will not save an order without a supplier", async () => {
    mount()
    await screen.findByText("PO-2026-00001")
    fireEvent.click(screen.getByText("New purchase order"))
    fireEvent.click(screen.getByText("Save"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/supplier/)
  })
})
