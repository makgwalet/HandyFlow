import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import CustomersTab from "./CustomersTab"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn(); const del = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const acct = (o: any = {}) => ({ id: "a1", crmCustomerId: "c1", customerName: "Gold Mine Ltd", customerType: "MINING_COMPANY", creditLimit: 50000, paymentTermsDays: 30, creditHold: false, creditHoldReason: null, licenceNumber: null, notes: null, siteCount: 1, sites: null, ...o })
let accounts: any[]

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><CustomersTab /></QueryClientProvider>)
}

describe("CustomersTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset(); del.mockReset(); perms = ["FUEL_MANAGE"]
    accounts = [acct(), acct({ id: "a2", customerName: "Held Farm", creditHold: true, creditHoldReason: "Overdue 60 days", customerType: "COMMERCIAL_FARM" })]
    post.mockResolvedValue({ data: { data: {} } })
    get.mockImplementation(async (u: string) => {
      if (u.endsWith("/customer-accounts")) return { data: { data: accounts } }
      if (u.includes("/customer-accounts/")) return { data: { data: acct({ sites: [{ id: "s1", accountId: "a1", name: "Pit 1", address: "Gate 3", contactName: null, contactPhone: null, deliveryInstructions: null, operatingHours: null }] }) } }
      if (u.includes("/crm/customers")) return { data: { content: [{ id: "c1", name: "Gold Mine Ltd" }, { id: "c9", name: "New Customer" }] } }
      return { data: { data: [] } }
    })
  })
  afterEach(() => cleanup())

  it("lists accounts with type, credit and hold status", async () => {
    mount()
    await waitFor(() => screen.getByText("Gold Mine Ltd"))
    expect(screen.getByText("Mining company")).toBeTruthy()
    expect(screen.getByText("On credit hold")).toBeTruthy()
    expect(screen.getByText("Active")).toBeTruthy()
  })

  it("hides write actions without FUEL_MANAGE", async () => {
    perms = []
    mount()
    await waitFor(() => screen.getByText("Gold Mine Ltd"))
    expect(screen.queryByText("New customer account")).toBeNull()
    expect(screen.queryByText("Release hold")).toBeNull()
  })

  it("refuses a hold without a reason, then places it", async () => {
    mount()
    await waitFor(() => screen.getByText("Gold Mine Ltd"))
    fireEvent.click(screen.getAllByText("Credit hold").find(e => e.tagName === "BUTTON")!)
    fireEvent.click(screen.getByText("Place on hold"))
    expect(screen.getByRole("alert").textContent).toMatch(/at least 5/)
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Overdue 90 days" } })
    fireEvent.click(screen.getByText("Place on hold"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/customer-accounts/a1/credit-hold", { hold: true, reason: "Overdue 90 days" }))
  })

  it("releases a hold", async () => {
    mount()
    await waitFor(() => screen.getByText("Release hold"))
    fireEvent.click(screen.getByText("Release hold"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/customer-accounts/a2/credit-hold", { hold: false, reason: null }))
  })

  it("creates an account only for a chosen customer and valid terms; customers with an account are not offered", async () => {
    mount()
    await waitFor(() => screen.getByText("Gold Mine Ltd"))
    fireEvent.click(screen.getByText("New customer account"))
    await waitFor(() => screen.getByText("New Customer"))
    expect(screen.getAllByText("Gold Mine Ltd").length).toBe(1) // only in the table, not the picker
    fireEvent.click(screen.getByText("Save account"))
    expect(screen.getByRole("alert").textContent).toMatch(/Choose the customer/)
    fireEvent.change(screen.getByLabelText("Customer"), { target: { value: "c9" } })
    fireEvent.change(screen.getByLabelText("Customer type"), { target: { value: "COMMERCIAL_FARM" } })
    fireEvent.change(screen.getByLabelText("Payment terms"), { target: { value: "400" } })
    fireEvent.click(screen.getByText("Save account"))
    expect(screen.getByRole("alert").textContent).toMatch(/terms/)
    fireEvent.change(screen.getByLabelText("Payment terms"), { target: { value: "45" } })
    fireEvent.click(screen.getByText("Save account"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/customer-accounts",
      expect.objectContaining({ crmCustomerId: "c9", customerType: "COMMERCIAL_FARM", paymentTermsDays: 45 })))
  })

  it("shows the server's message when saving fails", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "This customer already has a fuel account." } } })
    mount()
    await waitFor(() => screen.getByText("Gold Mine Ltd"))
    fireEvent.click(screen.getByText("New customer account"))
    await waitFor(() => screen.getByText("New Customer"))
    fireEvent.change(screen.getByLabelText("Customer"), { target: { value: "c9" } })
    fireEvent.change(screen.getByLabelText("Customer type"), { target: { value: "OTHER" } })
    fireEvent.click(screen.getByText("Save account"))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toMatch(/already has a fuel account/))
  })

  it("expands an account to show and add sites", async () => {
    mount()
    await waitFor(() => screen.getByText("Gold Mine Ltd"))
    fireEvent.click(screen.getByText("Gold Mine Ltd"))
    await waitFor(() => screen.getByText("Pit 1"))
    fireEvent.click(screen.getByText("Add site"))
    fireEvent.click(screen.getByText("Save site"))
    expect(screen.getByRole("alert").textContent).toMatch(/name/)
    fireEvent.change(screen.getByLabelText("Site name"), { target: { value: "Pit 2" } })
    fireEvent.click(screen.getByText("Save site"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/customer-accounts/a1/sites", expect.objectContaining({ name: "Pit 2" })))
  })
})
