import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { RequisitionsTab } from "./RequisitionsTab"
import { BudgetsTab } from "./BudgetsTab"

const get = vi.fn(); const post = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const refused = (m: string) => Promise.reject(Object.assign(new Error(m), { response: { data: { message: m } } }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>{ui}</QueryClientProvider>)

beforeEach(() => { get.mockReset(); post.mockReset(); post.mockResolvedValue({ data: {} }); perms = ["SCM_ADMIN"] })
afterEach(cleanup)

describe("RequisitionsTab", () => {
  const req = (status: string) => ({ id: "r1", number: "REQ-1", type: "OPERATIONAL", status, fulfilment: "NOT_ORDERED", title: "Gloves", department: "Workshop", costCentre: null, projectRef: null, requiredByDate: null, estimatedTotal: 250 })
  it("refuses to save without a budget scope, and sends nothing", async () => {
    get.mockImplementation(() => ok([]))
    wrap(<RequisitionsTab />)
    fireEvent.click(await screen.findByText("New requisition"))
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Gloves" } })
    fireEvent.change(screen.getByLabelText("Item 1"), { target: { value: "Gloves" } })
    fireEvent.change(screen.getByLabelText("Unit cost 1"), { target: { value: "25" } })
    fireEvent.click(screen.getByText("Save draft"))
    expect(await screen.findByText(/budget check needs it/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })
  it("saves a valid draft", async () => {
    get.mockImplementation(() => ok([]))
    wrap(<RequisitionsTab />)
    fireEvent.click(await screen.findByText("New requisition"))
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Gloves" } })
    fireEvent.change(screen.getByLabelText("Department"), { target: { value: "Workshop" } })
    fireEvent.change(screen.getByLabelText("Item 1"), { target: { value: "Gloves" } })
    fireEvent.change(screen.getByLabelText("Unit cost 1"), { target: { value: "25" } })
    fireEvent.click(screen.getByText("Save draft"))
    await waitFor(() => expect(post).toHaveBeenCalledTimes(1))
    expect(post.mock.calls[0][1]).toMatchObject({ title: "Gloves", department: "Workshop", lines: [{ itemName: "Gloves", quantity: 1, estimatedUnitCost: 25 }] })
  })
  it("rejecting needs a reason", async () => {
    get.mockImplementation(() => ok([req("PENDING_APPROVAL")]))
    wrap(<RequisitionsTab />)
    fireEvent.click(await screen.findByText("Reject"))
    fireEvent.click(screen.getAllByText("Reject").pop()!)
    expect(await screen.findByText("A reason is required")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Over budget" } })
    fireEvent.click(screen.getAllByText("Reject").pop()!)
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringContaining("/r1/reject"), { note: "Over budget" }))
  })
  it("hides actions the person cannot take", async () => {
    perms = ["SCM_REQUISITION_APPROVE"]
    get.mockImplementation(() => ok([req("DRAFT")]))
    wrap(<RequisitionsTab />)
    await screen.findByText("REQ-1")
    expect(screen.queryByText("Submit")).toBeNull()
    expect(screen.queryByText("New requisition")).toBeNull()
  })
})

describe("BudgetsTab", () => {
  it("shows the server's message when budgets overlap", async () => {
    get.mockImplementation(() => ok([]))
    post.mockImplementation(() => refused("A budget for Workshop already covers part of this period"))
    wrap(<BudgetsTab />)
    fireEvent.click(await screen.findByText("New budget"))
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "Workshop 2026" } })
    fireEvent.change(screen.getByLabelText("Scope value"), { target: { value: "Workshop" } })
    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2026-01-01" } })
    fireEvent.change(screen.getByLabelText("To"), { target: { value: "2026-12-31" } })
    fireEvent.change(screen.getByLabelText("Amount"), { target: { value: "100000" } })
    fireEvent.click(screen.getByText("Create budget"))
    expect(await screen.findByText(/already covers part of this period/)).toBeTruthy()
  })
  it("marks an exceeded budget", async () => {
    get.mockImplementation(() => ok([{ id: "b1", name: "Workshop", scopeType: "DEPARTMENT", scopeValue: "Workshop", periodStart: "2026-01-01", periodEnd: "2026-12-31", amount: 1000, committed: 900, actual: 300, used: 1200, available: -200, utilisationPct: 120, state: "EXCEEDED", controlMode: "HARD_STOP" }]))
    wrap(<BudgetsTab />)
    expect(await screen.findByText(/120% — exceeded/)).toBeTruthy()
  })
})
