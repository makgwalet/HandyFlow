import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import CostToServeTab from "./CostToServeTab"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))

const row = (o: any = {}) => ({ customerId: "c1", customerName: "Acme Mining", deliveries: 4, litres: 20000, revenue: 440000, margin: 40000, driverCost: 1200, vehicleCost: 3000,
  fixedCost: 200, overheadCost: 13200, netMargin: 22400, netPerLitre: 1.12, deliveriesWithoutDistance: 0, deliveriesWithEstimatedHours: 0, ...o })
const body = (o: any = {}) => ({ from: "2026-10-13", to: "2026-11-11", ratesConfigured: true, deliveries: 5, deliveriesWithoutCost: 0, deliveriesWithoutDistance: 0, deliveriesWithEstimatedHours: 0,
  revenue: 500000, margin: 45000, driverCost: 1500, vehicleCost: 3000, fixedCost: 250, overheadCost: 15000, netMargin: 25250, customers: [row()], ...o })

function mount(onNavigate = vi.fn()) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={qc}><CostToServeTab onNavigate={onNavigate} /></QueryClientProvider>)
  return onNavigate
}

describe("CostToServeTab", () => {
  beforeEach(() => { get.mockReset(); get.mockResolvedValue({ data: { data: body() } }) })
  afterEach(cleanup)

  it("shows the customer's margin, each cost and the net margin", async () => {
    mount()
    expect(await screen.findByText("Acme Mining")).toBeTruthy()
    expect(get.mock.calls[0][0]).toMatch(/cost-to-serve\?days=30/)
    expect(screen.getByText("Net margin", { selector: "div" })).toBeTruthy()
  })

  it("changing the period asks for that many days", async () => {
    mount()
    await screen.findByText("Acme Mining")
    fireEvent.change(screen.getByLabelText("Period"), { target: { value: "90" } })
    await waitFor(() => expect(get.mock.calls.some(c => /days=90/.test(c[0]))).toBe(true))
  })

  it("tells the manager when no rates are set and links to Settings", async () => {
    get.mockResolvedValue({ data: { data: body({ ratesConfigured: false }) } })
    const nav = mount()
    fireEvent.click(await screen.findByText("Set your rates"))
    expect(nav).toHaveBeenCalledWith("settings")
  })

  it("says what is estimated: missing distances, estimated hours, uncosted deliveries", async () => {
    get.mockResolvedValue({ data: { data: body({ deliveriesWithoutCost: 2, deliveriesWithoutDistance: 2, deliveriesWithEstimatedHours: 1,
      customers: [row({ deliveriesWithoutDistance: 2, deliveriesWithEstimatedHours: 1 })] }) } })
    mount()
    expect(await screen.findByText(/2 deliveries have no cost at sale/)).toBeTruthy()
    expect(screen.getByText(/2 deliveries go to a site with no distance/)).toBeTruthy()
    expect(screen.getByText(/2 of 4 with no site distance/)).toBeTruthy()
    expect(screen.getByText(/1 of 4 with estimated driver hours/)).toBeTruthy()
  })

  it("marks a customer who loses money once service is counted", async () => {
    get.mockResolvedValue({ data: { data: body({ customers: [row({ margin: 500, netMargin: -300 })] }) } })
    mount()
    expect(await screen.findByText("Loses money to serve")).toBeTruthy()
  })

  it("shows an empty state and an error state", async () => {
    get.mockResolvedValue({ data: { data: body({ customers: [] }) } })
    mount()
    expect(await screen.findByText(/No costed deliveries/)).toBeTruthy()
    cleanup()
    get.mockRejectedValue(new Error("boom"))
    mount()
    expect((await screen.findByRole("alert")).textContent).toMatch(/Could not load/)
  })
})
