import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import RefundApprovals, { requestedByText } from "./RefundApprovals"

const get = vi.fn(), post = vi.fn()
let allowed = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => allowed }))

const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><RefundApprovals /></QueryClientProvider>)
const pending = { id: "f1", refundNumber: "RF000001", receiptId: "r1", receiptNumber: "RC000001", amount: 300, reason: "Customer cancelled the order",
  requestedBy: "u1", requestedByName: "Thandi Mokoena", createdAt: "2026-11-05T10:00:00" }

beforeEach(() => { allowed = true; get.mockReset(); post.mockReset(); get.mockResolvedValue({ data: { data: [pending] } }) })
afterEach(cleanup)

describe("refunds waiting for approval", () => {
  it("names who asked, and falls back when the name is unknown", () => {
    expect(requestedByText({ requestedByName: " Thandi Mokoena " })).toBe("Thandi Mokoena")
    expect(requestedByText({ requestedByName: "" })).toBe("A colleague")
    expect(requestedByText({ requestedByName: null })).toBe("A colleague")
  })

  it("shows nothing to someone without the refund permission, and does not ask the server", () => {
    allowed = false
    const { container } = wrap()
    expect(container.textContent).toBe("")
    expect(get).not.toHaveBeenCalled()
  })

  it("shows nothing when no refund is waiting", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    const { container } = wrap()
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })

  it("lists the refund with who asked and why, and approves it", async () => {
    post.mockResolvedValue({ data: { data: {} } })
    wrap()
    expect(await screen.findByText(/Thandi Mokoena asked on/)).toBeTruthy()
    expect(screen.getByText(/Customer cancelled the order/)).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Approve refund RF000001" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/invoicing/refunds/f1/approve", {}))
    expect(await screen.findByText(/Refund RF000001 approved and paid/)).toBeTruthy()
  })

  it("shows the server's reason when the same person tries to approve", async () => {
    post.mockRejectedValue({ response: { data: { message: "A refund has to be approved by someone other than the person who asked for it" } } })
    wrap()
    fireEvent.click(await screen.findByRole("button", { name: "Approve refund RF000001" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/someone other than the person who asked/)
  })

  it("will not decline without a reason, then sends it", async () => {
    post.mockResolvedValue({ data: { data: {} } })
    wrap()
    fireEvent.click(await screen.findByRole("button", { name: "Decline refund RF000001" }))
    const d = screen.getByRole("dialog")
    const go = within(d).getByRole("button", { name: "Decline refund" }) as HTMLButtonElement
    expect(go.disabled).toBe(true)
    fireEvent.change(within(d).getByLabelText("Reason"), { target: { value: "Wrong bank account" } })
    expect(go.disabled).toBe(false)
    fireEvent.click(go)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/invoicing/refunds/f1/reject", { reason: "Wrong bank account" }))
  })
})
