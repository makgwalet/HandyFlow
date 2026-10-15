import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: vi.fn(), put: vi.fn(), patch: vi.fn(), delete: vi.fn() } }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: perms } }) }))

import { UtilisationReport, CustomerRevenueReport, DowntimeReport, ComplianceReport } from "./ReportsTab"
import { complianceRows, complianceTotals } from "./shared/compliance"

afterEach(cleanup)
const mount = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const row = (over: Record<string, unknown> = {}) => ({ assetId: "a1", machine: "D9-001 — CAT D9", shifts: 3, hours: 24.5, daysWorked: 3, daysWorkedPercent: 10, ...over })
const plan = (id: string, assetId: string, state: string, over: Record<string, unknown> = {}) => ({ id, assetId, name: "P" + id, state, remaining: 10, unit: "hrs", active: true, ...over })
const asset = (id: string, over: Record<string, unknown> = {}) => ({ id, name: "Machine " + id, fleetNumber: "F" + id, status: "AVAILABLE", ...over })

beforeEach(() => { vi.clearAllMocks(); perms = ["EARTHMOVING_READ"] })

describe("compliance calculation", () => {
  it("lists the worst machines first and leaves out retired or plan-less machines", () => {
    const rows = complianceRows(
      [asset("1"), asset("2"), asset("3"), asset("4", { status: "RETIRED" })] as never,
      [plan("a", "1", "OK") , plan("b", "1", "OVERDUE"), plan("c", "2", "OK"), plan("d", "2", "DUE_SOON"), plan("e", "4", "OVERDUE"), plan("f", "1", "DUE", { active: false })] as never)
    expect(rows.map(r => r.assetId)).toEqual(["1", "2"])
    expect(rows[0]).toMatchObject({ plans: 2, ok: 1, overdue: 1, inOrderPercent: 50 })
    expect(rows[1].inOrderPercent).toBe(100)
    expect(complianceTotals(rows)).toMatchObject({ machines: 2, plans: 4, overdue: 1, inOrderPercent: 75 })
    expect(complianceTotals([]).inOrderPercent).toBeNull()
  })
})

describe("period reports", () => {
  it("shows hours per machine", async () => {
    get.mockResolvedValue({ data: { data: { from: "2026-10-01", to: "2026-10-15", days: 15, totalHours: 24.5, machines: [row()] } } })
    mount(<UtilisationReport />)
    expect(await screen.findByText("D9-001 — CAT D9")).toBeTruthy()
    expect(screen.getByText("24.5 hrs")).toBeTruthy()
    expect(screen.getByText("10.0%")).toBeTruthy()
    expect(String(get.mock.calls[0][0])).toContain("/reports/machine-utilisation?from=")
  })
  it("shows revenue per customer to an administrator and refuses everyone else", async () => {
    perms = ["EARTHMOVING_READ"]
    mount(<CustomerRevenueReport />)
    expect(screen.getByText(/needs Earthmoving administrator access/)).toBeTruthy()
    expect(get).not.toHaveBeenCalled()
    cleanup()
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    const r = { customerId: "c1", customer: "Acme Civils", charges: 2, revenue: 1000, invoiced: 400, notInvoiced: 600, sharePercent: 100 }
    get.mockResolvedValue({ data: { data: { from: "2026-10-01", to: "2026-10-15", totals: { ...r, customer: "All customers" }, customers: [r] } } })
    mount(<CustomerRevenueReport />)
    expect(await screen.findByText("Acme Civils")).toBeTruthy()
    expect(screen.getByText("All customers")).toBeTruthy()
  })
  it("shows downtime and lets the period be changed", async () => {
    const d = { assetId: "a1", machine: "T-01", breakdowns: 2, breakdownHours: 30, maintenanceHours: 6, totalHours: 36, availabilityPercent: 90 }
    get.mockResolvedValue({ data: { data: { from: "2026-10-01", to: "2026-10-15", totals: { ...d, machine: "All machines" }, machines: [d] } } })
    mount(<DowntimeReport />)
    expect(await screen.findByText("T-01")).toBeTruthy()
    expect(screen.getByText("90.0%")).toBeTruthy()
    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2026-09-01" } })
    await waitFor(() => expect(get.mock.calls.some(c => String(c[0]).includes("downtime?from=2026-09-01"))).toBe(true))
  })
  it("says so when a report cannot be loaded", async () => {
    get.mockRejectedValue(new Error("x"))
    mount(<DowntimeReport />)
    expect(await screen.findByText(/Could not load the report/)).toBeTruthy()
  })
})

describe("ComplianceReport", () => {
  it("shows how many service plans are in order", async () => {
    get.mockImplementation((url: string) => Promise.resolve(url.includes("maintenance-plans")
      ? { data: { data: [plan("a", "1", "OVERDUE"), plan("b", "1", "OK")] } }
      : { data: { data: { content: [asset("1")], last: true } } }))
    mount(<ComplianceReport />)
    expect(await screen.findByText("F1 — Machine 1")).toBeTruthy()
    expect(screen.getAllByText("50.0%").length).toBeGreaterThan(0)
  })
})
