import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { ContractsTab } from "./ContractsTab"
import { ReturnsTab } from "./ReturnsTab"
import { QualityTab } from "./QualityTab"
import { ReplenishmentTab } from "./ReplenishmentTab"
import { ScmDashboard } from "./ScmDashboard"

const get = vi.fn(), post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
let perms = ["SCM_ADMIN"]
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (needed: string[]) => needed.some(n => perms.includes(n)) }))
vi.mock("../../theme/ThemeContext", () => ({ useThemeColors: () => (t: string) => `var(${t})` }))
vi.mock("recharts", () => {
  const Pass = ({ children }: { children?: React.ReactNode }) => <div>{children}</div>
  return { ResponsiveContainer: Pass, PieChart: Pass, Pie: Pass, Cell: Pass, Tooltip: Pass, AreaChart: Pass, Area: Pass, XAxis: Pass, YAxis: Pass, CartesianGrid: Pass }
})
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
beforeEach(() => { get.mockReset(); post.mockReset(); perms = ["SCM_ADMIN"] }); afterEach(cleanup)

const iso = (days: number) => { const d = new Date(); d.setDate(d.getDate() + days); return d.toISOString().slice(0, 10) }
const contract = (id: string, over = {}) => ({ id, contractNumber: `C-${id}`, supplierName: "Acme", title: "Supply", status: "ACTIVE", startDate: "2026-01-01", endDate: iso(400), valueCap: null, autoRenew: false, renewalNoticeDays: 30, renewalNoticeDue: false, lines: [], ...over })

describe("ContractsTab", () => {
  it("separates active, expiring and expired and terminates with a reason", async () => {
    get.mockImplementation(() => ok([contract("1"), contract("2", { endDate: iso(20) }), contract("3", { endDate: iso(-5) })]))
    post.mockImplementation(() => ok({}))
    wrap(<ContractsTab />)
    await screen.findByText("C-1")
    fireEvent.click(screen.getByRole("button", { name: /^Expiring/ }))
    expect(screen.queryByText("C-1")).toBeNull(); expect(screen.getAllByText("C-2").length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole("button", { name: /^All/ }))
    fireEvent.click(screen.getAllByRole("button", { name: "Terminate" })[0])
    fireEvent.click(screen.getAllByRole("button", { name: "Terminate" }).at(-1)!)
    expect(await screen.findByText("Give a reason.")).toBeTruthy(); expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Breach" } })
    fireEvent.click(screen.getAllByRole("button", { name: "Terminate" }).at(-1)!)
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/\/contracts\/.+\/terminate$/), { reason: "Breach" }))
  })
  it("hides activate/terminate without manage rights", async () => {
    perms = ["SCM_READ"]; get.mockImplementation(() => ok([contract("1", { status: "DRAFT" })]))
    wrap(<ContractsTab />); await screen.findByText("C-1")
    expect(screen.queryByRole("button", { name: "Activate" })).toBeNull()
  })
})

const ret = (over = {}) => ({ id: "r1", returnNumber: "RET-1", kind: "RETURN", reason: "DAMAGED", status: "DISPATCHED", supplierName: "Acme", expectedCredit: 100, creditNoteRef: null, creditAmount: null, statusReason: null, createdAt: "2026-11-01T00:00:00Z", lines: [{ id: "l", itemName: "Bolts", qty: 2, lineTotal: 100 }], ...over })
describe("ReturnsTab", () => {
  it("records a credit only with a reference and an amount", async () => {
    get.mockImplementation(() => ok([ret()])); post.mockImplementation(() => ok({}))
    wrap(<ReturnsTab />); await screen.findByText(/RET-1/)
    fireEvent.click(screen.getByRole("button", { name: "Record credit" }))
    fireEvent.click(screen.getAllByRole("button", { name: "Record credit" }).at(-1)!)
    expect(await screen.findByText(/Enter the supplier/)).toBeTruthy(); expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Credit note reference"), { target: { value: "CN-9" } })
    fireEvent.change(screen.getByLabelText("Credit total"), { target: { value: "115" } })
    fireEvent.click(screen.getAllByRole("button", { name: "Record credit" }).at(-1)!)
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/\/returns\/r1\/credit$/), { creditNoteRef: "CN-9", creditAmount: 115 }))
  })
  it("offers authorise only to approvers", async () => {
    perms = ["SCM_RETURN_MANAGE"]; get.mockImplementation(() => ok([ret({ status: "SUBMITTED" })]))
    wrap(<ReturnsTab />); await screen.findByText(/RET-1/)
    expect(screen.queryByRole("button", { name: "Authorise" })).toBeNull()
  })
})

describe("QualityTab", () => {
  it("refuses to scrap more than is held or without a reason", async () => {
    get.mockImplementation(() => ok([{ id: "h1", itemName: "Valve", qty: 10, released: 0, returned: 0, writtenOff: 0, open: 4, status: "OPEN", defectCode: "CRACK", createdAt: "2026-11-01T00:00:00Z" }]))
    post.mockImplementation(() => ok({}))
    wrap(<QualityTab />); await screen.findByText("Valve")
    fireEvent.click(screen.getByRole("button", { name: "Scrap" }))
    fireEvent.click(screen.getByRole("button", { name: "Scrap stock" }))
    expect(await screen.findByText(/reason is required/i)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Quantity"), { target: { value: "9" } })
    fireEvent.click(screen.getByRole("button", { name: "Scrap stock" }))
    expect(await screen.findByText(/Only 4/)).toBeTruthy(); expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Quantity"), { target: { value: "4" } })
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "cracked" } })
    fireEvent.click(screen.getByRole("button", { name: "Scrap stock" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/quarantine\/h1\/write-off$/), { qty: 4, reason: "cracked" }))
  })
})

describe("ReplenishmentTab", () => {
  it("raises a draft requisition from ticked items with edited quantity", async () => {
    get.mockImplementation(() => ok([{ catalogueItemId: "i1", itemName: "Gloves", available: 0, onOrder: 0, requested: 0, reorderLevel: 10, dailyDemand: 1, daysCover: 0, leadTimeDays: 5, suggestedQty: 20, urgency: "STOCKOUT", reason: "Out", suggestedUnitCost: 10, priceSource: null, contractNumber: null }]))
    post.mockImplementation(() => ok({}))
    wrap(<ReplenishmentTab />); await screen.findByText("Gloves")
    fireEvent.click(screen.getByRole("button", { name: "Raise draft requisition" }))
    expect(await screen.findByText("Tick at least one item.")).toBeTruthy()
    fireEvent.click(screen.getByLabelText("Select Gloves"))
    fireEvent.change(screen.getByLabelText("Quantity for Gloves"), { target: { value: "25" } })
    fireEvent.click(screen.getByRole("button", { name: "Raise draft requisition" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/replenishment\/requisition$/), expect.objectContaining({ items: [{ catalogueItemId: "i1", qty: 25 }] })))
  })
})

describe("ScmDashboard", () => {
  it("shows alerts and routes them", async () => {
    const onNav = vi.fn()
    get.mockImplementation((url: string) => {
      if (url.endsWith("/summary")) return ok({ totalSuppliers: 3, openPurchaseOrders: 2, pendingInvoices: 1, invoicesForApproval: 0, lowStockItems: 1, overdueInvoices: 2 })
      return ok([])
    })
    wrap(<ScmDashboard onNav={onNav} />)
    fireEvent.click(await screen.findByRole("button", { name: /2 supplier invoices overdue/ }))
    expect(onNav).toHaveBeenCalledWith("invoices")
  })
  it("survives endpoints that fail", async () => {
    get.mockImplementation((url: string) => url.endsWith("/summary") ? ok({ totalSuppliers: 1, openPurchaseOrders: 0, pendingInvoices: 0, invoicesForApproval: 0, lowStockItems: 0, overdueInvoices: 0 }) : Promise.reject(new Error("403")))
    wrap(<ScmDashboard onNav={() => {}} />)
    expect(await screen.findByText("Nothing needs attention.")).toBeTruthy()
  })
})
