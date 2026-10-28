import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ReportsTab from "./ReportsTab"
import { complianceRows, payrollRows, workforceRows } from "./reports.csv"
import { defaultPeriod, groupText, pct, periodProblem, presets, rand, reportFileName, reportRights, toCsv } from "./reports.logic"

const get = vi.fn()
const post = vi.fn()
const del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), delete: (...a: unknown[]) => del(...a) } }))

let perms: string[] = []
vi.mock("../../hooks/useAuth", () => ({ useAuth: () => ({ user: { userId: "u-me", permissions: perms } }) }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (data: unknown) => Promise.resolve({ data: { data } })
const BASE = "/api/v1/hr/reports"

const g = (label: string, count: number | null, hidden = false) => ({ label, count, hidden })
const workforce = (o: object = {}) => ({
  from: "2026-01-01", to: "2026-10-22", opening: 17, closing: 20, joiners: 6, leavers: 3, turnoverPercent: 16.2, annualisedTurnoverPercent: 19.6,
  byDepartment: [g("Finance", 12), g("Operations", 8)], byEmploymentType: [g("Permanent", 14)], tenure: [g("Under 1 year", 6)], age: [g("35 to 44", 12)],
  leaversByReason: [g("Resigned", 2)], demographicsShown: false, gender: [], race: [], disability: [], notes: ["Headcount counts everyone employed on the day."], ...o,
})
const payroll = (o: object = {}) => ({
  from: "2026-01-01", to: "2026-10-22", runs: [
    { number: "PR-1", periodStart: "2026-08-01", periodEnd: "2026-08-31", payDate: "2026-08-31", status: "FINALISED", employees: 10, gross: 100000, paye: 18000, uifEmployee: 1000, uifEmployer: 1000, sdl: 1000, net: 81000, employerCost: 102000, counted: true },
    { number: "PR-2", periodStart: "2026-09-01", periodEnd: "2026-09-30", payDate: "2026-09-30", status: "PREPARED", employees: 10, gross: 100000, paye: 18000, uifEmployee: 1000, uifEmployer: 1000, sdl: 1000, net: 81000, employerCost: 102000, counted: false },
  ],
  totals: { gross: 100000, overtime: 2000, paye: 18000, uifEmployee: 1000, uifEmployer: 1000, sdl: 1000, net: 81000, employerCost: 102000, runs: 1 },
  byDepartment: [{ label: "Finance", count: 6, amount: 70000, hours: null }], emp201: [], notes: [], ...o,
})
const compliance = (o: object = {}) => ({
  from: "2026-01-01", to: "2026-10-22", checks: [{ code: "NO_ID", title: "No ID number", count: 2, people: ["E1 Ann Dlamini"], why: "Needed for the tax certificate." },
    { code: "NO_TAX", title: "No tax number", count: 0, people: [], why: "Needed for PAYE." }],
  employeeRelations: { opened: 4, closed: 3, open: 2, longOpen: 1, hearingsOverdue: 0, warningsExpiringSoon: 1, averageDaysToClose: 41.3, byType: [g("Disciplinary", 3)], byCategory: [], outcomes: [], confidentialLeftOut: 2 },
  notes: [], ...o,
})

function routes(map: Record<string, unknown>) {
  const all = { "/api/v1/hr/saved-reports": [], ...map }
  get.mockImplementation((url: string) => {
    for (const [k, v] of Object.entries(all)) if (url.startsWith(k)) return v instanceof Error ? Promise.reject(v) : ok(v)
    return Promise.reject(new Error(`unmocked ${url}`))
  })
}

beforeEach(() => { get.mockReset(); post.mockReset(); del.mockReset(); perms = ["HR_REPORT_READ"] })
afterEach(cleanup)

describe("report rules in the browser", () => {
  it("works out rights", () => {
    expect(reportRights(["HR_REPORT_READ"])).toEqual({ read: true, payroll: false, employeeRelations: false, sensitive: false })
    expect(reportRights(["HR_REPORT_READ", "HR_COMPENSATION_READ", "HR_ER_READ", "HR_SENSITIVE_READ"])).toEqual({ read: true, payroll: true, employeeRelations: true, sensitive: true })
    expect(reportRights([]).read).toBe(false)
  })
  it("offers quick periods worked out from today", () => {
    const now = new Date("2026-10-22T08:00:00Z")
    const p = Object.fromEntries(presets(now).map(x => [x.id, x.period]))
    expect(p.month).toEqual({ from: "2026-10-01", to: "2026-10-22" })
    expect(p.lastmonth).toEqual({ from: "2026-09-01", to: "2026-09-30" })
    expect(p.quarter).toEqual({ from: "2026-10-01", to: "2026-10-22" })
    expect(p.year).toEqual({ from: "2026-01-01", to: "2026-10-22" })
    expect(p["12m"]).toEqual({ from: "2025-10-23", to: "2026-10-22" })
    expect(defaultPeriod(now)).toEqual(p.year)
    for (const x of presets(now)) expect(periodProblem(x.period)).toBeNull()
  })
  it("last month works across a new year", () => {
    const p = presets(new Date("2026-01-15T08:00:00Z")).find(x => x.id === "lastmonth")!.period
    expect(p).toEqual({ from: "2025-12-01", to: "2025-12-31" })
  })
  it("checks a period", () => {
    expect(periodProblem({ from: "", to: "2026-01-01" })).toMatch(/Choose/)
    expect(periodProblem({ from: "2026-02-01", to: "2026-01-01" })).toMatch(/before/)
    expect(periodProblem({ from: "2026-01-01", to: "2026-12-31" })).toBeNull()
    expect(periodProblem({ from: "2026-01-01", to: "2027-01-02" })).toMatch(/366/)
  })
  it("words hidden groups and formats figures", () => {
    expect(groupText(g("A", 12))).toBe("12")
    expect(groupText(g("B", null, true))).toBe("fewer than 5")
    expect(pct(16.24)).toBe("16.2%")
    expect(pct(null)).toBe("–")
    expect(rand(null)).toBe("–")
    expect(rand(1234.5).replace(/\s/g, " ")).toContain("1 234,50")
  })
  it("writes a CSV that a spreadsheet cannot run as a formula", () => {
    const csv = toCsv([["Name", "Note"], ["=HYPERLINK(\"x\")", "a,b"], ["@cmd", -5], ["+1", null], ["Say \"hi\"", "line\nbreak"]])
    const lines = csv.split("\r\n")
    expect(lines[0]).toBe("Name,Note")
    expect(lines[1]).toBe(`"'=HYPERLINK(""x"")","a,b"`)
    expect(lines[2]).toBe("'@cmd,-5")
    expect(lines[3]).toBe("'+1,")
    expect(csv).toContain(`"Say ""hi""","line\nbreak"`)
  })
  it("names the file after the report and period", () => {
    expect(reportFileName("payroll", { from: "2026-01-01", to: "2026-03-31" })).toBe("hr-payroll-2026-01-01-to-2026-03-31.csv")
  })
  it("leaves demographics out of the spreadsheet when they were not shown", () => {
    const text = JSON.stringify(workforceRows(workforce({ gender: [g("Female", 12)] }) as never))
    expect(text).not.toContain("Female")
    const shown = JSON.stringify(workforceRows(workforce({ demographicsShown: true, gender: [g("Female", 12), g("Male", null, true)] }) as never))
    expect(shown).toContain("Female")
    expect(shown).toContain("fewer than 5")
  })
  it("marks runs that are not counted in the payroll spreadsheet", () => {
    const rows = payrollRows(payroll() as never)
    expect(rows.some(r => r[0] === "PR-2" && r[3] === "No")).toBe(true)
    expect(rows.some(r => r[0] === "PR-1" && r[3] === "Yes")).toBe(true)
  })
  it("puts the people into the compliance spreadsheet", () => {
    expect(complianceRows(compliance() as never).some(r => r[0] === "No ID number" && r[2] === "E1 Ann Dlamini")).toBe(true)
  })
})

describe("report screens", () => {
  it("refuses a person without access", () => {
    perms = []
    wrap(<ReportsTab />)
    expect(screen.getByRole("alert").textContent).toMatch(/do not have access/)
    expect(get).not.toHaveBeenCalled()
  })
  it("shows the workforce report for the default period", async () => {
    routes({ [`${BASE}/workforce`]: workforce() })
    wrap(<ReportsTab />)
    expect(await screen.findByText("Headcount at end")).toBeTruthy()
    expect(screen.getByText("16.2%")).toBeTruthy()
    expect(screen.getByText(/19\.6% a year at this rate/)).toBeTruthy()
    expect(screen.getByText("Finance")).toBeTruthy()
    expect(screen.getByText(/only to people with access to sensitive records/)).toBeTruthy()
    expect(get.mock.calls[0][0]).toMatch(/^\/api\/v1\/hr\/reports\/workforce\?from=\d{4}-01-01&to=\d{4}-\d{2}-\d{2}$/)
  })
  it("shows hidden groups as fewer than five rather than a number", async () => {
    perms = ["HR_REPORT_READ", "HR_SENSITIVE_READ"]
    routes({ [`${BASE}/workforce`]: workforce({ demographicsShown: true, gender: [g("Female", 12), g("Male", 8)], race: [g("African", null, true), g("White", null, true)], disability: [] }) })
    wrap(<ReportsTab />)
    expect(await screen.findByLabelText("Race")).toBeTruthy()
    expect(screen.getAllByText("fewer than 5")).toHaveLength(2)
    expect(screen.queryByText(/only to people with access/)).toBeNull()
  })
  it("asks for the report again when the period changes", async () => {
    routes({ [`${BASE}/workforce`]: workforce() })
    wrap(<ReportsTab />)
    await screen.findByText("Headcount at end")
    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2026-07-01" } })
    fireEvent.change(screen.getByLabelText("To"), { target: { value: "2026-09-30" } })
    fireEvent.click(screen.getByRole("button", { name: "Show" }))
    await waitFor(() => expect(get).toHaveBeenCalledWith(`${BASE}/workforce?from=2026-07-01&to=2026-09-30`))
  })
  it("will not ask for a period longer than a year", async () => {
    routes({ [`${BASE}/workforce`]: workforce() })
    wrap(<ReportsTab />)
    await screen.findByText("Headcount at end")
    fireEvent.change(screen.getByLabelText("From"), { target: { value: "2024-01-01" } })
    expect((screen.getByRole("button", { name: "Show" }) as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText(/366 days or less/)).toBeTruthy()
  })
  it("hides the payroll report from those without a compensation right", async () => {
    routes({ [`${BASE}/workforce`]: workforce() })
    wrap(<ReportsTab />)
    await screen.findByText("Headcount at end")
    expect(screen.queryByRole("tab", { name: "Payroll cost" })).toBeNull()
  })
  it("shows payroll totals and warns about runs that are not finished", async () => {
    perms = ["HR_REPORT_READ", "HR_COMPENSATION_READ"]
    routes({ [`${BASE}/workforce`]: workforce(), [`${BASE}/payroll`]: payroll() })
    wrap(<ReportsTab />)
    fireEvent.click(await screen.findByRole("tab", { name: "Payroll cost" }))
    expect(await screen.findByText(/1 pay run is not finished and not in the totals/)).toBeTruthy()
    expect(screen.getByText(/not counted/)).toBeTruthy()
    expect(screen.getAllByText(/Employer cost/).length).toBeGreaterThan(0)
  })
  it("shows the compliance checks, the people on them and the cases left out", async () => {
    perms = ["HR_REPORT_READ", "HR_ER_READ"]
    routes({ [`${BASE}/workforce`]: workforce(), [`${BASE}/compliance`]: compliance() })
    wrap(<ReportsTab />)
    fireEvent.click(await screen.findByRole("tab", { name: "Compliance and relations" }))
    expect(await screen.findByText("No ID number")).toBeTruthy()
    expect(screen.getByText("E1 Ann Dlamini")).toBeTruthy()
    expect(screen.getByText("None")).toBeTruthy()
    expect(screen.getByText(/2 confidential cases are not included/)).toBeTruthy()
    expect(screen.getByText(/41\.3 days on average/)).toBeTruthy()
  })
  it("says so when the server refuses", async () => {
    routes({ [`${BASE}/workforce`]: Object.assign(new Error("denied"), { response: { data: { message: "You do not have access to payroll figures." } } }) })
    wrap(<ReportsTab />)
    expect((await screen.findByRole("alert")).textContent).toMatch(/payroll figures|could not be produced/)
  })
})

const saved = (o: object = {}) => ({
  id: "s1", name: "Last month cost", kind: "TIME", kindLabel: "Leave and attendance", period: "LAST_MONTH", periodLabel: "last month", schedule: "MONTHLY", runDay: 3,
  from: "2026-09-01", to: "2026-09-30", lastNotifiedOn: null, createdAt: "2026-10-01T05:00:00Z", ...o,
})

describe("saved reports", () => {
  it("opens a saved report for the dates it means today", async () => {
    routes({ "/api/v1/hr/saved-reports": [saved()], [`${BASE}/time`]: { from: "2026-09-01", to: "2026-09-30", leave: [], leavePending: 0, leavePendingStale: 0, sickDays: 0, sickEmployees: 0, attendance: null, attendanceNote: null, overtimeByKind: [], overtimeByDepartment: [], overtimePending: 0, notes: [] }, [BASE]: workforce() })
    wrap(<ReportsTab />)
    fireEvent.click(await screen.findByRole("button", { name: "Open Last month cost" }))
    await waitFor(() => expect(get).toHaveBeenCalledWith(`${BASE}/time?from=2026-09-01&to=2026-09-30`))
    expect(screen.getByText(/Reminder on day 3 of each month/)).toBeTruthy()
  })

  it("hides a saved payroll report from someone without the payroll right", async () => {
    routes({ "/api/v1/hr/saved-reports": [saved({ id: "s2", name: "Payroll cost", kind: "PAYROLL", kindLabel: "Payroll" })], [BASE]: workforce() })
    wrap(<ReportsTab />)
    await screen.findByRole("region", { name: "Saved reports" })
    await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/hr/saved-reports"))
    expect(screen.queryByText("Payroll cost")).toBeNull()
  })

  it("saves the report on screen with a monthly reminder", async () => {
    routes({ [BASE]: workforce() })
    post.mockResolvedValue(ok(saved()))
    wrap(<ReportsTab />)
    fireEvent.click(await screen.findByRole("button", { name: "Save this report…" }))
    fireEvent.change(screen.getByLabelText("Name"), { target: { value: " Headcount " } })
    fireEvent.change(screen.getByLabelText("Reminder"), { target: { value: "MONTHLY" } })
    expect((screen.getByRole("button", { name: "Save" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reminder day"), { target: { value: "5" } })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/saved-reports", { name: "Headcount", kind: "WORKFORCE", period: "LAST_MONTH", schedule: "MONTHLY", runDay: 5 }))
  })
})
