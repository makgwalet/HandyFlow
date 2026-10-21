import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import LeaveTab from "./LeaveTab"
import { adjustmentProblem, canCancel, certificateExpected, kindLabel, leaveRights, reasonProblem, requestProblem, signedDays, workingDays } from "./leave.logic"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))

let perms: string[] = []
vi.mock("../../hooks/useAuth", () => ({ useAuth: () => ({ user: { userId: "u-me", permissions: perms } }) }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (data: unknown) => Promise.resolve({ data: { data } })

describe("leave rules in the browser", () => {
  it("counts weekdays, skips holidays, and a half day is 0.5", () => {
    expect(workingDays("2026-11-02", "2026-11-06")).toBe(5)
    expect(workingDays("2026-11-02", "2026-11-08")).toBe(5)
    expect(workingDays("2026-11-02", "2026-11-06", false, ["2026-11-04"])).toBe(4)
    expect(workingDays("2026-11-02", "2026-11-02", true)).toBe(0.5)
    expect(workingDays("2026-11-07", "2026-11-08")).toBe(0)
    expect(workingDays("2026-11-06", "2026-11-02")).toBe(0)
    expect(workingDays("", "")).toBe(0)
  })
  it("expects a certificate for sick leave over two days only", () => {
    expect(certificateExpected("SICK", 3)).toBe(true)
    expect(certificateExpected("SICK", 2)).toBe(false)
    expect(certificateExpected("ANNUAL", 10)).toBe(false)
  })
  it("catches request mistakes", () => {
    const f = { leaveType: "ANNUAL", startDate: "2026-11-02", endDate: "2026-11-03", halfDay: false }
    expect(requestProblem(f, 2)).toBeNull()
    expect(requestProblem({ ...f, endDate: "2026-11-01" }, 0)).toMatch(/before the first/)
    expect(requestProblem({ ...f, halfDay: true }, 0.5)).toMatch(/half day/i)
    expect(requestProblem({ ...f, startDate: "2026-11-07", endDate: "2026-11-08" }, 0)).toMatch(/no working day/)
    expect(requestProblem({ ...f, startDate: "" }, 0)).toMatch(/Enter the first/)
  })
  it("needs reasons and valid adjustments", () => {
    expect(reasonProblem(" ", "rejecting the request")).toMatch(/rejecting/)
    expect(reasonProblem("Busy", "x")).toBeNull()
    expect(adjustmentProblem("", "r", "2026-10-01", "2026-10-21")).toMatch(/number of days/)
    expect(adjustmentProblem("0", "r", "2026-10-01", "2026-10-21")).toMatch(/number of days/)
    expect(adjustmentProblem("400", "r", "2026-10-01", "2026-10-21")).toMatch(/too many/)
    expect(adjustmentProblem("2", " ", "2026-10-01", "2026-10-21")).toMatch(/reason/)
    expect(adjustmentProblem("2", "r", "2026-10-22", "2026-10-21")).toMatch(/future/)
    expect(adjustmentProblem("-2", "r", "2026-10-21", "2026-10-21")).toBeNull()
  })
  it("offers cancel only where the server would allow it", () => {
    const approver = leaveRights(["LEAVE_APPROVE"]), hr = leaveRights(["LEAVE_ADJUST"]), none = leaveRights([])
    expect(canCancel({ status: "PENDING", startDate: "2026-11-02" }, approver, "2026-10-21")).toBe(true)
    expect(canCancel({ status: "PENDING", startDate: "2026-11-02" }, none, "2026-10-21")).toBe(false)
    expect(canCancel({ status: "APPROVED", startDate: "2026-11-02" }, approver, "2026-10-21")).toBe(true)
    expect(canCancel({ status: "APPROVED", startDate: "2026-10-01" }, approver, "2026-10-21")).toBe(false)
    expect(canCancel({ status: "APPROVED", startDate: "2026-10-01" }, hr, "2026-10-21")).toBe(true)
    expect(canCancel({ status: "REJECTED", startDate: "2026-11-02" }, hr, "2026-10-21")).toBe(false)
  })
  it("labels and signs ledger lines", () => {
    expect(kindLabel("ACCRUAL")).toBe("Earned")
    expect(kindLabel("WHATEVER")).toBe("WHATEVER")
    expect(signedDays("1.5")).toBe("+1.5")
    expect(signedDays(-2)).toBe("−2.0")
    expect(signedDays(0)).toBe("0.0")
  })
})

const req = (over: Record<string, unknown> = {}) => ({
  id: "r1", employeeId: "e1", employeeName: "Jane Dlamini", leaveType: "ANNUAL", startDate: "2099-11-02", endDate: "2099-11-03",
  daysRequested: 2, reason: null, status: "PENDING", halfDay: false, medicalCertificate: false, certificateExpected: false, rejectionReason: null, ...over,
})

function mockApi(requests: unknown[]) {
  get.mockImplementation((url: string) => {
    if (url.includes("/leave-requests")) return ok({ content: requests })
    if (url.includes("/employees?")) return ok({ content: [{ id: "e1", firstName: "Jane", lastName: "Dlamini", fullName: "Jane Dlamini", employeeNumber: "EMP001" }] })
    if (url.includes("/leave-statement")) return ok({
      employeeId: "e1", employeeName: "Jane Dlamini", year: 2026,
      balances: [{ id: "b", leaveType: "ANNUAL", leaveYear: 2026, entitledDays: 15, takenDays: 1, pendingDays: 0, availableDays: 14 }],
      entries: [{ id: "l1", leaveType: "ANNUAL", entryDate: "2026-03-01", kind: "ACCRUAL", days: 1.25, note: "Earned to date", requestId: null, createdBy: null, createdAt: "2026-03-01T00:00:00Z" }],
    })
    if (url.includes("/leave-balances")) return ok([])
    return ok([])
  })
}

describe("LeaveTab", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); perms = [] })
  afterEach(cleanup)

  it("hides decide buttons without the right", async () => {
    mockApi([req()])
    wrap(<LeaveTab />)
    await screen.findByText("2.0 working days")
    expect(screen.queryByRole("button", { name: /^approve$/i })).toBeNull()
    expect(screen.queryByRole("button", { name: /^reject$/i })).toBeNull()
  })

  it("approves with the right", async () => {
    perms = ["LEAVE_APPROVE"]
    mockApi([req()])
    post.mockReturnValue(ok({}))
    wrap(<LeaveTab />)
    fireEvent.click(await screen.findByRole("button", { name: /^approve$/i }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/leave-requests/r1/approve"))
  })

  it("will not reject without a reason, then sends it", async () => {
    perms = ["LEAVE_APPROVE"]
    mockApi([req()])
    post.mockReturnValue(ok({}))
    wrap(<LeaveTab />)
    fireEvent.click(await screen.findByRole("button", { name: /^reject$/i }))
    const send = await screen.findByRole("button", { name: /reject request/i })
    expect((send as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByPlaceholderText(/explain why/i), { target: { value: "Busy period" } })
    fireEvent.click(send)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/leave-requests/r1/reject?reason=Busy%20period"))
  })

  it("cancels an approved request that has not started", async () => {
    perms = ["LEAVE_APPROVE"]
    mockApi([req({ status: "APPROVED" })])
    post.mockReturnValue(ok({}))
    wrap(<LeaveTab />)
    fireEvent.click(await screen.findByRole("button", { name: /^cancel leave$/i }))
    fireEvent.click(await screen.findByRole("button", { name: /confirm cancel/i }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/leave-requests/r1/cancel", { reason: null }))
  })

  it("shows a certificate note and half day on a row", async () => {
    mockApi([req({ leaveType: "SICK", certificateExpected: true, medicalCertificate: false, halfDay: false }), req({ id: "r2", halfDay: true, daysRequested: 0.5 })])
    wrap(<LeaveTab />)
    await screen.findByText(/certificate expected/i)
    expect(screen.getByText(/certificate expected/i)).toBeTruthy()
    expect(screen.getByText(/half day/i)).toBeTruthy()
  })

  it("opens the statement for an employee", async () => {
    mockApi([])
    wrap(<LeaveTab />)
    fireEvent.click(await screen.findByText("Jane Dlamini"))
    fireEvent.click(await screen.findByRole("button", { name: /statement/i }))
    expect(await screen.findByText("Earned to date")).toBeTruthy()
    expect(screen.getByText("+1.3")).toBeTruthy()
  })

  it("records an adjustment with the adjust right only", async () => {
    perms = ["LEAVE_ADJUST"]
    mockApi([])
    post.mockReturnValue(ok({}))
    wrap(<LeaveTab />)
    fireEvent.click(await screen.findByText("Jane Dlamini"))
    fireEvent.click(await screen.findByRole("button", { name: /statement/i }))
    fireEvent.click(await screen.findByRole("button", { name: /adjust balance/i }))
    fireEvent.change(screen.getByLabelText(/days/i), { target: { value: "2" } })
    fireEvent.change(screen.getByLabelText(/reason/i), { target: { value: "Owed from last year" } })
    fireEvent.click(screen.getByRole("button", { name: /record adjustment/i }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const [url, body] = post.mock.calls[0]
    expect(url).toBe("/api/v1/hr/employees/e1/leave-adjustments")
    expect(body).toMatchObject({ leaveType: "ANNUAL", days: 2, reason: "Owed from last year" })
  })

  it("does not offer adjustment without the right", async () => {
    mockApi([])
    wrap(<LeaveTab />)
    fireEvent.click(await screen.findByText("Jane Dlamini"))
    fireEvent.click(await screen.findByRole("button", { name: /statement/i }))
    await screen.findByText("Earned to date")
    expect(screen.queryByRole("button", { name: /adjust balance/i })).toBeNull()
  })
})
