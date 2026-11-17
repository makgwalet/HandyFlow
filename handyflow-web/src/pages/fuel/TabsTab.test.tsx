import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TabsTab from "./TabsTab"

const get = vi.fn(), post = vi.fn(), del = vi.fn()
let can = true
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: () => can }))

const tab = (o: any = {}) => ({ id: "t1", vehicleRegistration: "GP 1", driverName: "Sipho", accountId: null, status: "OPEN", paymentMethod: null, openedAt: "2026-11-17T08:00:00Z", settledAt: null,
  voidReason: null, invoiceState: null, invoiceNumber: null, invoiceError: null, lines: [{ id: "l1", offeringId: "o1", description: "Shower", quantity: 2, unitPrice: 45, amount: 90 }], total: 90, ...o })
let tabsNow: any[]
function mount() { render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><TabsTab /></QueryClientProvider>) }
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })

describe("TabsTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); del.mockReset(); can = true; tabsNow = [tab()]
    get.mockImplementation((u: string) => {
      if (u.includes("/tabs")) return ok(tabsNow)
      if (u.includes("/offerings")) return ok([{ id: "o1", serviceName: "Shower", active: true, price: 45 }, { id: "o2", serviceName: "Wash", active: true, price: null }])
      if (u.includes("customer-accounts")) return ok([{ id: "a1", customerName: "Acme Haulage", creditHold: false }, { id: "a2", customerName: "Held Co", creditHold: true }])
      return ok([])
    })
    post.mockResolvedValue({ data: { data: {}, message: "done" } }); del.mockResolvedValue({ data: { data: {} } })
  })
  afterEach(cleanup)

  it("lists tabs with their total and says the price comes from Bookings", async () => {
    mount()
    expect(await screen.findByText("GP 1")).toBeTruthy()
    expect(screen.getByText(/Prices come from Bookings/)).toBeTruthy()
    expect(screen.getByText("Not paid yet")).toBeTruthy()
  })

  it("adds a service by choosing it and a quantity, never a price, and lists only services that have a price", async () => {
    mount()
    fireEvent.click(await screen.findByText("GP 1"))
    const select = await screen.findByLabelText("Service") as HTMLSelectElement
    expect(Array.from(select.options).map(o => o.text).join("|")).not.toMatch(/Wash/)
    fireEvent.change(select, { target: { value: "o1" } })
    fireEvent.change(screen.getByLabelText("Quantity"), { target: { value: "3" } })
    fireEvent.click(screen.getByRole("button", { name: "Add" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/tabs/t1/lines", { offeringId: "o1", quantity: 3 }))
  })

  it("settles a tab with no account at the till with the chosen method", async () => {
    mount()
    fireEvent.click(await screen.findByText("GP 1"))
    fireEvent.change(await screen.findByLabelText("Paid by"), { target: { value: "CARD" } })
    fireEvent.click(screen.getByRole("button", { name: "Settle at the till" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/tabs/t1/settle", { paymentMethod: "CARD" }))
  })

  it("offers charge to account, not settle, for a tab on an account", async () => {
    tabsNow = [tab({ accountId: "a1" })]
    mount()
    fireEvent.click(await screen.findByText("GP 1"))
    expect(await screen.findByRole("button", { name: "Charge to account" })).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Settle at the till" })).toBeNull()
    fireEvent.click(screen.getByRole("button", { name: "Charge to account" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/tabs/t1/charge", {}))
  })

  it("needs a reason of 5 characters to void", async () => {
    mount()
    fireEvent.click(await screen.findByText("GP 1"))
    fireEvent.change(await screen.findByLabelText("Void reason"), { target: { value: "no" } })
    fireEvent.click(screen.getByRole("button", { name: "Void tab" }))
    expect(await screen.findByRole("alert")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Void reason"), { target: { value: "Left without paying" } })
    fireEvent.click(screen.getByRole("button", { name: "Void tab" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/tabs/t1/void", { reason: "Left without paying" }))
  })

  it("shows a failed invoice and lets it be raised again", async () => {
    tabsNow = [tab({ status: "SETTLED", accountId: "a1", paymentMethod: "ACCOUNT", invoiceState: "FAILED", invoiceError: "No series" })]
    mount()
    expect(await screen.findByText("Invoice failed: No series")).toBeTruthy()
    fireEvent.click(screen.getByText("GP 1"))
    fireEvent.click(await screen.findByRole("button", { name: "Raise invoice" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/tabs/t1/invoice", {}))
  })

  it("opens a tab with a registration, and does not list accounts on credit hold", async () => {
    mount()
    fireEvent.click(await screen.findByRole("button", { name: "New tab" }))
    const acct = await screen.findByLabelText("Charge to") as HTMLSelectElement
    expect(Array.from(acct.options).map(o => o.text).join("|")).toMatch(/Acme Haulage/)
    expect(Array.from(acct.options).map(o => o.text).join("|")).not.toMatch(/Held Co/)
    fireEvent.click(screen.getByRole("button", { name: "Open tab" }))
    expect(await screen.findByRole("alert")).toBeTruthy()
    fireEvent.change(screen.getByLabelText(/Vehicle registration/), { target: { value: "CA 5" } })
    fireEvent.change(acct, { target: { value: "a1" } })
    fireEvent.click(screen.getByRole("button", { name: "Open tab" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/truck-stop/tabs", { vehicleRegistration: "CA 5", driverName: null, accountId: "a1" }))
  })

  it("hides the actions from someone who cannot manage", async () => {
    can = false
    mount()
    fireEvent.click(await screen.findByText("GP 1"))
    await screen.findByText("Shower")
    expect(screen.queryByRole("button", { name: "New tab" })).toBeNull()
    expect(screen.queryByRole("button", { name: "Settle at the till" })).toBeNull()
  })
})
