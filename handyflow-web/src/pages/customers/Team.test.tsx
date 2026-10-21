import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TeamPanel from "./TeamPanel"
import { buildTeam, ownerName, UNASSIGNED } from "./team.logic"
import { addDays } from "./tasks.logic"
import { localToday, type PipelineLead } from "./pipeline.logic"
import type { Task } from "./tasks.logic"

const get = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a) } }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: (s: { user: { userId: string } }) => unknown) => sel({ user: { userId: "me" } }) }))
const TODAY = localToday()
const lead = (id: string, owner: string | null, stage: PipelineLead["pipelineStage"], value: number | null, close: string | null = null): PipelineLead => ({ id, name: id, ownerId: owner, dealValue: value, expectedCloseDate: close, pipelineStage: stage })
const task = (id: string, to: string | null, offset: number, completed = false): Task => ({ id, customerId: "c", customerName: "C", dueDate: addDays(TODAY, offset), note: "", assignedTo: to, priority: "NORMAL", completed })
const leads = [
  lead("1", "me", "QUALIFIED", 100000, addDays(TODAY, -2)), lead("2", "me", "WON", 50000), lead("3", "me", "LOST", 10000), lead("4", "me", "WON", 20000),
  lead("5", "other", "NEW", 30000), lead("6", null, "CONTACTED", null),
]
const tasks = [task("a", "me", -4), task("b", "me", 2), task("c", "other", -1), task("d", "me", -9, true), task("e", null, 1)]
beforeEach(() => { get.mockReset() })
afterEach(cleanup)

describe("team rules", () => {
  it("adds up each owner's deals and tasks", () => {
    const rows = buildTeam(leads, tasks, TODAY), me = rows.find(r => r.owner === "me")!
    expect(rows.map(r => r.owner)).toEqual(["me", "other", UNASSIGNED])
    expect(me).toMatchObject({ openDeals: 1, openValue: 100000, weighted: 50000, won: 2, wonValue: 70000, lost: 1, overdueDeals: 1, openTasks: 2, lateTasks: 1 })
    expect(me.winRate).toBe(66.7); expect(rows[1].winRate).toBeNull(); expect(rows[2]).toMatchObject({ openDeals: 1, openValue: 0, openTasks: 1, lateTasks: 0 })
    expect(buildTeam([], [], TODAY)).toEqual([])
  })
  it("names owners", () => { expect(ownerName("me", "me", {})).toBe("You"); expect(ownerName(UNASSIGNED, "me", {})).toBe("Unassigned"); expect(ownerName("o", "me", { o: "Olive" })).toBe("Olive"); expect(ownerName("z", "me", {})).toBe("Colleague") })
})

describe("TeamPanel", () => {
  const mk = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><TeamPanel /></QueryClientProvider>)
  it("shows a row per person with real names", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("identity") ? { content: [{ id: "other", firstName: "Olive", lastName: "Other" }] } : url.includes("/tasks") ? { tasks, truncated: false } : leads } })); mk()
    const t = await screen.findByLabelText("Team performance"); expect(within(t).getByLabelText("You").textContent).toContain("66.7%"); expect(await within(t).findByLabelText("Olive Other")).toBeTruthy(); expect(within(t).getByLabelText("Unassigned")).toBeTruthy()
  })
  it("still works when the user list cannot be loaded", async () => {
    get.mockImplementation((url: string) => (url.includes("identity") ? Promise.reject(new Error("no")) : Promise.resolve({ data: { data: url.includes("/tasks") ? { tasks, truncated: false } : leads } }))); mk()
    expect(await screen.findByLabelText("Colleague")).toBeTruthy()
  })
  it("says so when loading fails", async () => {
    get.mockImplementation(() => Promise.reject(new Error("x"))); mk(); expect(await screen.findByText(/Couldn.t load the team figures/)).toBeTruthy()
  })
})
