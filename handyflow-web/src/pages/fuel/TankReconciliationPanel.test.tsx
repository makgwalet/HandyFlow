import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TankReconciliationPanel from "./TankReconciliationPanel"
import { validateTolerance, varianceSentence, windowParams } from "./tankReconciliationOps"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))

const ready = (o: any = {}) => ({ ready: true, message: null, tankId: "t1", tankName: "Diesel 1", fuelType: "DIESEL", fromDipAt: "2026-11-01T06:00:00Z", fromDipLitres: 5000, toDipAt: "2026-11-08T06:00:00Z", toDipLitres: 9900,
  movements: [{ label: "Supplier receipts", litres: 10000, count: 1 }, { label: "Forecourt sales (pump meters)", litres: -4000, count: 3 }], expectedLitres: 11000, varianceLitres: -1100, throughputLitres: 4000, variancePct: -27.5,
  meterLitres: 4000, ledgerForecourtLitres: 4000, meterVsLedgerLitres: 0, tolerancePct: null, outsideTolerance: false, ...o })
let body: any
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={qc}><TankReconciliationPanel /></QueryClientProvider>)
}

describe("TankReconciliationPanel", () => {
  beforeEach(() => {
    get.mockReset(); body = ready()
    get.mockImplementation(async (u: string) => ({ data: { data: String(u).endsWith("/tanks") ? [{ id: "t1", name: "Diesel 1" }] : body } }))
  })
  afterEach(() => cleanup())

  it("does nothing until opened (no requests on the forecourt page)", () => {
    mount()
    expect(get).not.toHaveBeenCalled()
    expect(screen.getByText("Tank reconciliation")).toBeTruthy()
  })

  it("asks for a tank first, then shows the workings and the difference", async () => {
    mount()
    fireEvent.click(screen.getByText("Tank reconciliation"))
    fireEvent.click(screen.getByText("Reconcile"))
    await waitFor(() => screen.getByText("Choose a tank."))
    await waitFor(() => screen.getByRole("option", { name: "Diesel 1" }))
    fireEvent.change(screen.getByLabelText("Tank"), { target: { value: "t1" } })
    fireEvent.click(screen.getByText("Reconcile"))
    await waitFor(() => screen.getByText("Should be in the tank"))
    expect(screen.getByText(/The tank holds 1.100 L less than the records say/)).toBeTruthy()
    expect(screen.getByText(/They agree/)).toBeTruthy()
    expect(get).toHaveBeenCalledWith("/api/v1/fuel/forecourt/tanks/t1/reconciliation")
  })

  it("sends the tolerance and shows the flag", async () => {
    body = ready({ tolerancePct: 1, outsideTolerance: true, meterVsLedgerLitres: 100, meterLitres: 4100 })
    mount()
    fireEvent.click(screen.getByText("Tank reconciliation"))
    await waitFor(() => screen.getByRole("option", { name: "Diesel 1" }))
    fireEvent.change(screen.getByLabelText("Tank"), { target: { value: "t1" } })
    fireEvent.change(screen.getByLabelText("Tolerance (%)"), { target: { value: "1" } })
    fireEvent.click(screen.getByText("Reconcile"))
    await waitFor(() => screen.getByText(/Outside 1% tolerance/))
    expect(get).toHaveBeenCalledWith("/api/v1/fuel/forecourt/tanks/t1/reconciliation?tolerancePct=1")
    expect(screen.getByText(/They differ by/)).toBeTruthy()
  })

  it("says what is missing when there are fewer than two dips", async () => {
    body = { ready: false, message: "Needs at least two dip readings in this period; found 1.", movements: [] }
    mount()
    fireEvent.click(screen.getByText("Tank reconciliation"))
    await waitFor(() => screen.getByRole("option", { name: "Diesel 1" }))
    fireEvent.change(screen.getByLabelText("Tank"), { target: { value: "t1" } })
    fireEvent.click(screen.getByText("Reconcile"))
    await waitFor(() => screen.getByText(/Needs at least two dip readings/))
  })
})

describe("tankReconciliationOps", () => {
  it("validates tolerance and the window", () => {
    expect(validateTolerance("")).toBeNull()
    expect(validateTolerance("2.5")).toBeNull()
    expect(validateTolerance("-1")).toMatch(/0 and 100/)
    expect(validateTolerance("101")).toMatch(/0 and 100/)
    expect(windowParams("2026-11-10", "2026-11-01").error).toMatch(/after/)
    expect(windowParams("", "").from).toBeUndefined()
    expect(windowParams("2026-11-01", "2026-11-08").from).toBeTruthy()
  })
  it("describes loss, gain and a match in words", () => {
    expect(varianceSentence({ varianceLitres: 0, variancePct: null })).toMatch(/matches/)
    expect(varianceSentence({ varianceLitres: 50, variancePct: 1 })).toMatch(/50 L more/)
    expect(varianceSentence({ varianceLitres: -50, variancePct: -1 })).toMatch(/50 L less/)
  })
})
