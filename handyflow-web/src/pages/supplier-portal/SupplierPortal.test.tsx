import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { SupplierPortalHomePage, SupplierPortalRfqPage } from "./SupplierPortalPages"

const get = vi.fn(), post = vi.fn()
vi.mock("../../api/supplierPortal.client", () => ({ supplierPortalApiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))

const summary = { rfqId: "r1", buyerName: "Buyer Co", supplierName: "Acme", rfqNumber: "RFQ-1", title: "Bolts", status: "OPEN", closesAt: null, openForQuotes: true, currency: "ZAR", requiredByDate: null, invitationStatus: "INVITED", quotationStatus: null, myLandedCost: null }
const detail = { summary, description: "M8 bolts", lines: [{ id: "l1", lineNumber: 1, itemName: "M8 bolt", description: null, quantity: 100 }], myQuotation: null, declineReason: null }

function wrap(ui: React.ReactElement, path = "/") {
  return render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter initialEntries={[path]}><Routes><Route path="/supplier-portal" element={ui} /><Route path="/supplier-portal/rfqs/:rfqId" element={ui} /></Routes></MemoryRouter></QueryClientProvider>)
}

beforeEach(() => { get.mockReset(); post.mockReset() })
afterEach(cleanup)

describe("supplier portal", () => {
  it("lists the invited RFQs with what the supplier still has to do", async () => {
    get.mockResolvedValue({ data: [summary, { ...summary, rfqId: "r2", rfqNumber: "RFQ-2", openForQuotes: false, status: "CLOSED" }] })
    wrap(<SupplierPortalHomePage />, "/supplier-portal")
    expect(await screen.findByText(/RFQ-1 · Bolts/)).toBeTruthy()
    expect(screen.getByText("Waiting for your quotation")).toBeTruthy()
    expect(screen.getByText("Closed")).toBeTruthy()
  })

  it("refuses an unpriced quotation without calling the server", async () => {
    get.mockResolvedValue({ data: detail })
    wrap(<SupplierPortalRfqPage />, "/supplier-portal/rfqs/r1")
    fireEvent.click(await screen.findByText("Submit quotation"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/lead time/i)
    expect(post).not.toHaveBeenCalled()
  })

  it("submits a priced quotation without naming a supplier", async () => {
    get.mockResolvedValue({ data: detail }); post.mockResolvedValue({ data: {} })
    wrap(<SupplierPortalRfqPage />, "/supplier-portal/rfqs/r1")
    fireEvent.change(await screen.findByLabelText("Lead time (days) *"), { target: { value: "14" } })
    fireEvent.change(screen.getByLabelText("Unit price M8 bolt"), { target: { value: "2.5" } })
    fireEvent.click(screen.getByText("Submit quotation"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, body] = post.mock.calls[0]
    expect(url).toBe("/api/v1/supplier/portal/rfqs/r1/quotation")
    expect((body as any).supplierId).toBeUndefined()
    expect((body as any).lines[0]).toMatchObject({ rfqLineId: "l1", unitPrice: 2.5, quantity: 100 })
    expect((body as any).leadTimeDays).toBe(14)
  })

  it("is read-only once the RFQ is closed", async () => {
    get.mockResolvedValue({ data: { ...detail, summary: { ...summary, openForQuotes: false, status: "CLOSED" } } })
    wrap(<SupplierPortalRfqPage />, "/supplier-portal/rfqs/r1")
    await screen.findByText(/No longer open/)
    expect(screen.queryByText("Submit quotation")).toBeNull()
    expect((screen.getByLabelText("Unit price M8 bolt") as HTMLInputElement).disabled).toBe(true)
  })
})
