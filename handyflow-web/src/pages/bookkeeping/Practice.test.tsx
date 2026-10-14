import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter } from "react-router-dom"

// Bookkeeping part 2: accounts, journals, time, agreements, invoices, client portal.
let perms = new Set<string>()
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const get = vi.fn(), post = vi.fn(), del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), put: vi.fn(), delete: (...a: any[]) => del(...a) } }))
import AccountsTab from "./AccountsTab"
import JournalsTab from "./JournalsTab"
import TimeTab from "./TimeTab"
import AgreementsTab from "./AgreementsTab"
import InvoicesTab from "./InvoicesTab"
import PortalUsersTab from "./PortalUsersTab"

const ok = (data: any) => Promise.resolve({ data: { data } })
const clients = [{ id: "c1", clientCode: "BK-1", tradingName: "Acme Traders", status: "ACTIVE" }]
const accounts = [{ id: "a1", accountCode: "1000", accountName: "Bank", accountType: "ASSET", system: true, openingBalance: 0 }, { id: "a2", accountCode: "4000", accountName: "Sales", accountType: "INCOME", system: true, openingBalance: 0 }]
const journals = [{ id: "j1", entryNumber: "J-1", entryDate: "2026-10-01", description: "Draft rent", reference: null, entryType: "MANUAL", status: "DRAFT", totalDebit: 500, totalCredit: 500, lines: [{ id: "l1", accountId: "a1", description: null, debitAmount: 500, creditAmount: 0 }] }]
const time = [{ id: "t1", practitionerName: "Sam", entryDate: "2026-10-02", activityType: "VAT", description: "VAT201", hours: 2, hourlyRate: 850, lineTotal: 1700, billable: true, status: "UNBILLED", invoiceId: null }]
const agreements = [{ id: "g1", billingType: "RETAINER", monthlyFee: 1500, hourlyRate: null, startDate: "2026-01-01", endDate: null, status: "ACTIVE" }]
const invoices = [{ id: "i0", invoiceNumber: "BKI-1", periodStart: "2026-08-01", periodEnd: "2026-08-31", issueDate: "2026-09-01", dueDate: "2026-10-01", total: 1500, amountPaid: 0, balance: 1500, status: "DRAFT" },
  { id: "i1", invoiceNumber: "BKI-2", periodStart: "2026-09-01", periodEnd: "2026-09-30", issueDate: "2026-10-01", dueDate: "2026-10-31", total: 1500, amountPaid: 500, balance: 1000, status: "PARTIAL" }]
const grants = [{ id: "p1", inviteEmail: "jo@acme.test", status: "ACTIVE", acceptedAt: "2026-09-01T08:00:00Z", invitedAt: "2026-08-30T08:00:00Z" }]

const show = (ui: React.ReactElement) => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><MemoryRouter initialEntries={["/bookkeeping/x?client=c1"]}>{ui}</MemoryRouter></QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["BOOKKEEPING_READ", "BOOKKEEPING_MANAGE", "BOOKKEEPING_ADMIN"])
  get.mockReset(); post.mockReset(); del.mockReset()
  get.mockImplementation((url: string) =>
    url.includes("/journal-entries") ? ok({ content: journals })
    : url.includes("/time-entries/unbilled") ? ok(time)
    : url.includes("/time-entries") ? ok({ content: time })
    : url.includes("/service-agreements") ? ok({ content: agreements })
    : url.includes("/invoices") ? ok({ content: invoices })
    : url.includes("/portal-users") ? ok(grants)
    : url.includes("/accounts") ? ok(accounts)
    : url.includes("/clients") ? ok({ content: clients })
    : ok([]))
  post.mockResolvedValue({ data: { data: {} } })
})
afterEach(cleanup)

describe("AccountsTab", () => {
  it("adds a custom account", async () => {
    show(<AccountsTab />)
    await screen.findByText("Sales")
    fireEvent.change(screen.getByLabelText("Account code"), { target: { value: "6500" } })
    fireEvent.change(screen.getByLabelText("Account name"), { target: { value: "Petty cash" } })
    fireEvent.click(screen.getByText("Add"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/clients/c1/accounts", expect.objectContaining({ accountCode: "6500", accountName: "Petty cash", accountType: "EXPENSE" })))
  })
})

describe("JournalsTab", () => {
  it("only saves a balanced journal", async () => {
    show(<JournalsTab />)
    await screen.findByText("Draft rent")
    const save = screen.getByText("Save as draft") as HTMLButtonElement
    fireEvent.change(screen.getByLabelText("Line 1 account"), { target: { value: "a1" } })
    fireEvent.change(screen.getByLabelText("Line 1 debit"), { target: { value: "300" } })
    fireEvent.change(screen.getByLabelText("Line 2 account"), { target: { value: "a2" } })
    fireEvent.change(screen.getByLabelText("Line 2 credit"), { target: { value: "250" } })
    expect(screen.getByText(/differ by/)).toBeTruthy()
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Line 2 credit"), { target: { value: "300" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/journal-entries", expect.objectContaining({
      clientId: "c1", entryType: "MANUAL", lines: [expect.objectContaining({ accountId: "a1", debitAmount: 300, creditAmount: 0 }), expect.objectContaining({ accountId: "a2", debitAmount: 0, creditAmount: 300 })] })))
  })
  it("posts a draft", async () => {
    show(<JournalsTab />)
    await screen.findByText("Draft rent")
    fireEvent.click(screen.getByText("Post"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/journal-entries/j1/post"))
  })
  it("read-only users cannot post or create", async () => {
    perms = new Set(["BOOKKEEPING_READ"])
    show(<JournalsTab />)
    await screen.findByText("Draft rent")
    expect(screen.queryByText("Post")).toBeNull()
    expect(screen.queryByText("Save as draft")).toBeNull()
  })
})

describe("TimeTab", () => {
  it("shows what is still to bill and logs time", async () => {
    show(<TimeTab />)
    await screen.findByText(/VAT201/)
    expect(screen.getByText(/Still to bill/).textContent).toMatch(/1[\s ,]?700/)
    fireEvent.change(screen.getByLabelText("Hours"), { target: { value: "1.5" } })
    fireEvent.change(screen.getByLabelText("Hourly rate"), { target: { value: "850" } })
    fireEvent.click(screen.getByText("Log"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/time-entries", expect.objectContaining({ clientId: "c1", hours: 1.5, hourlyRate: 850, billable: true, activityType: "BOOKKEEPING" })))
  })
  it("writes an entry off", async () => {
    show(<TimeTab />)
    await screen.findByText(/VAT201/)
    fireEvent.click(screen.getByText("Write off"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/time-entries/t1/write-off"))
  })
})

describe("AgreementsTab", () => {
  it("asks for the fee that matches the type", async () => {
    show(<AgreementsTab />)
    await screen.findByText("Monthly retainer", { selector: "td" })
    const add = screen.getByText("Add") as HTMLButtonElement
    expect(add.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Billing type"), { target: { value: "TIME_AND_MATERIALS" } })
    fireEvent.change(screen.getByLabelText("Hourly rate"), { target: { value: "900" } })
    expect(add.disabled).toBe(false)
    fireEvent.click(add)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/service-agreements", expect.objectContaining({ clientId: "c1", billingType: "TIME_AND_MATERIALS", monthlyFee: null, hourlyRate: 900 })))
  })
})

describe("InvoicesTab", () => {
  it("generates for the chosen period and sends a draft", async () => {
    show(<InvoicesTab />)
    await screen.findByText("BKI-1")
    fireEvent.change(screen.getByLabelText("Period from"), { target: { value: "2026-09-01" } })
    fireEvent.change(screen.getByLabelText("Period to"), { target: { value: "2026-09-30" } })
    fireEvent.click(screen.getByText("Generate"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/clients/c1/invoices/generate", { periodStart: "2026-09-01", periodEnd: "2026-09-30" }))
    fireEvent.click(screen.getByText("Send"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/invoices/i0/send"))
  })
  it("will not take more than is owing", async () => {
    show(<InvoicesTab />)
    await screen.findByText("BKI-2")
    fireEvent.click(screen.getByText("Record payment"))
    fireEvent.change(screen.getByLabelText("Amount received"), { target: { value: "1200" } })
    expect(screen.getByText(/more than the/)).toBeTruthy()
    expect((screen.getByText("Save payment") as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Amount received"), { target: { value: "1000" } })
    fireEvent.click(screen.getByText("Save payment"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/invoices/i1/payments", { amount: 1000 }))
  })
  it("only an administrator can generate, send or take payment", async () => {
    perms = new Set(["BOOKKEEPING_READ", "BOOKKEEPING_MANAGE"])
    show(<InvoicesTab />)
    await screen.findByText("BKI-1")
    expect(screen.queryByText("Generate")).toBeNull()
    expect(screen.queryByText("Send")).toBeNull()
    expect(screen.queryByText("Record payment")).toBeNull()
  })
})

describe("PortalUsersTab", () => {
  it("invites only a plausible email address", async () => {
    show(<PortalUsersTab />)
    await screen.findByText("jo@acme.test")
    const send = screen.getByText("Send invite") as HTMLButtonElement
    fireEvent.change(screen.getByLabelText("Email address"), { target: { value: "nobody" } })
    expect(send.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Email address"), { target: { value: "new@acme.test" } })
    fireEvent.click(send)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/clients/c1/portal-users", { clientId: "c1", email: "new@acme.test" }))
  })
  it("removes access after confirming", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true)
    show(<PortalUsersTab />)
    await screen.findByText("jo@acme.test")
    fireEvent.click(screen.getByText("Remove access"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/bookkeeping/clients/c1/portal-users/p1/revoke"))
  })
})
