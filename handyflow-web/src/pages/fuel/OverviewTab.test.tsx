import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import OverviewTab from "./OverviewTab"

const get = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))

const overview = (o: any = {}) => ({ today: "2026-11-11", stock: [{ fuelType: "DIESEL", litresOnHand: 50000, litresReserved: 12000, litresAvailable: 38000, capacityLitres: 80000 }], lowTanks: 0, quotes: 2, confirmedOrders: 3, partDeliveredOrders: 1, deliveredNotInvoicedCount: 4, deliveredNotInvoicedValue: 88000, invoiceFailures: 0, customersOnCreditHold: 1, complianceExpired: 1, complianceExpiring: 2, loadsToday: 5, loadsTodayUnallocated: 2, purchaseOrdersAwaitingDelivery: 1, ...o })
const margin = { from: "2026-10-13", to: "2026-11-11", revenue: 330000, margin: 20000, deliveriesWithoutCost: 1, customers: [{ customerId: "c1", customerName: "Gold Mine Ltd", deliveries: 3, litres: 15000, revenue: 330000, margin: 20000, marginPerLitre: 1.3333 }] }
const nav = vi.fn()
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={qc}><OverviewTab onNavigate={nav} /></QueryClientProvider>)
}

describe("OverviewTab", () => {
  beforeEach(() => {
    get.mockReset(); nav.mockReset(); perms = ["FUEL_READ"]
    get.mockImplementation(async (u: string) => u.endsWith("/margin") ? { data: { data: margin } } : { data: { data: overview() } })
  })
  afterEach(cleanup)

  it("lists what needs attention and jumps to it", async () => {
    mount()
    expect(await screen.findByText(/compliance item has expired/)).toBeTruthy()
    fireEvent.click(screen.getAllByText("Open")[0])
    expect(nav).toHaveBeenCalledWith("compliance")
  })

  it("says so when nothing needs attention", async () => {
    get.mockImplementation(async () => ({ data: { data: overview({ complianceExpired: 0, complianceExpiring: 0, loadsTodayUnallocated: 0, customersOnCreditHold: 0 }) } }))
    mount()
    expect(await screen.findByText("Nothing needs attention right now.")).toBeTruthy()
  })

  it("hides margin without the margin permission and never asks the server for it", async () => {
    mount()
    await screen.findByText("Needs attention")
    expect(screen.queryByText("Delivery margin by customer")).toBeNull()
    expect(get.mock.calls.some(c => String(c[0]).endsWith("/margin"))).toBe(false)
  })

  it("shows margin by customer with the honest caveat", async () => {
    perms = ["FUEL_READ", "FUEL_MARGIN_READ"]
    mount()
    expect(await screen.findByText("Gold Mine Ltd")).toBeTruthy()
    expect(screen.getByText(/not full cost-to-serve/)).toBeTruthy()
    expect(screen.getByText(/1 delivery has no recorded fuel cost/)).toBeTruthy()
  })
})
