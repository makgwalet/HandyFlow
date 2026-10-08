import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, render, screen } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a) } }))
const perms: Record<string, boolean> = {}
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => !!perms[p] }))
vi.mock("recharts", () => {
  const Pass = ({ children }: any) => <div>{children}</div>
  return { ResponsiveContainer: Pass, ComposedChart: Pass, CartesianGrid: () => null, XAxis: () => null, YAxis: () => null,
    Tooltip: () => null, Legend: () => null, Line: () => null, Scatter: () => null }
})
import GrowthTab from "./GrowthTab"

const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><GrowthTab patientId="p1"/></QueryClientProvider>)
const chart = (over: object = {}) => ({ patientId: "p1", sexAtBirth: "MALE", currentAgeMonths: 7, notes: [], measures: [
  { code: "WEIGHT", label: "Weight", unit: "kg", banner: "DATA NOT CLINICALLY APPROVED", curves: null,
    measurements: [{ observationId: "o1", takenAt: "2026-07-01T06:00:00Z", ageMonths: 6, value: 6.5, zScore: null, percentile: null }] },
  { code: "HEIGHT", label: "Height", unit: "cm", banner: "DATA NOT CLINICALLY APPROVED", curves: null, measurements: [] }], ...over })

afterEach(cleanup)
beforeEach(() => { get.mockReset(); for (const k of Object.keys(perms)) delete perms[k] })

describe("GrowthTab", () => {
  it("does not load anything without the growth permission", () => {
    wrap()
    expect(screen.getByText(/do not have permission/)).toBeTruthy()
    expect(get).not.toHaveBeenCalled()
  })
  it("shows measurements and the not-approved banner when no reference set is active", async () => {
    perms.CLINIC_GROWTH_READ = true
    get.mockResolvedValue({ data: { data: chart() } })
    wrap()
    await screen.findByText("6.5 kg")
    expect(screen.getAllByText("DATA NOT CLINICALLY APPROVED").length).toBe(2)
    expect(screen.getByText(/No height recorded/)).toBeTruthy()
    expect(screen.queryByText(/SD$/)).toBeNull()
  })
  it("shows z-score, percentile and the source when an approved set supplies them", async () => {
    perms.CLINIC_GROWTH_READ = true
    get.mockResolvedValue({ data: { data: chart({ measures: [{ code: "WEIGHT", label: "Weight", unit: "kg", banner: null,
      curves: { setId: "s", source: "Test source", sourceVersion: "2", zLines: [0], points: [{ ageMonths: 0, values: [3] }, { ageMonths: 12, values: [9] }] },
      measurements: [{ observationId: "o1", takenAt: "2026-07-01T06:00:00Z", ageMonths: 6, value: 6.5, zScore: 0.5, percentile: 69.1 }] }] }) } })
    wrap()
    await screen.findByText("+0.50 SD")
    expect(screen.getByText("69")).toBeTruthy()
    expect(screen.getByText("Reference: Test source, version 2")).toBeTruthy()
    expect(screen.queryByText("DATA NOT CLINICALLY APPROVED")).toBeNull()
  })
  it("reports a failed load", async () => {
    perms.CLINIC_GROWTH_READ = true
    get.mockRejectedValue(new Error("x"))
    wrap()
    await screen.findByText(/Could not load the growth chart/)
  })
})
