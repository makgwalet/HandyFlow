import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, within, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import DashboardPanel from "./DashboardPanel"
import { attentionTasks, buildDashboard } from "./dashboard.logic"
import { addDays, type Task } from "./tasks.logic"
import { localToday, type PipelineLead } from "./pipeline.logic"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
const TODAY = localToday()
const lead = (id: string, stage: PipelineLead["pipelineStage"], value: number | null, close: string | null = null): PipelineLead => ({ id, name: id, ownerId: null, dealValue: value, expectedCloseDate: close, pipelineStage: stage })
const task = (id: string, name: string, offset: number, completed = false): Task => ({ id, customerId: "c", customerName: name, dueDate: addDays(TODAY, offset), note: "", assignedTo: null, priority: "NORMAL", completed })
const leads = [lead("1", "QUALIFIED", 100000, addDays(TODAY, -2)), lead("2", "WON", 50000), lead("3", "NEW", 20000, addDays(TODAY, 20)), lead("4", "LOST", 9000)]
const tasks = [task("a", "Acme", -5), task("b", "Blue", -1), task("c", "Cobalt", 0), task("d", "Delta", 3), task("e", "Echo", -9, true)]
beforeEach(() => { get.mockReset() })
afterEach(cleanup)

describe("dashboard rules", () => {
  it("adds up the headline figures", () => {
    const f = buildDashboard(leads, tasks, TODAY)
    expect(f).toMatchObject({ openDeals: 2, openValue: 120000, wonValue: 50000, overdueDeals: 1, overdueTasks: 2, escalatedTasks: 1, dueToday: 1 })
    expect(f.weighted).toBe(52000)
  })
  it("lists the latest tasks first, then those due today, never done or future ones", () => {
    expect(attentionTasks(tasks, TODAY).map(t => t.id)).toEqual(["a", "b", "c"])
    expect(attentionTasks(tasks, TODAY, 2).map(t => t.id)).toEqual(["a", "b"])
  })
})

function mount(go = vi.fn(), seed?: (qc: QueryClient) => void) {
  get.mockImplementation(async (url: string) => {
    if (url.includes("customers/pipeline")) return { data: { data: leads } }
    if (url.includes("crm/tasks")) return { data: { data: { tasks, truncated: false } } }
    if (url.includes("reports/funnel")) return { data: { data: { stages: [{ stage: "NEW", reachedCount: 4, conversionFromPreviousStage: null, avgDaysInStage: null }, { stage: "WON", reachedCount: 1, conversionFromPreviousStage: 0.25, avgDaysInStage: null }, { stage: "LOST", reachedCount: 1, conversionFromPreviousStage: null, avgDaysInStage: null }], totalLeads: 4, totalWon: 1, totalLost: 1, totalStillOpen: 2, overallConversionRate: 0.25 } } }
    if (url.includes("reports/sources")) return { data: { data: [{ source: "WEBSITE", leads: 3, won: 1, lost: 0, open: 2, winRate: 100, wonValue: 50000 }] } }
    return { data: { data: { content: [], totalElements: 16 } } }
  })
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } }); seed?.(qc)
  render(<QueryClientProvider client={qc}><DashboardPanel go={go} /></QueryClientProvider>)
  return go
}

describe("DashboardPanel", () => {
  it("shows the tiles, what needs attention, the funnel and the sources", async () => {
    mount()
    expect(await screen.findByLabelText("Customers: 16")).toBeTruthy()
    expect(screen.getByLabelText("Open deals: 2")).toBeTruthy()
    expect(screen.getByLabelText("Late tasks: 2")).toBeTruthy()
    const att = within(screen.getByLabelText("Needs attention"))
    expect(att.getByText("Acme")).toBeTruthy(); expect(att.queryByText("Delta")).toBeNull()
    expect(att.getByRole("note").textContent).toMatch(/1 deal is past/)
    await waitFor(() => expect(screen.getByLabelText("New: 4")).toBeTruthy())
    expect(screen.getByLabelText("Website: 3")).toBeTruthy()
  })
  it("opens the tab behind a tile", async () => {
    const go = mount()
    fireEvent.click(await screen.findByLabelText("Open deals: 2"))
    expect(go).toHaveBeenCalledWith("pipeline")
  })
  it("does not break when the task queue has already cached its own list shape", async () => {
    mount(vi.fn(), qc => qc.setQueryData(["crm", "tasks", "open"], { tasks, truncated: false }))
    expect(await screen.findByLabelText("Late tasks: 2")).toBeTruthy()
  })
})
