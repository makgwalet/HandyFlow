import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ForecourtSupplySection from "./ForecourtSupplySection"
import { isFcReport, supplyCaveat } from "./forecourtSupplyOps"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))

const rep = (o: any = {}) => ({ from: "2026-10-14", to: "2026-11-12", moneyShown: true, totalLitres: 150, totalSales: 3450, readingsLeftOut: 0,
  suppliers: [{ supplierId: "t", name: "Terminal", unassigned: false, litresSold: 150, salesValue: 3450,
    forecourts: [{ depotId: "f1", name: "Forecourt One", shifts: 2, litresSold: 100, salesValue: 2300 }, { depotId: "f2", name: "Forecourt Two", shifts: 1, litresSold: 50, salesValue: 1150 }] }], ...o })
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={qc}><ForecourtSupplySection days={30} /></QueryClientProvider>)
}

describe("ForecourtSupplySection", () => {
  beforeEach(() => { get.mockReset(); get.mockResolvedValue({ data: { data: rep() } }) })
  afterEach(cleanup)

  it("lists each supplying depot with its forecourts and the total", async () => {
    mount()
    expect(await screen.findByText("Terminal")).toBeTruthy()
    expect(screen.getByText("Forecourt One")).toBeTruthy(); expect(screen.getByText("Forecourt Two")).toBeTruthy()
    expect(screen.getByText("All forecourts")).toBeTruthy(); expect(screen.getByText("Sales")).toBeTruthy()
    expect(get.mock.calls[0][0]).toBe("/api/v1/fuel/forecourt/supply-report?days=30")
  })

  it("hides the sales column when the server sends no money", async () => {
    get.mockResolvedValue({ data: { data: rep({ moneyShown: false, totalSales: null }) } })
    mount()
    await screen.findByText("Terminal")
    expect(screen.queryByText("Sales")).toBeNull()
  })

  it("warns when forecourt sales have no supplying depot", async () => {
    get.mockResolvedValue({ data: { data: rep({ suppliers: [{ supplierId: null, name: "No supplier set", unassigned: true, litresSold: 10, salesValue: 230, forecourts: [] }] }) } })
    mount()
    expect((await screen.findByRole("status")).textContent).toMatch(/no supplying depot/)
  })

  it("says so when there are no closed shifts", async () => {
    get.mockResolvedValue({ data: { data: rep({ suppliers: [], totalLitres: 0, totalSales: 0 }) } })
    mount()
    expect(await screen.findByText("No closed forecourt shifts in this period.")).toBeTruthy()
  })

  it("renders nothing when the Retail division is off (request refused) or the answer is not a report", async () => {
    get.mockRejectedValue({ response: { status: 403 } })
    mount()
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(screen.queryByLabelText("Forecourt sales by supplying depot")).toBeNull()
    cleanup()
    get.mockResolvedValue({ data: { data: { entities: [] } } })
    mount()
    await waitFor(() => expect(get).toHaveBeenCalledTimes(2))
    expect(screen.queryByLabelText("Forecourt sales by supplying depot")).toBeNull()
  })
})

describe("forecourtSupplyOps", () => {
  it("isFcReport checks the shape", () => { expect(isFcReport(rep())).toBe(true); expect(isFcReport({})).toBe(false); expect(isFcReport(null)).toBe(false) })
  it("supplyCaveat is null when all is well and mentions left-out readings", () => {
    expect(supplyCaveat(rep())).toBeNull()
    expect(supplyCaveat(rep({ readingsLeftOut: 2 }))).toMatch(/2 meter reading/)
  })
})
