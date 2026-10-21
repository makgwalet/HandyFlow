import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import OnboardingTab from "./OnboardingTab"
import { canReopen, canTick, canWaive, dueLabel, groupByCategory, onboardingRights, progressText, reasonProblem, taskProblem } from "./onboarding.logic"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))

let perms: string[] = []
vi.mock("../../hooks/useAuth", () => ({ useAuth: () => ({ user: { userId: "u-me", permissions: perms } }) }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (data: unknown) => Promise.resolve({ data: { data } })

const summary = { id: "o1", employeeId: "e1", employeeNumber: "EMP-1", employeeName: "Jane Dlamini", jobTitle: "Clerk", department: "Finance",
  startDate: "2026-11-02", probationEndDate: "2027-02-02", status: "IN_PROGRESS", total: 3, finished: 1, requiredOpen: 2, overdue: 0, percent: 33 }
const task = (o: object) => ({ id: "t", title: "Task", description: null, category: "CONTRACT", required: true, dueDate: null, automatic: false, check: null,
  assigneeId: null, status: "OPEN", overdue: false, note: null, ...o })
const detail = (over: object = {}) => ({ summary, cancelReason: null, canManage: true, finishProblems: ['"Sign contract" is still open.'], tasks: [
  task({ id: "t1", title: "Sign contract" }),
  task({ id: "t2", title: "ID number recorded", category: "DOCUMENTS", automatic: true, check: "ID_NUMBER" }),
  task({ id: "t3", title: "Induction", category: "INDUCTION", status: "DONE" }),
], ...over })

describe("onboarding rules in the browser", () => {
  it("works out rights", () => {
    expect(onboardingRights(["HR_ONBOARDING_MANAGE"])).toEqual({ manage: true, view: true })
    expect(onboardingRights(["HR_TEAM_READ"])).toEqual({ manage: false, view: true })
    expect(onboardingRights(["INVOICE_READ"])).toEqual({ manage: false, view: false })
  })
  it("decides who can tick, waive and reopen", () => {
    const open = { status: "OPEN", automatic: false, assigneeId: "u-me" }
    expect(canTick(open, false, "u-me")).toBe(true)
    expect(canTick(open, false, "u-other")).toBe(false)
    expect(canTick(open, true, "u-other")).toBe(true)
    expect(canTick({ ...open, automatic: true }, true, "u-me")).toBe(false)
    expect(canTick({ ...open, status: "DONE" }, true, "u-me")).toBe(false)
    expect(canWaive({ status: "OPEN" }, true)).toBe(true)
    expect(canWaive({ status: "OPEN" }, false)).toBe(false)
    expect(canReopen({ status: "DONE", automatic: false }, true)).toBe(true)
    expect(canReopen({ status: "DONE", automatic: true }, true)).toBe(false)
  })
  it("words due dates", () => {
    const today = new Date(2026, 10, 10)
    expect(dueLabel({ dueDate: "2026-11-10", status: "OPEN" }, today)).toBe("Due today")
    expect(dueLabel({ dueDate: "2026-11-11", status: "OPEN" }, today)).toBe("Due in 1 day")
    expect(dueLabel({ dueDate: "2026-11-13", status: "OPEN" }, today)).toBe("Due in 3 days")
    expect(dueLabel({ dueDate: "2026-11-08", status: "OPEN" }, today)).toBe("2 days overdue")
    expect(dueLabel({ dueDate: "2026-11-08", status: "DONE" }, today)).toBe("")
    expect(dueLabel({ dueDate: null, status: "OPEN" }, today)).toBe("")
  })
  it("groups tasks in the usual order and drops empty groups", () => {
    const g = groupByCategory([{ category: "INDUCTION" }, { category: "CONTRACT" }, { category: "OTHER" }])
    expect(g.map(x => x.category)).toEqual(["CONTRACT", "INDUCTION", "OTHER"])
  })
  it("words progress and catches empty reasons and titles", () => {
    expect(progressText({ finished: 1, total: 3, requiredOpen: 2 })).toBe("1 of 3 done, 2 required still open")
    expect(progressText({ finished: 3, total: 3, requiredOpen: 0 })).toBe("3 of 3 done, all required tasks done")
    expect(reasonProblem(" ", "for cancelling")).toMatch(/reason/)
    expect(reasonProblem("x".repeat(501), "x")).toMatch(/500/)
    expect(reasonProblem("fine", "x")).toBeNull()
    expect(taskProblem("")).toMatch(/title/)
    expect(taskProblem("Issue laptop")).toBeNull()
  })
})

describe("OnboardingTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); perms = ["HR_ONBOARDING_MANAGE"]
    get.mockImplementation((url: string) => (url.endsWith("/onboardings") ? ok([summary]) : ok(detail())))
    post.mockImplementation(() => ok(detail()))
  })
  afterEach(() => cleanup())

  it("tells people without access", () => {
    perms = []
    wrap(<OnboardingTab />)
    expect(screen.getByText(/do not have access/i)).toBeTruthy()
    expect(get).not.toHaveBeenCalled()
  })

  it("lists onboardings with progress and opens one", async () => {
    wrap(<OnboardingTab />)
    expect(await screen.findByText(/Jane Dlamini/)).toBeTruthy()
    expect(screen.getByText(/1 of 3 done, 2 required still open/)).toBeTruthy()
    fireEvent.click(screen.getByText(/Jane Dlamini/))
    expect(await screen.findByText("Sign contract")).toBeTruthy()
    expect(screen.getByText(/Checked: ID number on the record/)).toBeTruthy()
  })

  it("offers Done only on tasks that can be ticked", async () => {
    wrap(<OnboardingTab />)
    fireEvent.click(await screen.findByText(/Jane Dlamini/))
    await screen.findByText("Sign contract")
    expect(screen.getAllByRole("button", { name: "Done" })).toHaveLength(1)
    fireEvent.click(screen.getByRole("button", { name: "Done" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/onboarding-tasks/t1/complete", {}))
  })

  it("will not waive without a reason", async () => {
    wrap(<OnboardingTab />)
    fireEvent.click(await screen.findByText(/Jane Dlamini/))
    await screen.findByText("Sign contract")
    fireEvent.click(screen.getAllByRole("button", { name: "Waive" })[0])
    fireEvent.click(screen.getByRole("button", { name: "Waive task" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/reason/i)
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText(/does not apply/i), { target: { value: "Contractor" } })
    fireEvent.click(screen.getByRole("button", { name: "Waive task" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/onboarding-tasks/t1/waive", { note: "Contractor" }))
  })

  it("keeps Finish switched off while required tasks are open and says why", async () => {
    wrap(<OnboardingTab />)
    fireEvent.click(await screen.findByText(/Jane Dlamini/))
    expect(await screen.findByText(/To finish:/)).toBeTruthy()
    expect((screen.getByRole("button", { name: "Finish onboarding" }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("hides management controls from someone who only reads", async () => {
    perms = ["HR_READ"]
    get.mockImplementation((url: string) => (url.endsWith("/onboardings") ? ok([summary]) : ok(detail({ canManage: false }))))
    wrap(<OnboardingTab />)
    fireEvent.click(await screen.findByText(/Jane Dlamini/))
    await screen.findByText("Sign contract")
    expect(screen.queryByRole("button", { name: "Waive" })).toBeNull()
    expect(screen.queryByRole("button", { name: "Finish onboarding" })).toBeNull()
  })
})
