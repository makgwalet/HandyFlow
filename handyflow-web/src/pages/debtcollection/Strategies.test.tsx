import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import StrategiesTab from "./StrategiesTab"
import StrategyNote from "./StrategyNote"

const perms = vi.hoisted(() => ({ held: new Set<string>() }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.held.has(p) }))
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
afterEach(cleanup)
beforeEach(() => { Object.values(api).forEach(f => f.mockReset()); perms.held = new Set(["DEBTCOLLECTION_READ", "DEBTCOLLECTION_ADMIN"]) })

const strat = { id: "s1", name: "Small debts", description: null, priority: 5, minBalance: null, maxBalance: 5000, minDays: null, maxDays: null, quietDays: 3, active: true,
  steps: [{ id: "x1", afterDays: 7, action: "SEND_REMINDER", instruction: null }, { id: "x2", afterDays: 30, action: "CALL", instruction: "Offer a plan" }] }

describe("StrategiesTab", () => {
  it("lists strategies with who they cover and their steps", async () => {
    api.get.mockResolvedValue({ data: { data: [strat] } })
    wrap(<StrategiesTab />)
    expect(await screen.findByText("Small debts")).toBeTruthy()
    expect(screen.getByText(/balance up to R/)).toBeTruthy()
    expect(screen.getByText(/Day 30: Call the debtor, Offer a plan/)).toBeTruthy()
  })

  it("will not save a strategy without a name and saves a valid one", async () => {
    api.get.mockResolvedValue({ data: { data: [] } })
    api.post.mockResolvedValue({ data: { data: strat } })
    wrap(<StrategiesTab />)
    fireEvent.click(await screen.findByText("New strategy"))
    const save = screen.getByText("Save") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Strategy name"), { target: { value: "Everyone" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/debtcollection/strategies", expect.objectContaining({ name: "Everyone", priority: 10, quietDays: 3 })))
  })

  it("shows what would happen today without changing anything", async () => {
    api.get.mockImplementation(async (url: string) => url.endsWith("/preview")
      ? { data: { data: { considered: 1, raised: 0, skippedLate: 0, rows: [{ caseId: "c1", caseNumber: "DC-1", debtorName: "Acme", balance: 1000, daysOverdue: 35, strategyName: "Small debts", decision: "RAISE", title: "Call: DC-1", reason: "1 earlier step(s) were missed and are skipped." }] } } }
      : { data: { data: [strat] } })
    wrap(<StrategiesTab />)
    fireEvent.click(await screen.findByText("Show"))
    expect(await screen.findByText(/Will raise a task/)).toBeTruthy()
    expect(screen.getByText(/earlier step/)).toBeTruthy()
    expect(api.post).not.toHaveBeenCalled()
  })

  it("hides editing and the run button from someone who is not an admin", async () => {
    perms.held = new Set(["DEBTCOLLECTION_READ"])
    api.get.mockResolvedValue({ data: { data: [strat] } })
    wrap(<StrategiesTab />)
    await screen.findByText("Small debts")
    expect(screen.queryByText("New strategy")).toBeNull()
    expect(screen.queryByText("Edit")).toBeNull()
    expect(screen.queryByText("Raise tasks now")).toBeNull()
  })
})

describe("StrategyNote", () => {
  it("shows the strategy, the next step and what was raised", async () => {
    api.get.mockResolvedValue({ data: { data: { caseId: "c1", strategyName: "Small debts", daysOverdue: 35, balance: 1000, nextStepDay: 60, nextAction: "SEND_DEMAND", reason: "A promise to pay is open.",
      history: [{ appliedOn: "2026-10-01", strategyName: "Small debts", stepDay: 30, action: "CALL", outcome: "RAISED", daysOverdue: 31 }] } } })
    wrap(<StrategyNote caseId="c1" />)
    expect(await screen.findByText(/next: Send a formal demand at day 60/)).toBeTruthy()
    expect(screen.getByText("A promise to pay is open.")).toBeTruthy()
    expect(screen.getByText(/day 30 Call the debtor, task raised/)).toBeTruthy()
  })
})
