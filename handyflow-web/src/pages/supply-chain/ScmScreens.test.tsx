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

describe("Inspection and return creation", () => {
  it("records inspection results and completes, refusing more than was received", async () => {
    get.mockImplementation((u: string) => u.includes("quality-inspections") ? ok([{ id: "i1", inspectionNumber: "QI-1", status: "OPEN", goodsReceiptId: "g1", result: null, inspectedByName: null, createdAt: "2026-11-01T00:00:00Z", returnId: null, lines: [{ id: "il1", grLineId: "gl1", itemName: "Valve", qtyAcceptedOnReceipt: 10, qtyInspected: 0, qtyPassed: 0, qtyFailed: 0, defectCode: null, notes: null }] }]) : ok([]))
    post.mockImplementation(() => ok({})); const put = vi.fn(() => ok({})); (await import("../../api/client")).apiClient.put = put as never
    wrap(<QualityTab />); fireEvent.click(await screen.findByRole("button", { name: "Inspections" }))
    fireEvent.click(await screen.findByRole("button", { name: "Record results" }))
    fireEvent.change(screen.getByLabelText("Passed Valve"), { target: { value: "9" } }); fireEvent.change(screen.getByLabelText("Failed Valve"), { target: { value: "2" } })
    fireEvent.click(screen.getByRole("button", { name: "Complete inspection" }))
    expect(await screen.findByText(/more than the 10 received/)).toBeTruthy(); expect(put).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Passed Valve"), { target: { value: "8" } })
    fireEvent.click(screen.getByRole("button", { name: "Complete inspection" }))
    expect(await screen.findByText(/what is wrong/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Defect Valve"), { target: { value: "cracked" } })
    fireEvent.click(screen.getByRole("button", { name: "Complete inspection" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/quality-inspections\/i1\/complete$/), { raiseReturnForFailures: true }))
    expect(put).toHaveBeenCalledWith(expect.stringMatching(/i1\/results$/), expect.objectContaining({ lines: [expect.objectContaining({ qtyPassed: 8, qtyFailed: 2, defectCode: "cracked" })] }))
  })
  it("raises a return by hand and checks the form first", async () => {
    get.mockImplementation((u: string) => u.endsWith("/suppliers") ? ok([{ id: "s1", name: "Acme" }]) : ok([])); post.mockImplementation(() => ok({}))
    wrap(<ReturnsTab />); fireEvent.click(await screen.findByRole("button", { name: "New return or claim" }))
    fireEvent.click(screen.getByRole("button", { name: "Save as draft" }))
    expect(await screen.findByText(/Choose the supplier/)).toBeTruthy(); expect(post).not.toHaveBeenCalled()
    await screen.findByRole("option", { name: "Acme" }); fireEvent.change(screen.getByLabelText("Supplier"), { target: { value: "s1" } }); fireEvent.change(screen.getByLabelText("Item 1"), { target: { value: "Bolts" } }); fireEvent.change(screen.getByLabelText("Unit cost 1"), { target: { value: "10" } })
    fireEvent.click(screen.getByRole("button", { name: "Save as draft" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/\/returns$/), expect.objectContaining({ kind: "RETURN", supplierId: "s1", lines: [{ grLineId: null, itemName: "Bolts", qty: 1, unitCost: 10 }] })))
  })
})

import { AccessTab } from "./AccessTab"
describe("AccessTab", () => {
  const roles = [{ id: "r1", name: "Buyer", description: "", permissions: ["SCM_READ", "FUEL_READ"], userCount: 3 }, { id: "r2", name: "Manager", description: "", permissions: ["SCM_ADMIN"], userCount: 1 }]
  const permsList = [{ id: "p1", name: "SCM_READ", description: "Read" }, { id: "p2", name: "SCM_ADMIN", description: "Admin" }, { id: "p3", name: "FUEL_READ", description: "Fuel" }, { id: "p4", name: "SCM_PRICE_OVERRIDE_APPROVE", description: "Approve price" }]
  const route = () => get.mockImplementation((u: string) => u.endsWith("/roles") ? ok(roles) : ok(permsList))
  it("lists only Supply Chain permissions and flags second-person approvals", async () => {
    route(); perms = ["ROLE_MANAGE"]; wrap(<AccessTab />)
    expect(await screen.findByLabelText("Buyer SCM_READ")).toBeTruthy(); expect(screen.queryByLabelText("Buyer FUEL_READ")).toBeNull()
    expect(screen.getAllByText("2nd person").length).toBeGreaterThan(0)
  })
  it("saves the whole permission list, keeping permissions from other modules", async () => {
    route(); perms = ["ROLE_MANAGE"]; const put = vi.fn(() => ok({})); (await import("../../api/client")).apiClient.put = put as never
    wrap(<AccessTab />); fireEvent.click(await screen.findByLabelText("Buyer SCM_PRICE_OVERRIDE_APPROVE"))
    fireEvent.click(await screen.findByRole("button", { name: "Save Buyer" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith(expect.stringMatching(/roles\/r1\/permissions$/), expect.arrayContaining(["p1", "p3", "p4"])))
    expect((put.mock.calls[0] as unknown[])[1]).toHaveLength(3)
  })
  it("warns before admin is taken from a role with users, and is read-only without ROLE_MANAGE", async () => {
    route(); perms = ["ROLE_MANAGE"]; wrap(<AccessTab />); fireEvent.click(await screen.findByLabelText("Manager SCM_ADMIN"))
    expect(await screen.findByText(/1 user in Manager will lose Supply Chain admin/)).toBeTruthy()
    cleanup(); perms = ["ROLE_READ"]; route(); wrap(<AccessTab />)
    expect(((await screen.findByLabelText("Buyer SCM_READ")) as HTMLInputElement).disabled).toBe(true)
  })
})
