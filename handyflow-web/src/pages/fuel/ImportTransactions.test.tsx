import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ImportTransactions from "./ImportTransactions"
import { asShiftTransactions, litresCheckText, problemCount } from "./importOps"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
const ok = (d: unknown) => Promise.resolve({ data: { data: d } })

const tx = (o: any = {}) => ({ id: "t1", externalId: "T1", kind: "SALE", originalExternalId: null, pumpLabel: "Pump 1", litres: 5, amount: 100, tender: "CASH", occurredAt: null, reason: null, status: "RECEIVED", problems: [], adjustmentId: null, ...o })
const summary = (o: any = {}) => ({ sales: 1, salesLitres: 5, salesAmount: 100, refundsAmount: 40, voidsAmount: 0, netAmount: 60, netByTender: { CASH: 60 }, meterLitres: 5, litresDifference: 0, notApplied: 0, ...o })
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={qc}><ImportTransactions importId="i1" /></QueryClientProvider>)
}

describe("ImportTransactions", () => {
  beforeEach(() => get.mockReset())
  afterEach(cleanup)

  it("shows the transactions with refunds as negative amounts and what they add up to", async () => {
    get.mockImplementation(() => ok({ transactions: [tx(), tx({ id: "t2", externalId: "R1", kind: "REFUND", originalExternalId: "T1", amount: 40, litres: null })], summary: summary() }))
    mount()
    expect(await screen.findByText("Live transactions")).toBeTruthy()
    expect(screen.getByText("Refund")).toBeTruthy()
    expect(screen.getByText(/Sales litres match the meters/)).toBeTruthy()
    expect(get.mock.calls[0][0]).toBe("/api/v1/fuel/forecourt/imports/i1/transactions")
  })

  it("lists problems and says they block the accept", async () => {
    get.mockImplementation(() => ok({ transactions: [tx({ id: "t2", externalId: "R1", kind: "REFUND", problems: ["REFUND R1 refers to sale T9, which has not been received for this shift."] })], summary: summary() }))
    mount()
    expect(await screen.findByText("1 transaction problem to fix before this shift can be accepted.")).toBeTruthy()
    expect(screen.getByText(/refers to sale T9/)).toBeTruthy()
  })

  it("shows nothing when there are no transactions or the reply is not the expected shape", async () => {
    get.mockImplementation(() => ok([]))
    mount()
    await Promise.resolve()
    expect(screen.queryByText("Live transactions")).toBeNull()
  })

  it("helpers: shape check, litres comparison and problem count", () => {
    expect(asShiftTransactions([])).toBeNull()
    expect(asShiftTransactions(null)).toBeNull()
    expect(asShiftTransactions({ transactions: [], summary: summary() })).not.toBeNull()
    expect(litresCheckText(summary({ litresDifference: -1 }))).toBe("Sales litres are 1 L less than the meters.")
    expect(litresCheckText(summary({ litresDifference: 2.5 }))).toBe("Sales litres are 2.5 L more than the meters.")
    expect(litresCheckText(summary({ litresDifference: null, meterLitres: null }))).toBe("")
    expect(problemCount({ transactions: [tx({ problems: ["a", "b"] }), tx({ problems: [] })], summary: summary() } as any)).toBe(2)
  })
})
