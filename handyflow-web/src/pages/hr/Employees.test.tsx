import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup, within } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EmployeesTab from "./EmployeesTab"
import { ApprovalsModal, RequestChangeModal } from "./EmployeeChanges"
import {
  auditText, bankProblem, cannotDecide, compensationProblem, effectiveProblem, employeeRights, reasonProblem, terminationProblem,
  type EmployeeChange,
} from "./employees.logic"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))

let perms: string[] = []
vi.mock("../../hooks/useAuth", () => ({ useAuth: () => ({ user: { userId: "u-me", permissions: perms } }) }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

const ok = (data: unknown) => Promise.resolve({ data: { data } })

const person = (over: Record<string, unknown> = {}) => ({
  id: "e1", employeeNumber: "EMP001", firstName: "Jane", lastName: "Dlamini", fullName: "Jane Dlamini",
  email: "jane@example.com", phone: null, employmentType: "PERMANENT", jobTitle: "Developer", department: "TECHNICAL",
  startDate: "2024-01-01", endDate: null, status: "ACTIVE", managerId: null, managerName: null, userId: null,
  canSeeSensitive: false, canSeeCompensation: false,
  idNumber: null, taxNumber: null, dateOfBirth: null, gender: null, race: null, emergencyContactName: null, emergencyContactPhone: null,
  terminationReason: null, grossSalary: null, payFrequency: null, medicalAidContribution: null, pensionContribution: null,
  travelAllowance: null, bankName: null, bankAccountMasked: null, bankBranchCode: null,
  ...over,
})

const change = (over: Partial<EmployeeChange> = {}): EmployeeChange => ({
  id: "c1", employeeId: "e1", employeeName: "Jane Dlamini", changeType: "COMPENSATION",
  fields: [{ key: "grossSalary", label: "Gross salary", oldValue: "30000", newValue: "35000" }],
  effectiveFrom: "2026-11-01", reason: "Annual increase", status: "PENDING", requestedBy: "u-other", requestedAt: "2026-10-20T08:00:00Z",
  decidedBy: null, decidedAt: null, decisionNote: null, selfApproved: false, appliedAt: null, ...over,
})

function serve(people: unknown[], pending: unknown[] = []) {
  get.mockImplementation((url: string) => {
    if (url.includes("employee-changes/pending")) return ok(pending)
    if (url.includes("/api/v1/hr/employees")) return ok({ content: people })
    if (url.includes("identity/users")) return ok({ content: [] })
    return ok([])
  })
}

beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); perms = [] })
afterEach(() => cleanup())

describe("employeeRights", () => {
  it("separates working details, personal data and pay", () => {
    expect(employeeRights(["HR_READ"])).toMatchObject({ manage: false, sensitive: false, compensation: false })
    expect(employeeRights(["HR_SENSITIVE_READ"])).toMatchObject({ sensitive: true, compensation: false })
    expect(employeeRights(["HR_COMPENSATION_READ"])).toMatchObject({ compensation: true, askPay: false, decidePay: false })
  })
  it("keeps asking for a pay change apart from deciding on it", () => {
    expect(employeeRights(["HR_COMPENSATION_MANAGE"])).toMatchObject({ askPay: true, decidePay: false })
    expect(employeeRights(["HR_COMPENSATION_APPROVE"])).toMatchObject({ askPay: false, decidePay: true })
  })
})

describe("checks before sending", () => {
  it("termination needs a last day that has come, and a reason", () => {
    expect(terminationProblem("", "Resigned", "2024-01-01", "2026-10-21")).toMatch(/last day/i)
    expect(terminationProblem("2026-10-25", "Resigned", "2024-01-01", "2026-10-21")).toMatch(/future/)
    expect(terminationProblem("2023-12-31", "Resigned", "2024-01-01", "2026-10-21")).toMatch(/before the start/)
    expect(terminationProblem("2026-10-20", " ", "2024-01-01", "2026-10-21")).toMatch(/reason/)
    expect(terminationProblem("2026-10-20", "Resigned", "2024-01-01", "2026-10-21")).toBeNull()
  })
  it("effective date runs from a month back to a year ahead", () => {
    expect(effectiveProblem("", null, "2026-10-21")).toBeTruthy()
    expect(effectiveProblem("2026-08-01", null, "2026-10-21")).toMatch(/month ago/)
    expect(effectiveProblem("2028-01-01", null, "2026-10-21")).toMatch(/year/)
    expect(effectiveProblem("2023-01-01", "2024-01-01", "2026-10-21")).toMatch(/started/)
    expect(effectiveProblem("2026-11-01", "2024-01-01", "2026-10-21")).toBeNull()
  })
  it("pay amounts", () => {
    const base = { grossSalary: "30000", travelAllowance: "0", medicalAidContribution: "0", pensionContribution: "0" }
    expect(compensationProblem({ ...base, grossSalary: "0" })).toMatch(/more than zero/)
    expect(compensationProblem({ ...base, pensionContribution: "-1" })).toMatch(/negative/)
    expect(compensationProblem({ ...base, medicalAidContribution: "20000", pensionContribution: "15000" })).toMatch(/more than the gross/)
    expect(compensationProblem(base)).toBeNull()
  })
  it("bank details", () => {
    expect(bankProblem("", "123456", "250655")).toMatch(/bank name/)
    expect(bankProblem("FNB", "12", "250655")).toMatch(/4 to 17/)
    expect(bankProblem("FNB", "62000011112222", "25")).toMatch(/6 digits/)
    expect(bankProblem("FNB", "62000011112222", "250655")).toBeNull()
  })
  it("reason", () => {
    expect(reasonProblem("  ")).toBeTruthy()
    expect(reasonProblem("Annual increase")).toBeNull()
  })
  it("a decided change cannot be decided again, and a requester is told", () => {
    expect(cannotDecide(change({ status: "APPLIED" }), "u-me", false)).toMatch(/already/)
    expect(cannotDecide(change({ requestedBy: "u-me" }), "u-me", false)).toMatch(/someone else/)
    expect(cannotDecide(change({ requestedBy: "u-me" }), "u-me", true)).toBeNull()
    expect(cannotDecide(change(), "u-me", false)).toBeNull()
  })
  it("reads audit actions in plain words", () => {
    expect(auditText("VIEWED_PAY")).toBe("Pay viewed")
    expect(auditText("SOMETHING_NEW")).toBe("SOMETHING_NEW")
  })
})

describe("EmployeesTab", () => {
  it("shows no salary column or buttons to someone who can only read", async () => {
    perms = ["HR_READ"]
    serve([person()])
    wrap(<EmployeesTab />)
    await screen.findByText("Jane Dlamini")
    expect(screen.queryByText("Gross Salary")).toBeNull()
    expect(screen.queryByText("Add Employee")).toBeNull()
    expect(screen.queryByLabelText("Edit Jane Dlamini")).toBeNull()
    expect(screen.queryByLabelText("Terminate Jane Dlamini")).toBeNull()
    fireEvent.click(screen.getByText("Jane Dlamini"))
    expect(await screen.findByText("Personal data")).toBeTruthy()
    expect(screen.getAllByText("Restricted").length).toBe(2)
  })

  it("shows pay and personal data to someone who holds the permissions, with the bank account masked", async () => {
    perms = ["HR_READ", "HR_SENSITIVE_READ", "HR_COMPENSATION_READ"]
    serve([person({ canSeeSensitive: true, canSeeCompensation: true, idNumber: "8001015009087", grossSalary: 30000, payFrequency: "MONTHLY", bankName: "FNB", bankAccountMasked: "••••2222" })])
    wrap(<EmployeesTab />)
    await screen.findByText("Jane Dlamini")
    expect(screen.getByText("Gross Salary")).toBeTruthy()
    fireEvent.click(screen.getByText("Jane Dlamini"))
    expect(await screen.findByText("8001015009087")).toBeTruthy()
    expect(screen.getByText("FNB ••••2222")).toBeTruthy()
    expect(screen.queryByText("Request pay change")).toBeNull()
  })

  it("offers pay and bank change requests only to someone allowed to ask", async () => {
    perms = ["HR_READ", "HR_COMPENSATION_MANAGE"]
    serve([person({ canSeeCompensation: true, grossSalary: 30000 })])
    wrap(<EmployeesTab />)
    fireEvent.click(await screen.findByText("Jane Dlamini"))
    expect(await screen.findByText("Request pay change")).toBeTruthy()
    expect(screen.getByText("Request bank change")).toBeTruthy()
  })

  it("tells an approver how many changes are waiting", async () => {
    perms = ["HR_READ", "HR_COMPENSATION_APPROVE"]
    serve([person({ canSeeCompensation: true, grossSalary: 30000 })], [change()])
    wrap(<EmployeesTab />)
    expect(await screen.findByText("Pay & bank changes (1 waiting)")).toBeTruthy()
  })

  it("will not send a termination without a reason", async () => {
    perms = ["HR_READ", "HR_MANAGE"]
    serve([person()])
    wrap(<EmployeesTab />)
    fireEvent.click(await screen.findByLabelText("Terminate Jane Dlamini"))
    const button = await screen.findByRole("button", { name: "Terminate" })
    expect((button as HTMLButtonElement).disabled).toBe(true)
    expect(post).not.toHaveBeenCalled()
  })

  it("sends the last day and the reason when terminating", async () => {
    perms = ["HR_READ", "HR_MANAGE"]
    serve([person()])
    post.mockResolvedValue({ data: { data: person({ status: "TERMINATED" }) } })
    wrap(<EmployeesTab />)
    fireEvent.click(await screen.findByLabelText("Terminate Jane Dlamini"))
    const dialogDate = document.querySelector('input[type="date"]') as HTMLInputElement
    fireEvent.change(dialogDate, { target: { value: "2026-10-20" } })
    fireEvent.change(screen.getByPlaceholderText(/Resigned, dismissed/), { target: { value: "Resigned" } })
    fireEvent.click(screen.getByRole("button", { name: "Terminate" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/employees/e1/terminate?endDate=2026-10-20&reason=Resigned"))
  })

  it("edits working details without sending pay, bank or notes", async () => {
    perms = ["HR_READ", "HR_MANAGE"]
    serve([person()])
    put.mockResolvedValue({ data: { data: person() } })
    wrap(<EmployeesTab />)
    fireEvent.click(await screen.findByLabelText("Edit Jane Dlamini"))
    fireEvent.change(await screen.findByPlaceholderText("Operations Manager"), { target: { value: "Senior Developer" } })
    fireEvent.click(screen.getByRole("button", { name: "Save Changes" }))
    await waitFor(() => expect(put).toHaveBeenCalled())
    const body = put.mock.calls[0][1] as Record<string, unknown>
    expect(body.jobTitle).toBe("Senior Developer")
    for (const k of ["grossSalary", "bankAccountNumber", "travelAllowance", "notes", "idNumber"]) expect(body).not.toHaveProperty(k)
  })
})

describe("RequestChangeModal", () => {
  const emp = { id: "e1", fullName: "Jane Dlamini", employeeNumber: "EMP001", startDate: "2024-01-01", grossSalary: 30000, payFrequency: "MONTHLY", travelAllowance: 0, medicalAidContribution: 0, pensionContribution: 0 }

  it("refuses a request with no reason and does not send it", async () => {
    wrap(<RequestChangeModal kind="pay" employee={emp} onClose={() => {}} />)
    fireEvent.change(screen.getByLabelText("Gross salary (R)"), { target: { value: "35000" } })
    fireEvent.click(screen.getByRole("button", { name: "Send for approval" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/reason/i)
    expect(post).not.toHaveBeenCalled()
  })

  it("sends a pay request with the date and reason", async () => {
    post.mockResolvedValue({ data: { data: change() } })
    wrap(<RequestChangeModal kind="pay" employee={emp} onClose={() => {}} />)
    fireEvent.change(screen.getByLabelText("Gross salary (R)"), { target: { value: "35000" } })
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "Annual increase" } })
    fireEvent.click(screen.getByRole("button", { name: "Send for approval" }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/hr/employees/e1/changes/compensation")
    expect(post.mock.calls[0][1]).toMatchObject({ grossSalary: 35000, reason: "Annual increase" })
  })

  it("checks a bank request before sending it", async () => {
    wrap(<RequestChangeModal kind="bank" employee={emp} onClose={() => {}} />)
    fireEvent.change(screen.getByLabelText("Bank"), { target: { value: "FNB" } })
    fireEvent.change(screen.getByLabelText("Account number"), { target: { value: "12" } })
    fireEvent.change(screen.getByLabelText("Reason"), { target: { value: "New bank" } })
    fireEvent.click(screen.getByRole("button", { name: "Send for approval" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/4 to 17/)
    expect(post).not.toHaveBeenCalled()
  })
})

describe("ApprovalsModal", () => {
  it("lets an approver approve, and lets the person who asked cancel", async () => {
    get.mockImplementation(() => ok([change({ id: "c1", requestedBy: "u-other" }), change({ id: "c2", requestedBy: "u-me" })]))
    post.mockResolvedValue({ data: { data: change() } })
    wrap(<ApprovalsModal canDecide onClose={() => {}} />)
    await screen.findAllByText(/Jane Dlamini · Pay change/)
    fireEvent.click(screen.getAllByRole("button", { name: "Approve" })[0])
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/employee-changes/c1/approve", { note: "" }))
    expect(screen.getAllByRole("button", { name: "Cancel request" }).length).toBe(1)
  })

  it("needs a reason to reject", async () => {
    get.mockImplementation(() => ok([change()]))
    wrap(<ApprovalsModal canDecide onClose={() => {}} />)
    await screen.findByText(/Jane Dlamini · Pay change/)
    fireEvent.click(screen.getByRole("button", { name: "Reject" }))
    const reject = screen.getByRole("button", { name: "Reject change" }) as HTMLButtonElement
    expect(reject.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Why is it rejected?"), { target: { value: "Not budgeted" } })
    expect(reject.disabled).toBe(false)
  })

  it("shows no decision buttons to someone who can only look", async () => {
    get.mockImplementation(() => ok([change()]))
    wrap(<ApprovalsModal canDecide={false} onClose={() => {}} />)
    const card = (await screen.findByText(/Jane Dlamini · Pay change/)).closest("div")!.parentElement!
    expect(within(card).queryByRole("button", { name: "Approve" })).toBeNull()
    expect(within(card).queryByRole("button", { name: "Reject" })).toBeNull()
  })
})
