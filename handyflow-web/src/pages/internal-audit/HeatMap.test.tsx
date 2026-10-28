import { describe, it, expect, vi, afterEach, beforeEach } from "vitest"
import { render, screen, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

vi.mock("../../api/auditHeatMap.api", () => ({ auditHeatMapApi: { get: vi.fn() } }))
import { auditHeatMapApi } from "../../api/auditHeatMap.api"
import { HeatMapSection } from "./HeatMapSection"

const m = auditHeatMapApi as unknown as { get: ReturnType<typeof vi.fn> }
afterEach(cleanup)
beforeEach(() => vi.clearAllMocks())
const mount = (onGo = vi.fn()) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><HeatMapSection onGo={onGo} /></QueryClientProvider>)

const cells = [] as { inherent: number; control: number; count: number; areas: string[] }[]
for (let i = 5; i >= 1; i--) for (let c = 1; c <= 5; c++) cells.push({ inherent: i, control: c, count: i === 5 && c === 4 ? 2 : 0, areas: i === 5 && c === 4 ? ["Payroll", "Treasury"] : [] })
const map = { cells, assessed: 2, notAssessed: 1, total: 3, byProcessArea: [
  { processArea: "Finance", low: 0, medium: 0, high: 1, critical: 1, notAssessed: 0, total: 2 }, { processArea: "Fleet", low: 0, medium: 0, high: 0, critical: 0, notAssessed: 1, total: 1 }] }

describe("Risk heat map", () => {
  it("shows the figures and the areas in a square when it is picked", async () => {
    m.get.mockResolvedValue(map)
    mount()
    const square = await screen.findByLabelText("Inherent risk very high, control risk high: 2 areas: Payroll, Treasury")
    fireEvent.click(square)
    expect(screen.getByText("Payroll")).toBeTruthy()
    expect(screen.getByText("Treasury")).toBeTruthy()
    expect(screen.getByText("100%")).toBeTruthy()
    fireEvent.click(square)
    expect(screen.queryByText("Treasury")).toBeNull()
  })
  it("has no button on an empty square", async () => {
    m.get.mockResolvedValue(map)
    mount()
    expect((await screen.findByLabelText("Inherent risk moderate, control risk low: no areas") as HTMLButtonElement).disabled).toBe(true)
  })
  it("lists process areas with their counts on the second tab", async () => {
    m.get.mockResolvedValue(map)
    mount()
    fireEvent.click(await screen.findByRole("tab", { name: "By process area" }))
    expect(screen.getByText("Finance")).toBeTruthy()
    expect(screen.getByText("Fleet")).toBeTruthy()
  })
  it("sends the user to the universe from the not-assessed tile", async () => {
    m.get.mockResolvedValue(map)
    const go = vi.fn()
    mount(go)
    fireEvent.click((await screen.findByText("Not assessed")).closest("button")!)
    expect(go).toHaveBeenCalledWith("universe")
  })
  it("says what to do before anything is assessed, and when the load fails", async () => {
    m.get.mockResolvedValue({ ...map, assessed: 0, notAssessed: 3, cells: cells.map(c => ({ ...c, count: 0, areas: [] })) })
    mount()
    expect(await screen.findByText(/No area has been assessed yet/)).toBeTruthy()
    cleanup()
    m.get.mockRejectedValue(new Error("x"))
    mount()
    expect((await screen.findByRole("alert")).textContent).toContain("could not be loaded")
  })
})
