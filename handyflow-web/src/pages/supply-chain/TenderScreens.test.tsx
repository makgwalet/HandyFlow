import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { TendersTab } from "./TendersTab"
import { BudgetsTab } from "./BudgetsTab"

const get = vi.fn(), post = vi.fn(), put = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })}>{ui}</QueryClientProvider>)
beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); post.mockResolvedValue({ data: {} }); put.mockResolvedValue({ data: {} }); perms = ["SCM_ADMIN"] })
afterEach(cleanup)

const rfq = { id: "q1", rfqNumber: "RFQ-1", status: "CLOSED", title: "Tyres", description: null, closesAt: null, requiredByDate: null, estimatedTotal: 8000, quotationCount: 2, awardedQuotationId: null, awardReason: null, overridesRecommendation: false, awardedByName: null, awardedAt: null, poId: null, createdByName: "Sam", soleSourceJustification: null,
  lines: [{ id: "l1", itemName: "Tyre", quantity: 8, estimatedUnitCost: 1000 }],
  invitations: [{ supplierId: "s1", supplierName: "Alpha", status: "RESPONDED", emailed: true, declineReason: null, invitedAt: "2026-11-01", respondedAt: "2026-11-03" }, { supplierId: "s2", supplierName: "Beta", status: "INVITED", emailed: true, declineReason: null, invitedAt: "2026-11-01", respondedAt: null }] }
const row = (o: object) => ({ quotationId: "qa", supplierId: "s1", supplierName: "Alpha", status: "SUBMITTED", landedCost: 8200, totalInclVat: 9430, leadTimeDays: 5, warrantyMonths: 12, paymentTermsDays: 30, validUntil: null, complete: true, expired: false, rank: 1, priceScore: 40, leadTimeScore: 15, technicalScore: 12, performanceScore: 8, bbbeeScore: 8, totalScore: 88, estimatedSaving: null, flags: [], ...o })
const cmp = { recommendedQuotationId: "qa", lowestPriceQuotationId: "qb", estimatedTotal: 8000, rows: [row({}), row({ quotationId: "qb", supplierId: "s2", supplierName: "Beta", landedCost: 7900, totalInclVat: 9085, leadTimeDays: 14, rank: 2, totalScore: 79, flags: ["LONG_LEAD_TIME"] })] }
const quotes = [
  { id: "qa", supplierId: "s1", supplierName: "Alpha", status: "SUBMITTED", statusReason: null, quoteReference: "A1", receivedAt: "2026-11-03", landedCost: 8200, totalInclVat: 9430, leadTimeDays: 5, technicalScore: null, technicalNotes: null, evaluatedByName: null, capturedByName: "Pat", lines: [{ rfqLineId: "l1", noBid: false, qtyOffered: 8, netUnitPrice: 1025, lineTotal: 8200 }] },
  { id: "qb", supplierId: "s2", supplierName: "Beta", status: "SUBMITTED", statusReason: null, quoteReference: "B1", receivedAt: "2026-11-03", landedCost: 7900, totalInclVat: 9085, leadTimeDays: 14, technicalScore: null, technicalNotes: null, evaluatedByName: null, capturedByName: "Pat", lines: [{ rfqLineId: "l1", noBid: false, qtyOffered: 8, netUnitPrice: 987.5, lineTotal: 7900 }] }]
const route = () => get.mockImplementation((u: string) => u.endsWith("/comparison") ? ok(cmp) : u.endsWith("/quotations") ? ok(quotes) : u.endsWith("/events") ? ok([]) : u.endsWith("/q1") ? ok(rfq) : u.includes("suppliers") ? ok([{ id: "s1", name: "Alpha", status: "ACTIVE", bbbeeLevel: 1, onTimeRate: 95, city: "Durban", contactEmail: "a@x" }]) : ok([rfq]))
const open = async () => { fireEvent.click(await screen.findByText("RFQ-1")); await screen.findByText("Supplier invitations") }

describe("Tenders", () => {
  it("compares side by side and highlights the best value", async () => {
    route(); wrap(<TendersTab />); await open()
    fireEvent.click(screen.getByRole("button", { name: "Comparison" }))
    expect(await screen.findByText("★ Recommended")).toBeTruthy(); expect(screen.getByText("Lowest price")).toBeTruthy(); expect(screen.getByText("long lead time")).toBeTruthy()
    const lowest = screen.getAllByText(/^R\s?7[\s\u00a0,]?900[.,]00$/).find(e => e.tagName === "TD")
    expect(lowest?.getAttribute("style")).toContain("font-weight: 700")
  })
  it("shows who has not answered an invitation", async () => {
    route(); wrap(<TendersTab />); await open()
    expect(screen.getByText("1 answered (50%)")).toBeTruthy();
  })
  it("awarding needs a reason, and warns when not the recommended quotation", async () => {
    route(); wrap(<TendersTab />); await open()
    fireEvent.click(screen.getByRole("button", { name: "Evaluation & award" }))
    fireEvent.click(await screen.findByLabelText("Award to Beta"))
    expect(await screen.findByText(/overriding the recommendation/)).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Award tender" }))
    expect(await screen.findByText(/Record why/)).toBeTruthy(); expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Award reason"), { target: { value: "Cheaper and in stock" } })
    fireEvent.click(screen.getByRole("button", { name: "Award tender" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringContaining("/q1/award"), { quotationId: "qb", reason: "Cheaper and in stock" }))
  })
  it("saves a technical score only when it is a valid 0-100 whole number", async () => {
    route(); wrap(<TendersTab />); await open()
    fireEvent.click(screen.getByRole("button", { name: "Evaluation & award" }))
    fireEvent.change(await screen.findByLabelText("Technical score for Alpha"), { target: { value: "150" } })
    fireEvent.click(screen.getAllByRole("button", { name: "Save score" })[0])
    expect((await screen.findAllByText(/0 to 100/)).length).toBeGreaterThan(0); expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Technical score for Alpha"), { target: { value: "85" } })
    fireEvent.click(screen.getAllByRole("button", { name: "Save score" })[0])
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringContaining("/quotations/qa/evaluate"), { technicalScore: 85, notes: "" }))
  })
  it("a person who cannot manage tenders sees no award or score controls", async () => {
    perms = ["SCM_READ"]; route(); wrap(<TendersTab />); await open()
    fireEvent.click(screen.getByRole("button", { name: "Evaluation & award" }))
    await screen.findByText("Technical evaluation")
    expect(screen.queryByRole("button", { name: "Award tender" })).toBeNull(); expect(screen.queryByRole("button", { name: "Save score" })).toBeNull()
  })
  it("blocks the create wizard step by step and sends nothing until review", async () => {
    route(); wrap(<TendersTab />)
    fireEvent.click(await screen.findByText("New tender"))
    fireEvent.click(screen.getByRole("button", { name: "Continue" }))
    expect(await screen.findByText(/Give the tender a title/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Title"), { target: { value: "Tyres" } }); fireEvent.click(screen.getByRole("button", { name: "Continue" }))
    fireEvent.change(screen.getByLabelText("Item 1"), { target: { value: "Tyre" } }); fireEvent.click(screen.getByRole("button", { name: "Continue" }))
    fireEvent.click(screen.getByRole("button", { name: "Continue" }))
    expect(await screen.findByText(/Invite at least one supplier/)).toBeTruthy()
    fireEvent.click(await screen.findByLabelText("Invite Alpha")); fireEvent.click(screen.getByRole("button", { name: "Continue" }))
    fireEvent.change(screen.getByLabelText("Price weight"), { target: { value: "70" } }); fireEvent.click(screen.getByRole("button", { name: "Continue" }))
    expect(await screen.findByText(/add up to 120/)).toBeTruthy(); expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Price weight"), { target: { value: "50" } }); fireEvent.click(screen.getByRole("button", { name: "Continue" }))
    post.mockResolvedValueOnce({ data: { data: { id: "new1" } } })
    fireEvent.click(screen.getByRole("button", { name: "Save draft" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(expect.stringMatching(/\/rfqs$/), expect.objectContaining({ title: "Tyres" })))
  })
  it("captures a quotation with live totals and refuses a missing price", async () => {
    route(); wrap(<TendersTab />); await open()
    fireEvent.click(screen.getByRole("button", { name: "Quotations" }))
    fireEvent.click((await screen.findAllByRole("button", { name: "Capture quotation" }))[0])
    fireEvent.change(await screen.findByLabelText("Supplier"), { target: { value: "s1" } }); fireEvent.change(screen.getByLabelText("Lead time"), { target: { value: "5" } })
    fireEvent.click(screen.getByRole("button", { name: "Save quotation" }))
    expect(await screen.findByText(/enter the unit price/)).toBeTruthy(); expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Unit price 1"), { target: { value: "100" } })
    expect(screen.getAllByText(/^R\s?800[.,]00$/, { selector: "strong" })[0]).toBeTruthy()
  })
})

const bud = (o = {}) => ({ id: "b1", name: "Workshop", scopeType: "DEPARTMENT", scopeValue: "Workshop", periodStart: "2026-01-01", periodEnd: "2026-12-31", amount: 1000, committed: 900, actual: 300, used: 1200, available: -200, utilisationPct: 120, state: "EXCEEDED", controlMode: "HARD_STOP", status: "ACTIVE", ...o })
describe("Budgets", () => {
  it("shows the server's message when budgets overlap", async () => {
    get.mockImplementation(() => ok([])); post.mockImplementation(() => Promise.reject(Object.assign(new Error("x"), { response: { data: { message: "A budget for Workshop already covers part of this period" } } })))
    wrap(<BudgetsTab />); fireEvent.click(await screen.findByText("New budget"))
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "W" } }); fireEvent.change(screen.getByLabelText("Scope value"), { target: { value: "Workshop" } })
    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2026-01-01" } }); fireEvent.change(screen.getByLabelText("To"), { target: { value: "2026-12-31" } }); fireEvent.change(screen.getByLabelText("Amount"), { target: { value: "100000" } })
    fireEvent.click(screen.getByText("Create budget"))
    expect(await screen.findByText(/already covers part of this period/)).toBeTruthy()
  })
  it("shows an exceeded budget with its negative variance, and what is committed", async () => {
    get.mockImplementation((u: string) => u.endsWith("/commitments") ? ok([{ purchaseOrderId: "p1", orderNumber: "PO-9", supplierName: "Alpha", poStatus: "APPROVED", committed: 900, actual: 1200, used: 1200, spendDate: "2026-03-01" }]) : ok([bud()]))
    wrap(<BudgetsTab />)
    expect(await screen.findByText(/120% — exceeded/)).toBeTruthy(); expect(screen.getAllByText(/R\s?[-−]\s?200/, { selector: "td" }).length).toBeGreaterThan(0)
    fireEvent.click(screen.getByText("Workshop")); expect(await screen.findByText("PO-9")).toBeTruthy()
  })
  it("edits the amount and refuses zero", async () => {
    get.mockImplementation(() => ok([bud()])); wrap(<BudgetsTab />)
    fireEvent.click(await screen.findByRole("button", { name: "Edit" }))
    fireEvent.change(screen.getByLabelText("New amount"), { target: { value: "0" } }); fireEvent.click(screen.getByText("Save changes"))
    expect(await screen.findByText(/above zero/)).toBeTruthy(); expect(put).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("New amount"), { target: { value: "2000" } }); fireEvent.click(screen.getByText("Save changes"))
    await waitFor(() => expect(put).toHaveBeenCalledWith(expect.stringContaining("/budgets/b1"), { amount: 2000, controlMode: "HARD_STOP" }))
  })
  it("runs a what-if check", async () => {
    get.mockImplementation((u: string) => u.endsWith("/check") ? ok({ hardStop: true, hasWarnings: false, findings: [{ budgetName: "Workshop", used: 1200, amount: 1000, requested: 500, availableAfter: -700, state: "EXCEEDED", message: "Over budget", controlMode: "HARD_STOP" }] }) : ok([bud()]))
    wrap(<BudgetsTab />); fireEvent.click(await screen.findByRole("button", { name: "What-if check" }))
    fireEvent.click(screen.getByRole("button", { name: "Check" })); expect(await screen.findByText(/Enter the amount to test/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Department"), { target: { value: "Workshop" } }); fireEvent.change(screen.getByLabelText("Amount to test"), { target: { value: "500" } })
    fireEvent.click(screen.getByRole("button", { name: "Check" })); expect(await screen.findByText(/would be blocked/)).toBeTruthy()
  })
  it("hides new and edit without manage rights", async () => {
    perms = ["SCM_BUDGET_READ"]; get.mockImplementation(() => ok([bud()])); wrap(<BudgetsTab />); await screen.findByText(/120%/)
    expect(screen.queryByText("New budget")).toBeNull(); expect(screen.queryByRole("button", { name: "Edit" })).toBeNull()
  })
})
