import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import AttendanceTab from "./AttendanceTab"
import {
  attendanceRights, claimableDays, entryFormProblem, fmtMinutes, fmtTime, lastDays, monthPeriod, periodProblem, rand, scheduleFormProblem, sast, toInstant,
} from "./attendance.logic"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))

let perms: string[] = []
vi.mock("../../hooks/useAuth", () => ({ useAuth: () => ({ user: { userId: "u-me", permissions: perms } }) }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (data: unknown) => Promise.resolve({ data: { data } })

const day = (o: object) => ({ date: "2026-11-02", status: "PRESENT", expectedMinutes: 480, workedMinutes: 480, lateMinutes: 0, shortMinutes: 0,
  overtimeKind: null, overtimeMinutes: 0, flags: [], entries: [], overtimeClaimId: null, overtimeClaimStatus: null, ...o })
const sheet = (o: object = {}) => ({ employeeId: "e1", employeeNumber: "EMP-1", employeeName: "Jane Dlamini", scheduleName: "Standard week", weeklyMinutes: 2400,
  from: "2026-09-28", to: "2026-09-29", workedMinutes: 1080, lateDays: 0, absentDays: 0, overtimeMinutes: 120, canCorrect: false, aboveThreshold: false, weekFlags: [],
  days: [
    day({ date: "2026-09-28", entries: [{ id: "en1", clockIn: "2026-09-28T06:00:00Z", clockOut: "2026-09-28T15:00:00Z", source: "CLOCK", corrected: false, log: [] }] }),
    day({ date: "2026-09-29", status: "LATE", lateMinutes: 30, workedMinutes: 600, overtimeKind: "OVERTIME", overtimeMinutes: 120, flags: ["Check this"] }),
  ], ...o })
const mine = (o: object = {}) => ({ employeeId: "e1", clockedIn: false, since: null, scheduleName: "Standard week", recent: sheet(), ...o })

describe("attendance rules in the browser", () => {
  it("works out rights", () => {
    expect(attendanceRights(["HR_ATTENDANCE_MANAGE"])).toEqual({ manage: true, decide: false, register: true, correct: true })
    expect(attendanceRights(["HR_TEAM_READ"])).toEqual({ manage: false, decide: false, register: true, correct: true })
    expect(attendanceRights(["HR_OVERTIME_APPROVE"])).toEqual({ manage: false, decide: true, register: false, correct: false })
    expect(attendanceRights([])).toEqual({ manage: false, decide: false, register: false, correct: false })
  })
  it("formats minutes and South African times", () => {
    expect(fmtMinutes(0)).toBe("–")
    expect(fmtMinutes(45)).toBe("45m")
    expect(fmtMinutes(120)).toBe("2h")
    expect(fmtMinutes(135)).toBe("2h 15m")
    expect(rand(1234.5)).toBe("R1 234.50")
    expect(rand(311.54)).toBe("R311.54")
    expect(sast("2026-11-02T06:00:00Z")).toEqual({ date: "2026-11-02", time: "08:00" })
    expect(sast("2026-11-01T23:30:00Z")).toEqual({ date: "2026-11-02", time: "01:30" })
    expect(fmtTime(null)).toBe("–")
    expect(toInstant("2026-11-02", "08:00")).toBe("2026-11-02T08:00:00+02:00")
    expect(Date.parse(toInstant("2026-11-02", "08:00"))).toBe(Date.parse("2026-11-02T06:00:00Z"))
  })
  it("catches entry mistakes", () => {
    const now = new Date("2026-11-20T12:00:00Z")
    const f = { date: "2026-11-02", timeIn: "08:00", timeOut: "17:00", reason: "Forgot to clock" }
    expect(entryFormProblem(f, now)).toBeNull()
    expect(entryFormProblem({ ...f, date: "" }, now)).toMatch(/date/)
    expect(entryFormProblem({ ...f, timeOut: "07:00" }, now)).toMatch(/after the time in/)
    expect(entryFormProblem({ ...f, date: "2026-11-25" }, now)).toMatch(/future/)
    expect(entryFormProblem({ ...f, reason: " " }, now)).toMatch(/reason/)
  })
  it("limits periods to 62 days", () => {
    expect(periodProblem("2026-11-01", "2026-11-30")).toBeNull()
    expect(periodProblem("2026-11-30", "2026-11-01")).toMatch(/before/)
    expect(periodProblem("2026-01-01", "2026-11-01")).toMatch(/62/)
    expect(periodProblem("", "")).toMatch(/dates/)
    expect(lastDays(7, new Date(2026, 10, 10))).toEqual({ from: "2026-11-04", to: "2026-11-10" })
    expect(monthPeriod(new Date(2026, 10, 10))).toEqual({ from: "2026-11-01", to: "2026-11-10" })
  })
  it("offers a claim only for finished days with overtime that is not already claimed", () => {
    const d = (o: object) => ({ date: "2026-11-03", overtimeKind: "OVERTIME", overtimeMinutes: 60, overtimeClaimStatus: null, status: "PRESENT", ...o })
    expect(claimableDays([d({})], false, "2026-11-10")).toHaveLength(1)
    expect(claimableDays([d({})], true, "2026-11-10")).toHaveLength(0)
    expect(claimableDays([d({ overtimeClaimStatus: "PENDING" })], false, "2026-11-10")).toHaveLength(0)
    expect(claimableDays([d({ overtimeClaimStatus: "REJECTED" })], false, "2026-11-10")).toHaveLength(1)
    expect(claimableDays([d({ overtimeKind: "PUBLIC_HOLIDAY" })], false, "2026-11-10")).toHaveLength(0)
    expect(claimableDays([d({ status: "INCOMPLETE" })], false, "2026-11-10")).toHaveLength(0)
    expect(claimableDays([d({ date: "2026-11-10" })], false, "2026-11-10")).toHaveLength(0)
  })
  it("catches schedule mistakes", () => {
    const f = { name: "Early", days: ["MON"], startTime: "06:00", endTime: "14:00", breakMinutes: "30" }
    expect(scheduleFormProblem(f)).toBeNull()
    expect(scheduleFormProblem({ ...f, days: [] })).toMatch(/working day/)
    expect(scheduleFormProblem({ ...f, endTime: "05:00" })).toMatch(/after the start/)
    expect(scheduleFormProblem({ ...f, breakMinutes: "300" })).toMatch(/break/)
    expect(scheduleFormProblem({ ...f, name: " " })).toMatch(/name/)
  })
})

describe("AttendanceTab", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); put.mockReset(); perms = []
    get.mockImplementation((url: string) => {
      if (url.endsWith("/attendance/me")) return ok(mine())
      if (url.endsWith("/attendance/schedules")) return ok([{ id: "s1", name: "Standard week", days: ["MON", "TUE", "WED", "THU", "FRI"], startTime: "08:00:00", endTime: "17:00:00", breakMinutes: 60, isDefault: true, active: true, dailyMinutes: 480, weeklyMinutes: 2400, warnings: [] }])
      if (url.endsWith("/attendance/register")) return ok([{ employeeId: "e1", employeeNumber: "EMP-1", employeeName: "Jane Dlamini", department: "Ops", presentDays: 9, lateDays: 1, absentDays: 2, leaveDays: 0, workedMinutes: 4000, overtimeMinutes: 120, flagged: 0 }])
      if (url.endsWith("/attendance/timesheet")) return ok(sheet({ canCorrect: true }))
      if (url.endsWith("/hr/overtime")) return ok([{ id: "c1", employeeId: "e1", employeeNumber: "EMP-1", employeeName: "Jane Dlamini", workDate: "2026-09-29", kind: "OVERTIME", minutes: 120, multiplier: 1.5, hourlyRate: 103.85, amount: 311.54, status: "PENDING", decisionNote: null, payRunId: null, canDecide: true }])
      return ok([])
    })
    post.mockImplementation(() => ok(mine({ clockedIn: true, since: "2026-11-03T06:00:00Z" })))
    put.mockImplementation(() => ok({}))
  })
  afterEach(() => cleanup())

  it("shows your two weeks and clocks you in", async () => {
    wrap(<AttendanceTab />)
    expect(await screen.findByText(/Not clocked in/)).toBeTruthy()
    expect(screen.getByText("Check this")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Clock in" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/attendance/clock-in"))
  })

  it("offers to clock out when clocked in", async () => {
    get.mockImplementation(() => ok(mine({ clockedIn: true, since: "2026-11-03T06:00:00Z" })))
    wrap(<AttendanceTab />)
    expect(await screen.findByText(/Clocked in since 08:00/)).toBeTruthy()
    expect(screen.getByRole("button", { name: "Clock out" })).toBeTruthy()
  })

  it("claims overtime for the finished days that have some", async () => {
    post.mockImplementation((url: string) => (url.includes("overtime/request") ? ok({ created: 1, updated: 0, withdrawn: 0, skipped: [] }) : ok({})))
    wrap(<AttendanceTab />)
    fireEvent.click(await screen.findByRole("button", { name: /Claim overtime \(1 day\)/ }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/overtime/request", { employeeId: "e1", from: "2026-09-28", to: "2026-09-29" }))
    expect(await screen.findByText(/1 claim\(s\) made/)).toBeTruthy()
  })

  it("explains a login that is not linked to an employee", async () => {
    get.mockImplementation(() => Promise.reject({ response: { data: { message: "Your login is not linked to an employee record. Ask HR to link it." } } }))
    wrap(<AttendanceTab />)
    expect((await screen.findByRole("alert")).textContent).toMatch(/not linked/)
  })

  it("hides the register from ordinary staff and shows it to managers", async () => {
    wrap(<AttendanceTab />)
    await screen.findByText(/Not clocked in/)
    expect(screen.queryByRole("tab", { name: "Register" })).toBeNull()
    cleanup()
    perms = ["HR_ATTENDANCE_MANAGE"]
    wrap(<AttendanceTab />)
    fireEvent.click(await screen.findByRole("tab", { name: "Register" }))
    expect(await screen.findByText("Jane Dlamini")).toBeTruthy()
    expect(screen.getByText("9")).toBeTruthy()
  })

  it("lets a manager correct an entry only with a reason", async () => {
    perms = ["HR_ATTENDANCE_MANAGE"]
    wrap(<AttendanceTab />)
    fireEvent.click(await screen.findByRole("tab", { name: "Register" }))
    fireEvent.click(await screen.findByText("Jane Dlamini"))
    fireEvent.click(await screen.findByRole("button", { name: "Correct" }))
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/reason/i)
    expect(put).not.toHaveBeenCalled()
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "Left at five" } })
    fireEvent.click(screen.getByRole("button", { name: "Save" }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/hr/attendance/entries/en1",
      { clockIn: "2026-09-28T08:00:00+02:00", clockOut: "2026-09-28T17:00:00+02:00", reason: "Left at five" }))
  })

  it("lets an approver approve a claim and asks for a reason to reject it", async () => {
    perms = ["HR_OVERTIME_APPROVE"]
    wrap(<AttendanceTab />)
    fireEvent.click(await screen.findByRole("tab", { name: "Overtime" }))
    expect(await screen.findByText("R311.54")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Reject" }))
    fireEvent.click(screen.getByRole("button", { name: "Confirm" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/reason/i)
    expect(post).not.toHaveBeenCalled()
    fireEvent.click(screen.getByRole("button", { name: "Approve" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/overtime/c1/approve", {}))
  })

  it("shows schedules to everyone but only managers can change them", async () => {
    wrap(<AttendanceTab />)
    fireEvent.click(await screen.findByRole("tab", { name: "Schedules" }))
    expect(await screen.findByText("Standard week")).toBeTruthy()
    expect(screen.queryByRole("button", { name: "Add a schedule" })).toBeNull()
    cleanup()
    perms = ["HR_MANAGE"]
    wrap(<AttendanceTab />)
    fireEvent.click(await screen.findByRole("tab", { name: "Schedules" }))
    expect(await screen.findByRole("button", { name: "Add a schedule" })).toBeTruthy()
  })
})
