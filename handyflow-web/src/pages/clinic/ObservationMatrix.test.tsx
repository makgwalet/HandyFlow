import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, render, screen } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a) } }))
import ObservationMatrix, { buildMatrix } from "./ObservationMatrix"

const o = (id: string, code: string, value: number, takenAt: string, extra: any = {}) =>
  ({ id, code, label: code === "WEIGHT" ? "Weight" : "Pulse", unit: code === "WEIGHT" ? "kg" : "bpm", value, takenAt, ...extra })
const obs = [
  o("1", "WEIGHT", 80, "2026-10-05T09:00:00Z"), o("2", "WEIGHT", 82, "2026-10-01T09:00:00Z"),
  o("3", "PULSE", 70, "2026-10-05T09:05:00Z"), o("4", "PULSE", 99, "2026-10-05T10:00:00Z", { abnormalFlag: "HIGH" }),
  o("5", "PULSE", 11, "2026-10-01T09:00:00Z", { status: "VOIDED" }),
]
const show = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ObservationMatrix patientId="p1" /></QueryClientProvider>)
beforeEach(() => { get.mockReset(); get.mockResolvedValue({ data: { data: obs } }) })
afterEach(cleanup)

describe("buildMatrix", () => {
  it("uses newest days first, the latest reading of a day, and ignores voided readings", () => {
    const m = buildMatrix(obs as any)
    expect(m.days).toEqual(["2026-10-05", "2026-10-01"])
    const pulse = m.rows.find(r => r.code === "PULSE")!
    expect(pulse.cells.map(c => c && c.value)).toEqual([99, null])
    expect(pulse.trend).toEqual([99])
    expect(m.rows.find(r => r.code === "WEIGHT")!.trend).toEqual([82, 80])
  })
  it("limits the number of days shown", () => {
    const many = Array.from({ length: 10 }, (_, i) => o(String(i), "WEIGHT", 70 + i, `2026-09-${String(10 + i).padStart(2, "0")}T08:00:00Z`))
    expect(buildMatrix(many as any, 4).days).toHaveLength(4)
  })
  it("drops readings that are not numbers", () => {
    expect(buildMatrix([o("x", "WEIGHT", NaN, "2026-10-05T09:00:00Z")] as any).rows).toEqual([])
  })
})

describe("ObservationMatrix", () => {
  it("shows the grid and marks only server-flagged values", async () => {
    show()
    expect(await screen.findByText("Weight")).toBeTruthy()
    expect(screen.getByText("99").getAttribute("title")).toMatch(/Flagged high/)
    expect(screen.getByText("80").getAttribute("title")).toBeNull()
  })
  it("says so when nothing is recorded", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    show()
    expect(await screen.findByText("No measurements recorded yet.")).toBeTruthy()
  })
})
