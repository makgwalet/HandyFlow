import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import NewCaseDialog from "./NewCaseDialog"
import CasesTab from "./CasesTab"
import DebtCollectionDashboard from "./DebtCollectionDashboard"

const perms = vi.hoisted(() => ({ held: new Set<string>() }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.held.has(p) }))
vi.mock("../../hooks/useAuth", () => ({ useAuth: () => ({ user: { userId: "u1" } }) }))
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
afterEach(cleanup)
beforeEach(() => { Object.values(api).forEach(f => f.mockReset()); perms.held = new Set(["DEBTCOLLECTION_READ", "DEBTCOLLECTION_MANAGE"]) })

const acme = { id: "c1", name: "Acme Traders", email: "a@acme.test", phone: "082" }
const inv = (o: any) => ({ id: "i1", invoiceNumber: "INV-1", status: "OVERDUE", customerId: "c1", total: 1000, amountPaid: 0, creditedAmount: 0, balanceDue: 1000, dueDate: "2026-08-01", walkinClientName: null, walkinClientEmail: null, walkinClientPhone: null, ...o })
const collectors = [{ userId: "u9", userName: "Thandi", active: true, openCases: 3, utilisationPercent: 30, awayUntil: null }]

function route(map: Record<string, any>) {
  api.get.mockImplementation(async (url: string) => {
    for (const k of Object.keys(map)) if (url.includes(k)) return { data: { data: map[k] } }
    return { data: { data: [] } }
  })
}

describe("NewCaseDialog", () => {
  it("opens a case for a searched customer without typing any ID", async () => {
    route({ "crm/customers": { content: [acme] }, "outstanding-invoices": [{ id: "i1", invoiceNumber: "INV-1", dueDate: "2026-08-01", total: 1000, amountPaid: 0, outstanding: 1000 }], "allocation/collectors": collectors })
    api.post.mockResolvedValue({ data: { data: {} } })
    const onOpened = vi.fn(), onClose = vi.fn()
    wrap(<NewCaseDialog onClose={onClose} onOpened={onOpened} />)
    expect((screen.getByText("Open case", { selector: "button" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/^Customer/), { target: { value: "Acme" } })
    fireEvent.click(await screen.findByText("Acme Traders"))
    expect(await screen.findByText("INV-1")).toBeTruthy()
    fireEvent.change(await screen.findByLabelText("Assigned to"), { target: { value: "u9" } })
    fireEvent.click(screen.getByText("Open case", { selector: "button" }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/debtcollection/cases/open-for-customer", expect.objectContaining({ customerId: "c1", assignedToUserId: "u9", assignedToUserName: "Thandi" })))
    await waitFor(() => expect(onOpened).toHaveBeenCalled())
  })

  it("opens a walk-in case from ticked invoices and pre-fills the debtor", async () => {
    route({ "invoicing/invoices": { content: [inv({ id: "w1", invoiceNumber: "INV-9", customerId: null, walkinClientName: "Mr Dube", walkinClientEmail: "d@x.test" }), inv({ id: "paid", invoiceNumber: "INV-2", status: "PAID", balanceDue: 0 })] } })
    api.post.mockResolvedValue({ data: { data: {} } })
    wrap(<NewCaseDialog onClose={() => {}} onOpened={() => {}} />)
    fireEvent.click(screen.getByText("Pick specific invoices"))
    expect(await screen.findByText("INV-9")).toBeTruthy()
    expect(screen.queryByText("INV-2")).toBeNull()
    fireEvent.click(screen.getByLabelText("Invoice INV-9"))
    expect((screen.getByLabelText("Debtor name") as HTMLInputElement).value).toBe("Mr Dube")
    fireEvent.click(screen.getByText("Open case", { selector: "button" }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/debtcollection/cases", expect.objectContaining({ customerId: null, debtorName: "Mr Dube", debtorEmail: "d@x.test", invoiceIds: ["w1"] })))
  })

  it("shows the server's reason when the case is refused", async () => {
    route({ "invoicing/invoices": { content: [inv({})] } })
    api.post.mockRejectedValue({ response: { data: { message: "Invoice already in an open case" } } })
    wrap(<NewCaseDialog onClose={() => {}} onOpened={() => {}} />)
    fireEvent.click(screen.getByText("Pick specific invoices"))
    fireEvent.click(await screen.findByLabelText("Invoice INV-1"))
    fireEvent.change(screen.getByLabelText("Debtor name"), { target: { value: "Acme" } })
    fireEvent.click(screen.getByText("Open case", { selector: "button" }))
    expect(await screen.findByText("Invoice already in an open case")).toBeTruthy()
  })
})

describe("CasesTab queues", () => {
  const c = (o: any) => ({ id: "x", caseNumber: "DC-1", debtorName: "Alpha", status: "OPEN", totalOutstanding: 100, assignedToUserId: null, assignedToUserName: null, nextActionDate: null, openedDate: "2026-09-01", linkedInvoiceIds: [], ...o })
  it("narrows to my cases and to a search", async () => {
    route({ "/cases": { content: [c({ id: "1", caseNumber: "DC-1", debtorName: "Alpha", assignedToUserId: "u1" }), c({ id: "2", caseNumber: "DC-2", debtorName: "Bravo" })] } })
    wrap(<CasesTab />)
    expect(await screen.findByText("Bravo")).toBeTruthy()
    fireEvent.click(screen.getByText(/My cases/))
    expect(screen.queryByText("Bravo")).toBeNull()
    expect(screen.getByText("Alpha")).toBeTruthy()
    fireEvent.click(screen.getByText(/All cases/))
    fireEvent.change(screen.getByLabelText("Search cases"), { target: { value: "brav" } })
    expect(screen.queryByText("Alpha")).toBeNull()
    expect(screen.getByText("Bravo")).toBeTruthy()
  })
})

describe("Dashboard", () => {
  it("shows the headline figures, aging and top debtors from the dashboard endpoints", async () => {
    const stats = { casesOpened: 6, casesClosed: 1, casesClosedSuccessfully: 1, successRate: 100 }
    route({
      "dashboard/summary": { asOf: "2026-10-14", totalOutstanding: 250000, activeCases: 12, casesByStatus: {}, current: stats, previous: { ...stats, casesOpened: 4 }, openWorkItems: 5, overdueWorkItems: 2, writtenOffToDate: 0 },
      "dashboard/aging": { asOf: "2026-10-14", totalOutstanding: 250000, buckets: [{ label: "0-30", amount: 100000, invoiceCount: 3, caseCount: 2, share: 40 }, { label: "31-60", amount: 150000, invoiceCount: 4, caseCount: 3, share: 60 }] },
      "dashboard/top-debtors": [{ caseId: "1", caseNumber: "DC-7", debtorName: "Big Debtor", status: "OPEN", outstanding: 90000, maxDaysOverdue: 75, assignedToUserName: "Thandi", nextActionDate: null }],
      "dashboard/recovery": { netRecovered: 42000, feesCharged: 0 },
    })
    wrap(<DebtCollectionDashboard onNavigate={() => {}} />)
    expect(await screen.findByText("Big Debtor")).toBeTruthy()
    expect(screen.getByText("75 days overdue")).toBeTruthy()
    expect(await screen.findByText("+50% vs last period")).toBeTruthy()
    expect(screen.getByText(/0-30 · 40%/)).toBeTruthy()
    expect((await screen.findAllByText(/R\s*250\s?000/)).length).toBeGreaterThan(0)
  })
})
