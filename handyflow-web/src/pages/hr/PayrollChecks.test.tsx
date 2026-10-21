import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import PayrollChecksTab from "./PayrollChecksTab"
import MedicalSchemeModal from "./MedicalSchemeModal"
import {
  EMPTY_SCHEME_FORM, impactSummary, schemeBody, schemeProblem, signedRand, taxYearName,
} from "./payrollChecks.logic"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

const year = (over: Record<string, unknown> = {}) => ({
  taxYear: 2027, label: "2026/27", effectiveFrom: "2026-03-01", effectiveTo: "2027-02-28", status: "APPROVED", usable: true, problems: [], bracketCount: 7,
  primaryRebate: 17820, secondaryRebate: 9765, tertiaryRebate: 3249, mtcTaxpayer: 376, mtcFirstDependant: 376, mtcAdditional: 254,
  uifMonthlyCeiling: 17712, retirementCapAmount: 430000, retirementCapPct: 0.275, sourceNote: "SARS pages", approvedAt: "2026-10-21T10:00:00", approvalNote: "Owner sign-off still to be recorded.", ...over,
})
const run = (over: Record<string, unknown> = {}) => ({
  runId: "r1", runNumber: "PR-001", periodStart: "2026-03-01", periodEnd: "2026-03-31", payDate: "2026-03-25", recordedTaxYear: 2026, correctTaxYear: 2027,
  status: "PROCESSED", emp201Status: null, payslips: 2, noDifference: 0, potentialDifference: 0, confirmedDifference: 2, reviewRequired: 0,
  recordedPaye: 1000, recalculatedPaye: 900, payeDifference: -100, verdict: "CONFIRMED_DIFFERENCE", action: "Review before any correction.", lines: null, ...over,
})

const SETTINGS = { allowSelfApproval: false, legacyAccessEnds: "2026-12-31", legacyAccessOpen: false }
/** Wraps a handler so the payroll settings request that the checks screen also makes gets a sensible answer. */
const serve = (handler: (url: string, cfg?: { params?: { runId?: string } }) => Promise<unknown>) =>
  get.mockImplementation((url: string, cfg?: { params?: { runId?: string } }) =>
    url.endsWith("/payroll/settings") ? Promise.resolve({ data: { data: SETTINGS } }) : handler(url, cfg))

beforeEach(() => { get.mockReset(); post.mockReset() })
afterEach(() => cleanup())

describe("payroll checks logic", () => {
  it("names tax years by the year they end", () => { expect(taxYearName(2027)).toBe("2026/27"); expect(taxYearName(2026)).toBe("2025/26") })
  it("shows a sign on differences", () => {
    expect(signedRand(-100)).toContain("−"); expect(signedRand(50)).toContain("+"); expect(signedRand(0.001)).not.toMatch(/[+−]/); expect(signedRand(null)).toBe("—")
  })
  it("summarises the impact in words", () => {
    expect(impactSummary({ runs: [], runsWithDifferences: 0 })).toMatch(/nothing to compare/)
    expect(impactSummary({ runs: [run()] as never, runsWithDifferences: 1 })).toMatch(/1 of 1 processed run differs/)
    expect(impactSummary({ runs: [run(), run()] as never, runsWithDifferences: 0 })).toMatch(/All 2 processed runs match/)
  })
  it("checks the medical scheme form", () => {
    expect(schemeProblem(EMPTY_SCHEME_FORM)).toMatch(/scheme name/)
    const f = { ...EMPTY_SCHEME_FORM, schemeName: "Discovery", effectiveFrom: "2026-03-01" }
    expect(schemeProblem(f)).toBe("")
    expect(schemeProblem({ ...f, dependants: "-1" })).toMatch(/whole number/)
    expect(schemeProblem({ ...f, dependants: "21" })).toMatch(/at most 20/)
    expect(schemeProblem({ ...f, effectiveFrom: "" })).toMatch(/starts/)
    expect(schemeBody({ ...f, memberNumber: " ", dependants: "2" })).toEqual({ schemeName: "Discovery", memberNumber: null, dependants: 2, effectiveFrom: "2026-03-01", notes: null })
  })
})

describe("PayrollChecksTab", () => {
  it("shows the rules and the impact, and flags a mislabelled run", async () => {
    serve((url: string) => Promise.resolve({ data: { data: url.includes("tax-years") ? [year(), year({ taxYear: 2028, label: "2027/28", usable: false, status: "DRAFT", problems: ["No tax brackets are loaded."] })] : { generatedAt: "x", runs: [run()], runsWithDifferences: 1, notes: ["Nothing is changed."] } } }))
    wrap(<PayrollChecksTab />)
    expect(await screen.findByText("Approved, in use")).toBeTruthy()
    expect(screen.getByText("Cannot be used")).toBeTruthy()
    expect(screen.getByText("No tax brackets are loaded.")).toBeTruthy()
    expect(await screen.findByText(/was labelled 2025\/26/)).toBeTruthy()
    expect(screen.getByText("Confirmed difference")).toBeTruthy()
    expect(screen.getByText(/1 of 1 processed run differs/)).toBeTruthy()
  })

  it("loads the payslips of a run only when asked", async () => {
    serve((url: string, cfg?: { params?: { runId?: string } }) => Promise.resolve({ data: { data: url.includes("tax-years") ? [] : cfg?.params?.runId
      ? { generatedAt: "x", runsWithDifferences: 1, notes: [], runs: [run({ lines: [{ payslipId: "p1", employeeId: "e1", employeeName: "Thandi Nkosi", employeeNumber: "E001", verdict: "CONFIRMED_DIFFERENCE", recordedPaye: 500, recalculatedPaye: 450, payeDifference: -50, uifDifference: 0, sdlDifference: 0, medicalMembers: 1, medicalAssumed: true, reasons: ["Medical membership is assumed."] }] })] }
      : { generatedAt: "x", runs: [run()], runsWithDifferences: 1, notes: [] } } }))
    wrap(<PayrollChecksTab />)
    fireEvent.click(await screen.findByRole("button", { name: /Show payslips of PR-001/ }))
    expect(await screen.findByText("Thandi Nkosi")).toBeTruthy()
    expect(screen.getByText("Medical membership is assumed.")).toBeTruthy()
  })

  it("says so when there are no tax years or the report fails", async () => {
    serve((url: string) => url.includes("tax-years") ? Promise.resolve({ data: { data: [] } }) : Promise.reject({ response: { data: { message: "Boom" } } }))
    wrap(<PayrollChecksTab />)
    expect(await screen.findByText(/payroll cannot run/)).toBeTruthy()
    expect(await screen.findByText("Boom")).toBeTruthy()
  })
})

describe("MedicalSchemeModal", () => {
  const emp = { id: "e1", fullName: "Thandi Nkosi", employeeNumber: "E001" }
  it("lists history and saves a membership", async () => {
    get.mockResolvedValue({ data: { data: [{ id: "m1", schemeName: "Bonitas", memberNumber: "99", dependants: 2, peopleCovered: 3, effectiveFrom: "2026-03-01", effectiveTo: null, current: true, notes: null }] } })
    post.mockResolvedValue({ data: {} })
    const onClose = vi.fn()
    wrap(<MedicalSchemeModal employee={emp} onClose={onClose} />)
    expect(await screen.findByText("Bonitas")).toBeTruthy()
    expect(screen.getByText("Current")).toBeTruthy()
    const save = screen.getByRole("button", { name: "Save membership" }) as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Scheme name/), { target: { value: "Discovery" } })
    fireEvent.change(screen.getByLabelText(/Starts on/), { target: { value: "2026-06-01" } })
    fireEvent.change(screen.getByLabelText(/Dependants/), { target: { value: "1" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/hr/employees/e1/medical-scheme")
    expect(post.mock.calls[0][1]).toMatchObject({ schemeName: "Discovery", dependants: 1, effectiveFrom: "2026-06-01" })
  })

  it("shows the server's reason and closes on Escape", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    post.mockRejectedValue({ response: { data: { message: "A membership already starts after that date." } } })
    const onClose = vi.fn()
    wrap(<MedicalSchemeModal employee={emp} onClose={onClose} />)
    expect(await screen.findByText(/No membership is recorded/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText(/Scheme name/), { target: { value: "X" } })
    fireEvent.change(screen.getByLabelText(/Starts on/), { target: { value: "2026-06-01" } })
    fireEvent.click(screen.getByRole("button", { name: "Save membership" }))
    expect(await screen.findByText("A membership already starts after that date.")).toBeTruthy()
    expect(screen.getByRole("dialog")).toBeTruthy()
    fireEvent.keyDown(document, { key: "Escape" })
    expect(onClose).toHaveBeenCalled()
  })
})
