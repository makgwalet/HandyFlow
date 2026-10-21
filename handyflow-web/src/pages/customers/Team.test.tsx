import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, within, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TeamPanel from "./TeamPanel"
import { buildTeam, monthOfDate, monthTitle, ownerName, shiftMonth, targetAmount, targetProblem, withTargets, UNASSIGNED } from "./team.logic"
import { addDays } from "./tasks.logic"
import { localToday, type PipelineLead } from "./pipeline.logic"
import type { Task } from "./tasks.logic"

const get = vi.fn(), put = vi.fn(), del = vi.fn()
let perms = new Set<string>()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), put: (...a: unknown[]) => put(...a), delete: (...a: unknown[]) => del(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: (s: { user: { userId: string } }) => unknown) => sel({ user: { userId: "me" } }) }))
const TODAY = localToday()
const lead = (id: string, owner: string | null, stage: PipelineLead["pipelineStage"], value: number | null, close: string | null = null): PipelineLead => ({ id, name: id, ownerId: owner, dealValue: value, expectedCloseDate: close, pipelineStage: stage })
const task = (id: string, to: string | null, offset: number, completed = false): Task => ({ id, customerId: "c", customerName: "C", dueDate: addDays(TODAY, offset), note: "", assignedTo: to, priority: "NORMAL", completed })
const targetRows = [{ ownerId: "me", target: 100000, achieved: 75000, wonDeals: 2, percent: 75 }, { ownerId: "gone", target: 20000, achieved: 0, wonDeals: 0, percent: 0 }]
const leads = [
  lead("1", "me", "QUALIFIED", 100000, addDays(TODAY, -2)), lead("2", "me", "WON", 50000), lead("3", "me", "LOST", 10000), lead("4", "me", "WON", 20000),
  lead("5", "other", "NEW", 30000), lead("6", null, "CONTACTED", null),
]
const tasks = [task("a", "me", -4), task("b", "me", 2), task("c", "other", -1), task("d", "me", -9, true), task("e", null, 1)]
beforeEach(() => { perms = new Set(["CUSTOMER_READ", "CUSTOMER_UPDATE"]); get.mockReset(); put.mockReset(); del.mockReset(); put.mockResolvedValue({ data: {} }); del.mockResolvedValue({ data: {} }) })
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
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("identity") ? { content: [{ id: "other", firstName: "Olive", lastName: "Other" }] } : url.includes("/targets") ? targetRows : url.includes("/tasks") ? { tasks, truncated: false } : leads } })); mk()
    const t = await screen.findByLabelText("Team performance"); expect(within(t).getByLabelText("You").textContent).toContain("66.7%"); expect(await within(t).findByLabelText("Olive Other")).toBeTruthy(); expect(within(t).getByLabelText("Unassigned")).toBeTruthy()
  })
  it("does not break when the task queue has already cached its own list shape", async () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } }); qc.setQueryData(["crm", "pipeline"], leads); qc.setQueryData(["crm", "tasks", "open"], { tasks, truncated: false })
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("identity") ? { content: [] } : url.includes("/targets") ? [] : url.includes("/tasks") ? { tasks, truncated: false } : leads } }))
    render(<QueryClientProvider client={qc}><TeamPanel /></QueryClientProvider>); expect(await screen.findByLabelText("Team performance")).toBeTruthy()
  })
  it("still works when the user list cannot be loaded", async () => {
    get.mockImplementation((url: string) => (url.includes("identity") ? Promise.reject(new Error("no")) : Promise.resolve({ data: { data: url.includes("/targets") ? [] : url.includes("/tasks") ? { tasks, truncated: false } : leads } }))); mk()
    expect(await screen.findByLabelText("Colleague")).toBeTruthy()
  })
  it("says so when loading fails", async () => {
    get.mockImplementation(() => Promise.reject(new Error("x"))); mk(); expect(await screen.findByText(/Couldn.t load the team figures/)).toBeTruthy()
  })
  it("shows this month's target and progress, including people who only have a target", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("identity") ? { content: [] } : url.includes("/targets") ? targetRows : url.includes("/tasks") ? { tasks, truncated: false } : leads } })); mk()
    const me = await screen.findByLabelText("You"); expect(me.textContent).toContain("75%"); expect(me.textContent).toContain("R 100"); expect(screen.getAllByLabelText("Colleague").length).toBeGreaterThan(0)
    expect(get).toHaveBeenCalledWith("/api/v1/crm/targets", { params: { period: monthOfDate(TODAY) } })
  })
  it("sets a target, refusing nonsense first", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("identity") ? { content: [] } : url.includes("/targets") ? [] : url.includes("/tasks") ? { tasks, truncated: false } : leads } })); mk()
    fireEvent.click(await screen.findByLabelText("Set target for You")); const box = screen.getByLabelText("Target for You")
    fireEvent.change(box, { target: { value: "abc" } }); fireEvent.click(screen.getByRole("button", { name: "Save" })); expect((await screen.findByRole("alert")).textContent).toContain("must be a number"); expect(put).not.toHaveBeenCalled()
    fireEvent.change(box, { target: { value: "120 000" } }); fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/crm/targets", { ownerId: "me", period: monthOfDate(TODAY), amount: 120000 }))
  })
  it("removes a target, and moves between months", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("identity") ? { content: [] } : url.includes("/targets") ? targetRows : url.includes("/tasks") ? { tasks, truncated: false } : leads } })); mk()
    fireEvent.click(await screen.findByLabelText("Remove target for You")); await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/crm/targets", { params: { ownerId: "me", period: monthOfDate(TODAY) } }))
    fireEvent.click(screen.getByLabelText("Next month")); await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/crm/targets", { params: { period: shiftMonth(monthOfDate(TODAY), 1) } }))
  })
  it("hides target editing from people who cannot update", async () => {
    perms = new Set(["CUSTOMER_READ"]); get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("identity") ? { content: [] } : url.includes("/targets") ? [] : url.includes("/tasks") ? { tasks, truncated: false } : leads } })); mk()
    await screen.findByLabelText("You"); expect(screen.queryByLabelText("Set target for You")).toBeNull()
  })
})

describe("target rules", () => {
  it("moves and names months", () => { expect(shiftMonth("2026-12", 1)).toBe("2027-01"); expect(shiftMonth("2026-01", -1)).toBe("2025-12"); expect(shiftMonth("2026-10", 14)).toBe("2027-12"); expect(monthTitle("2026-03")).toBe("March 2026"); expect(monthOfDate("2026-10-21")).toBe("2026-10") })
  it("checks typed targets", () => { expect(targetProblem("")).toMatch(/Enter/); expect(targetProblem("x")).toMatch(/number/); expect(targetProblem("0")).toMatch(/more than zero/); expect(targetProblem("-5")).toMatch(/more than zero/); expect(targetProblem("1e20")).toMatch(/too large/); expect(targetProblem("85 000.50")).toBeNull(); expect(targetAmount(" 85 000.50 ")).toBe(85000.5) })
  it("attaches targets and adds owners who only have a target", () => {
    const rows = withTargets(buildTeam(leads, tasks, TODAY), targetRows)
    expect(rows.find(r => r.owner === "me")).toMatchObject({ target: 100000, achieved: 75000, percent: 75 }); expect(rows.find(r => r.owner === "other")).toMatchObject({ target: null, achieved: 0, percent: null }); expect(rows.find(r => r.owner === "gone")).toMatchObject({ target: 20000, openDeals: 0 })
  })
})
