import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import PerformanceTab from "./PerformanceTab"
import CasePlans from "./CasePlans"
import ReviewScreen from "./PerfReviews"
import { PlanView } from "./PerfPlans"
import {
  calibrationProblem, cycleProblem, extendProblem, goalCloseProblem, goalProblem, managerProblem, perfRights, planElapsed, planProblem, ratingText,
  selfProblem, startPlanProblem, checkinProblem, closePlanProblem, feedbackProblem, progressProblem,
} from "./perf.logic"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), delete: vi.fn() } }))

let perms: string[] = []
vi.mock("../../hooks/useAuth", () => ({ useAuth: () => ({ user: { userId: "u-me", permissions: perms } }) }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (data: unknown) => Promise.resolve({ data: { data } })
const BASE = "/api/v1/hr/performance"

const me = (o: object = {}) => ({ employeeId: "emp1", name: "Jane", directReports: 0, canRead: false, canManage: false, ...o })
const row = (o: object = {}) => ({ id: "r1", cycleId: "c1", cycleName: "Annual 2026", employeeId: "emp1", employeeName: "Jane Dlamini", jobTitle: "Clerk", reviewerId: "emp9",
  reviewerName: "Sam Boss", status: "AWAITING_SELF", selfDue: "2026-11-01", managerDue: "2026-11-15", rating: null, sharedAt: null, ackAt: null, ...o })
const review = (o: object = {}, r: object = {}) => ({ row: row(r), include360: false, periodStart: "2026-01-01", periodEnd: "2026-12-31", selfRating: null, selfComment: null, selfAt: null,
  managerRating: null, managerComment: null, managerAt: null, finalRating: null, calibrated: false, calibrationReason: null, employeeComment: null, excludedReason: null,
  feedback: null, requests: [], goals: [], events: [], actions: [], ratingLabels: [], ...o })
const goal = (o: object = {}) => ({ id: "g1", employeeId: "emp1", employeeName: "Jane Dlamini", cycleId: null, cycleName: null, title: "Cut errors", description: null, measure: null,
  dueDate: "2026-12-01", status: "PROPOSED", progress: 0, overdue: false, outcomeNote: null, setByName: null, history: [], actions: [], ...o })
const plan = (o: object = {}) => ({ id: "p1", employeeId: "emp1", employeeName: "Jane Dlamini", ownerId: "emp9", ownerName: "Sam Boss", caseId: null, caseNumber: null, title: "Accuracy plan",
  status: "ACTIVE", startDate: "2026-09-01", endDate: "2026-12-01", communicatedOn: "2026-09-01", shorterThanMinimum: false, lastProgress: null, acknowledged: false, ...o })
const planDetail = (o: object = {}, r: object = {}) => ({ row: plan(r), shortfall: "Errors in payroll input.", standard: "Fewer than two errors a month.", support: "Weekly coaching.",
  closedOn: null, outcomeNote: null, ackAt: null, employeeComment: null, minReviewDays: 30, checkins: [], events: [], actions: ["CHECKIN", "CLOSE"], ...o })

function routes(map: Record<string, unknown>) {
  get.mockImplementation((url: string) => {
    for (const [k, v] of Object.entries(map)) if (url === k || url.startsWith(`${k}?`)) return v instanceof Error ? Promise.reject(v) : ok(v)
    return Promise.reject(new Error(`unmocked ${url}`))
  })
}
const base = { [`${BASE}/me`]: me(), [`${BASE}/plans`]: [], [`${BASE}/reviews`]: [], [`${BASE}/goals`]: [] }

beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); perms = [] })
afterEach(cleanup)

describe("performance rules in the browser", () => {
  it("works out rights", () => {
    expect(perfRights(["HR_PERF_READ"])).toEqual({ read: true, manage: false })
    expect(perfRights(["HR_PERF_MANAGE"])).toEqual({ read: true, manage: true })
    expect(perfRights([])).toEqual({ read: false, manage: false })
  })
  it("words ratings", () => {
    expect(ratingText(3)).toBe("3 – Meets expectations")
    expect(ratingText(null)).toBe("–")
  })
  it("checks self and manager reviews", () => {
    expect(selfProblem(null, "")).toMatch(/1 to 5/)
    expect(selfProblem(4, "")).toBeNull()
    expect(managerProblem(5, "Great")).toMatch(/20 characters/)
    expect(managerProblem(5, "Consistently excellent, with examples.")).toBeNull()
    expect(managerProblem(3, "Solid year")).toBeNull()
  })
  it("checks a calibration", () => {
    expect(calibrationProblem(3, "Too short", 3)).toMatch(/already/)
    expect(calibrationProblem(4, "short", 3)).toMatch(/too short/)
    expect(calibrationProblem(4, "Aligned with the team's results", 3)).toBeNull()
  })
  it("checks a cycle", () => {
    const f = { name: "Annual", periodStart: "2026-01-01", periodEnd: "2026-12-31", selfDue: "2027-01-10", managerDue: "2027-01-20", include360: false }
    expect(cycleProblem(f)).toBeNull()
    expect(cycleProblem({ ...f, managerDue: "2027-01-05" })).toMatch(/manager review/)
    expect(cycleProblem({ ...f, periodEnd: "2025-12-31" })).toMatch(/cannot end/)
  })
  it("checks goals and progress", () => {
    const now = new Date("2026-10-22T10:00:00Z")
    expect(goalProblem({ title: "Cut errors", description: "", measure: "", dueDate: "2026-10-01" }, true, now)).toMatch(/past/)
    expect(goalProblem({ title: "Cut errors", description: "", measure: "", dueDate: "2026-10-01" }, false, now)).toBeNull()
    expect(progressProblem("101")).toMatch(/0 to 100/)
    expect(progressProblem("40")).toBeNull()
    expect(goalCloseProblem("NOT_ACHIEVED", "")).toMatch(/too short|required/)
    expect(goalCloseProblem("ACHIEVED", "")).toBeNull()
  })
  it("checks 360 feedback", () => {
    expect(feedbackProblem(4, "ok", "")).toMatch(/specific/)
    expect(feedbackProblem(4, "Always helps the team on tight deadlines", "")).toBeNull()
  })
  it("checks a plan", () => {
    const f = { title: "Accuracy", shortfall: "Errors in payroll input", standard: "Fewer than two errors", support: "Weekly coaching", startDate: "2026-09-01", endDate: "2026-12-01" }
    expect(planProblem(f)).toBeNull()
    expect(planProblem({ ...f, endDate: "2028-01-01" })).toMatch(/year/)
    expect(planProblem({ ...f, support: "" })).toMatch(/support/)
  })
  it("keeps a plan open until the time promised has passed", () => {
    const now = new Date("2026-10-22T10:00:00Z")
    expect(closePlanProblem("NOT_MET", "Did not reach the standard.", "2026-12-01", 2, now)).toMatch(/runs until/)
    expect(closePlanProblem("NOT_MET", "Did not reach the standard.", "2026-10-01", 0, now)).toMatch(/at least one check-in/)
    expect(closePlanProblem("NOT_MET", "Did not reach the standard.", "2026-10-01", 2, now)).toBeNull()
    expect(closePlanProblem("MET", "Reached the standard.", "2026-12-01", 0, now)).toBeNull()
  })
  it("checks starting, check-ins and extending", () => {
    const now = new Date("2026-10-22T10:00:00Z")
    expect(startPlanProblem("2026-11-01", now)).toMatch(/future/)
    expect(checkinProblem("2026-08-01", "2026-09-01", "ON_TRACK", "Discussed progress.", now)).toMatch(/before the plan/)
    expect(checkinProblem("2026-10-01", "2026-09-01", "ON_TRACK", "Discussed progress.", now)).toBeNull()
    expect(extendProblem("2026-11-01", "2026-12-01", "More time needed")).toMatch(/later than/)
  })
  it("measures elapsed time", () => {
    expect(planElapsed("2026-01-01", "2026-01-11", new Date("2026-01-06T10:00:00Z"))).toBe(50)
    expect(planElapsed("2026-01-01", "2026-01-11", new Date("2027-01-01T10:00:00Z"))).toBe(100)
  })
})

describe("performance screens", () => {
  it("shows an employee only their own tabs", async () => {
    routes(base)
    wrap(<PerformanceTab />)
    expect(await screen.findByRole("tab", { name: "My reviews" })).toBeTruthy()
    expect(screen.getByRole("tab", { name: "My goals" })).toBeTruthy()
    expect(screen.queryByRole("tab", { name: "Review cycles" })).toBeNull()
    expect(screen.queryByRole("tab", { name: "My team" })).toBeNull()
    expect(screen.queryByRole("tab", { name: "My plan" })).toBeNull()
  })
  it("shows a manager the team tab and HR the cycles", async () => {
    perms = ["HR_PERF_READ"]
    routes({ ...base, [`${BASE}/me`]: me({ directReports: 3 }), [`${BASE}/plans`]: [plan()] })
    wrap(<PerformanceTab />)
    expect(await screen.findByRole("tab", { name: "My team" })).toBeTruthy()
    expect(await screen.findByRole("tab", { name: "My plan" })).toBeTruthy()
    expect(screen.getByRole("tab", { name: "Review cycles" })).toBeTruthy()
    expect(screen.getByRole("tab", { name: "Improvement plans" })).toBeTruthy()
  })
  it("lets an employee submit a self-review", async () => {
    routes({ [`${BASE}/reviews/r1`]: review({ actions: ["SELF"] }) })
    post.mockResolvedValue(ok(review({ selfRating: 4 })))
    wrap(<ReviewScreen id="r1" back={() => {}} />)
    const select = await screen.findByLabelText("How would you rate your own performance?")
    const submit = screen.getByRole("button", { name: "Submit self-review" }) as HTMLButtonElement
    expect(submit.disabled).toBe(true)
    fireEvent.change(select, { target: { value: "4" } })
    fireEvent.change(screen.getByLabelText(/Your comments/), { target: { value: "A good year." } })
    expect(submit.disabled).toBe(false)
    fireEvent.click(submit)
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${BASE}/reviews/r1/self`, { rating: 4, comment: "A good year." }))
  })
  it("hides the manager's side from the employee until the result is released", async () => {
    routes({ [`${BASE}/reviews/r1`]: review({ selfRating: 4, selfComment: "Fine", selfAt: "2026-10-20T06:00:00Z" }, { status: "SUBMITTED" }) })
    wrap(<ReviewScreen id="r1" back={() => {}} />)
    expect(await screen.findByText(/once HR releases the result/)).toBeTruthy()
    expect(screen.queryByText("Final rating")).toBeNull()
  })
  it("shows the final rating and the reason it changed once released", async () => {
    routes({ [`${BASE}/reviews/r1`]: review({ selfRating: 4, managerRating: 3, managerComment: "Solid.", finalRating: 4, calibrated: true, calibrationReason: "Aligned with team results", actions: ["ACKNOWLEDGE"] }, { status: "SHARED", rating: 4 }) })
    wrap(<ReviewScreen id="r1" back={() => {}} />)
    expect(await screen.findByText("Final rating")).toBeTruthy()
    expect(screen.getByText(/Aligned with team results/)).toBeTruthy()
    expect(screen.getByRole("button", { name: "I have read my result" })).toBeTruthy()
  })
  it("shows no 360 summary until enough people have replied", async () => {
    const feedback = { shown: false, replies: 1, average: null, strengths: [], improvements: [], asked: 4, declined: 0, minReplies: 3 }
    routes({ [`${BASE}/reviews/r1`]: review({ include360: true, feedback, requests: [{ id: "f1", relationship: "PEER", status: "SUBMITTED", providerName: "Thandi", requestedAt: "2026-10-01T06:00:00Z", respondedAt: "2026-10-05T06:00:00Z", rating: null, strengths: null, improvements: null }] }) })
    wrap(<ReviewScreen id="r1" back={() => {}} />)
    expect(await screen.findByText(/at least 3 people have replied/)).toBeTruthy()
    expect(screen.queryByText(/Average/)).toBeNull()
    expect(screen.queryByText(/HR only/)).toBeNull()
  })
  it("shows the 360 summary without names once enough people have replied", async () => {
    const feedback = { shown: true, replies: 3, average: 4.0, strengths: ["Reliable"], improvements: ["Delegates little"], asked: 4, declined: 1, minReplies: 3 }
    routes({ [`${BASE}/reviews/r1`]: review({ include360: true, feedback }, { status: "SHARED" }) })
    wrap(<ReviewScreen id="r1" back={() => {}} />)
    expect(await screen.findByText("Reliable")).toBeTruthy()
    expect(screen.getByText(/no names/)).toBeTruthy()
  })
  it("lets an employee propose a goal", async () => {
    routes(base)
    post.mockResolvedValue(ok(goal()))
    wrap(<PerformanceTab />)
    fireEvent.click(await screen.findByRole("tab", { name: "My goals" }))
    fireEvent.click(await screen.findByRole("button", { name: "Propose a goal" }))
    fireEvent.change(screen.getByLabelText("Goal"), { target: { value: "Cut errors" } })
    fireEvent.change(screen.getByLabelText("Due date"), { target: { value: "2099-01-01" } })
    fireEvent.click(screen.getByRole("button", { name: "Propose goal" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${BASE}/goals`, expect.objectContaining({ employeeId: null, title: "Cut errors", dueDate: "2099-01-01" })))
  })
  it("offers a manager the approval of a proposed goal", async () => {
    perms = []
    routes({ ...base, [`${BASE}/me`]: me({ directReports: 2 }), [`${BASE}/goals`]: [goal({ actions: ["APPROVE"] })] })
    post.mockResolvedValue(ok(goal({ status: "ACTIVE" })))
    wrap(<PerformanceTab />)
    fireEvent.click(await screen.findByRole("tab", { name: "My team" }))
    fireEvent.click(await screen.findByRole("tab", { name: "Goals" }))
    expect(await screen.findByText(/1 waiting for your approval/)).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Approve goal" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${BASE}/goals/g1/approve`, {}))
  })
  it("explains why a plan cannot yet be ended as not met", async () => {
    routes({ [`${BASE}/plans/p1`]: planDetail() })
    wrap(<PlanView id="p1" back={() => {}} />)
    fireEvent.click(await screen.findByRole("button", { name: "Close the plan" }))
    fireEvent.change(screen.getByLabelText("Outcome"), { target: { value: "NOT_MET" } })
    fireEvent.change(screen.getByLabelText("Reasons"), { target: { value: "Did not reach the standard." } })
    expect(await screen.findByText(/must have all the time they were promised/)).toBeTruthy()
    expect((screen.getByRole("button", { name: "Close plan" }) as HTMLButtonElement).disabled).toBe(true)
  })
  it("warns that a plan is shorter than policy", async () => {
    routes({ [`${BASE}/plans/p1`]: planDetail({}, { shorterThanMinimum: true }) })
    wrap(<PlanView id="p1" back={() => {}} />)
    expect(await screen.findByText(/shorter than the 30 days/)).toBeTruthy()
  })
  it("offers a plan on a poor performance case only to those who may make one", async () => {
    routes({ [`${BASE}/plans`]: [] })
    wrap(<CasePlans caseId="k1" caseType="INCAPACITY" category="POOR_PERFORMANCE" employeeId="emp1" canManage />)
    expect(await screen.findByRole("button", { name: "Make a plan" })).toBeTruthy()
    cleanup()
    wrap(<CasePlans caseId="k1" caseType="MISCONDUCT" category="OTHER" employeeId="emp1" canManage />)
    await waitFor(() => expect(get).toHaveBeenCalledTimes(2))
    expect(screen.queryByRole("button", { name: "Make a plan" })).toBeNull()
  })
  it("leaves the plans out of a case the person may not see them for", async () => {
    routes({ [`${BASE}/plans`]: new Error("403") })
    const { container } = wrap(<CasePlans caseId="k1" caseType="INCAPACITY" category="POOR_PERFORMANCE" employeeId="emp1" canManage />)
    await waitFor(() => expect(get).toHaveBeenCalled())
    await new Promise(r => setTimeout(r, 50))
    expect(container.textContent).toBe("")
  })
})
