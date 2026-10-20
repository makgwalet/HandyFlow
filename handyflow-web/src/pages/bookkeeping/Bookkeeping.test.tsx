import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter, useLocation } from "react-router-dom"

// Bookkeeping screens (finance F19 front end).
let perms = new Set<string>()
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), put: (...a: any[]) => put(...a), delete: (...a: any[]) => del(...a) } }))
import WorkQueueTab from "./WorkQueueTab"
import ClientsTab from "./ClientsTab"
import BankLinesTab from "./BankLinesTab"
import BankRulesTab from "./BankRulesTab"
import QueriesTab from "./QueriesTab"
import HandoverTab from "./HandoverTab"
import PeriodsTab from "./PeriodsTab"

const ok = (data: any) => Promise.resolve({ data: { data } })
const clients = [{ id: "c1", clientCode: "BK-001", tradingName: "Acme Traders", status: "ACTIVE", vatNumber: "4123", contactName: "Jo", contactEmail: "jo@acme.test" },
  { id: "c2", clientCode: "BK-002", tradingName: "Old Co", status: "INACTIVE" }]
const banks = [{ id: "b1", clientId: "c1", accountId: "led1", bankName: "FNB", accountName: "Cheque", accountNumber: "6200", accountType: "CURRENT", currentBalance: 1000, active: true }]
const accounts = [{ id: "a1", accountCode: "6100", accountName: "Fuel", accountType: "EXPENSE" }, { id: "led1", accountCode: "1000", accountName: "Bank", accountType: "ASSET" }]
const txs = [
  { id: "t1", bankAccountId: "b1", transactionDate: "2026-10-01", description: "ENGEN SANDTON", reference: "R1", amount: 250, transactionType: "DEBIT", reconciled: false },
  { id: "t2", bankAccountId: "b1", transactionDate: "2026-10-02", description: "SALE INV 7", reference: null, amount: 900, transactionType: "CREDIT", reconciled: true },
]
const suggestions = [
  { transactionId: "t1", date: "2026-10-01", description: "ENGEN SANDTON", amount: 250, type: "DEBIT", ruleId: "r1", ruleName: "Fuel", contraAccountId: "a1" },
  { transactionId: "t3", date: "2026-10-03", description: "ENGEN WEST", amount: 80, type: "DEBIT", ruleId: "r1", ruleName: "Fuel", contraAccountId: "a1" },
]
const rule = { id: "r1", name: "Fuel", bankAccountId: null, direction: "OUT", text: "engen", field: "EITHER", min: null, max: null, priority: 10, active: true, contraAccountId: "a1" }
const query = { id: "q1", kind: "MISSING_DOCUMENT", transactionId: null, question: "Slip for R250", status: "OPEN", answer: null, documentRef: null, askedAt: "2026-10-01T08:00:00Z", answeredAt: null, daysOpen: 3 }
let work = [{ clientId: "c1", clientName: "Acme Traders", unreconciled: 4, oldestUnreconciled: "2026-08-01", openQueries: 1, missingDocuments: 1, draftJournals: 0 },
  { clientId: "c2", clientName: "Old Co", unreconciled: 0, oldestUnreconciled: null, openQueries: 0, missingDocuments: 0, draftJournals: 0 }]
let handovers: any[] = []

function Probe() { const l = useLocation(); return <div data-testid="loc">{l.pathname}{l.search}</div> }
const show = (ui: React.ReactElement, at = "/bookkeeping/x?client=c1") => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter initialEntries={[at]}>{ui}<Probe /></MemoryRouter></QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["BOOKKEEPING_READ", "BOOKKEEPING_MANAGE", "BOOKKEEPING_ADMIN"])
  get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset(); handovers = []
  work = [{ ...work[0] }, { ...work[1] }]
  get.mockImplementation((url: string) =>
    url.includes("/work-queue") ? ok(work)
    : url.includes("/suggestions") ? ok(suggestions)
    : url.includes("/match-candidates") ? ok([{ journalLineId: "jl1", entryNumber: "J-9", entryDate: "2026-10-01", description: "Fuel", amount: 250, exactMatch: true }])
    : url.includes("/bank-transactions") ? ok({ content: txs, last: true })
    : url.includes("/bank-accounts") ? ok({ content: banks })
    : url.includes("/bank-rules") ? ok([rule])
    : url.includes("/queries") ? ok([query])
    : url.includes("/handovers") ? ok(handovers)
    : url.includes("/periods") ? ok({ content: [{ id: "p1", periodYear: 2026, periodMonth: 9, status: "OPEN" }, { id: "p0", periodYear: 2026, periodMonth: 8, status: "CLOSED" }] })
    : url.includes("/accounts") ? ok(accounts)
    : url.includes("/clients") ? ok({ content: clients })
    : ok([]))
  post.mockResolvedValue({ data: { data: [] } }); put.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

describe("WorkQueueTab", () => {
  it("lists clients with something outstanding and hides finished ones", async () => {
    show(<WorkQueueTab />)
    await screen.findByText("Acme Traders")
    expect(screen.queryByText("Old Co")).toBeNull()
    fireEvent.click(screen.getByLabelText("Show clients with nothing outstanding"))
    expect(await screen.findByText("Old Co")).toBeTruthy()
  })
  it("opens the client's bank lines from the count", async () => {
    show(<WorkQueueTab />)
    await screen.findByText("Acme Traders")
    fireEvent.click(screen.getByText("4"))
    expect(screen.getByTestId("loc").textContent).toBe("/bookkeeping/bank?client=c1")
  })
})

describe("ClientsTab", () => {
  it("adds a client with blank optional fields sent as null", async () => {
    show(<ClientsTab />)
    await screen.findByText("Acme Traders")
    const add = screen.getByText("Add client") as HTMLButtonElement
    expect(add.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Trading name"), { target: { value: "  New Co " } })
    fireEvent.click(add)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/clients", expect.objectContaining({ tradingName: "New Co", vatNumber: null, address: null })))
  })
  it("switches a client off", async () => {
    show(<ClientsTab />)
    await screen.findByText("Acme Traders")
    fireEvent.click(screen.getAllByText("Switch off")[0])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/clients/c1/deactivate"))
  })
  it("read-only users see no editing controls", async () => {
    perms = new Set(["BOOKKEEPING_READ"])
    show(<ClientsTab />)
    await screen.findByText("Acme Traders")
    expect(screen.queryByText("Add client")).toBeNull()
    expect(screen.queryByText("Edit")).toBeNull()
  })
})

describe("BankLinesTab", () => {
  it("shows only lines not yet categorised by default", async () => {
    show(<BankLinesTab />)
    await screen.findByText("Book to account")
    expect(screen.queryByText("SALE INV 7")).toBeNull()
    fireEvent.click(screen.getByLabelText("Only lines not yet categorised"))
    expect(await screen.findByText("SALE INV 7")).toBeTruthy()
  })
  it("categorises the ticked rule suggestions only", async () => {
    post.mockResolvedValueOnce({ data: { data: { done: 1 } } })
    show(<BankLinesTab />)
    await screen.findByText(/Your rules recognise 2 lines/)
    fireEvent.click(screen.getByLabelText("Include ENGEN WEST"))
    fireEvent.click(screen.getByText("Categorise 1 line"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/clients/c1/bank-transactions/categorise", { items: [{ transactionId: "t1", contraAccountId: "a1" }] }))
    await screen.findByText("1 line categorised.")
  })
  it("never sends a line that no rule recognised", async () => {
    const plain = { transactionId: "t9", date: "2026-10-04", description: "UNKNOWN PAYEE", amount: 10, type: "DEBIT", ruleId: null, ruleName: null, contraAccountId: null }
    get.mockImplementation(((orig: any) => (url: string) => url.includes("/suggestions") ? ok([...suggestions, plain]) : orig(url))(get.getMockImplementation()))
    post.mockResolvedValueOnce({ data: { data: { done: 2 } } })
    show(<BankLinesTab />)
    await screen.findByText(/Your rules recognise 2 lines/)
    fireEvent.click(screen.getByText("Categorise 2 lines"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/clients/c1/bank-transactions/categorise",
      { items: [{ transactionId: "t1", contraAccountId: "a1" }, { transactionId: "t3", contraAccountId: "a1" }] }))
  })
  it("reloads the list when a batch fails so booked lines are not sent again", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "Line 1 of 2 could not be categorised, so none were: period locked" } } })
    show(<BankLinesTab />)
    await screen.findByText(/Your rules recognise 2 lines/)
    const before = get.mock.calls.filter(c => String(c[0]).includes("/suggestions")).length
    fireEvent.click(screen.getByText("Categorise 2 lines"))
    await screen.findByRole("alert")
    await waitFor(() => expect(get.mock.calls.filter(c => String(c[0]).includes("/suggestions")).length).toBeGreaterThan(before))
  })
  it("books one line to an account", async () => {
    show(<BankLinesTab />)
    await screen.findByText("Book to account")
    fireEvent.click(screen.getByText("Book to account"))
    fireEvent.change(screen.getByLabelText("Account to book to"), { target: { value: "a1" } })
    fireEvent.click(screen.getByText("Book it"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/clients/c1/bank-transactions/t1/reconcile-new", { contraAccountId: "a1", description: "ENGEN SANDTON" }))
  })
  it("matches a line to a journal line", async () => {
    show(<BankLinesTab />)
    await screen.findByText("Book to account")
    fireEvent.click(screen.getByText("Match"))
    await screen.findByText(/J-9/)
    fireEvent.click(screen.getAllByText("Match").pop()!)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/clients/c1/bank-transactions/t1/reconcile", { journalLineId: "jl1" }))
  })
  it("read-only users cannot categorise, book or import", async () => {
    perms = new Set(["BOOKKEEPING_READ"])
    show(<BankLinesTab />)
    await screen.findByText("ENGEN SANDTON")
    expect(screen.queryByText("Book to account")).toBeNull()
    expect(screen.queryByText(/Your rules recognise/)).toBeNull()
    expect(screen.queryByLabelText("Import CSV")).toBeNull()
  })
  it("asks for a client first", async () => {
    show(<BankLinesTab />, "/bookkeeping/bank")
    expect(await screen.findByText("Choose a client to work on.")).toBeTruthy()
  })
})

describe("BankRulesTab", () => {
  it("explains why a rule cannot be added yet, then adds it", async () => {
    show(<BankRulesTab />)
    await screen.findByText("Fuel")
    const add = screen.getByText("Add rule") as HTMLButtonElement
    fireEvent.change(screen.getByLabelText("Rule name"), { target: { value: "Rent" } })
    fireEvent.change(screen.getByLabelText("Book to account"), { target: { value: "a1" } })
    expect(screen.getByText(/would match every line/)).toBeTruthy()
    expect(add.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Words to look for"), { target: { value: "landlord" } })
    expect(add.disabled).toBe(false)
    fireEvent.click(add)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/clients/c1/bank-rules",
      expect.objectContaining({ name: "Rent", text: "landlord", direction: "ANY", contraAccountId: "a1", bankAccountId: null, min: null })))
  })
  it("switches a rule off using the query-string the server expects", async () => {
    show(<BankRulesTab />)
    await screen.findByText("Fuel")
    fireEvent.click(screen.getByText("Switch off"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/clients/c1/bank-rules/r1/active?active=false"))
  })
})

describe("QueriesTab", () => {
  it("shows open queries and asks a new one", async () => {
    show(<QueriesTab />)
    await screen.findByText("Slip for R250")
    fireEvent.change(screen.getByLabelText("What do you need"), { target: { value: "hi" } })
    expect((screen.getByText("Ask") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("What do you need"), { target: { value: "Statement for August" } })
    fireEvent.click(screen.getByText("Ask"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/clients/c1/queries", { kind: "QUESTION", transactionId: null, question: "Statement for August" }))
  })
  it("records an answer", async () => {
    show(<QueriesTab />)
    await screen.findByText("Slip for R250")
    fireEvent.click(screen.getByText("Record answer"))
    expect((screen.getByText("Save answer") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Where the document is"), { target: { value: "Dropbox/Oct" } })
    fireEvent.click(screen.getByText("Save answer"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/clients/c1/queries/q1/answer", { answer: null, documentRef: "Dropbox/Oct" }))
  })
})

describe("HandoverTab", () => {
  it("says what stands in the way and sends once clear", async () => {
    show(<HandoverTab />)
    await screen.findByText(/Before handing over: 4 not categorised/)
    fireEvent.change(screen.getByLabelText("Handover date"), { target: { value: "2026-09-30" } })
    expect(screen.getByText("4 bank lines are not categorised yet.")).toBeTruthy()
    expect((screen.getByText("Send") as HTMLButtonElement).disabled).toBe(true)
    work[0].unreconciled = 0
    cleanup()
    show(<HandoverTab />)
    await screen.findByText(/Before handing over/)
    fireEvent.change(screen.getByLabelText("Handover date"), { target: { value: "2026-09-30" } })
    fireEvent.click(screen.getByText("Send"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/clients/c1/handovers", { periodEnd: "2026-09-30", note: null }))
  })
  it("a returned handover needs a reason", async () => {
    handovers = [{ id: "h1", periodEnd: "2026-09-30", status: "SENT", openQueries: 0, note: null, sentAt: "2026-10-02T08:00:00Z", respondedAt: null, responseNote: null, warnings: [] }]
    show(<HandoverTab />)
    await screen.findByText("With the accountant")
    fireEvent.click(screen.getByText("Return"))
    fireEvent.change(screen.getByLabelText("Reply note"), { target: { value: "short" } })
    expect((screen.getByText("Return handover") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reply note"), { target: { value: "July bank lines are missing" } })
    fireEvent.click(screen.getByText("Return handover"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/handovers/h1/return", { note: "July bank lines are missing" }))
  })
  it("only an administrator can accept or return", async () => {
    perms = new Set(["BOOKKEEPING_READ", "BOOKKEEPING_MANAGE"])
    handovers = [{ id: "h1", periodEnd: "2026-09-30", status: "SENT", openQueries: 0, note: null, sentAt: "2026-10-02T08:00:00Z", respondedAt: null, responseNote: null, warnings: [] }]
    show(<HandoverTab />)
    await screen.findByText("With the accountant")
    expect(screen.queryByText("Accept")).toBeNull()
  })
})

describe("PeriodsTab", () => {
  it("closes an open month and offers reopen only on a closed one", async () => {
    show(<PeriodsTab />)
    await screen.findByText(/September 2026/)
    fireEvent.click(screen.getByText("Close month"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/periods/p1/close"))
    expect(screen.getAllByText("Reopen")).toHaveLength(1)
  })
})
