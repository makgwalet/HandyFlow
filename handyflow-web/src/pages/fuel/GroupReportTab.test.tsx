import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import GroupReportTab from "./GroupReportTab"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))

const row = (o: any = {}) => ({ legalEntityId: "e1", name: "Acme Fuels", unassigned: false, deliveries: 4, litres: 20000, revenue: 440000, margin: 40000, deliveriesWithoutCost: 0,
  previousDeliveries: 3, previousLitres: 16000, previousRevenue: 352000, previousMargin: 40000, stockLitres: 12000, stockValue: 240000, tanksWithoutCost: 0, openVariances: 0, ...o })
const report = (o: any = {}) => ({ from: "2026-10-14", to: "2026-11-12", previousFrom: "2026-09-14", previousTo: "2026-10-13", moneyShown: true,
  entities: [row(), row({ legalEntityId: "e2", name: "Beta Transport", deliveries: 0, litres: 0, revenue: 0, margin: 0, previousLitres: 0, previousRevenue: 0, previousMargin: 0, stockLitres: 0, stockValue: 0 })],
  total: row({ legalEntityId: null, name: "Whole group" }), deliveriesLeftOut: 0, ...o })

function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={qc}><GroupReportTab /></QueryClientProvider>)
}

describe("GroupReportTab", () => {
  beforeEach(() => { get.mockReset(); get.mockResolvedValue({ data: { data: report() } }) })
  afterEach(cleanup)

  it("shows each legal entity and the whole group, with the change on the previous period", async () => {
    mount()
    expect(await screen.findByText("Acme Fuels")).toBeTruthy()
    expect(screen.getByText("Beta Transport")).toBeTruthy(); expect(screen.getByText("Whole group")).toBeTruthy()
    expect(screen.getAllByText("+25.0%").length).toBeGreaterThan(0)          // litres 16000 -> 20000
    expect(screen.getByText("Revenue")).toBeTruthy(); expect(screen.getByText("Stock value")).toBeTruthy()
    expect(get.mock.calls[0][0]).toBe("/api/v1/fuel/group-report?days=30")
  })

  it("shows forecourt litres, shifts and sales per entity, and the sales column only with money", async () => {
    get.mockResolvedValue({ data: { data: report({ entities: [row({ forecourtLitres: 1500, forecourtSales: 34500, forecourtShifts: 3 })] }) } })
    mount()
    expect(await screen.findByText("Forecourt litres")).toBeTruthy(); expect(screen.getByText("Forecourt sales")).toBeTruthy()
    expect(screen.getByText("3 shifts")).toBeTruthy()
    cleanup(); get.mockResolvedValue({ data: { data: report({ moneyShown: false, entities: [row({ forecourtLitres: 1500, forecourtSales: null, forecourtShifts: 1 })] }) } })
    mount()
    expect(await screen.findByText("Forecourt litres")).toBeTruthy(); expect(screen.queryByText("Forecourt sales")).toBeNull(); expect(screen.getByText("1 shift")).toBeTruthy()
  })

  it("an older server reply without forecourt figures still renders", async () => {
    mount()
    expect(await screen.findByText("Acme Fuels")).toBeTruthy(); expect(screen.getByText("Forecourt litres")).toBeTruthy()
  })

  it("changing the period asks for that many days", async () => {
    mount()
    await screen.findByText("Acme Fuels")
    fireEvent.change(screen.getByLabelText("Period"), { target: { value: "90" } })
    await waitFor(() => expect(get.mock.calls.some(c => /days=90/.test(c[0]))).toBe(true))
  })

  it("hides every money column when the server sends no money, and says why", async () => {
    get.mockResolvedValue({ data: { data: report({ moneyShown: false, entities: [row({ revenue: null, margin: null, previousRevenue: null, previousMargin: null, stockValue: null })], total: row({ revenue: null, margin: null, previousRevenue: null, previousMargin: null, stockValue: null, name: "Whole group" }) }) } })
    mount()
    expect(await screen.findByText("Acme Fuels")).toBeTruthy()
    expect(screen.queryByText("Revenue")).toBeNull(); expect(screen.queryByText("Margin")).toBeNull(); expect(screen.queryByText("Stock value")).toBeNull()
    expect(screen.getByText(/shown to people who can see margin/)).toBeTruthy()
  })

  it("flags the 'Not assigned' bucket and says what the figures leave out", async () => {
    get.mockResolvedValue({ data: { data: report({ entities: [row({ legalEntityId: null, unassigned: true, name: "Not assigned to a legal entity" })], total: row({ name: "Whole group", deliveriesWithoutCost: 2, tanksWithoutCost: 1, openVariances: 3 }), deliveriesLeftOut: 1 }) } })
    mount()
    expect(await screen.findByText("Needs a legal entity")).toBeTruthy()
    expect(screen.getByText(/2 deliveries have no cost at sale/)).toBeTruthy()
    expect(screen.getByText(/stock value is a floor/)).toBeTruthy()
    expect(screen.getByText(/1 reversed delivery is left out/)).toBeTruthy()
  })

  it("shows an error when it cannot load", async () => {
    get.mockRejectedValue(new Error("boom"))
    mount()
    await waitFor(() => expect(screen.getAllByRole("alert").some(a => /Could not load the group report/.test(a.textContent ?? ""))).toBe(true))
  })
})
