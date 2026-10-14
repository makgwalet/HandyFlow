import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import RecurringJournalsTab from "./RecurringJournalsTab"

const perms = vi.hoisted(() => ({ held: new Set<string>() }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.held.has(p) }))
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))

const schedule = (over: any = {}) => ({ id: "s1", name: "Office rent", kind: "RECURRING", frequency: "MONTHLY", description: "Rent",
  status: "ACTIVE", nextRunDate: "2026-11-01", runsDone: 0, runsTotal: null, runs: [], ...over })

function mount(list: any[]) {
  api.get.mockImplementation(async (url: string) => url.endsWith("/accounts")
    ? { data: { data: [{ id: "a", code: "5000", name: "Rent" }, { id: "b", code: "1000", name: "Bank" }] } }
    : { data: { data: list } })
  api.post.mockResolvedValue({ data: { data: { made: 1, problems: [] } } })
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><RecurringJournalsTab /></QueryClientProvider>)
}

describe("RecurringJournalsTab", () => {
  beforeEach(() => { vi.clearAllMocks(); perms.held = new Set() })
  afterEach(() => cleanup())

  it("shows the schedule and what is next", async () => {
    mount([schedule()])
    expect(await screen.findByText("Office rent")).toBeTruthy()
    expect(screen.getByText(/Next draft on 2026-11-01/)).toBeTruthy()
  })

  it("hides the controls without manage permission", async () => {
    mount([schedule()])
    await screen.findByText("Office rent")
    expect(screen.queryByText("+ New schedule")).toBeNull()
    expect(screen.queryByText("Pause")).toBeNull()
  })

  it("makes due drafts and says to post them", async () => {
    perms.held = new Set(["ACCOUNTING_MANAGE"])
    mount([schedule()])
    fireEvent.click(await screen.findByText("Make due drafts now"))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/accounting/recurring-journals/s1/run-now"))
    expect(await screen.findByText(/Post them from Journal Entries/)).toBeTruthy()
  })

  it("saves a balanced schedule only", async () => {
    perms.held = new Set(["ACCOUNTING_MANAGE"])
    mount([])
    fireEvent.click(await screen.findByText("+ New schedule"))
    const save = screen.getByText("Save schedule") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Rent" } })
    fireEvent.change(screen.getByLabelText("Description"), { target: { value: "Monthly rent" } })
    fireEvent.change(await screen.findByLabelText("Account 1"), { target: { value: "a" } })
    fireEvent.change(screen.getByLabelText("Account 2"), { target: { value: "b" } })
    fireEvent.change(screen.getByLabelText("Debit 1"), { target: { value: "100" } })
    fireEvent.change(screen.getByLabelText("Credit 2"), { target: { value: "100" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/accounting/recurring-journals", expect.objectContaining({
      name: "Rent", kind: "RECURRING", lines: [expect.objectContaining({ accountId: "a", debit: 100 }), expect.objectContaining({ accountId: "b", credit: 100 })] })))
  })

  it("shows why a schedule stopped", async () => {
    mount([schedule({ lastError: "2026-11-01: The books are locked through 30 November 2026" })])
    expect(await screen.findByText(/Stopped: .*locked/)).toBeTruthy()
  })
})
