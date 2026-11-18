import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { RequisitionsTab } from "./RequisitionsTab"
import { BudgetsTab } from "./BudgetsTab"
import { RfqTab } from "./RfqTab"

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

describe("RfqTab", () => {
  const rfq = { id: "q1", rfqNumber: "RFQ-1", status: "CLOSED", title: "Tyres", closesAt: null, estimatedTotal: 8000, quotationCount: 2, awardedQuotationId: null, awardReason: null, overridesRecommendation: false, poId: null,
    weights: { price: 50, leadTime: 15, technical: 15, performance: 10, bbbee: 10 }, lines: [{ id: "l1", itemName: "Tyre", quantity: 8 }], invitations: [{ supplierId: "s1", supplierName: "Alpha", status: "QUOTED" }, { supplierId: "s2", supplierName: "Beta", status: "QUOTED" }] }
  const cmp = { recommendedQuotationId: "qa", lowestPriceQuotationId: "qb", rows: [
    { quotationId: "qa", supplierName: "Alpha", status: "SUBMITTED", landedCost: 8200, totalInclVat: 9430, leadTimeDays: 5, rank: 1, totalScore: 88, ranked: true, flags: [], estimatedSaving: null },
    { quotationId: "qb", supplierName: "Beta", status: "SUBMITTED", landedCost: 7900, totalInclVat: 9085, leadTimeDays: 14, rank: 2, totalScore: 79, ranked: true, flags: ["LONG_LEAD_TIME"], estimatedSaving: null }] }
  const route = () => get.mockImplementation((u: string) => u.endsWith("/comparison") ? ok(cmp) : u.endsWith("/q1") ? ok(rfq) : ok([rfq]))

  it("shows the recommendation and the lowest price", async () => {
    route(); wrap(<RfqTab />)
    fireEvent.click(await screen.findByText("RFQ-1"))
    expect(await screen.findByText(/Recommended/)).toBeTruthy()
    expect(screen.getByText("Lowest price")).toBeTruthy()
    expect(screen.getByText("long lead time")).toBeTruthy()
  })
  it("awarding needs a reason, and warns when not the recommended quotation", async () => {
    route(); wrap(<RfqTab />)
    fireEvent.click(await screen.findByText("RFQ-1"))
    await screen.findByText(/Recommended/)
    fireEvent.click(screen.getAllByText("Choose")[1])
    expect(await screen.findByText(/overriding the recommendation/)).toBeTruthy()
    fireEvent.click(screen.getByText("Award"))
    expect(await screen.findByText(/Record why/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Award reason"), { target: { value: "Cheaper and in stock" } })
    fireEvent.click(screen.getByText("Award"))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringContaining("/q1/award"), { quotationId: "qb", reason: "Cheaper and in stock" }))
  })
  it("will not save an RFQ whose weights do not add to 100", async () => {
    route(); get.mockImplementation((u: string) => u.includes("suppliers") ? ok([{ id: "s1", name: "Alpha" }]) : ok([]))
    wrap(<RfqTab />)
    fireEvent.click(await screen.findByText("New RFQ"))
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Tyres" } })
    fireEvent.change(screen.getByLabelText("Item 1"), { target: { value: "Tyre" } })
    fireEvent.change(screen.getByLabelText("Price weight"), { target: { value: "70" } })
    fireEvent.click(screen.getByText("Save draft"))
    expect(await screen.findByText(/add up to 120/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })
  it("a person who cannot award sees no award controls", async () => {
    perms = ["SCM_RFQ_MANAGE"]; route(); wrap(<RfqTab />)
    fireEvent.click(await screen.findByText("RFQ-1"))
    await screen.findByText(/Recommended/)
    expect(screen.queryByText("Award")).toBeNull()
  })
})
