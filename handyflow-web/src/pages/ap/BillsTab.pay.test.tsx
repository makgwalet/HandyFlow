import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

// Finance patch F10: a bill can be paid in parts; the dialog says what is left and refuses more than that.
let perms = new Set<string>()
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const get = vi.fn(), post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), put: vi.fn() } }))
import { BillsTab } from "./BillsTab"

const bill = (o: any) => ({ id: "b1", supplierId: null, supplierName: "Acme", billNumber: "INV-1", billDate: "2026-09-01", dueDate: "2026-10-01",
  category: "OTHER", description: "Stock", amount: 1000, vatAmount: 150, totalAmount: 1150, currency: "ZAR", status: "APPROVED",
  overdue: false, daysUntilDue: 5, hasAttachment: false, hasPop: false, paymentRef: null, batchId: null, notes: null, paidAt: null,
  createdAt: "2026-09-01T00:00:00Z", firstApprovedBy: null, firstApprovedAt: null, rejectionReason: null,
  paidAmount: 0, creditedAmount: 0, balance: 1150, ...o })
let bills: any[] = []
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><BillsTab onRefreshSummary={() => {}} /></QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["AP_READ", "AP_MANAGE", "AP_PAY"])
  get.mockReset(); post.mockReset()
  bills = [bill({})]
  get.mockImplementation((url: string) => Promise.resolve({ data: { data:
    url.includes("bank-accounts") ? { content: [{ id: "bank1", accountName: "Main" }] }
    : url.includes("/payments") ? [] : { content: bills } } }))
  post.mockResolvedValue({ data: { data: bill({}) } })
})
afterEach(cleanup)

async function openPay(label = "Pay bill") {
  show()
  fireEvent.click(await screen.findByText(/#INV-1/))
  fireEvent.click(await screen.findByRole("button", { name: new RegExp(label) }))
  return await screen.findByLabelText("Amount to pay")
}

describe("BillsTab payment", () => {
  it("pays the whole balance when no amount is typed", async () => {
    await openPay()
    await waitFor(() => expect((screen.getByRole("button", { name: /Pay in full/ }) as HTMLButtonElement).disabled).toBe(false))
    fireEvent.click(screen.getByRole("button", { name: /Pay in full/ }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/ap/bills/b1/pay", { paymentRef: null, bankAccountId: "bank1", amount: null }))
  })

  it("sends the amount for a part payment", async () => {
    const input = await openPay()
    fireEvent.change(input, { target: { value: "400" } })
    await waitFor(() => expect((screen.getByRole("button", { name: /Pay this amount/ }) as HTMLButtonElement).disabled).toBe(false))
    fireEvent.click(screen.getByRole("button", { name: /Pay this amount/ }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/ap/bills/b1/pay", { paymentRef: null, bankAccountId: "bank1", amount: 400 }))
  })

  it("refuses more than the bill still owes", async () => {
    bills = [bill({ status: "PARTIALLY_PAID", paidAmount: 400, balance: 750 })]
    const input = await openPay("Pay balance")
    fireEvent.change(input, { target: { value: "750.01" } })
    expect((await screen.findByRole("alert")).textContent).toMatch(/more than the/)
    expect((screen.getByRole("button", { name: /Pay this amount/ }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("does not offer cancelling a bill that has been part paid", async () => {
    bills = [bill({ status: "PARTIALLY_PAID", paidAmount: 400, balance: 750 })]
    show()
    fireEvent.click(await screen.findByText(/#INV-1/))
    await screen.findByText("Still owed")
    expect(screen.queryByRole("button", { name: /cancel bill/i })).toBeNull()
  })
})
