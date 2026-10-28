import { defaultEndType, endBody, endProblem } from "./erEnd.logic"
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EmployeeRelationsTab from "./EmployeeRelationsTab"
import {
  appealDecisionProblem, decisionProblem, dueText, erRights, fmtDate, fmtInstant, noticeProblem, openProblem, policyProblem, todaySast, words,
} from "./er.logic"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))

let perms: string[] = []
vi.mock("../../hooks/useAuth", () => ({ useAuth: () => ({ user: { userId: "u-me", permissions: perms } }) }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (data: unknown) => Promise.resolve({ data: { data } })
const BASE = "/api/v1/hr/employee-relations"

const policy = { noticeHours: 48, verbalMonths: 3, writtenMonths: 6, finalMonths: 12, appealDays: 7, grievanceResponseDays: 5, minReviewDays: 28 }
const emps = [{ id: "emp1", fullName: "Jane Dlamini", employeeNumber: "EMP-1" }, { id: "emp2", fullName: "Sipho Nkosi", employeeNumber: "EMP-2" }]
const row = (o: object = {}) => ({ id: "c1", number: "ER-2026-0001", type: "DISCIPLINARY", category: "LATENESS", title: "Late three times", status: "INVESTIGATING",
  employeeId: "emp1", employeeName: "Jane Dlamini", confidential: false, legacy: false, reportedOn: "2026-10-05", outcome: null, dueOn: null, dueLabel: null, overdue: false, ...o })
const detail = (o: object = {}) => ({
  id: "c1", number: "ER-2026-0001", type: "DISCIPLINARY", category: "LATENESS", title: "Late three times", description: "Arrived after 09:00 on three days.", status: "INVESTIGATING",
  confidential: false, legacy: false, employee: { id: "emp1", name: "Jane Dlamini" }, jobTitle: "Clerk", department: "Admin", incidentDate: "2026-10-01", reportedOn: "2026-10-05",
  owner: null, initiator: null, chair: null, respondent: null, hearing: null, decision: null, appeal: null, suspension: null, review: null, closedOn: null, closedReason: null,
  charges: [], steps: [], events: [{ action: "OPENED", note: null, visibleToEmployee: false, doneBy: "Sam", doneAt: "2026-10-05T06:00:00Z" }],
  advice: { activeWarnings: [], unknownValidity: [], guidance: null, responseDue: null, overdue: false }, actions: ["INVESTIGATE", "NOTICE", "WITHDRAW"], policy, outcomes: [], missingSteps: [], ...o,
})
const mine = (o: object = {}) => ({ id: "m1", number: "ER-2026-0007", type: "DISCIPLINARY", category: "LATENESS", title: "Late three times", description: "Arrived late.", status: "DECIDED",
  reportedOn: "2026-10-05", hearing: null, charges: [], decision: { outcome: "WRITTEN_WARNING", decidedOn: "2026-10-12", note: "Third lateness.", warningMonths: 6, warningValidUntil: "2027-04-12",
    employeeResponse: null, communicatedOn: "2026-10-13", dismissalRightsExplainedOn: null, ccmaDeadline: null, acknowledged: false, acknowledgedAt: null },
  appeal: null, canAcknowledge: true, canAppeal: true, events: [], ...o })

function routes(map: Record<string, unknown>) {
  get.mockImplementation((url: string) => {
    for (const [k, v] of Object.entries(map)) if (url === k || url.startsWith(`${k}?`)) return v instanceof Error ? Promise.reject(v) : ok(v)
    return Promise.reject(new Error(`unmocked ${url}`))
  })
}

beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); perms = [] })
afterEach(cleanup)

describe("employee relations rules in the browser", () => {
  it("works out rights", () => {
    expect(erRights(["HR_ER_READ", "HR_ER_MANAGE"])).toEqual({ read: true, manage: true, decide: false })
    expect(erRights(["HR_MANAGE"])).toEqual({ read: false, manage: false, decide: false })
    expect(erRights(["HR_ER_DECIDE"])).toEqual({ read: false, manage: false, decide: true })
  })
  it("words things the same way wherever the browser is", () => {
    expect(words("FINAL_WRITTEN_WARNING")).toBe("Final written warning")
    expect(words(null)).toBe("–")
    expect(fmtDate("2026-10-05")).toBe("5 Oct 2026")
    expect(fmtDate(null)).toBe("–")
    expect(fmtInstant("2026-10-05T06:30:00Z")).toBe("5 Oct 2026 08:30")
    expect(fmtInstant("2026-10-05T23:30:00Z")).toBe("6 Oct 2026 01:30")
    expect(todaySast(new Date("2026-10-05T23:30:00Z"))).toBe("2026-10-06")
    expect(dueText("Response due", "2026-10-09", true)).toBe("Response due 9 Oct 2026 (late)")
    expect(dueText(null, null, false)).toBe("–")
  })
  it("catches mistakes opening a case", () => {
    const now = new Date("2026-10-21T10:00:00Z")
    const f = { employeeId: "e", type: "DISCIPLINARY", category: "LATENESS", title: "Late", description: "Late three times", incidentDate: "2026-10-01" }
    expect(openProblem(f, now)).toBeNull()
    expect(openProblem({ ...f, employeeId: "" }, now)).toMatch(/employee/)
    expect(openProblem({ ...f, category: "ILL_HEALTH" }, now)).toMatch(/category/)
    expect(openProblem({ ...f, incidentDate: "2026-10-22" }, now)).toMatch(/future/)
    expect(openProblem({ ...f, title: " " }, now)).toMatch(/title/)
  })
  it("catches mistakes in a notice", () => {
    const now = new Date("2026-10-21T10:00:00Z")
    const f = { hearingDate: "2026-11-02", hearingTime: "09:00", venue: "Boardroom", chairId: "c", initiatorId: "i", charges: ["Late"] }
    expect(noticeProblem(f, "DISCIPLINARY", now)).toBeNull()
    expect(noticeProblem({ ...f, hearingDate: "2026-10-20" }, "DISCIPLINARY", now)).toMatch(/past/)
    expect(noticeProblem({ ...f, chairId: "i" }, "DISCIPLINARY", now)).toMatch(/chair/)
    expect(noticeProblem({ ...f, charges: [" "] }, "DISCIPLINARY", now)).toMatch(/charge/)
    expect(noticeProblem({ ...f, charges: [] }, "INCAPACITY", now)).toBeNull()
  })
  it("catches mistakes in a decision", () => {
    const base = { findings: { a: "GUILTY", b: "NOT_GUILTY" }, sanction: "WRITTEN_WARNING", warningMonths: "6", reasons: "Because" }
    expect(decisionProblem(base, ["a", "b"])).toBeNull()
    expect(decisionProblem({ ...base, findings: { a: "GUILTY" } }, ["a", "b"])).toMatch(/every charge/)
    expect(decisionProblem({ ...base, findings: { a: "NOT_GUILTY", b: "NOT_GUILTY" } }, ["a", "b"])).toMatch(/no sanction/)
    expect(decisionProblem({ ...base, sanction: "NOT_GUILTY" }, ["a", "b"])).toMatch(/needs a sanction/)
    expect(decisionProblem({ ...base, warningMonths: "0" }, ["a", "b"])).toMatch(/months/)
    expect(decisionProblem({ ...base, sanction: "DISMISSAL", warningMonths: "" }, ["a", "b"])).toBeNull()
    expect(decisionProblem({ ...base, reasons: "" }, ["a", "b"])).toMatch(/reasons/)
  })
  it("catches mistakes in an appeal decision", () => {
    expect(appealDecisionProblem("CONFIRMED", "", "", "Fair", "d")).toBeNull()
    expect(appealDecisionProblem("VARIED", "", "", "Fair", "d")).toMatch(/replaces/)
    expect(appealDecisionProblem("VARIED", "WRITTEN_WARNING", "", "Fair", "d")).toMatch(/months/)
    expect(appealDecisionProblem("VARIED", "COUNSELLING", "", "Fair", "d")).toBeNull()
    expect(appealDecisionProblem("CONFIRMED", "", "", "Fair", "")).toMatch(/who decides/)
  })
  it("checks the policy", () => {
    const p = { noticeHours: 48, verbalMonths: 3, writtenMonths: 6, finalMonths: 12, appealDays: 7, grievanceResponseDays: 5, minReviewDays: 28 }
    expect(policyProblem(p)).toBeNull()
    expect(policyProblem({ ...p, verbalMonths: 12 })).toMatch(/at least as long/)
    expect(policyProblem({ ...p, noticeHours: 0 })).toMatch(/Notice/)
    expect(policyProblem({ ...p, appealDays: 1.5 })).toMatch(/appeal/)
  })
})

describe("HR cases", () => {
  it("lists cases, marks late ones and filters by kind", async () => {
    perms = ["HR_ER_READ"]
    routes({ [`${BASE}/cases`]: [row(), row({ id: "c2", number: "ER-2026-0002", type: "GRIEVANCE", category: "WORKLOAD", title: "Workload", dueOn: "2026-10-09", dueLabel: "Response due", overdue: true, confidential: true })] })
    wrap(<EmployeeRelationsTab />)
    expect(await screen.findByText("ER-2026-0001")).toBeTruthy()
    expect(screen.getByText("Response due 9 Oct 2026 (late)")).toBeTruthy()
    expect(screen.getByText("Confidential")).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Open a case" })).toBeNull()
    fireEvent.change(screen.getByLabelText("Kind"), { target: { value: "GRIEVANCE" } })
    await waitFor(() => expect(get.mock.calls.some(c => String(c[0]).includes("type=GRIEVANCE"))).toBe(true))
  })

  it("opens a case and goes to it", async () => {
    perms = ["HR_ER_READ", "HR_ER_MANAGE"]
    routes({ [`${BASE}/cases`]: [], "/api/v1/hr/employees": emps, [`${BASE}/cases/c9`]: detail({ id: "c9", number: "ER-2026-0009" }) })
    post.mockResolvedValue({ data: { data: detail({ id: "c9", number: "ER-2026-0009" }) } })
    wrap(<EmployeeRelationsTab />)
    fireEvent.click(await screen.findByRole("button", { name: "Open a case" }))
    await screen.findAllByRole("option", { name: "Jane Dlamini · EMP-1" })
    fireEvent.click(screen.getByRole("button", { name: "Open case" }))
    expect(await screen.findByText("Choose the employee.")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Employee"), { target: { value: "emp1" } })
    fireEvent.change(screen.getByLabelText("Category"), { target: { value: "LATENESS" } })
    fireEvent.change(screen.getByLabelText("Short title"), { target: { value: "Late three times" } })
    fireEvent.change(screen.getByLabelText("What happened"), { target: { value: "Arrived after 09:00." } })
    fireEvent.click(screen.getByRole("button", { name: "Open case" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, body] = post.mock.calls[0] as [string, Record<string, unknown>]
    expect(url).toBe(`${BASE}/cases`)
    expect(body).toMatchObject({ employeeId: "emp1", type: "DISCIPLINARY", category: "LATENESS", title: "Late three times", respondentId: null, confidential: null })
    expect(await screen.findByText(/ER-2026-0009 · Late three times/)).toBeTruthy()
  })

  it("offers only the buttons the server allows and issues a notice", async () => {
    perms = ["HR_ER_READ", "HR_ER_MANAGE"]
    routes({ [`${BASE}/cases`]: [row()], "/api/v1/hr/employees": emps, [`${BASE}/cases/c1`]: detail() })
    post.mockResolvedValue({ data: { data: detail({ status: "NOTICE_ISSUED", actions: ["HEARING"] }) } })
    wrap(<EmployeeRelationsTab />)
    fireEvent.click(await screen.findByRole("button", { name: "ER-2026-0001" }))
    const actions = await screen.findByLabelText("Actions")
    expect(within(actions).getAllByRole("button").map(b => b.textContent)).toEqual(["Start investigation", "Issue notice of hearing", "Withdraw"])
    fireEvent.click(within(actions).getByRole("button", { name: "Issue notice of hearing" }))
    await screen.findAllByRole("option", { name: "Sipho Nkosi · EMP-2" })
    fireEvent.click(screen.getByRole("button", { name: /^Issue notice$/ }))
    expect(await screen.findByText("Give the hearing date.")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Date"), { target: { value: "2099-12-01" } })
    fireEvent.change(screen.getByLabelText("Venue"), { target: { value: "Boardroom" } })
    fireEvent.change(screen.getByLabelText("Chair"), { target: { value: "emp2" } })
    fireEvent.change(screen.getByLabelText("Charge 1"), { target: { value: "Late three times" } })
    fireEvent.click(screen.getByRole("button", { name: /^Issue notice$/ }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, body] = post.mock.calls[0] as [string, Record<string, unknown>]
    expect(url).toBe(`${BASE}/cases/c1/notice`)
    expect(body).toEqual({ hearingDate: "2099-12-01", hearingTime: "09:00", venue: "Boardroom", chairId: "emp2", initiatorId: null, charges: ["Late three times"] })
    expect(await screen.findByText("Issue notice of hearing – saved.")).toBeTruthy()
    expect(within(await screen.findByLabelText("Actions")).getAllByRole("button").map(b => b.textContent)).toEqual(["Record the hearing"])
  })

  it("shows what the server says when it refuses", async () => {
    perms = ["HR_ER_READ", "HR_ER_MANAGE"]
    routes({ [`${BASE}/cases`]: [row()], "/api/v1/hr/employees": emps, [`${BASE}/cases/c1`]: detail() })
    post.mockRejectedValue({ response: { data: { message: "The employee must get at least 48 hours' notice." } } })
    wrap(<EmployeeRelationsTab />)
    fireEvent.click(await screen.findByRole("button", { name: "ER-2026-0001" }))
    fireEvent.click(await screen.findByRole("button", { name: "Withdraw" }))
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Raised in error" } })
    fireEvent.click(screen.getByRole("button", { name: "Withdraw the case" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/48 hours/)
  })

  it("records a hearing decision with a finding on every charge", async () => {
    perms = ["HR_ER_READ", "HR_ER_DECIDE"]
    const d = detail({ status: "HEARING_HELD", actions: ["DECIDE"], charges: [{ id: "ch1", seq: 1, charge: "Late on 1 Oct", finding: null, findingNote: null }, { id: "ch2", seq: 2, charge: "Late on 2 Oct", finding: null, findingNote: null }] })
    routes({ [`${BASE}/cases`]: [row({ status: "HEARING_HELD" })], "/api/v1/hr/employees": emps, [`${BASE}/cases/c1`]: d })
    post.mockResolvedValue({ data: { data: detail({ status: "DECIDED", actions: [] }) } })
    wrap(<EmployeeRelationsTab />)
    fireEvent.click(await screen.findByRole("button", { name: "ER-2026-0001" }))
    fireEvent.click(await screen.findByRole("button", { name: "Record the decision" }))
    fireEvent.change(screen.getByLabelText("Finding on charge 1"), { target: { value: "GUILTY" } })
    fireEvent.change(screen.getByLabelText("Outcome"), { target: { value: "WRITTEN_WARNING" } })
    fireEvent.change(screen.getByLabelText("Reasons for the decision"), { target: { value: "Pattern of lateness." } })
    fireEvent.click(screen.getByRole("button", { name: "Record decision" }))
    expect(await screen.findByText("Give a finding on every charge.")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Finding on charge 2"), { target: { value: "NOT_GUILTY" } })
    fireEvent.click(screen.getByRole("button", { name: "Record decision" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, body] = post.mock.calls[0] as [string, Record<string, unknown>]
    expect(url).toBe(`${BASE}/cases/c1/decision`)
    expect(body).toEqual({ findings: [{ chargeId: "ch1", finding: "GUILTY", note: null }, { chargeId: "ch2", finding: "NOT_GUILTY", note: null }],
      sanction: "WRITTEN_WARNING", warningMonths: 6, reasons: "Pattern of lateness." })
  })

  it("shows warnings in force, late grievances and the timeline", async () => {
    perms = ["HR_ER_READ"]
    const d = detail({ type: "GRIEVANCE", category: "WORKLOAD", actions: [],
      advice: { activeWarnings: [{ caseId: "w1", number: "ER-2026-0003", sanction: "VERBAL_WARNING", issuedOn: "2026-09-01", validUntil: "2026-12-01" }],
        unknownValidity: [{ caseId: "w0", number: "ER-2025-0002", sanction: "WRITTEN_WARNING", issuedOn: "2025-01-10", validUntil: null }], guidance: "Two warnings in force.", responseDue: "2026-10-12", overdue: true } })
    routes({ [`${BASE}/cases`]: [row({ type: "GRIEVANCE" })], [`${BASE}/cases/c1`]: d })
    wrap(<EmployeeRelationsTab />)
    fireEvent.click(await screen.findByRole("button", { name: "ER-2026-0001" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/due 12 Oct 2026 and is late/)
    expect(screen.getByText(/Verbal warning \(ER-2026-0003\), given 1 Sep 2026, valid until 1 Dec 2026/)).toBeTruthy()
    expect(screen.getByText(/Written warning \(ER-2025-0002\), given 10 Jan 2025/)).toBeTruthy()
    expect(screen.getByText("Two warnings in force.")).toBeTruthy()
    expect(screen.getByText("The timeline cannot be edited or deleted.")).toBeTruthy()
    expect(screen.queryByLabelText("Actions")).toBeNull()
  })

  it("ends employment after a dismissal that the employee was told of", async () => {
    perms = ["HR_ER_READ", "HR_ER_DECIDE"]
    const d = detail({ status: "DECIDED", actions: ["END_EMPLOYMENT"], decision: { outcome: "DISMISSAL", decidedOn: "2026-10-12", note: "Guilty.", warningMonths: null, warningValidUntil: null,
      employeeResponse: null, communicatedOn: "2026-10-13", dismissalRightsExplainedOn: "2026-10-13", ccmaDeadline: "2026-11-12", acknowledged: false, acknowledgedAt: null } })
    routes({ [`${BASE}/cases`]: [row({ status: "DECIDED" })], [`${BASE}/cases/c1`]: d })
    post.mockResolvedValue({ data: { data: detail({ status: "DECIDED", actions: [] }) } })
    wrap(<EmployeeRelationsTab />)
    fireEvent.click(await screen.findByRole("button", { name: "ER-2026-0001" }))
    fireEvent.click(await screen.findByRole("button", { name: "End employment (dismissal)" }))
    expect(screen.getByText(/The next pay run pays them up to this day/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Last day of employment"), { target: { value: "2026-10-01" } })
    fireEvent.click(screen.getByRole("button", { name: "End employment" }))
    expect(await screen.findByText("The last day cannot be before the employee was told.")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("Last day of employment"), { target: { value: "2026-10-14" } })
    fireEvent.click(screen.getByRole("button", { name: "End employment" }))
    expect(await screen.findByText(/Say how the dismissal ends employment/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("How does the dismissal end employment?"), { target: { value: "EMPLOYER_NOTICE" } })
    fireEvent.change(screen.getByLabelText(/^Notice given on/), { target: { value: "2026-10-14" } })
    fireEvent.click(screen.getByRole("button", { name: "End employment" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${BASE}/cases/c1/end-employment`, { lastDay: "2026-10-14", endType: "EMPLOYER_NOTICE", noticeGivenOn: "2026-10-14" }))
  })

  it("lets someone who may decide give an old warning its validity", async () => {
    perms = ["HR_ER_READ", "HR_ER_DECIDE"]
    const d = detail({ actions: [], advice: { activeWarnings: [], guidance: null, responseDue: null, overdue: false,
      unknownValidity: [{ caseId: "w0", number: "ER-2025-0002", sanction: "WRITTEN_WARNING", issuedOn: "2025-01-10", validUntil: null }] } })
    routes({ [`${BASE}/cases`]: [row()], [`${BASE}/cases/c1`]: d })
    post.mockResolvedValue({ data: { data: d } })
    wrap(<EmployeeRelationsTab />)
    fireEvent.click(await screen.findByRole("button", { name: "ER-2026-0001" }))
    const setBtn = await screen.findByRole("button", { name: "Set validity" })
    expect((setBtn as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Months valid for ER-2025-0002"), { target: { value: "6" } })
    fireEvent.click(setBtn)
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${BASE}/cases/w0/warning-validity`, { months: 6 }))
  })

  it("does not offer to set a validity to someone who may only read", async () => {
    perms = ["HR_ER_READ"]
    const d = detail({ actions: [], advice: { activeWarnings: [], guidance: null, responseDue: null, overdue: false,
      unknownValidity: [{ caseId: "w0", number: "ER-2025-0002", sanction: "WRITTEN_WARNING", issuedOn: "2025-01-10", validUntil: null }] } })
    routes({ [`${BASE}/cases`]: [row()], [`${BASE}/cases/c1`]: d })
    wrap(<EmployeeRelationsTab />)
    fireEvent.click(await screen.findByRole("button", { name: "ER-2026-0001" }))
    expect(await screen.findByText(/Written warning \(ER-2025-0002\), given 10 Jan 2025/)).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Set validity" })).toBeNull()
  })

  it("says plainly when a case cannot be opened", async () => {
    perms = ["HR_ER_READ"]
    routes({ [`${BASE}/cases`]: [row()], [`${BASE}/cases/c1`]: Object.assign(new Error("404"), { response: { data: { message: "Case not found." } } }) })
    wrap(<EmployeeRelationsTab />)
    fireEvent.click(await screen.findByRole("button", { name: "ER-2026-0001" }))
    expect((await screen.findByRole("alert")).textContent).toBe("Case not found.")
  })

  it("saves policy changes and refuses a ladder that runs backwards", async () => {
    perms = ["HR_ER_READ", "HR_ER_MANAGE"]
    routes({ [`${BASE}/cases`]: [], [`${BASE}/policy`]: policy })
    put.mockResolvedValue({ data: { data: { ...policy, noticeHours: 72 } } })
    wrap(<EmployeeRelationsTab />)
    fireEvent.click(await screen.findByRole("tab", { name: "Policy and warnings" }))
    const hours = await screen.findByLabelText("Notice of a hearing (hours)")
    fireEvent.change(hours, { target: { value: "72" } })
    fireEvent.click(screen.getByRole("button", { name: "Save policy" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith(`${BASE}/policy`, { ...policy, noticeHours: 72 }))
    expect(await screen.findByText(/Policy saved/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Verbal warning stays valid (months)"), { target: { value: "12" } })
    expect(screen.getByText(/at least as long/)).toBeTruthy()
    expect((screen.getByRole("button", { name: "Save policy" }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("lets a reader look at policy but not change it", async () => {
    perms = ["HR_ER_READ"]
    routes({ [`${BASE}/cases`]: [], [`${BASE}/policy`]: policy })
    wrap(<EmployeeRelationsTab />)
    fireEvent.click(await screen.findByRole("tab", { name: "Policy and warnings" }))
    const hours = (await screen.findByLabelText("Notice of a hearing (hours)")) as HTMLInputElement
    expect(hours.disabled).toBe(true)
    expect(screen.queryByRole("button", { name: "Save policy" })).toBeNull()
  })
})

describe("my cases", () => {
  it("shows an employee only their own cases", async () => {
    perms = []
    routes({ [`${BASE}/mine`]: [mine()] })
    wrap(<EmployeeRelationsTab />)
    expect(await screen.findByText("ER-2026-0007")).toBeTruthy()
    expect(screen.queryByRole("tab", { name: "Cases" })).toBeNull()
    expect(screen.queryByRole("tab", { name: "Policy and warnings" })).toBeNull()
    expect(screen.getByText("Written warning")).toBeTruthy()
  })

  it("acknowledges an outcome and records an appeal", async () => {
    perms = []
    let server = mine()
    get.mockImplementation((url: string) => ok(url === `${BASE}/mine` ? [server] : server))
    post.mockImplementation((url: string) => {
      server = url.endsWith("acknowledge")
        ? mine({ canAcknowledge: false, decision: { ...mine().decision, acknowledged: true } })
        : mine({ canAcknowledge: false, canAppeal: false, appeal: { deadline: "2026-10-20", lodgedOn: "2026-10-15", grounds: "Unfair", decider: null, decidedOn: null, outcome: null, note: null } })
      return Promise.resolve({ data: { data: server } })
    })
    wrap(<EmployeeRelationsTab />)
    fireEvent.click(await screen.findByRole("button", { name: "ER-2026-0007" }))
    expect(await screen.findByText("Third lateness.")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "I have received this" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${BASE}/mine/m1/acknowledge`, {}))
    expect(await screen.findByText(/recorded that you received this/)).toBeTruthy()
    expect(screen.queryByRole("button", { name: "I have received this" })).toBeNull()
    fireEvent.click(screen.getByRole("button", { name: "Appeal" }))
    fireEvent.change(screen.getByLabelText("Why do you disagree?"), { target: { value: "Unfair" } })
    fireEvent.click(screen.getByRole("button", { name: "Send" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith(`${BASE}/mine/m1/appeal`, { grounds: "Unfair" }))
    expect(await screen.findByText("Your appeal has been recorded.")).toBeTruthy()
  })

  it("lodges a grievance even when the list of colleagues is not available", async () => {
    perms = []
    routes({ [`${BASE}/mine`]: [], "/api/v1/hr/employees": new Error("403") })
    post.mockResolvedValue({ data: { data: mine({ id: "m2", number: "ER-2026-0008", type: "GRIEVANCE", title: "Grievance: workload", canAcknowledge: false, canAppeal: false, decision: null }) } })
    get.mockImplementationOnce(() => ok([]))
    wrap(<EmployeeRelationsTab />)
    fireEvent.click(await screen.findByRole("button", { name: "Lodge a grievance" }))
    fireEvent.click(screen.getByRole("button", { name: "Lodge grievance" }))
    expect(await screen.findByText("Choose what the grievance is about.")).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText("What is it about?"), { target: { value: "WORKLOAD" } })
    fireEvent.change(screen.getByLabelText("Describe what happened"), { target: { value: "Too much work." } })
    fireEvent.click(screen.getByRole("button", { name: "Lodge grievance" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0]).toEqual([`${BASE}/mine/grievances`, { category: "WORKLOAD", description: "Too much work.", incidentDate: todaySast(), respondentId: null }])
  })
})

describe("how a dismissal ends employment", () => {
  const T = "2026-10-28"
  const f = (o = {}) => ({ lastDay: "2026-10-20", endType: "EMPLOYER_NOTICE", noticeGivenOn: "", ...o })
  it("must be with notice or summary", () => {
    expect(endProblem(f({ endType: "" }), "2026-10-13", T)).toMatch(/with notice, or summarily/)
    expect(endProblem(f({ endType: "RESIGNATION" }), "2026-10-13", T)).toMatch(/with notice, or summarily/)
    expect(endProblem(f(), "2026-10-13", T)).toBeNull()
    expect(endProblem(f({ endType: "SUMMARY_DISMISSAL" }), "2026-10-13", T)).toBeNull()
  })
  it("keeps notice dates in order", () => {
    expect(endProblem(f({ noticeGivenOn: "2026-10-12" }), "2026-10-13", T)).toMatch(/before the employee was told/)
    expect(endProblem(f({ noticeGivenOn: "2026-10-25" }), "2026-10-13", T)).toMatch(/after the last day/)
    expect(endProblem(f({ lastDay: "2026-10-12" }), "2026-10-13", T)).toMatch(/last day cannot be before/)
    expect(endProblem(f({ lastDay: "2026-11-01" }), "2026-10-13", T)).toMatch(/future/)
  })
  it("sends no notice date for a summary dismissal, and picks notice for incapacity", () => {
    expect(endBody(f({ endType: "SUMMARY_DISMISSAL", noticeGivenOn: "2026-10-14" })).noticeGivenOn).toBeNull()
    expect(endBody(f())).toEqual({ lastDay: "2026-10-20", endType: "EMPLOYER_NOTICE", noticeGivenOn: null })
    expect(defaultEndType("INCAPACITY")).toBe("EMPLOYER_NOTICE")
    expect(defaultEndType("DISCIPLINARY")).toBe("")
  })
})
