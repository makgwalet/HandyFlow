import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import TaxCertificatesTab from "./TaxCertificatesTab"
import {
  countBySeverity, defaultSelection, issueSummary, needsReason, periodLabel, settingsProblem, taxRights, taxYearOf, yearOptions,
} from "./taxCertificates.logic"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a) } }))

let perms: string[] = []
vi.mock("../../hooks/useAuth", () => ({ useAuth: () => ({ user: { userId: "u-me", permissions: perms } }) }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const ok = (data: unknown) => Promise.resolve({ data: { data } })

describe("tax certificate rules in the browser", () => {
  it("names the tax year by the year it ends in", () => {
    expect(taxYearOf(new Date(2026, 9, 21))).toBe(2027)
    expect(taxYearOf(new Date(2027, 1, 28))).toBe(2027)
    expect(taxYearOf(new Date(2027, 2, 1))).toBe(2028)
  })
  it("suggests the reconciliation that is usually wanted", () => {
    expect(defaultSelection(new Date(2026, 9, 21))).toEqual({ taxYear: 2027, period: "INTERIM" })
    expect(defaultSelection(new Date(2027, 0, 15))).toEqual({ taxYear: 2027, period: "INTERIM" })
    expect(defaultSelection(new Date(2027, 3, 10))).toEqual({ taxYear: 2027, period: "ANNUAL" })
    expect(defaultSelection(new Date(2027, 8, 1))).toEqual({ taxYear: 2028, period: "INTERIM" })
  })
  it("words the periods", () => {
    expect(periodLabel(2027, "INTERIM")).toBe("Interim 2027: 1 March 2026 to 31 August 2026")
    expect(periodLabel(2027, "ANNUAL")).toContain("end of February 2027")
  })
  it("offers six years ending with the current one", () => {
    expect(yearOptions(new Date(2026, 9, 21))).toEqual([2027, 2026, 2025, 2024, 2023, 2022])
  })
  it("separates viewing, issuing and configuring", () => {
    const t = new Date(2026, 9, 21)
    expect(taxRights(["PAYROLL_STATUTORY_EXPORT"], t)).toEqual({ view: true, issue: false, configure: false })
    expect(taxRights(["PAYROLL_CERTIFICATE_ISSUE", "PAYROLL_CONFIGURATION_ADMIN"], t)).toEqual({ view: false, issue: true, configure: true })
    expect(taxRights(["PAYROLL_READ"], t).view).toBe(true)
    expect(taxRights(["PAYROLL_READ"], new Date(2027, 0, 5)).view).toBe(false)
  })
  it("counts problems and knows when a reason is needed", () => {
    expect(countBySeverity([{ severity: "ERROR", subject: "", message: "" }, { severity: "WARNING", subject: "", message: "" }, { severity: "INFO", subject: "", message: "" }]))
      .toEqual({ errors: 1, warnings: 1 })
    expect(needsReason([{ state: "NEW" }, { state: "UNCHANGED" }])).toBe(false)
    expect(needsReason([{ state: "CHANGED" }])).toBe(true)
    expect(issueSummary([{ state: "NEW" }, { state: "NEW" }, { state: "CHANGED" }, { state: "UNCHANGED" }])).toBe("2 new, 1 to replace, 1 already issued")
    expect(issueSummary([])).toBe("nothing to issue")
  })
  it("checks the PAYE reference", () => {
    expect(settingsProblem("")).toBeNull()
    expect(settingsProblem("7123 456 789")).toBeNull()
    expect(settingsProblem("123")).toMatch(/10 digits/)
  })
})

const cert = (over: Record<string, unknown> = {}) => ({
  id: null, taxYear: 2027, period: "INTERIM", employeeId: "e1", employeeNumber: "EMP001", employeeName: "Jane Dlamini",
  idNumber: "8001015009087", taxNumber: "0123456789", certificateNumber: null, certificateType: "IRP5", version: 1, status: "DRAFT", state: "NEW",
  periodFrom: "2026-03-01", periodTo: "2026-08-31", payslipCount: 6, issuedAt: null, cancelledAt: null, cancelReason: null, releasedAt: null,
  lines: [{ code: "3601", description: "Income: salary or wages", amount: 180000 }, { code: "4102", description: "PAYE", amount: 21000.5 }], problems: [], ...over,
})

const recon = (over: Record<string, unknown> = {}) => ({
  taxYear: 2027, period: "INTERIM", periodFrom: "2026-03-01", periodTo: "2026-08-31", runs: 6, canIssue: true, interim: true,
  problems: [], reconciliation: [
    { tax: "PAYE", payslips: 100, certificates: 100, emp201: 100, difference: 0, balanced: true },
    { tax: "UIF", payslips: 10, certificates: 10, emp201: 10, difference: 0, balanced: true },
    { tax: "SDL", payslips: 5, certificates: 5, emp201: 5, difference: 0, balanced: true }],
  certificates: [cert()], notProduced: ["3695 to 3699 totals could not be confirmed."], ...over,
})

function mockApi(r: unknown) {
  get.mockImplementation((url: string) => {
    if (url.includes("/settings")) return ok({ payeReference: "7123456789", sdlReference: null, uifReference: null, retirementFundType: "PENSION" })
    if (url.includes("/reconciliation")) return ok(r)
    return ok([])
  })
}

describe("TaxCertificatesTab", () => {
  beforeEach(() => { get.mockReset(); post.mockReset(); put.mockReset(); perms = ["PAYROLL_STATUTORY_EXPORT"] })
  afterEach(cleanup)

  it("shows the three-way comparison and what is not produced", async () => {
    mockApi(recon())
    wrap(<TaxCertificatesTab />)
    expect(await screen.findByText("PAYE")).toBeTruthy()
    expect(screen.getAllByText("Agrees").length).toBe(3)
    expect(screen.getByText(/3695 to 3699 totals could not be confirmed/)).toBeTruthy()
    expect(screen.getByText(/workpaper/i)).toBeTruthy()
  })

  it("does not offer to issue without the right", async () => {
    mockApi(recon())
    wrap(<TaxCertificatesTab />)
    await screen.findByText("PAYE")
    expect(screen.queryByRole("button", { name: /issue certificates/i })).toBeNull()
  })

  it("will not issue while something must be fixed", async () => {
    perms = ["PAYROLL_STATUTORY_EXPORT", "PAYROLL_CERTIFICATE_ISSUE"]
    mockApi(recon({ canIssue: false, problems: [{ severity: "ERROR", subject: "PR-2", message: "This pay run is approved. Finalise it before certificates are prepared." }] }))
    wrap(<TaxCertificatesTab />)
    expect(await screen.findByText(/Finalise it before/)).toBeTruthy()
    expect((screen.getByRole("button", { name: /issue certificates/i }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("issues when everything checks out", async () => {
    perms = ["PAYROLL_STATUTORY_EXPORT", "PAYROLL_CERTIFICATE_ISSUE"]
    mockApi(recon())
    post.mockReturnValue(ok({ issued: 1, unchanged: 0, replaced: 0 }))
    wrap(<TaxCertificatesTab />)
    const btn = await screen.findByRole("button", { name: /issue certificates/i })
    await waitFor(() => expect((btn as HTMLButtonElement).disabled).toBe(false))
    fireEvent.click(btn)
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/hr/tax-certificates/issue")
    expect(post.mock.calls[0][1]).toMatchObject({ period: "INTERIM", reason: null })
  })

  it("asks for a reason before replacing certificates", async () => {
    perms = ["PAYROLL_STATUTORY_EXPORT", "PAYROLL_CERTIFICATE_ISSUE"]
    mockApi(recon({ certificates: [cert({ state: "CHANGED", certificateNumber: "7123456789202708" + "00000000000001", version: 2 })] }))
    post.mockReturnValue(ok({ issued: 1, unchanged: 0, replaced: 1 }))
    wrap(<TaxCertificatesTab />)
    const btn = await screen.findByRole("button", { name: /issue certificates/i })
    expect((btn as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/reason for replacing/i), { target: { value: "Back pay added" } })
    expect((btn as HTMLButtonElement).disabled).toBe(false)
    fireEvent.click(btn)
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1]).toMatchObject({ reason: "Back pay added" })
  })

  it("shows the code lines of a certificate when opened", async () => {
    mockApi(recon())
    wrap(<TaxCertificatesTab />)
    fireEvent.click(await screen.findByText("Jane Dlamini"))
    expect(await screen.findByText("Income: salary or wages")).toBeTruthy()
    expect(screen.getByText("3601")).toBeTruthy()
  })

  it("cannot release interim certificates", async () => {
    perms = ["PAYROLL_STATUTORY_EXPORT", "PAYROLL_CERTIFICATE_ISSUE"]
    mockApi(recon())
    wrap(<TaxCertificatesTab />)
    await screen.findByText("PAYE")
    expect(screen.queryByRole("button", { name: /release to employees/i })).toBeNull()
    expect(screen.getByText(/interim certificates are for SARS only/i)).toBeTruthy()
  })

  it("saves the PAYE reference with the configuration right", async () => {
    perms = ["PAYROLL_STATUTORY_EXPORT", "PAYROLL_CONFIGURATION_ADMIN"]
    mockApi(recon())
    put.mockReturnValue(ok({}))
    wrap(<TaxCertificatesTab />)
    const input = await screen.findByLabelText(/PAYE reference/i)
    await waitFor(() => expect((input as HTMLInputElement).value).toBe("7123456789"))
    fireEvent.change(input, { target: { value: "12" } })
    expect(screen.getByText(/10 digits/)).toBeTruthy()
    fireEvent.change(input, { target: { value: "7000000001" } })
    fireEvent.click(screen.getByRole("button", { name: /save tax settings/i }))
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0][1]).toMatchObject({ payeReference: "7000000001" })
  })
})
