import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import BudgetsTab from "./BudgetsTab"
import CashForecastTab from "./CashForecastTab"

const perms = vi.hoisted(() => ({ held: new Set<string>() }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.held.has(p) }))
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
afterEach(cleanup)
beforeEach(() => { Object.values(api).forEach(f => f.mockReset()); perms.held = new Set(["ACCOUNTING_READ", "ACCOUNTING_MANAGE"]) })

const summary = (over: any = {}) => ({ id: "b1", name: "Budget 2027", startDate: "2026-10-01", endDate: "2027-09-30", status: "DRAFT", income: 0, expense: 0, lines: 0, ...over })
const detail = (over: any = {}) => ({ summary: summary(over), accounts: [{ accountId: "a1", code: "4000", name: "Sales", type: "INCOME", months: [1000, ...Array(11).fill(0)] }] })

describe("BudgetsTab", () => {
  it("opens a draft, edits a cell and saves only non-empty cells", async () => {
    api.get.mockImplementation(async (url: string) => {
      if (url.endsWith("/budgets")) return { data: { data: [summary()] } }
      if (url.endsWith("/budgets/b1")) return { data: { data: detail() } }
      return { data: { data: [{ id: "a1", accountCode: "4000", accountName: "Sales", accountType: "INCOME" }] } }
    })
    api.put.mockResolvedValue({ data: { data: detail() } })
    wrap(<BudgetsTab />)
    fireEvent.click(await screen.findByText("Budget 2027"))
    const cell = await screen.findByLabelText(/4000 Nov/)
    fireEvent.change(cell, { target: { value: "250" } })
    fireEvent.click(screen.getByText("Save"))
    await waitFor(() => expect(api.put).toHaveBeenCalled())
    expect(api.put.mock.calls[0][1]).toEqual({ lines: [{ accountId: "a1", month: 0, amount: 1000 }, { accountId: "a1", month: 1, amount: 250 }] })
  })

  it("shows the budget against actual once active and no edit boxes", async () => {
    api.get.mockImplementation(async (url: string) => {
      if (url.endsWith("/budgets")) return { data: { data: [summary({ status: "ACTIVE" })] } }
      if (url.endsWith("/vs-actual")) return { data: { data: { name: "B", from: "2026-10-01", through: "2026-10-14", monthsCounted: 1,
        rows: [{ accountId: "a1", code: "4000", name: "Sales", type: "INCOME", budget: 1000, actual: 800, variance: -200, percent: -20, favourable: false }],
        budgetIncome: 1000, actualIncome: 800, budgetExpense: 0, actualExpense: 0, budgetNet: 1000, actualNet: 800 } } }
      if (url.endsWith("/budgets/b1")) return { data: { data: detail({ status: "ACTIVE" }) } }
      return { data: { data: [] } }
    })
    wrap(<BudgetsTab />)
    fireEvent.click(await screen.findByText("Budget 2027"))
    expect(await screen.findByText(/Worse by/)).toBeTruthy()
    expect(screen.queryByLabelText(/4000 Nov/)).toBeNull()
    expect(screen.queryByText("Save")).toBeNull()
  })

  it("creates a budget from a name and a start date", async () => {
    api.get.mockResolvedValue({ data: { data: [] } })
    api.post.mockResolvedValue({ data: { data: summary() } })
    wrap(<BudgetsTab />)
    fireEvent.change(await screen.findByLabelText("Budget name"), { target: { value: "FY27" } })
    fireEvent.change(screen.getByLabelText("Starts on"), { target: { value: "2026-10-01" } })
    fireEvent.click(screen.getByText("Create"))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/accounting/budgets", { name: "FY27", startDate: "2026-10-01" }))
  })
})

describe("CashForecastTab", () => {
  const forecast = { today: "2026-10-14", openingCash: 1500, weeks: [{ index: 0, start: "2026-10-14", end: "2026-10-20", opening: 1500, in: 0, out: 2000, closing: -500 }],
    lowest: -500, lowestWeekStart: "2026-10-14", firstNegativeWeek: 0, laterIn: 0, laterOut: 0, warnings: ["Cash is expected to run out in week 1 (from 2026-10-14)."], items: [], receivablesTotal: 1000, payablesTotal: 300 }
  it("shows the warning and passes the assumptions to the server", async () => {
    api.get.mockImplementation(async (url: string) => url.includes("/items") ? { data: { data: [] } } : { data: { data: forecast } })
    wrap(<CashForecastTab />)
    expect((await screen.findByRole("alert")).textContent).toMatch(/run out in week 1/)
    fireEvent.change(screen.getByLabelText(/Customers pay this many days late/), { target: { value: "10" } })
    await waitFor(() => expect(api.get.mock.calls.some(c => String(c[0]).includes("customerDelayDays=10"))).toBe(true))
  })
  it("adds an item only when it is complete", async () => {
    api.get.mockImplementation(async (url: string) => url.includes("/items") ? { data: { data: [] } } : { data: { data: { ...forecast, warnings: [] } } })
    api.post.mockResolvedValue({ data: { data: {} } })
    wrap(<CashForecastTab />)
    const add = await screen.findByText("Add")
    expect((add as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Item date"), { target: { value: "2026-10-25" } })
    fireEvent.change(screen.getByLabelText("Item description"), { target: { value: "Payroll" } })
    fireEvent.change(screen.getByLabelText("Item amount"), { target: { value: "5000" } })
    expect((add as HTMLButtonElement).disabled).toBe(false)
    fireEvent.click(add)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/accounting/cash-forecast/items", { date: "2026-10-25", amount: 5000, direction: "OUT", label: "Payroll" }))
  })
})
