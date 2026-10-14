import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

// Finance F24: cost centres, the by-cost-centre report, and tagging journal lines.
let perms = new Set<string>()
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
vi.mock("../../hooks/useAuth", () => ({ useAuth: () => ({ user: { userId: "me" } }) }))
const get = vi.fn(), post = vi.fn(), put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: {
  get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), put: (...a: any[]) => put(...a), delete: vi.fn() } }))
import CostCentresTab from "./CostCentresTab"
import ByCostCentreTab from "./ByCostCentreTab"
import JournalEntriesTab from "./JournalEntriesTab"

const centres = [
  { id: "b1", code: "DBN", name: "Durban", kind: "BRANCH", parentId: null, active: true, lineCount: 4 },
  { id: "d1", code: "DBN-S", name: "Durban sales", kind: "DEPARTMENT", parentId: "b1", active: true, lineCount: 0 },
  { id: "x1", code: "OLD", name: "Old project", kind: "PROJECT", parentId: null, active: false, lineCount: 9 },
]
const report = {
  from: "2026-03-01", to: "2026-10-14", rollUp: true,
  columns: [{ id: "b1", code: "DBN", name: "Durban", kind: "BRANCH" }, { id: null, code: "-", name: "Unallocated", kind: null }],
  income: [{ accountId: "a", code: "4000", name: "Sales", type: "INCOME", amounts: [1000, 50], total: 1050 }],
  expenses: [{ accountId: "f", code: "5100", name: "Fuel", type: "EXPENSE", amounts: [200, 0], total: 200 }],
  incomeTotals: [1000, 50], expenseTotals: [200, 0], resultTotals: [800, 50], incomeTotal: 1050, expenseTotal: 200, resultTotal: 850,
}
const show = (ui: React.ReactElement) => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["ACCOUNTING_READ", "ACCOUNTING_MANAGE"])
  get.mockReset(); post.mockReset(); put.mockReset()
  get.mockImplementation((url: string) => Promise.resolve({ data: { data:
    url.includes("cost-centres/report") ? report
    : url.includes("cost-centres") ? centres
    : url.includes("/accounts") ? [{ id: "acc1", accountCode: "4000", accountName: "Sales", accountType: "INCOME" }, { id: "acc2", accountCode: "1000", accountName: "Bank", accountType: "ASSET" }]
    : url.includes("journal-entries") ? { content: [], totalElements: 0 } : [] } }))
  post.mockResolvedValue({ data: { data: {} } }); put.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

describe("CostCentresTab", () => {
  it("lists centres with children indented and switched-off ones marked", async () => {
    show(<CostCentresTab />)
    await screen.findByText("Durban sales")
    expect(screen.getByText("Switched off")).toBeTruthy()
    expect(screen.getAllByText("Active")).toHaveLength(2)
  })

  it("creates one inside a parent", async () => {
    show(<CostCentresTab />)
    await screen.findByText("Durban sales")
    fireEvent.change(screen.getByLabelText("Code"), { target: { value: "dbn-ops" } })
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Durban ops" } })
    fireEvent.change(screen.getByLabelText("Kind"), { target: { value: "DEPARTMENT" } })
    fireEvent.change(screen.getByLabelText("Inside"), { target: { value: "b1" } })
    fireEvent.click(screen.getByText("Add"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/cost-centres",
      { code: "dbn-ops", name: "Durban ops", kind: "DEPARTMENT", parentId: "b1" }))
  })

  it("offers neither itself nor its children as a parent when editing", async () => {
    show(<CostCentresTab />)
    await screen.findByText("Durban sales")
    fireEvent.click(screen.getAllByText("Edit")[0])
    const options = within(screen.getByLabelText("Inside")).getAllByRole("option").map(o => o.textContent)
    expect(options.some(t => t?.includes("DBN —"))).toBe(false)
    expect(options.some(t => t?.includes("DBN-S"))).toBe(false)
    expect(options.some(t => t?.includes("OLD"))).toBe(true)
  })

  it("switches a centre off", async () => {
    show(<CostCentresTab />)
    await screen.findByText("Durban sales")
    fireEvent.click(screen.getAllByText("Switch off")[0])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/cost-centres/b1/active", { active: false }))
  })

  it("hides the editing controls without ACCOUNTING_MANAGE", async () => {
    perms = new Set(["ACCOUNTING_READ"])
    show(<CostCentresTab />)
    await screen.findByText("Durban sales")
    expect(screen.queryByText("Edit")).toBeNull()
    expect(screen.queryByText("Add")).toBeNull()
  })

  it("shows the server's reason when a save is refused", async () => {
    post.mockRejectedValue({ response: { data: { message: "Another cost centre already uses the code X." } } })
    show(<CostCentresTab />)
    await screen.findByText("Durban sales")
    fireEvent.change(screen.getByLabelText("Code"), { target: { value: "X" } })
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "y" } })
    fireEvent.click(screen.getByText("Add"))
    expect(await screen.findByText(/already uses the code X/)).toBeTruthy()
  })
})

describe("ByCostCentreTab", () => {
  it("shows a column per centre plus Unallocated, with totals", async () => {
    show(<ByCostCentreTab />)
    await screen.findByText("Total income")
    expect(screen.getByText("Unallocated")).toBeTruthy()
    expect(screen.getByText("Profit / (loss)")).toBeTruthy()
    expect(screen.getAllByText(/1[\s ,]?050[.,]00/).length).toBeGreaterThan(0)
    expect(screen.getByText(/Overall margin 81%/)).toBeTruthy()
  })

  it("asks for the lines behind a centre", async () => {
    show(<ByCostCentreTab />)
    await screen.findByText("Total income")
    fireEvent.click(screen.getByText("DBN"))
    await waitFor(() => expect(get.mock.calls.some(c => String(c[0]).includes("/cost-centres/b1/lines?from="))).toBe(true))
  })

  it("passes the kind filter and the roll-up switch", async () => {
    show(<ByCostCentreTab />)
    await screen.findByText("Total income")
    fireEvent.change(screen.getByLabelText("Kind"), { target: { value: "BRANCH" } })
    fireEvent.click(screen.getByLabelText("Include everything inside"))
    await waitFor(() => expect(get.mock.calls.some(c => String(c[0]).includes("rollUp=false&kind=BRANCH"))).toBe(true))
  })
})

describe("Journal lines with a cost centre", () => {
  it("sends the chosen cost centre with the line", async () => {
    show(<JournalEntriesTab />)
    await screen.findByText("New Journal Entry")
    fireEvent.click(screen.getByText("New Journal Entry"))
    await waitFor(() => expect(screen.getAllByLabelText("Cost centre").length).toBe(2))
    const accounts = screen.getAllByDisplayValue("Select account...")
    fireEvent.change(accounts[0], { target: { value: "acc2" } })
    fireEvent.change(accounts[1], { target: { value: "acc1" } })
    fireEvent.change(screen.getAllByLabelText("Cost centre")[1], { target: { value: "b1" } })
    const amounts = screen.getAllByPlaceholderText("0.00")
    fireEvent.change(amounts[0], { target: { value: "100" } })
    fireEvent.change(amounts[3], { target: { value: "100" } })
    fireEvent.change(screen.getByPlaceholderText(/Cash sale/), { target: { value: "Sale" } })
    fireEvent.click(screen.getByText("Create Journal Entry"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const body = post.mock.calls[0][1]
    expect(body.lines[0].costCentreId).toBeUndefined()
    expect(body.lines[1].costCentreId).toBe("b1")
  })

  it("does not offer switched-off centres on a new line", async () => {
    show(<JournalEntriesTab />)
    await screen.findByText("New Journal Entry")
    fireEvent.click(screen.getByText("New Journal Entry"))
    const select = (await screen.findAllByLabelText("Cost centre"))[0]
    const labels = within(select).getAllByRole("option").map(o => o.textContent ?? "")
    expect(labels.some(t => t.includes("OLD"))).toBe(false)
    expect(labels.some(t => t.includes("DBN-S"))).toBe(true)
  })
})
