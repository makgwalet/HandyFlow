import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

// Finance patch F13: month-end close checklist and cash flow statement.
let perms = new Set<string>()
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const get = vi.fn(), post = vi.fn(), del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), delete: (...a: any[]) => del(...a) } }))
import MonthEndCloseTab from "./MonthEndCloseTab"
import CashFlowTab from "./CashFlowTab"

const item = (key: string, status: string, over: any = {}) => ({ key, title: key + " title", kind: "AUTO", status, detail: "d", blocking: true, confirmedBy: null, confirmedAt: null, ...over })
const list = (items: any[], over: any = {}) => ({ data: { data: { periodEnd: "2026-09-30", lockedThrough: null, closable: false, closeProblem: "2 items are not done", items, ...over } } })
const show = (c: any) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{c}</QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["ACCOUNTING_READ", "ACCOUNTING_MANAGE", "ACCOUNTING_ADMIN"])
  get.mockReset(); post.mockReset(); del.mockReset(); post.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("MonthEndCloseTab", () => {
  it("shows what is left and keeps Close disabled", async () => {
    get.mockResolvedValue(list([item("DRAFTS", "BLOCKED"), item("PAYROLL", "WAITING", { kind: "MANUAL" }), item("VAT", "WARNING", { blocking: false })]))
    show(<MonthEndCloseTab />)
    await waitFor(() => expect(screen.getByTestId("summary").textContent).toBe("2 items still to do."))
    expect(screen.getByTestId("close-problem").textContent).toContain("not done")
    expect((screen.getByRole("button", { name: /^Close / }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("confirms a manual step and takes one back", async () => {
    get.mockResolvedValue(list([item("PAYROLL", "WAITING", { kind: "MANUAL" }), item("ACCRUALS", "DONE", { kind: "MANUAL" })]))
    show(<MonthEndCloseTab />)
    fireEvent.click(await screen.findByRole("button", { name: "Confirm" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/month-end/confirmations", { periodEnd: expect.any(String), stepKey: "PAYROLL" }))
    fireEvent.click(screen.getByRole("button", { name: "Undo" }))
    await waitFor(() => expect(del).toHaveBeenCalledWith(expect.stringContaining("stepKey=ACCRUALS")))
  })

  it("closes when ready", async () => {
    get.mockResolvedValue(list([item("DRAFTS", "DONE")], { closable: true, closeProblem: null }))
    show(<MonthEndCloseTab />)
    await waitFor(() => expect(screen.getByTestId("summary").textContent).toBe("Everything is done. Ready to close."))
    fireEvent.change(screen.getByLabelText("Close note"), { target: { value: "Sept" } })
    fireEvent.click(screen.getByRole("button", { name: /^Close / }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/month-end/close", { periodEnd: expect.any(String), note: "Sept" }))
  })

  it("shows a closed month without confirm or close buttons", async () => {
    get.mockResolvedValue(list([item("PAYROLL", "DONE", { kind: "MANUAL" })], { lockedThrough: "2026-09-30", closable: false }))
    show(<MonthEndCloseTab />)
    await waitFor(() => expect(screen.getByTestId("summary").textContent).toContain("Closed."))
    expect(screen.queryByRole("button", { name: "Undo" })).toBeNull()
    expect(screen.queryByRole("button", { name: /^Close / })).toBeNull()
  })

  it("hides the buttons without permission", async () => {
    perms = new Set(["ACCOUNTING_READ"])
    get.mockResolvedValue(list([item("PAYROLL", "WAITING", { kind: "MANUAL" })]))
    show(<MonthEndCloseTab />)
    await screen.findByTestId("item-PAYROLL")
    expect(screen.queryByRole("button", { name: "Confirm" })).toBeNull()
    expect(screen.queryByRole("button", { name: /^Close / })).toBeNull()
  })
})

describe("CashFlowTab", () => {
  const cf = (over: any = {}) => ({ data: { data: { fromDate: "2026-09-01", toDate: "2026-09-30", netProfit: 800,
    operating: [{ label: "Net profit for the period", amount: 800 }, { label: "Increase in Debtors", amount: -400 }], operatingTotal: 400,
    investing: [], investingTotal: 0, financing: [{ label: "Loan", amount: 500 }], financingTotal: 500,
    netChange: 900, openingCash: 1000, closingCash: 1900, difference: 0, agrees: true, ...over } } })

  it("shows the three sections and that it agrees", async () => {
    get.mockResolvedValue(cf())
    show(<CashFlowTab />)
    await screen.findByText("Increase in Debtors")
    expect(screen.getByTestId("operating-total").textContent).toMatch(/400[.,]00/)
    expect(screen.getByTestId("closing").textContent).toContain("1")
    expect(screen.getByText(/Agrees with the cash and bank accounts/)).toBeTruthy()
    expect(screen.queryByTestId("disagree")).toBeNull()
  })

  it("warns when it does not agree", async () => {
    get.mockResolvedValue(cf({ agrees: false, difference: -10 }))
    show(<CashFlowTab />)
    expect((await screen.findByTestId("disagree")).textContent).toMatch(/10[.,]00/)
  })
})
