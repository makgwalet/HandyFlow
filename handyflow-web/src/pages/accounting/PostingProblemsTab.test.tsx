import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

// Finance patch F4: failed postings are shown, retried and dismissed with a reason.
let perms = new Set<string>()
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const get = vi.fn(), post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a) } }))
import PostingProblemsTab from "./PostingProblemsTab"

const problem = (o: any = {}) => ({ id: "f1", sourceKey: "invoicing:invoice-issued:1", module: "invoicing", summary: "Invoice issued: INV-0007",
  reason: "Journal does not balance", status: "OPEN", attempts: 2, retryable: true, firstFailedAt: "2026-10-13T08:00:00Z",
  lastFailedAt: "2026-10-14T08:00:00Z", resolvedAt: null, resolutionNote: null, journalEntryId: null, ...o })
const rows = [problem(), problem({ id: "f2", summary: "Expense claim approved: EXP-3", module: "expenses", retryable: false,
  reason: "The chart of accounts has no Staff Expense (5240) account" })]

const show = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><PostingProblemsTab /></QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["ACCOUNTING_READ", "ACCOUNTING_ADMIN"])
  get.mockReset(); post.mockReset()
  get.mockResolvedValue({ data: { data: rows } })
  post.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("PostingProblemsTab", () => {
  it("lists open problems with module, attempts and reason", async () => {
    show()
    expect(await screen.findByText("Invoice issued: INV-0007")).toBeTruthy()
    expect(screen.getByText(/Invoicing · 2 attempts/)).toBeTruthy()
    expect(screen.getByText("Journal does not balance")).toBeTruthy()
    expect(screen.getByText(/cannot be retried here/)).toBeTruthy()
    expect(get).toHaveBeenCalledWith("/api/v1/accounting/posting-failures?status=OPEN")
  })

  it("retries only what can be retried", async () => {
    show()
    await screen.findByText("Invoice issued: INV-0007")
    const retries = screen.getAllByRole("button", { name: /Retry/ })
    expect(retries).toHaveLength(1)
    fireEvent.click(retries[0])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/posting-failures/f1/retry"))
    expect(await screen.findByText(/Posted: Invoice issued: INV-0007/)).toBeTruthy()
  })

  it("shows the server's reason when a retry fails again", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "Still could not post: Journal does not balance" } } })
    show()
    await screen.findByText("Invoice issued: INV-0007")
    fireEvent.click(screen.getByRole("button", { name: /Retry/ }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/Still could not post/)
  })

  it("dismisses with a reason of at least 5 characters", async () => {
    show()
    await screen.findByText("Expense claim approved: EXP-3")
    fireEvent.click(screen.getAllByRole("button", { name: "Dismiss" })[1])
    const confirm = () => screen.getAllByRole("button", { name: "Dismiss" }).at(-1) as HTMLButtonElement
    expect(confirm().disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Why should this not be posted?"), { target: { value: "Posted by hand as JE-0042" } })
    fireEvent.click(confirm())
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/posting-failures/f2/dismiss", { note: "Posted by hand as JE-0042" }))
  })

  it("hides retry and dismiss without ACCOUNTING_ADMIN", async () => {
    perms = new Set(["ACCOUNTING_READ"])
    show()
    await screen.findByText("Invoice issued: INV-0007")
    expect(screen.queryByRole("button", { name: /Retry/ })).toBeNull()
    expect(screen.queryByRole("button", { name: "Dismiss" })).toBeNull()
  })
})
