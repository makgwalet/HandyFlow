import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import SupplierInvoicesTab from "./SupplierInvoicesTab"

const get = vi.fn(); const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const inv = (o: any = {}) => ({
  id: "i1", supplierId: "s1", supplierName: "Acme Fuels", purchaseOrderId: "po1", poNumber: "PO-2026-00001", invoiceNumber: "INV-100", invoiceDate: "2026-11-10", dueDate: null,
  litres: 10000, pricePerLitre: 20, otherCharges: 0, vatAmount: 0, totalAmount: 200000, status: "CAPTURED", matchStatus: "MATCHED", decisionReason: null, notes: null,
  capturedAt: "2026-11-10T08:00:00Z", decidedAt: null, litresReceived: 10000, receiptValue: 200000, litresVariance: 0, goodsValue: 200000, valueVariance: 0, priceVsPo: 0, issues: [], receipts: [], ...o,
})
const cand = [{ receiptId: "r1", receivedAt: "2026-11-05T08:00:00Z", litres: 6000, pricePerLitre: 20, totalCost: 120000, deliveryNote: "DN-1", reversed: false },
              { receiptId: "r2", receivedAt: "2026-11-06T08:00:00Z", litres: 4000, pricePerLitre: 20, totalCost: 80000, deliveryNote: "DN-2", reversed: false }]
let list: any[]

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><SupplierInvoicesTab /></QueryClientProvider>)
}
const type = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } })

describe("SupplierInvoicesTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); perms = ["FUEL_MANAGE"]
    list = [inv(), inv({ id: "i2", invoiceNumber: "INV-200", matchStatus: "VARIANCE", issues: ["Invoiced 10,500 L but 10,000 L was received (+500 L)."] })]
    post.mockResolvedValue({ data: { data: {} } })
    get.mockImplementation(async (u: string) => {
      if (u.includes("/supplier-invoices/candidates")) return { data: { data: cand } }
      if (u.includes("/supplier-invoices")) return { data: { data: list } }
      if (u.includes("/fuel/suppliers")) return { data: { data: [{ id: "s1", name: "Acme Fuels" }] } }
      if (u.includes("/purchase-orders")) return { data: { data: [{ id: "po1", poNumber: "PO-2026-00001", supplierId: "s1", fuelType: "DIESEL", status: "ORDERED" }] } }
      return { data: { data: [] } }
    })
  })
  afterEach(() => cleanup())

  it("lists invoices with their match and status, and counts those waiting", async () => {
    mount()
    await waitFor(() => screen.getByText("INV-100"))
    expect(screen.getByText("Matches")).toBeTruthy()
    expect(screen.getByText("Does not match")).toBeTruthy()
    expect(screen.getByText("2 invoices awaiting approval.")).toBeTruthy()
  })

  it("shows what does not match when a row is opened", async () => {
    mount()
    await waitFor(() => screen.getByText("INV-200"))
    fireEvent.click(screen.getByText("INV-200"))
    await waitFor(() => screen.getByText(/10,500 L but 10,000 L was received/))
  })

  it("B08: an approved invoice not yet in Accounts Payable offers Send to Accounts Payable", async () => {
    list = [inv({ status: "APPROVED" })]
    mount()
    await waitFor(() => screen.getByText("Send to Accounts Payable"))
    fireEvent.click(screen.getByText("Send to Accounts Payable"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/supplier-invoices/i1/send-to-ap"))
  })

  it("B08: an invoice already in Accounts Payable says so and has no button", async () => {
    list = [inv({ status: "APPROVED", apBillId: "b1" })]
    mount()
    await waitFor(() => screen.getByText("In Accounts Payable"))
    expect(screen.queryByText("Send to Accounts Payable")).toBeNull()
  })

  it("B08: a refusal from the server (for example the module is off) is shown", async () => {
    list = [inv({ status: "APPROVED" })]
    post.mockRejectedValueOnce({ response: { data: { message: "Accounts Payable is not enabled for this tenant." } } })
    mount()
    await waitFor(() => screen.getByText("Send to Accounts Payable"))
    fireEvent.click(screen.getByText("Send to Accounts Payable"))
    await waitFor(() => screen.getByText("Accounts Payable is not enabled for this tenant."))
  })

  it("approves a matching invoice without a reason", async () => {
    list = [inv()]
    mount()
    await waitFor(() => screen.getByText("Approve"))
    fireEvent.click(screen.getByText("Approve"))
    await waitFor(() => screen.getByText("Approve invoice"))
    fireEvent.click(screen.getByText("Approve invoice"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/supplier-invoices/i1/approve", { reason: null }))
  })

  it("will not approve a mismatch without a reason, then sends it", async () => {
    list = [inv({ id: "i2", matchStatus: "VARIANCE", issues: ["Invoiced 10,500 L but 10,000 L was received (+500 L)."] })]
    mount()
    await waitFor(() => screen.getByText("Approve"))
    fireEvent.click(screen.getByText("Approve"))
    await waitFor(() => screen.getByText("This invoice does not match:"))
    fireEvent.click(screen.getByText("Approve invoice"))
    await waitFor(() => screen.getByText(/at least 5 characters/))
    expect(post).not.toHaveBeenCalled()
    type("Reason", "Supplier confirmed, credit note to follow")
    fireEvent.click(screen.getByText("Approve invoice"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/supplier-invoices/i2/approve", { reason: "Supplier confirmed, credit note to follow" }))
  })

  it("rejecting always needs a reason", async () => {
    list = [inv()]
    mount()
    await waitFor(() => screen.getByText("Reject"))
    fireEvent.click(screen.getByText("Reject"))
    await waitFor(() => screen.getByText("Reject invoice"))
    fireEvent.click(screen.getByText("Reject invoice"))
    await waitFor(() => screen.getByText(/at least 5 characters/))
    type("Reason", "Wrong supplier invoice")
    fireEvent.click(screen.getByText("Reject invoice"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/supplier-invoices/i1/reject", { reason: "Wrong supplier invoice" }))
  })

  it("captures: ticking receipts fills litres and price, and the posted body carries the receipts", async () => {
    list = []
    mount()
    await waitFor(() => screen.getByText("Capture invoice"))
    fireEvent.click(screen.getByText("Capture invoice"))
    await waitFor(() => screen.getByLabelText("Supplier"))
    await waitFor(() => screen.getByText("Acme Fuels"))
    type("Supplier", "s1")
    await waitFor(() => screen.getByLabelText("Receipt DN-1"))
    fireEvent.click(screen.getByLabelText("Receipt DN-1")); fireEvent.click(screen.getByLabelText("Receipt DN-2"))
    expect((screen.getByLabelText("Litres invoiced") as HTMLInputElement).value).toBe("10000")
    expect((screen.getByLabelText("Price per litre") as HTMLInputElement).value).toBe("20")
    type("Invoice number", "INV-300"); type("VAT", "30000")
    expect(screen.getByTestId("total").textContent).toMatch(/230/)
    type("Purchase order", "po1")
    fireEvent.click(screen.getAllByText("Capture invoice").pop()!)
    await waitFor(() => expect(post).toHaveBeenCalled())
    const body = post.mock.calls[0][1]
    expect(post.mock.calls[0][0]).toBe("/api/v1/fuel/supplier-invoices")
    expect(body).toMatchObject({ supplierId: "s1", purchaseOrderId: "po1", invoiceNumber: "INV-300", litres: 10000, pricePerLitre: 20, vatAmount: 30000, otherCharges: null })
    expect(body.receiptIds.sort()).toEqual(["r1", "r2"])
  })

  it("capture refuses an incomplete form and shows the server's message", async () => {
    list = []
    mount()
    await waitFor(() => screen.getByText("Capture invoice"))
    fireEvent.click(screen.getByText("Capture invoice"))
    await waitFor(() => screen.getByLabelText("Supplier"))
    fireEvent.click(screen.getAllByText("Capture invoice").pop()!)
    await waitFor(() => screen.getByText("Choose the supplier."))
    expect(post).not.toHaveBeenCalled()
    await waitFor(() => screen.getByText("Acme Fuels"))
    type("Supplier", "s1"); type("Invoice number", "INV-1"); type("Litres invoiced", "100"); type("Price per litre", "20")
    post.mockRejectedValueOnce({ response: { data: { message: "This supplier's invoice INV-1 has already been captured." } } })
    fireEvent.click(screen.getAllByText("Capture invoice").pop()!)
    await waitFor(() => screen.getByText(/already been captured/))
  })

  it("read-only users cannot capture, approve or reject", async () => {
    perms = ["FUEL_READ"]
    mount()
    await waitFor(() => screen.getByText("INV-100"))
    expect(screen.queryByText("Capture invoice")).toBeNull()
    expect(screen.queryByText("Approve")).toBeNull()
    expect(screen.queryByText("Reject")).toBeNull()
  })
})
