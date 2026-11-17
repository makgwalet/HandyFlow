import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import StatementsTab from "./StatementsTab"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })
let statement: any
function mount() { render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><StatementsTab /></QueryClientProvider>) }

describe("StatementsTab", () => {
  beforeEach(() => {
    get.mockReset()
    statement = { accountId: "a1", from: "x", to: "y", total: 1520, invoiced: 1350, notInvoiced: 170, lines: [
      { date: "2026-11-06", kind: "STAY", sourceId: "s1", vehicleRegistration: "GP 1", description: "Overnight parking, 3 nights at 450", amount: 1350, invoiceState: "INVOICED", invoiceNumber: "INV-1" },
      { date: "2026-11-07", kind: "TAB", sourceId: "t1", vehicleRegistration: "CA 9", description: "2 x Shower", amount: 170, invoiceState: null, invoiceNumber: null }] }
    get.mockImplementation((u: string) => {
      if (u.includes("account-statements")) return ok(statement)
      if (u.includes("customer-accounts")) return ok([{ id: "a1", customerName: "Acme Haulage", creditHold: false }])
      return ok([])
    })
  })
  afterEach(cleanup)

  it("asks for an account before showing anything", async () => {
    mount()
    fireEvent.click(await screen.findByRole("button", { name: "Show statement" }))
    expect(await screen.findByRole("alert")).toBeTruthy()
    expect(get).not.toHaveBeenCalledWith(expect.stringContaining("account-statements"))
  })

  it("shows the lines, how far each got in Invoicing, and the totals", async () => {
    mount()
    const select = await screen.findByLabelText("Customer account")
    await screen.findByText("Acme Haulage")
    fireEvent.change(select, { target: { value: "a1" } })
    fireEvent.click(screen.getByRole("button", { name: "Show statement" }))
    expect(await screen.findByText("Draft invoice INV-1")).toBeTruthy()
    expect(screen.getByText("Not invoiced yet", { selector: "td" })).toBeTruthy()
    expect(screen.getByText("Overnight stay")).toBeTruthy(); expect(screen.getByText("Driver tab")).toBeTruthy()
    expect(screen.getByText(/Total/)).toBeTruthy()
    expect(get).toHaveBeenCalledWith(expect.stringMatching(/account-statements\/a1\?from=\d{4}-\d{2}-\d{2}&to=\d{4}-\d{2}-\d{2}/))
  })

  it("says so when nothing was charged", async () => {
    statement = { ...statement, lines: [], total: 0, invoiced: 0, notInvoiced: 0 }
    mount()
    await screen.findByText("Acme Haulage")
    fireEvent.change(screen.getByLabelText("Customer account"), { target: { value: "a1" } })
    fireEvent.click(screen.getByRole("button", { name: "Show statement" }))
    expect(await screen.findByText("Nothing was charged to this account in this period.")).toBeTruthy()
    expect((screen.getByRole("button", { name: "Download CSV" }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("shows the server's reason when the statement is refused", async () => {
    get.mockImplementation((u: string) => u.includes("account-statements") ? Promise.reject({ response: { data: { message: "A statement can cover at most 366 days." } } }) : ok(u.includes("customer-accounts") ? [{ id: "a1", customerName: "Acme Haulage" }] : []))
    mount()
    await screen.findByText("Acme Haulage")
    fireEvent.change(screen.getByLabelText("Customer account"), { target: { value: "a1" } })
    fireEvent.click(screen.getByRole("button", { name: "Show statement" }))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toMatch(/366 days/))
  })

  it("asks for the chosen division and shows deliveries and where the invoices stand", async () => {
    statement = { ...statement, division: "DISTRIBUTION", settlementKnown: true, invoicedWithVat: 23000, paid: 10000, credited: 3000, outstanding: 10000,
      lines: [{ date: "2026-11-10", kind: "DELIVERY", sourceId: "d1", vehicleRegistration: "GP 77 ZZ", description: "5000 L DIESEL delivered", amount: 20000, invoiceState: "INVOICED", invoiceNumber: "INV-42", invoiceId: "i1" }],
      invoices: [{ invoiceId: "i1", invoiceNumber: "INV-42", status: "PARTIALLY_PAID", issuedAt: null, total: 23000, paid: 10000, credited: 3000, outstanding: 10000 }] }
    mount()
    await screen.findByText("Acme Haulage")
    fireEvent.change(screen.getByLabelText("Customer account"), { target: { value: "a1" } })
    fireEvent.change(screen.getByLabelText("Show"), { target: { value: "DISTRIBUTION" } })
    fireEvent.click(screen.getByRole("button", { name: "Show statement" }))
    expect(await screen.findByText("Fuel delivery")).toBeTruthy()
    expect(await screen.findByText("Where the invoices stand (including VAT)")).toBeTruthy()
    expect(screen.getByText("Part paid")).toBeTruthy()
    expect(screen.getByText(/Still owed/, { selector: "strong" })).toBeTruthy()
    expect(get).toHaveBeenCalledWith(expect.stringContaining("division=DISTRIBUTION"))
  })

  it("says so when Invoicing could not be read, instead of showing zero paid", async () => {
    statement = { ...statement, settlementKnown: false, invoices: [] }
    mount()
    await screen.findByText("Acme Haulage")
    fireEvent.change(screen.getByLabelText("Customer account"), { target: { value: "a1" } })
    fireEvent.click(screen.getByRole("button", { name: "Show statement" }))
    expect(await screen.findByText(/Invoicing could not be read just now/)).toBeTruthy()
    expect(screen.queryByText("Where the invoices stand (including VAT)")).toBeNull()
  })
})
