import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { RunActions, RunReview } from "./PayRunControls"
import { PayrollControlsPanel } from "./PayrollChecksTab"
import {
  eventText, hasTotals, isIssued, noteProblem, reviewSummary, rightsFrom, stepsFor, waitingFor,
} from "./payrollControls.logic"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))

let perms: string[] = []
vi.mock("../../hooks/useAuth", () => ({ useAuth: () => ({ user: { userId: "u-me", permissions: perms } }) }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

const everything = rightsFrom(["PAYROLL_PREPARE", "PAYROLL_APPROVE", "PAYROLL_FINALISE"], false)
const run = (status: string, preparedBy: string | null = "u-other") => ({ id: "r1", payRunNumber: "PR00001", status, preparedBy })

beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); perms = [] })
afterEach(() => cleanup())

describe("rightsFrom", () => {
  it("uses the payroll permissions", () => {
    expect(rightsFrom(["PAYROLL_APPROVE"], false)).toMatchObject({ approve: true, prepare: false, finalise: false })
    expect(rightsFrom(["PAYROLL_PREPARE"], false)).toMatchObject({ prepare: true, approve: false, read: true })
  })
  it("lets the old permissions prepare only while the server says so, and never approve", () => {
    expect(rightsFrom(["USER_UPDATE"], true).prepare).toBe(true)
    expect(rightsFrom(["USER_UPDATE"], false).prepare).toBe(false)
    expect(rightsFrom(["PAYROLL_RUN", "USER_UPDATE"], true)).toMatchObject({ approve: false, finalise: false })
  })
  it("gives nothing for unrelated permissions", () => {
    expect(rightsFrom(["HR_READ", "USER_READ"], true)).toMatchObject({ prepare: false, approve: false, finalise: false, read: false })
  })
})

describe("stepsFor", () => {
  it("offers Calculate on a draft and Calculate again while waiting for approval", () => {
    expect(stepsFor(run("DRAFT"), everything, "u-me", false).map(s => s.label)).toEqual(["Calculate", "Cancel this run"])
    expect(stepsFor(run("PREPARED"), everything, "u-me", false).map(s => s.label)).toEqual(["Calculate again", "Approve", "Send back"])
  })
  it("blocks approving your own run unless the business allows it, and says why", () => {
    const own = stepsFor(run("PREPARED", "u-me"), everything, "u-me", false).find(s => s.key === "approve")!
    expect(own.enabled).toBe(false)
    expect(own.reason).toMatch(/another person/)
    expect(stepsFor(run("PREPARED", "u-me"), everything, "u-me", true).find(s => s.key === "approve")!.enabled).toBe(true)
  })
  it("offers Finalise only on an approved run, to someone who may", () => {
    expect(stepsFor(run("APPROVED"), everything, "u-me", false).map(s => s.key)).toEqual(["sendBack", "finalise"])
    expect(stepsFor(run("APPROVED"), rightsFrom(["PAYROLL_APPROVE"], false), "u-me", false).map(s => s.key)).toEqual(["sendBack"])
  })
  it("offers nothing on a finalised or cancelled run, and Approve on an old completed one", () => {
    expect(stepsFor(run("FINALISED"), everything, "u-me", false)).toEqual([])
    expect(stepsFor(run("CANCELLED"), everything, "u-me", false)).toEqual([])
    expect(stepsFor(run("COMPLETED", null), everything, "u-me", false).map(s => s.key)).toEqual(["approve"])
  })
  it("offers nothing to someone who only reads", () => {
    expect(stepsFor(run("PREPARED"), rightsFrom(["PAYROLL_READ"], false), "u-me", false)).toEqual([])
  })
})

describe("wording", () => {
  it("says what a run is waiting for", () => {
    expect(waitingFor("PREPARED")).toMatch(/Employees cannot see/)
    expect(waitingFor("FINALISED")).toMatch(/locked/)
    expect(waitingFor("CANCELLED")).toMatch(/Cancelled/)
  })
  it("knows which runs have totals and which employees can see", () => {
    expect(hasTotals("DRAFT")).toBe(false); expect(hasTotals("PREPARED")).toBe(true)
    expect(isIssued("FINALISED")).toBe(true); expect(isIssued("COMPLETED")).toBe(true); expect(isIssued("APPROVED")).toBe(false)
  })
  it("reads the history in words and marks your own steps", () => {
    expect(eventText({ action: "APPROVED", actorId: "u-me" }, "u-me")).toBe("Approved by you")
    expect(eventText({ action: "APPROVED_OWN_RUN", actorId: "u-x" }, "u-me")).toMatch(/person who calculated/)
  })
  it("needs a reason to send back and caps notes", () => {
    expect(noteProblem({ needsNote: true }, " ")).toMatch(/reason/)
    expect(noteProblem({ needsNote: false }, "")).toBe("")
    expect(noteProblem({ needsNote: false }, "x".repeat(501))).toMatch(/500/)
  })
  it("summarises a review", () => {
    expect(reviewSummary({ errors: 1, warnings: 2, comparedWith: "PR00001" })).toBe("1 error to fix before approval, 2 warnings to look at. Compared with PR00001.")
    expect(reviewSummary({ errors: 0, warnings: 0, comparedWith: null })).toMatch(/No errors, no warnings. There is no earlier run/)
  })
})

describe("RunActions", () => {
  const props = { rights: everything, userId: "u-me", allowSelf: false, onCalculated: vi.fn(), onError: vi.fn() }

  it("approves with a note and sends it to the server", async () => {
    post.mockResolvedValue({ data: {} })
    wrap(<RunActions run={run("PREPARED")} {...props} />)
    fireEvent.click(screen.getByRole("button", { name: "Approve" }))
    fireEvent.change(screen.getByLabelText(/Note for the record/), { target: { value: "Checked" } })
    fireEvent.click(screen.getByRole("button", { name: /Confirm: Approve/ }))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/hr/pay-runs/r1/approve")
    expect(post.mock.calls[0][1]).toEqual({ note: "Checked" })
  })

  it("will not send a run back without a reason", () => {
    wrap(<RunActions run={run("PREPARED")} {...props} />)
    fireEvent.click(screen.getByRole("button", { name: "Send back" }))
    expect((screen.getByRole("button", { name: /Confirm: Send back/ }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Reason for sending it back/), { target: { value: "Overtime missing" } })
    expect((screen.getByRole("button", { name: /Confirm: Send back/ }) as HTMLButtonElement).disabled).toBe(false)
  })

  it("disables approving your own run with the reason", () => {
    wrap(<RunActions run={run("PREPARED", "u-me")} {...props} />)
    expect((screen.getByRole("button", { name: "Approve" }) as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText(/You calculated this run/)).toBeTruthy()
  })

  it("passes the warnings of a calculation on and shows the server's refusal", async () => {
    const onCalculated = vi.fn(), onError = vi.fn()
    post.mockResolvedValueOnce({ data: { data: { warnings: ["Thandi: no membership"] } } })
    wrap(<RunActions run={run("DRAFT", null)} {...props} onCalculated={onCalculated} onError={onError} />)
    fireEvent.click(screen.getByRole("button", { name: "Calculate" }))
    await waitFor(() => expect(onCalculated).toHaveBeenCalledWith(["Thandi: no membership"]))
    post.mockRejectedValueOnce({ response: { data: { message: "There are no approved statutory rules for tax year 2028" } } })
    fireEvent.click(screen.getByRole("button", { name: "Calculate" }))
    await waitFor(() => expect(onError).toHaveBeenCalledWith("There are no approved statutory rules for tax year 2028"))
  })

  it("cancels a draft only with a reason, and sends it", async () => {
    post.mockResolvedValueOnce({ data: { data: {} } })
    wrap(<RunActions run={run("DRAFT", null)} {...props} />)
    fireEvent.click(screen.getByRole("button", { name: "Cancel this run" }))
    fireEvent.change(screen.getByLabelText(/Reason for cancelling/), { target: { value: "  " } })
    fireEvent.change(screen.getByLabelText(/Reason for cancelling/), { target: { value: "x" } })
    fireEvent.change(screen.getByLabelText(/Reason for cancelling/), { target: { value: "Wrong period" } })
    fireEvent.click(screen.getByRole("button", { name: /Confirm: Cancel this run/ }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/pay-runs/r1/cancel", { note: "Wrong period" }))
  })

  it("will not confirm a cancel without a reason", () => {
    wrap(<RunActions run={run("DRAFT", null)} {...props} />)
    fireEvent.click(screen.getByRole("button", { name: "Cancel this run" }))
    expect((screen.getByRole("button", { name: /Confirm: Cancel this run/ }) as HTMLButtonElement).disabled).toBe(true)
    expect(noteProblem({ needsNote: true, key: "cancel" }, " ")).toMatch(/cancelling/)
  })

  it("does not offer cancel to someone who cannot prepare, or on a calculated run", () => {
    expect(stepsFor(run("DRAFT"), rightsFrom(["PAYROLL_APPROVE"], false), "u-me", false).map(s => s.key)).toEqual([])
    expect(stepsFor(run("PREPARED"), everything, "u-me", false).map(s => s.key)).not.toContain("cancel")
  })

  it("only says what a run is waiting for when the person has no step to take", () => {
    wrap(<RunActions run={run("PREPARED")} {...props} rights={rightsFrom(["PAYROLL_READ"], false)} />)
    expect(screen.getByText(/Waiting for an approver/)).toBeTruthy()
    expect(screen.queryByRole("button")).toBeNull()
  })
})

describe("RunReview", () => {
  it("lists errors and warnings and the history", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.endsWith("/review")
      ? { blocking: true, errors: 1, warnings: 1, comparedWith: "PR00001", items: [
          { severity: "ERROR", code: "NET_NOT_POSITIVE", employee: "Thandi (E1)", message: "Net pay is zero or negative." },
          { severity: "WARNING", code: "MEDICAL_ASSUMED", employee: "Sipho (E2)", message: "No medical scheme membership is on record." }] }
      : [{ id: "e1", action: "CALCULATED", fromStatus: "DRAFT", toStatus: "PREPARED", actorId: "u-me", note: "2 payslips", at: "2026-10-21T10:00:00" }] } }))
    wrap(<RunReview run={run("PREPARED")} userId="u-me" />)
    expect(await screen.findByText(/1 error to fix before approval/)).toBeTruthy()
    expect(screen.getByText("Net pay is zero or negative.", { exact: false })).toBeTruthy()
    expect(await screen.findByText("Calculated by you")).toBeTruthy()
    expect(screen.getByText("2 payslips")).toBeTruthy()
  })

  it("does not ask for a review of a draft", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    wrap(<RunReview run={run("DRAFT")} userId="u-me" />)
    expect(await screen.findByText(/Nothing recorded yet/)).toBeTruthy()
    expect(get.mock.calls.some(c => String(c[0]).endsWith("/review"))).toBe(false)
  })
})

describe("PayrollControlsPanel", () => {
  const settings = { allowSelfApproval: false, legacyAccessEnds: "2026-12-31", legacyAccessOpen: true }
  const review = { legacyAccessEnds: "2026-12-31", rolesNeedingReview: 1, roles: [
    { roleId: "a", roleName: "BOOKKEEPER", usersInRole: 2, held: "USER_UPDATE", granted: "", needsReview: true, note: "Can still prepare until 31 Dec." },
    { roleId: "b", roleName: "ADMIN", usersInRole: 1, held: "PAYROLL_RUN", granted: "PAYROLL_READ, PAYROLL_PREPARE", needsReview: false, note: "Administrator role." }] }
  const serve = () => get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.endsWith("/settings") ? settings : review } }))

  it("shows the legacy end date and lets a configuration admin switch self-approval", async () => {
    perms = ["PAYROLL_CONFIGURATION_ADMIN"]; serve(); put.mockResolvedValue({ data: {} })
    wrap(<PayrollControlsPanel />)
    expect(await screen.findByText(/still calculate pay runs until/)).toBeTruthy()
    expect(await screen.findByText("1 role needs a decision.")).toBeTruthy()
    expect(screen.getByText("Needs a decision")).toBeTruthy()
    fireEvent.click(screen.getByRole("checkbox", { name: /One person may calculate and approve/ }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/hr/payroll/settings", { allowSelfApproval: true }))
  })

  it("is read-only for everyone else and does not ask for the access review", async () => {
    perms = ["PAYROLL_READ"]; serve()
    wrap(<PayrollControlsPanel />)
    const boxes = (await screen.findAllByRole("checkbox")) as HTMLInputElement[]
    expect(boxes.length).toBe(2)
    expect(boxes.every(b => b.disabled)).toBe(true)
    expect(screen.getByText(/Only someone with payroll configuration access/)).toBeTruthy()
    expect(get.mock.calls.some(c => String(c[0]).includes("access-review"))).toBe(false)
  })

  it("turns the second-person check on without touching the account", async () => {
    perms = ["PAYROLL_CONFIGURATION_ADMIN"]; serve(); put.mockResolvedValue({ data: {} })
    wrap(<PayrollControlsPanel />)
    fireEvent.click(await screen.findByRole("checkbox", { name: /A second person confirms/ }))
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/hr/payroll/settings",
      { allowSelfApproval: false, bankFileSecondCheck: true, nominatedBranch: null, nominatedAccount: null }))
  })

  it("saves the company's own account only when it is valid", async () => {
    perms = ["PAYROLL_CONFIGURATION_ADMIN"]; serve(); put.mockResolvedValue({ data: {} })
    wrap(<PayrollControlsPanel />)
    await screen.findByText(/The company's own bank account/)
    const save = screen.getByRole("button", { name: "Save account" }) as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Branch code"), { target: { value: "2506" } })
    expect(await screen.findByText("The branch code must be 6 digits.")).toBeTruthy()
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Branch code"), { target: { value: "250 655" } })
    fireEvent.change(screen.getByLabelText("Account number"), { target: { value: "6201 123 4567" } })
    fireEvent.click(save)
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/hr/payroll/settings",
      { allowSelfApproval: false, bankFileSecondCheck: false, nominatedBranch: "250655", nominatedAccount: "62011234567" }))
  })
})

import { isTaxMethod, taxMethodBody, taxMethodHint, TAX_METHODS } from "./taxMethod.logic"

describe("tax method", () => {
  it("offers the two methods and explains each", () => {
    expect(TAX_METHODS.map(t => t.value)).toEqual(["PERIODIC", "AVERAGING"])
    expect(taxMethodHint("AVERAGING")).toMatch(/year to date/)
    expect(taxMethodHint("PERIODIC")).toMatch(/each month/i)
    expect(taxMethodHint("other")).toBe("")
  })
  it("accepts only the two names and sends the method with the current approval choice", () => {
    expect(isTaxMethod("AVERAGING")).toBe(true)
    expect(isTaxMethod("cumulative")).toBe(false)
    expect(taxMethodBody(true, "AVERAGING")).toEqual({ allowSelfApproval: true, taxMethod: "AVERAGING" })
  })
})
