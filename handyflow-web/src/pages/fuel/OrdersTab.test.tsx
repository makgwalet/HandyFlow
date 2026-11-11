import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import OrdersTab from "./OrdersTab"

const get = vi.fn(); const post = vi.fn(); const put = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const order = (o: any = {}) => ({ id: "o1", orderNumber: "SO-2026-00001", accountId: "a1", customerId: "c1", customerName: "Gold Mine Ltd", siteId: null, siteName: null, fuelType: "DIESEL", litresOrdered: 30000, pricePerLitre: 22.5, value: 675000, status: "QUOTE", requestedDate: null, quoteValidUntil: null, tankId: null, tankName: null, litresDelivered: 0, litresScheduled: 0, litresRemaining: 30000, creditOverrideReason: null, cancelReason: null, customerReference: null, notes: null, deliveries: null, ...o })
const check = (o: any = {}) => ({ creditLimit: 1000000, invoicesOutstanding: 0, deliveredNotInvoiced: 0, otherOpenOrders: 0, thisOrder: 675000, exposureAfter: 675000, withinLimit: true, onCreditHold: false, ...o })
let orders: any[]; let credit: any

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><OrdersTab /></QueryClientProvider>)
}

describe("OrdersTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset(); perms = ["FUEL_MANAGE"]
    orders = [order(), order({ id: "o2", orderNumber: "SO-2026-00002", status: "PART_DELIVERED", litresDelivered: 10000, litresRemaining: 20000, tankName: "Diesel 1", deliveries: [
      { id: "d1", status: "DELIVERED", litresOrdered: 10000, litresDelivered: 9800, scheduledAt: "2026-11-11T08:00:00Z", loadedLitres: 10000, sealNumbers: "S-1", receiptNumber: "FDR-2026-00001", invoiceState: "NONE", invoiceNumber: null, orderId: "o2" },
      { id: "d2", status: "SCHEDULED", litresOrdered: 5000, litresDelivered: null, scheduledAt: "2026-11-12T08:00:00Z", loadedLitres: null, sealNumbers: null, receiptNumber: null, invoiceState: "NONE", invoiceNumber: null, orderId: "o2" }] })]
    credit = check()
    post.mockResolvedValue({ data: { data: {} } })
    get.mockImplementation(async (u: string) => {
      if (u.endsWith("/orders")) return { data: { data: { content: orders } } }
      if (u.includes("/credit-check")) return { data: { data: credit } }
      if (u.includes("/orders/")) return { data: { data: orders.find(o => u.endsWith(o.id)) } }
      if (u.endsWith("/tanks")) return { data: [{ id: "t1", name: "Diesel 1", fuelType: "DIESEL", currentLitres: 40000 }, { id: "t2", name: "Petrol 1", fuelType: "PETROL_95", currentLitres: 9000 }] }
      if (u.endsWith("/customer-accounts")) return { data: { data: [{ id: "a1", customerName: "Gold Mine Ltd", creditHold: false }] } }
      return { data: { data: { sites: [] } } }
    })
  })
  afterEach(() => cleanup())

  it("lists orders with status, delivered litres and value", async () => {
    mount()
    await waitFor(() => screen.getByText("SO-2026-00001"))
    expect(screen.getByText("Quotation")).toBeTruthy()
    expect(screen.getByText("Part delivered")).toBeTruthy()
  })

  it("offers actions by status and none without FUEL_MANAGE", async () => {
    mount()
    await waitFor(() => screen.getByText("SO-2026-00001"))
    expect(screen.getByText("Confirm")).toBeTruthy()
    expect(screen.getByText("Schedule load")).toBeTruthy()
    cleanup(); perms = []; mount()
    await waitFor(() => screen.getByText("SO-2026-00001"))
    expect(screen.queryByText("Confirm")).toBeNull()
    expect(screen.queryByText("New quotation")).toBeNull()
  })

  it("confirm shows the credit position, offers only tanks of the right fuel and posts the tank", async () => {
    mount()
    await waitFor(() => screen.getByText("SO-2026-00001"))
    fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => screen.getByText("Within credit limit"))
    expect(screen.queryByText(/Petrol 1/)).toBeNull()
    fireEvent.click(screen.getByText("Confirm and allocate stock"))
    expect(screen.getByRole("alert").textContent).toMatch(/tank/)
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Tank"), { target: { value: "t1" } })
    fireEvent.click(screen.getByText("Confirm and allocate stock"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/orders/o1/confirm", { tankId: "t1", overrideReason: null }))
  })

  it("over the limit: without override permission it is refused; with it a reason is required", async () => {
    credit = check({ withinLimit: false, exposureAfter: 1500000 })
    mount()
    await waitFor(() => screen.getByText("SO-2026-00001"))
    fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => screen.getByText("Over credit limit"))
    fireEvent.change(screen.getByLabelText("Tank"), { target: { value: "t1" } })
    fireEvent.click(screen.getByText("Confirm and allocate stock"))
    expect(screen.getByRole("alert").textContent).toMatch(/permission/)
    cleanup(); perms = ["FUEL_MANAGE", "FUEL_CREDIT_OVERRIDE"]; mount()
    await waitFor(() => screen.getByText("SO-2026-00001"))
    fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => screen.getByLabelText("Override reason"))
    fireEvent.change(screen.getByLabelText("Tank"), { target: { value: "t1" } })
    fireEvent.click(screen.getByText("Confirm and allocate stock"))
    expect(screen.getByRole("alert").textContent).toMatch(/reason/)
    fireEvent.change(screen.getByLabelText("Override reason"), { target: { value: "Prepaid by EFT" } })
    fireEvent.click(screen.getByText("Confirm and allocate stock"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/orders/o1/confirm", { tankId: "t1", overrideReason: "Prepaid by EFT" }))
  })

  it("shows the server's message when stock cannot cover the order", async () => {
    post.mockRejectedValueOnce({ response: { data: { message: "Only 100L of Diesel 1 is available to promise" } } })
    mount()
    await waitFor(() => screen.getByText("SO-2026-00001"))
    fireEvent.click(screen.getByText("Confirm"))
    await waitFor(() => screen.getByText("Within credit limit"))
    fireEvent.change(screen.getByLabelText("Tank"), { target: { value: "t1" } })
    fireEvent.click(screen.getByText("Confirm and allocate stock"))
    await waitFor(() => expect(screen.getByRole("alert").textContent).toMatch(/available to promise/))
  })

  it("scheduling a load cannot exceed what is left", async () => {
    mount()
    await waitFor(() => screen.getByText("SO-2026-00002"))
    fireEvent.click(screen.getByText("Schedule load"))
    fireEvent.change(screen.getByLabelText("Load litres"), { target: { value: "99999" } })
    fireEvent.change(screen.getByLabelText("Scheduled for"), { target: { value: "2026-11-12T08:00" } })
    fireEvent.click(screen.getAllByText("Schedule load").pop()!)
    expect(screen.getByRole("alert").textContent).toMatch(/Only/)
    expect(post).not.toHaveBeenCalled()
  })

  it("flags a loading variance beyond tolerance and lets a scheduled load record loading", async () => {
    mount()
    await waitFor(() => screen.getByText("SO-2026-00002"))
    fireEvent.click(screen.getByText("SO-2026-00002"))
    await waitFor(() => screen.getByText(/needs review/))
    fireEvent.click(screen.getByText("Record loading"))
    fireEvent.change(screen.getByLabelText("Loaded litres"), { target: { value: "5010" } })
    fireEvent.change(screen.getByLabelText("Seal numbers"), { target: { value: "S-9" } })
    fireEvent.click(screen.getByText("Save loading"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/deliveries/d2/loading", { loadedLitres: 5010, sealNumbers: "S-9" }))
  })

  it("cancelling needs a reason", async () => {
    mount()
    await waitFor(() => screen.getByText("SO-2026-00001"))
    fireEvent.click(screen.getAllByText("Cancel")[0])
    fireEvent.click(screen.getByText("Cancel order"))
    expect(screen.getByRole("alert").textContent).toMatch(/at least 5/)
    fireEvent.change(screen.getByLabelText("Cancel reason"), { target: { value: "Customer withdrew" } })
    fireEvent.click(screen.getByText("Cancel order"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/orders/o1/cancel", { reason: "Customer withdrew" }))
  })
})
