import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EventPaymentsTab from "./EventPaymentsTab"

const get = vi.fn(); const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const dlg = () => within(screen.getByRole("dialog"))
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><EventPaymentsTab eventId="e1" eventTitle="Summit" onChangeEvent={() => {}} /></QueryClientProvider>)
const pay = (o: object = {}) => ({ id: "p1", guestId: "g1", ticketNumber: "T-1", amount: 500, receivedAmount: null, feeAmount: null, refundedAmount: 0, refundableAmount: 0, currency: "ZAR", provider: "MANUAL",
  providerRef: null, status: "PENDING", expiresAt: null, paidAt: null, failureReason: null, reviewReason: null, createdAt: "2026-11-11T08:00:00Z", ...o })
let payments: object[] = []; let refunds: object[] = []; let eventStatus = "PUBLISHED"
beforeEach(() => {
  get.mockReset(); post.mockReset(); perms = []; payments = [pay()]; refunds = []; eventStatus = "PUBLISHED"; post.mockResolvedValue({ data: { data: {} } })
  get.mockImplementation(async (url: string) => url.endsWith("/payments") ? { data: { data: { content: payments } } } : url.endsWith("/refunds") ? { data: { data: refunds } } : { data: { data: { status: eventStatus } } })
})
afterEach(cleanup)

describe("event payments", () => {
  it("a reader sees the payment but no actions", async () => {
    perms = ["EVENTS_PAYMENTS_READ"]; wrap()
    await waitFor(() => screen.getByText(/T-1/)); expect(screen.getAllByText("Waiting for payment").length).toBeGreaterThan(1)
    for (const t of ["Confirm payment", "Cancel checkout", "Refund"]) expect(screen.queryByText(t)).toBeNull()
  })
  it("budget-read is enough to see payments, as on the server", async () => { perms = ["EVENTS_BUDGET_READ"]; wrap(); await waitFor(() => screen.getByText(/T-1/)) })
  it("without read access nothing is fetched", () => { wrap(); expect(screen.getByText(/do not have access/)).toBeTruthy(); expect(get).not.toHaveBeenCalled() })

  it("confirming the right amount posts it with the reference", async () => {
    perms = ["EVENTS_PAYMENTS_READ", "EVENTS_PAYMENTS_MANAGE"]; wrap()
    await waitFor(() => screen.getByText("Confirm payment")); fireEvent.click(screen.getByText("Confirm payment"))
    expect((dlg().getByLabelText(/Amount received/) as HTMLInputElement).value).toBe("500")
    expect(screen.queryByRole("note")).toBeNull()
    fireEvent.change(dlg().getByLabelText(/Bank or receipt/), { target: { value: "EFT-77" } })
    fireEvent.click(dlg().getByRole("button", { name: "Confirm payment" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/payments/p1/confirm", { amountReceived: 500, reference: "EFT-77", fee: null }))
  })
  it("a different amount shows the warning that no ticket will be issued", async () => {
    perms = ["EVENTS_MANAGE"]; wrap()
    await waitFor(() => screen.getByText("Confirm payment")); fireEvent.click(screen.getByText("Confirm payment"))
    fireEvent.change(dlg().getByLabelText(/Amount received/), { target: { value: "450" } })
    expect(screen.getByRole("note").textContent).toMatch(/ticket will not be issued/)
  })

  it("a refund cannot exceed what is left, then posts amount, reason and the cancel-ticket choice", async () => {
    perms = ["EVENTS_REFUND", "EVENTS_PAYMENTS_READ"]; payments = [pay({ status: "PAID", receivedAmount: 500, refundableAmount: 500 })]; wrap()
    await waitFor(() => screen.getByText("Refund")); fireEvent.click(screen.getByText("Refund"))
    const go = dlg().getByRole("button", { name: "Raise refund" }) as HTMLButtonElement
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "cannot attend" } })
    fireEvent.change(screen.getByLabelText(/^Amount \(R\)/), { target: { value: "600" } })
    expect(go.disabled).toBe(true); expect(screen.getByText(/At most/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText(/^Amount \(R\)/), { target: { value: "400" } })
    fireEvent.click(screen.getByLabelText(/cancel the ticket/))
    expect(go.disabled).toBe(false); fireEvent.click(go)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/payments/p1/refunds", { amount: 400, reason: "cannot attend", cancelGuest: true }))
  })
  it("a refund already awaiting payout hides the Refund button and can be marked paid out", async () => {
    perms = ["EVENTS_REFUND", "EVENTS_PAYMENTS_READ"]; payments = [pay({ status: "PAID", receivedAmount: 500, refundableAmount: 500 })]
    refunds = [{ id: "r1", paymentId: "p1", amount: 500, reason: "cancelled", status: "REQUESTED", providerRef: null, requestedByName: "T", requestedAt: "2026-11-11T09:00:00Z", completedAt: null, failureReason: null }]
    wrap(); await waitFor(() => screen.getByText("Mark paid out"))
    expect(screen.queryByRole("button", { name: "Refund" })).toBeNull()
    fireEvent.click(screen.getByText("Mark paid out")); fireEvent.change(screen.getByLabelText(/payout reference/), { target: { value: "BANK-9" } })
    fireEvent.click(dlg().getByRole("button", { name: "Mark paid out" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/refunds/r1/complete", { reference: "BANK-9" }))
  })
  it("refund-everyone appears only on a cancelled event and reports the outcome", async () => {
    perms = ["EVENTS_REFUND", "EVENTS_PAYMENTS_READ"]; payments = [pay({ status: "PAID", receivedAmount: 500, refundableAmount: 500 })]; eventStatus = "CANCELLED"
    post.mockResolvedValue({ data: { data: { requested: 3, failed: 1, moreRemaining: false, refunds: [], failures: ["x"] } } })
    wrap(); await waitFor(() => screen.getByText(/Refund everyone/)); fireEvent.click(screen.getByText(/Refund everyone/))
    await waitFor(() => expect(screen.getByRole("status").textContent).toMatch(/3 refunds raised, 1 could not be/))
  })
  it("shows the server's message when a confirmation is refused", async () => {
    perms = ["EVENTS_MANAGE"]; post.mockRejectedValue({ response: { data: { message: "Payment is no longer pending" } } }); wrap()
    await waitFor(() => screen.getByText("Confirm payment")); fireEvent.click(screen.getByText("Confirm payment"))
    fireEvent.click(dlg().getByRole("button", { name: "Confirm payment" }))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toBe("Payment is no longer pending"))
  })
})
