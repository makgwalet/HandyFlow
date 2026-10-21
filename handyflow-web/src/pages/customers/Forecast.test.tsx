import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ForecastPanel from "./ForecastPanel"
import { buildForecast, monthLabel, monthOf, weighted, NO_DATE, OVERDUE } from "./forecast.logic"
import type { PipelineLead } from "./pipeline.logic"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
const lead = (id: string, stage: PipelineLead["pipelineStage"], value: number | null, close: string | null): PipelineLead => ({ id, name: `Lead ${id}`, ownerId: null, dealValue: value, expectedCloseDate: close, pipelineStage: stage })
const TODAY = "2026-10-21"
const leads = [
  lead("a", "QUALIFIED", 100000, "2026-11-15"), lead("b", "NEW", 50000, "2026-11-30"), lead("c", "CONTACTED", 40000, "2026-12-01"),
  lead("d", "QUALIFIED", 20000, "2026-09-30"), lead("e", "NEW", null, null), lead("f", "WON", 99999, "2026-11-01"), lead("g", "LOST", 99999, "2026-11-01"),
]
beforeEach(() => { get.mockReset() })
afterEach(cleanup)

describe("forecast rules", () => {
  it("weights a deal by the chance at its stage", () => {
    expect(weighted(leads[0])).toBe(50000); expect(weighted(leads[1])).toBe(5000); expect(weighted(leads[2])).toBe(10000); expect(weighted(leads[4])).toBe(0)
  })
  it("names months and handles missing dates", () => {
    expect(monthOf(leads[0])).toBe("2026-11"); expect(monthOf(leads[4])).toBe(NO_DATE); expect(monthLabel("2026-11")).toBe("November 2026"); expect(monthLabel(NO_DATE)).toBe("No close date")
  })
  it("ignores won and lost, puts late deals first and undated last", () => {
    const f = buildForecast(leads, TODAY)
    expect(f.deals).toBe(5); expect(f.months.map(m => m.month)).toEqual([OVERDUE, "2026-11", "2026-12", NO_DATE])
    expect(f.months[1]).toEqual({ month: "2026-11", deals: 2, total: 150000, weighted: 55000 })
    expect(f.total).toBe(210000); expect(f.weighted).toBe(50000 + 5000 + 10000 + 10000); expect(f.noValue).toBe(1); expect(f.undated).toBe(1)
    expect(buildForecast([], TODAY).deals).toBe(0)
  })
})

describe("ForecastPanel", () => {
  it("shows the totals, the months and the caveats", async () => {
    get.mockResolvedValue({ data: { data: leads } }); render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ForecastPanel /></QueryClientProvider>)
    const t = await screen.findByLabelText("Forecast by month"); expect(within(t).getByLabelText("November 2026").textContent).toContain("2")
    expect(screen.getByLabelText("Open deals").textContent).toContain("5"); expect(screen.getByRole("note").textContent).toContain("no value"); expect(screen.getByText(/rules of thumb/)).toBeTruthy()
  })
  const mk = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ForecastPanel /></QueryClientProvider>)
  it("says so when there is nothing open", async () => {
    get.mockResolvedValue({ data: { data: [lead("w", "WON", 10, null)] } }); mk(); expect(await screen.findByText(/nothing to forecast/)).toBeTruthy()
  })
  it("says so when loading fails", async () => {
    get.mockRejectedValue(new Error("down")); mk(); expect(await screen.findByText(/Couldn.t load the pipeline/)).toBeTruthy()
  })
})
