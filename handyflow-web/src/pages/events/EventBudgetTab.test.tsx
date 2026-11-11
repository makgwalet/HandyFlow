import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup, fireEvent, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EventBudgetTab from "./EventBudgetTab"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn(); const del = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))
const dlg = () => within(screen.getByRole("dialog"))
const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><EventBudgetTab eventId="e1" eventTitle="Summit" onChangeEvent={() => {}} /></QueryClientProvider>)

let budget: any; let summary: any; let lines: any[]; let commitments: any[]
const sum = (o: object = {}) => ({ planned: 100000, committed: 60000, invoiced: 20000, paid: 10000, forecast: 60000, variance: 40000, outstanding: 50000, costPerRegistered: null, alerts: [], lines: [{ id: "l1", category: "CATERING", description: "Food", planned: 100000, committed: 60000, invoiced: 20000, paid: 10000, forecast: 60000, variance: 40000, overBudget: false }], ...o })
const cm = (o: object = {}) => ({ id: "c1", budgetLineId: "l1", category: "CATERING", vendorId: null, vendorName: "Tasty Co", description: "Lunch", amount: 50000, status: "QUOTED", quoteReference: null, deliverable: null, dueDate: null, depositAmount: null, invoiced: 0, paid: 0, overBudgetReason: null, cancelledReason: null, ...o })
beforeEach(() => {
  get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset(); perms = []; post.mockResolvedValue({ data: {} }); put.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} })
  budget = { status: "DRAFT", lineCount: 1, plannedTotal: 100000, submittedByName: null, approvedByName: null, rejectionReason: null }; summary = sum(); commitments = []
  lines = [{ id: "l1", category: "CATERING", description: "Food", plannedAmount: 100000, notes: null }]
  get.mockImplementation(async (u: string) => {
    const d = u.endsWith("/budget/summary") ? summary : u.endsWith("/budget/lines") ? lines : u.endsWith("/budget") ? budget : u.endsWith("/commitments") ? commitments : u.endsWith("/vendors") ? [{ id: "v1", companyName: "Tasty Co" }] : { content: [] }
    return { data: { data: d } }
  })
})
afterEach(cleanup)

describe("event budget", () => {
  it("is closed without read access and fetches nothing", () => { wrap(); expect(screen.getByText(/do not have access/)).toBeTruthy(); expect(get).not.toHaveBeenCalled() })
  it("a reader sees totals and lines but no buttons", async () => {
    perms = ["EVENTS_BUDGET_READ"]; wrap(); await waitFor(() => screen.getByText("Food"))
    expect(screen.getByText("Draft")).toBeTruthy()
    for (const t of ["Add line", "Submit for approval", "Edit", "Delete"]) expect(screen.queryByText(t)).toBeNull()
  })
  it("a manager can add a line while the budget is a draft, and the form is checked", async () => {
    perms = ["EVENTS_BUDGET_MANAGE"]; wrap(); await waitFor(() => screen.getByText("Add line")); fireEvent.click(screen.getByText("Add line"))
    const save = dlg().getByRole("button", { name: "Save line" }) as HTMLButtonElement; expect(save.disabled).toBe(true)
    fireEvent.change(dlg().getByLabelText(/Category/), { target: { value: "VENUE" } }); fireEvent.change(dlg().getByLabelText(/Description/), { target: { value: "Hall hire" } }); fireEvent.change(dlg().getByLabelText(/Planned amount/), { target: { value: "12000" } })
    expect(save.disabled).toBe(false); fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/budget/lines", { category: "VENUE", description: "Hall hire", plannedAmount: 12000, notes: null }))
  })
  it("once submitted the lines are locked and only an approver sees Approve, Send back, Reopen", async () => {
    budget = { ...budget, status: "SUBMITTED", submittedByName: "Thabo" }; perms = ["EVENTS_BUDGET_MANAGE"]; wrap(); await waitFor(() => screen.getByText("Food"))
    expect(screen.queryByText("Add line")).toBeNull(); expect(screen.queryByText("Approve budget")).toBeNull(); cleanup()
    perms = ["EVENTS_BUDGET_READ", "EVENTS_BUDGET_APPROVE"]; wrap(); await waitFor(() => screen.getByText("Approve budget")); expect(screen.getByText("Send back")).toBeTruthy(); expect(screen.getByText("Reopen")).toBeTruthy()
    fireEvent.click(screen.getByText("Approve budget")); fireEvent.click(dlg().getByRole("button", { name: "Approve budget" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/budget/approve"))
  })
  it("sending back needs a reason", async () => {
    budget = { ...budget, status: "SUBMITTED" }; perms = ["EVENTS_MANAGE"]; wrap(); await waitFor(() => screen.getByText("Send back")); fireEvent.click(screen.getByText("Send back"))
    const go = dlg().getByRole("button", { name: "Send back" }) as HTMLButtonElement; expect(go.disabled).toBe(true)
    fireEvent.change(dlg().getByLabelText(/Reason/), { target: { value: "Catering too low" } }); fireEvent.click(go)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/budget/reject", { reason: "Catering too low" }))
  })
  it("shows the server's message when approval is refused", async () => {
    budget = { ...budget, status: "SUBMITTED" }; perms = ["EVENTS_MANAGE"]; post.mockRejectedValue({ response: { data: { message: "The person who submitted the budget can't also approve it" } } })
    wrap(); await waitFor(() => screen.getByText("Approve budget")); fireEvent.click(screen.getByText("Approve budget")); fireEvent.click(dlg().getByRole("button", { name: "Approve budget" }))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toMatch(/can't also approve/))
  })
  it("approving a quote that takes the line over plan warns and asks for a reason", async () => {
    perms = ["EVENTS_MANAGE"]; summary = sum({ lines: [{ ...sum().lines[0], planned: 100000, committed: 80000 }] }); commitments = [cm({ amount: 50000 })]
    wrap(); await waitFor(() => screen.getByText("Food")); fireEvent.click(screen.getByRole("button", { name: "Supplier quotes" })); await waitFor(() => screen.getByText("Approve"))
    fireEvent.click(screen.getByText("Approve")); expect(dlg().getByRole("note").textContent).toMatch(/over its planned amount/)
    const go = dlg().getByRole("button", { name: "Approve commitment" }) as HTMLButtonElement; expect(go.disabled).toBe(true)
    fireEvent.change(dlg().getByLabelText(/Reason for going over/), { target: { value: "Client upgraded menu" } }); fireEvent.click(go)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/commitments/c1/approve", { overrideReason: "Client upgraded menu" }))
  })
  it("records a supplier quote without touching the optional fields", async () => {
    perms = ["EVENTS_SUPPLIER_MANAGE"]; wrap(); await waitFor(() => screen.getByText("Food")); fireEvent.click(screen.getByRole("button", { name: "Supplier quotes" })); await waitFor(() => screen.getByText("Record supplier quote"))
    fireEvent.click(screen.getByText("Record supplier quote"))
    fireEvent.change(dlg().getByLabelText(/Budget line/), { target: { value: "l1" } }); fireEvent.change(dlg().getByLabelText(/What they will provide/), { target: { value: "Lunch" } }); fireEvent.change(dlg().getByLabelText(/Quoted amount/), { target: { value: "5000" } })
    fireEvent.click(dlg().getByRole("button", { name: "Record quote" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/commitments", expect.objectContaining({ budgetLineId: "l1", description: "Lunch", amount: 5000, quoteReference: null, depositAmount: null, vendorId: null })))
  })
  it("records an invoice against an approved commitment", async () => {
    perms = ["EVENTS_MANAGE"]; commitments = [cm({ status: "APPROVED" })]
    wrap(); await waitFor(() => screen.getByText("Food")); fireEvent.click(screen.getByRole("button", { name: "Supplier quotes" })); await waitFor(() => screen.getByText(/Lunch/)); fireEvent.click(screen.getByRole("button", { name: "Invoices and payments" })); await waitFor(() => screen.getByText("Record invoice or payment"))
    fireEvent.click(screen.getByText("Record invoice or payment"))
    fireEvent.change(dlg().getByLabelText(/Against/), { target: { value: "c:c1" } }); fireEvent.change(dlg().getByLabelText(/Kind/), { target: { value: "INVOICE" } }); fireEvent.change(dlg().getByLabelText(/Amount/), { target: { value: "20000" } })
    fireEvent.click(dlg().getByRole("button", { name: "Record" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/events/e1/cost-entries", expect.objectContaining({ commitmentId: "c1", budgetLineId: null, kind: "INVOICE", amount: 20000 })))
  })
})
