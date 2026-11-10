import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import WhseDashboard from "./WhseDashboard"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (u: string) => get(u) } }))

const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><WhseDashboard /></QueryClientProvider>)

const summary = {
  totalUnitsOnHand: 120, totalUnitsAvailable: 100, stockedPositions: 9,
  work: { expectedInbound: 2, partiallyReceivedInbound: 1, overdueInbound: 1, pendingOutbound: 3, pickingOutbound: 2, packedOutbound: 1, overdueOutbound: 0 },
  today: { unitsReceived: 40, unitsDispatched: 25, inboundReceived: 2, ordersShipped: 3, adjustments: 1 },
  adjustmentsAwaitingApproval: 2, invoicesAwaitingApproval: 1, stockAccuracyPct: 91.5, linesCountedLast30Days: 40,
  stockValue: [{ currency: "ZAR", value: 1500 }, { currency: "USD", value: 200 }], unitsWithoutCost: 7,
}

function serve(over: Record<string, unknown> = {}) {
  get.mockImplementation(async (url: string) => {
    if (url in over) { const v = over[url]; if (v instanceof Error) throw v; return { data: v } }
    if (url.endsWith("/operations/summary")) return { data: summary }
    if (url.endsWith("/tasks/summary")) return { data: { openPicks: 4, inProgressPicks: 1, shortPicks: 2, openPutaways: 3, inProgressPutaways: 0 } }
    if (url.endsWith("/operations/stock-alerts")) return { data: [{ clientName: "Acme", sku: "MILK", description: null, alert: "BELOW_MIN", available: 2, minQty: 10, maxQty: 50, toOrder: 48 }, { clientName: "Acme", sku: "RICE", description: null, alert: "ABOVE_MAX", available: 90, minQty: 5, maxQty: 60, toOrder: null }] }
    if (url.endsWith("/clients/all")) return { data: [{ id: "c", tradingName: "Acme", status: "ACTIVE" }] }
    if (url.endsWith("/locations")) return { data: [] }
    return { data: null }
  })
}

// braces matter: a returned function would be run by vitest as a teardown hook
beforeEach(() => { get.mockReset() })
afterEach(cleanup)

describe("warehouse dashboard tiles", () => {
  it("shows stock value one line per currency, never summed, and says what could not be valued", async () => {
    serve(); wrap()
    expect(await screen.findByText(/Stock value/i)).toBeTruthy()
    await waitFor(() => expect(screen.getByText(/7 units have no cost/)).toBeTruthy())
    expect(screen.getByText(/1[\s ,]?500/)).toBeTruthy()
    expect(screen.getByText(/200/)).toBeTruthy()
  })

  it("shows task counts, flags short picks and lists the stock alerts", async () => {
    serve(); wrap()
    expect(await screen.findByText(/Holding orders back/)).toBeTruthy()
    expect(screen.getByText(/4 open · 1 in progress/)).toBeTruthy()
    expect(screen.getByText(/MILK \(2\/10\)/)).toBeTruthy()
    expect(screen.getByText("RICE")).toBeTruthy()
    expect(screen.getByText(/2 adjustments · 1 invoices/)).toBeTruthy()
  })

  it("shows count accuracy and overdue work", async () => {
    serve(); wrap()
    expect(await screen.findByText("91.5%")).toBeTruthy()
    expect(screen.getByText("1 overdue")).toBeTruthy()
  })

  it("still shows the other tiles when one endpoint fails", async () => {
    serve({ "/api/v1/warehousing/operations/summary": new Error("boom") }); wrap()
    expect(await screen.findByText(/Holding orders back/)).toBeTruthy()
    expect(screen.queryByText("Today")).toBeNull()
  })
})
