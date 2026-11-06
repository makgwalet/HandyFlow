import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseApprovalsTab from "./WhseApprovalsTab"
import WhseBillingInvoicesTab from "./WhseBillingInvoicesTab"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.includes(p) }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

const adjustment = { id: "a1", inventoryId: "i1", delta: -8, reason: "damaged in rack", requestedByName: "Thabo M", requestedAt: "2026-11-06T08:00:00Z",
  clientName: "Acme", sku: "SKU-1", locationCode: "A-01", qtyOnHand: 40 }

function route(threshold: number | null, pending: unknown[] = [adjustment]) {
  get.mockImplementation(async (url: string) => {
    if (url.includes("adjustment-approval-threshold")) return { data: { thresholdQty: threshold } }
    if (url.includes("adjustment-requests/pending")) return { data: pending }
    return { data: { content: [] } }
  })
}

beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); perms = []; post.mockResolvedValue({ data: null }); put.mockResolvedValue({ data: null }) })
afterEach(cleanup)

describe("adjustment approvals", () => {
  it("lists the waiting adjustment with item, place, amount and who asked", async () => {
    route(10)
    wrap(<WhseApprovalsTab />)
    await waitFor(() => expect(screen.getByText(/SKU-1 @ A-01/)).toBeTruthy())
    expect(screen.getByText(/damaged in rack/)).toBeTruthy()
    expect(screen.getByText(/Asked by Thabo M/)).toBeTruthy()
    expect(screen.getByText(/more than 10 units/)).toBeTruthy()
  })

  it("only someone holding the approve permission sees Approve and Reject", async () => {
    route(10)
    wrap(<WhseApprovalsTab />)
    await waitFor(() => screen.getByText(/SKU-1 @ A-01/))
    expect(screen.queryByText("Approve")).toBeNull()
    cleanup()
    perms = ["WAREHOUSING_ADJUST_APPROVE"]
    wrap(<WhseApprovalsTab />)
    await waitFor(() => expect(screen.getByText("Approve")).toBeTruthy())
    expect(screen.getByText("Reject")).toBeTruthy()
  })

  it("approving posts to the approve endpoint", async () => {
    route(10)
    perms = ["WAREHOUSING_ADJUST_APPROVE"]
    wrap(<WhseApprovalsTab />)
    fireEvent.click(await screen.findByText("Approve"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/adjustment-requests/a1/approve", {}))
  })

  it("rejecting needs a reason of at least 5 characters", async () => {
    route(10)
    perms = ["WAREHOUSING_ADJUST_APPROVE"]
    wrap(<WhseApprovalsTab />)
    fireEvent.click(await screen.findByText("Reject"))
    const confirm = screen.getByText("Confirm rejection") as HTMLButtonElement
    expect(confirm.disabled).toBe(true)
    fireEvent.change(screen.getByPlaceholderText(/Reason for rejecting/), { target: { value: "not counted" } })
    expect(confirm.disabled).toBe(false)
    fireEvent.click(confirm)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/adjustment-requests/a1/reject", { note: "not counted" }))
  })

  it("says the rule is off when no threshold is set", async () => {
    route(null, [])
    wrap(<WhseApprovalsTab />)
    await waitFor(() => expect(screen.getByText(/Off: every manual stock adjustment applies at once/)).toBeTruthy())
    expect(await screen.findByText("Nothing is waiting.")).toBeTruthy()
  })

  it("a warehouse administrator can change the threshold only with a reason", async () => {
    route(null, [])
    perms = ["WAREHOUSING_ADMIN"]
    wrap(<WhseApprovalsTab />)
    const save = await screen.findByText("Save threshold")
    fireEvent.change(screen.getByLabelText(/Units/), { target: { value: "25" } })
    expect((save as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByPlaceholderText(/At least 5 characters/), { target: { value: "owner policy" } })
    expect((save as HTMLButtonElement).disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/warehousing/adjustment-approval-threshold", { thresholdQty: 25, reason: "owner policy" }))
  })

  it("others are told only an administrator can change it", async () => {
    route(10, [])
    wrap(<WhseApprovalsTab />)
    await waitFor(() => expect(screen.getByText(/Only a warehouse administrator can change this/)).toBeTruthy())
    expect(screen.queryByText("Save threshold")).toBeNull()
  })
})

describe("invoice approval", () => {
  const invoice = (status: string) => ({ id: "inv1", clientId: "c1", invoiceNumber: "WHI00001", periodStart: "2026-10-01", periodEnd: "2026-10-31",
    invoiceDate: "2026-11-01", dueDate: "2026-12-01", storageFee: 300, handlingFee: 100, vatAmount: 0, subtotal: 400, total: 400,
    amountPaid: 0, balance: 400, status, sentAt: null, paidAt: null })
  const withInvoice = (status: string) => get.mockResolvedValue({ data: { content: [invoice(status)] } })

  it("a prepared invoice is labelled as awaiting approval and cannot take a payment", async () => {
    withInvoice("DRAFT")
    perms = ["WAREHOUSING_BILLING_APPROVE"]
    wrap(<WhseBillingInvoicesTab clientId="c1" />)
    await waitFor(() => expect(screen.getByText("AWAITING APPROVAL")).toBeTruthy())
    expect(screen.queryByTitle("Record payment")).toBeNull()
    expect(screen.getByText("Approve")).toBeTruthy()
  })

  it("without the billing-approve permission there are no Approve or Reject buttons", async () => {
    withInvoice("DRAFT")
    wrap(<WhseBillingInvoicesTab clientId="c1" />)
    await waitFor(() => screen.getByText("AWAITING APPROVAL"))
    expect(screen.queryByText("Approve")).toBeNull()
  })

  it("approving posts to the approve endpoint", async () => {
    withInvoice("DRAFT")
    perms = ["WAREHOUSING_BILLING_APPROVE"]
    wrap(<WhseBillingInvoicesTab clientId="c1" />)
    fireEvent.click(await screen.findByText("Approve"))
    fireEvent.click(await screen.findByText("Approve and issue"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/warehousing/billing-invoices/inv1/approve", { note: null }))
  })

  it("an issued invoice shows no approval buttons but still takes a payment", async () => {
    withInvoice("SENT")
    perms = ["WAREHOUSING_BILLING_APPROVE"]
    wrap(<WhseBillingInvoicesTab clientId="c1" />)
    await waitFor(() => screen.getByText("SENT"))
    expect(screen.queryByText("Approve")).toBeNull()
    expect(screen.getByTitle("Record payment")).toBeTruthy()
  })

  it("a rejected invoice is shown as rejected", async () => {
    withInvoice("REJECTED")
    wrap(<WhseBillingInvoicesTab clientId="c1" />)
    await waitFor(() => expect(screen.getByText("REJECTED")).toBeTruthy())
    expect(screen.queryByTitle("Record payment")).toBeNull()
  })
})
