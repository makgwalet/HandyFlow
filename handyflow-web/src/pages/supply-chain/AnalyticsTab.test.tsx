import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { AnalyticsTab } from "./AnalyticsTab"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><AnalyticsTab /></QueryClientProvider>)
beforeEach(() => get.mockReset()); afterEach(cleanup)

describe("AnalyticsTab", () => {
  it("shows spend, and warns when much of it had no purchase order", async () => {
    get.mockImplementation(() => ok({ ordered: 500, invoiced: 400, paid: 300, invoicedWithoutPo: 300, withoutPoSharePct: 75, creditsReceived: 0, top5SupplierSharePct: 100,
      byMonth: [{ label: "2026-11", amount: 400, sharePct: 100 }], bySupplier: [{ supplierId: "s1", supplierName: "Alpha", invoiced: 400, sharePct: 100, invoices: 2 }], byBudgetScope: [{ label: "Workshop", amount: 400, sharePct: 100 }] }))
    wrap()
    expect(await screen.findByText("Alpha")).toBeTruthy()
    expect(screen.getByText(/75.0% of spend/)).toBeTruthy()
  })
  it("flags a large supplier with a low score", async () => {
    get.mockImplementation((u: string) => String(u).endsWith("/suppliers") ? ok([{ supplierId: "s1", supplierName: "Alpha", invoiced: 900, spendSharePct: 40, score: 45, rating: "POOR", onTimePct: 50, rejectionPct: 8, lowConfidence: false, flag: "HIGH_SPEND_LOW_SCORE" }])
      : ok({ ordered: 0, invoiced: 0, paid: 0, invoicedWithoutPo: 0, withoutPoSharePct: null, creditsReceived: 0, top5SupplierSharePct: null, byMonth: [], bySupplier: [], byBudgetScope: [] }))
    wrap()
    fireEvent.click(await screen.findByText("Supplier performance"))
    expect(await screen.findByText(/act first/)).toBeTruthy()
  })
  it("shows contract overpayment", async () => {
    get.mockImplementation((u: string) => String(u).endsWith("/savings") ? ok({ rfqsAwarded: 1, rfqsWithEstimate: 1, savedVsEstimate: 500, savedVsHighestBid: 900, paidAboveContract: 80, linesAboveContract: 1, creditsRecovered: 0,
      rfqs: [{ rfqId: "r1", rfqNumber: "RFQ-1", title: "Tyres", estimate: 8000, awarded: 7500, highestBid: 8400, savedVsEstimate: 500, savedVsHighestBid: 900 }],
      contractLeaks: [{ orderNumber: "PO-1", itemName: "Tyre", qty: 8, unitCost: 110, agreedPrice: 100, overpaid: 80 }] })
      : ok({ ordered: 0, invoiced: 0, paid: 0, invoicedWithoutPo: 0, withoutPoSharePct: null, creditsReceived: 0, top5SupplierSharePct: null, byMonth: [], bySupplier: [], byBudgetScope: [] }))
    wrap()
    fireEvent.click(await screen.findByText("Savings"))
    expect(await screen.findByText("PO-1")).toBeTruthy()
    expect(screen.getByText("RFQ-1 — Tyres")).toBeTruthy()
  })
  it("will not query with a backwards period", async () => {
    get.mockImplementation(() => ok({ ordered: 0, invoiced: 0, paid: 0, invoicedWithoutPo: 0, withoutPoSharePct: null, creditsReceived: 0, top5SupplierSharePct: null, byMonth: [], bySupplier: [], byBudgetScope: [] }))
    wrap(); await screen.findByText(/No spend in this period/)
    get.mockClear()
    fireEvent.change(screen.getByLabelText("To"), { target: { value: "2000-01-01" } })
    expect(await screen.findByText(/cannot end before it starts/)).toBeTruthy()
    expect(get).not.toHaveBeenCalled()
  })
})
