import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({
  apiClient: { get: (...a: unknown[]) => get(...a), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() },
}))
vi.mock("../../store/auth.store", () => ({
  useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: perms } }),
}))

import ProfitabilityTab from "./ProfitabilityTab"
import { toCsv } from "./shared/format"

afterEach(cleanup)

const row = (over: Record<string, unknown> = {}) => ({
  assetId: "a1", machine: "D9-001 CAT D9", charges: 2, revenue: 1500, invoiced: 1000, notInvoiced: 500, maintenanceCost: 300, margin: 1200, marginPercent: 80, ...over,
})
const report = {
  from: "2026-01-01", to: "2026-10-15",
  totals: row({ assetId: null, machine: "All machines", revenue: 1700, notInvoiced: 700, maintenanceCost: 550, margin: 1150, marginPercent: 67.6 }),
  machines: [row(), row({ assetId: "a2", machine: "L-02 Volvo L120", charges: 1, revenue: 200, invoiced: 0, notInvoiced: 200, maintenanceCost: 250, margin: -50, marginPercent: -25 })],
}
const mount = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><ProfitabilityTab /></QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
  get.mockResolvedValue({ data: { data: report } })
})

describe("Profitability", () => {
  it("shows the totals and a row per machine, with a loss in the loss colour and the caveat in words", async () => {
    mount()
    expect(await screen.findByText("D9-001 CAT D9")).toBeTruthy()
    expect(screen.getByText("L-02 Volvo L120")).toBeTruthy()
    expect(screen.getByText("80.0%")).toBeTruthy()
    expect(screen.getByText("-25.0%")).toBeTruthy()
    expect(screen.getByText(/Fuel, operators, depreciation and finance costs are not included/)).toBeTruthy()
    expect(get.mock.calls[0][0]).toMatch(/\/reports\/profitability\?from=\d{4}-01-01&to=\d{4}-\d{2}-\d{2}/)
  })

  it("is not shown, and asks nothing of the server, without administrator access", () => {
    perms = ["EARTHMOVING_READ"]
    mount()
    expect(screen.getByText(/needs Earthmoving administrator access/)).toBeTruthy()
    expect(get).not.toHaveBeenCalled()
  })

  it("warns about a backwards date range instead of asking the server", async () => {
    mount()
    await screen.findByText("D9-001 CAT D9")
    get.mockClear()
    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2026-12-31" } })
    fireEvent.change(screen.getByLabelText("To"), { target: { value: "2026-01-01" } })
    expect(await screen.findByText(/end date is before the start date/)).toBeTruthy()
    expect(get).not.toHaveBeenCalled()
  })
})

describe("toCsv", () => {
  it("keeps commas and quotes inside their cell and leaves nulls empty", () => {
    expect(toCsv([["a,b", 'say "hi"', null, 3]])).toBe('"a,b","say ""hi""",,"3"')
  })
})
