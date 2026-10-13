import { describe, it, expect, vi, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import UnpostedPaymentsBanner from "./UnpostedPaymentsBanner"

const get = vi.fn(); const post = vi.fn(); let allowed = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => allowed }))
afterEach(() => { cleanup(); get.mockReset(); post.mockReset(); allowed = true })

const pay = { id: "p1", claimId: "c", patientName: "Thandi", type: "SCHEME_PAYMENT_RECEIVED", amount: 500, occurredOn: "2026-10-13", status: "PENDING", needsBank: true }
const wire = (items: any[]) => get.mockImplementation((u: string) =>
  Promise.resolve({ data: { data: u.endsWith("/banks") ? [{ id: "b1", label: "FNB Cheque" }] : items } }))
const ui = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><UnpostedPaymentsBanner /></QueryClientProvider>)

describe("UnpostedPaymentsBanner", () => {
  it("shows nothing when nothing waits", async () => {
    wire([]); const { container } = ui()
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })
  it("will not post a scheme payment without a bank account", async () => {
    wire([pay]); ui()
    fireEvent.click(await screen.findByText("Post to accounts"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/bank account/)
    expect(post).not.toHaveBeenCalled()
  })
  it("posts into the chosen bank account", async () => {
    wire([pay]); post.mockResolvedValue({ data: { data: { posted: 1, failed: [] } } }); ui()
    fireEvent.change(await screen.findByLabelText("Paid into"), { target: { value: "b1" } })
    await screen.findByText("FNB Cheque")
    fireEvent.change(screen.getByLabelText("Paid into"), { target: { value: "b1" } })
    fireEvent.click(screen.getByText("Post to accounts"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/clinic/billing/money-postings/post", { bankAccountId: "b1", postingIds: ["p1"] }))
  })
  it("tells people without the right to post that someone else must", async () => {
    allowed = false; wire([pay]); ui()
    expect(await screen.findByText(/needs to post these/)).toBeTruthy()
    expect(screen.queryByText("Post to accounts")).toBeNull()
  })
})
