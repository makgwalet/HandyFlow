import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import DivisionSalesTab from "./DivisionSalesTab"
import { shares, unitFor } from "./divisionSalesOps"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))

const row = (division: string, label: string, o: any = {}) => ({ division, label, count: 3, litres: 1000, revenue: 21000, dailyRevenue: [1, 2, 3], previousCount: 2, previousLitres: 800, previousRevenue: 16800, ...o })
const report = (o: any = {}) => ({ from: "2026-10-20", to: "2026-11-18", previousFrom: "2026-09-20", previousTo: "2026-10-19", moneyShown: true,
  divisions: [row("DISTRIBUTION", "Distribution"), row("TRUCK_STOP", "Truck stop", { litres: 0, count: 1, revenue: 7000, previousRevenue: 0 })], total: row("TOTAL", "All divisions", { count: 4, revenue: 28000, litres: 1000 }), ...o })

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={qc}><DivisionSalesTab /></QueryClientProvider>)
}

describe("DivisionSalesTab", () => {
  beforeEach(() => { get.mockReset(); get.mockResolvedValue({ data: { data: report() } }) })
  afterEach(cleanup)

  it("shows each division with its revenue, share and definition", async () => {
    mount()
    expect(await screen.findByText("Distribution")).toBeTruthy()
    expect(screen.getByText("Truck stop")).toBeTruthy()
    expect(screen.getByText("75% of revenue")).toBeTruthy()
    expect(screen.getByText(/Delivered loads, at the amount invoiced/)).toBeTruthy()
    expect(screen.getByText(/nothing in the period before/)).toBeTruthy()
  })

  it("hides amounts and says why when the server sends none", async () => {
    get.mockResolvedValue({ data: { data: report({ moneyShown: false, divisions: [row("DISTRIBUTION", "Distribution", { revenue: null, dailyRevenue: null, previousRevenue: null })], total: row("TOTAL", "All", { revenue: null, dailyRevenue: null, previousRevenue: null }) }) } })
    mount()
    expect(await screen.findByText(/Amounts are hidden because you do not have the margin permission/)).toBeTruthy()
    expect(screen.queryByText("Revenue")).toBeNull()
    expect(screen.queryByText(/% of revenue/)).toBeNull()
    expect(screen.getByText("3 loads")).toBeTruthy()
  })

  it("asks again when the period changes", async () => {
    mount()
    await screen.findByText("Distribution")
    fireEvent.change(screen.getByLabelText("Period"), { target: { value: "90" } })
    await screen.findByText("Distribution")
    expect(get.mock.calls.some(c => c[1]?.params?.days === 90)).toBe(true)
  })

  it("says so when there is nothing to show, and when loading fails", async () => {
    get.mockResolvedValue({ data: { data: report({ divisions: [] }) } })
    mount()
    expect(await screen.findByText("No divisions to show")).toBeTruthy()
    cleanup(); get.mockRejectedValue(new Error("x")); mount()
    expect(await screen.findByRole("alert")).toBeTruthy()
  })
})

describe("divisionSalesOps", () => {
  it("shares add to 100 and are null without money or sales", () => {
    const s = shares([row("A", "a", { revenue: 30 }), row("B", "b", { revenue: 70 })])!
    expect(s.A + s.B).toBeCloseTo(100)
    expect(shares([row("A", "a", { revenue: null })])).toBeNull()
    expect(shares([row("A", "a", { revenue: 0 })])).toBeNull()
  })
  it("names the unit for each division", () => {
    expect(unitFor("RETAIL_FORECOURT", 1)).toBe("shift")
    expect(unitFor("WHOLESALE_SUPPLY", 2)).toBe("dispatches")
  })
})
