import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ForecourtReconciliationSection from "./ForecourtReconciliationSection"
import { isFcRecon, reconNotes } from "./forecourtSupplyOps"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))

const row = (o: any = {}) => ({ depotId: "f1", name: "Forecourt One", supplierId: "t", supplierName: "Terminal", fromSupplierDepot: 6000, fromOtherDepots: 1000, fromSuppliers: 3000, totalIn: 10000, supplierDepotSharePct: 60, litresSold: 9000, ...o })
const rec = (o: any = {}) => ({ from: "2026-10-14", to: "2026-11-12", forecourts: [row()], totalIn: 10000, totalSold: 9000, ...o })
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={qc}><ForecourtReconciliationSection days={30} /></QueryClientProvider>)
}

describe("ForecourtReconciliationSection", () => {
  beforeEach(() => { get.mockReset(); get.mockResolvedValue({ data: { data: rec() } }) })
  afterEach(cleanup)

  it("shows each forecourt with its sources and the share from the supplying depot", async () => {
    mount()
    expect(await screen.findByText("Forecourt One")).toBeTruthy()
    expect(screen.getByText("Terminal")).toBeTruthy(); expect(screen.getByText("60%")).toBeTruthy()
    expect(get.mock.calls[0][0]).toBe("/api/v1/fuel/forecourt/supply-report/reconciliation?days=30")
  })
  it("says when part of the fuel did not come from the supplying depot", async () => {
    mount()
    expect((await screen.findByRole("status")).textContent).toMatch(/did not come from Terminal/)
  })
  it("warns when a forecourt has no supplying depot", () => {
    expect(reconNotes(rec({ forecourts: [row({ supplierId: null, supplierName: null })] }) as any)[0]).toMatch(/no supplying depot/)
  })
  it("is quiet when everything came from the supplying depot", () => {
    expect(reconNotes(rec({ forecourts: [row({ fromOtherDepots: 0, fromSuppliers: 0, totalIn: 6000, fromSupplierDepot: 6000 })] }) as any)).toEqual([])
  })
  it("renders nothing when the request is refused or the answer is not a reconciliation", async () => {
    get.mockRejectedValue({ response: { status: 403 } })
    mount(); await new Promise(r => setTimeout(r, 30))
    expect(screen.queryByLabelText("Forecourt supply reconciliation")).toBeNull()
    expect(isFcRecon({ forecourts: "x" })).toBe(false)
  })
})
