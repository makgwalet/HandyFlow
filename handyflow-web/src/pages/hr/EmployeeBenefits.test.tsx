import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import EmployeeBenefitsModal from "./EmployeeBenefitsModal"
import { EMPTY_BENEFIT_FORM, benefitBody, benefitProblem, canEditBenefits, endProblem, type Benefit, type BenefitForm } from "./benefits.logic"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

const line = (over: Partial<Benefit> = {}): Benefit => ({
  id: "b1", employeeId: "e1", kind: "EMPLOYER_MEDICAL", kindLabel: "Employer medical scheme contribution", monthlyAmount: 3000,
  effectiveFrom: "2026-03-01", effectiveTo: null, description: null, reason: "Scheme joined", inForce: true, ...over,
})
const form = (over: Partial<BenefitForm> = {}): BenefitForm => ({ ...EMPTY_BENEFIT_FORM, kind: "EMPLOYER_MEDICAL", amount: "3000", from: "2026-04-01", reason: "Joined", ...over })
const EMP = { id: "e1", fullName: "Thandi Nkosi", employeeNumber: "E001" }

beforeEach(() => { get.mockReset(); post.mockReset() })
afterEach(cleanup)

describe("benefit rules", () => {
  it("accepts a complete line and rejects each missing part", () => {
    expect(benefitProblem(form(), [])).toBe("")
    expect(benefitProblem(form({ kind: "" }), [])).toMatch(/kind/)
    expect(benefitProblem(form({ amount: "0" }), [])).toMatch(/amount/)
    expect(benefitProblem(form({ amount: "10.123" }), [])).toMatch(/amount/)
    expect(benefitProblem(form({ from: "" }), [])).toMatch(/date/)
    expect(benefitProblem(form({ to: "2026-03-01" }), [])).toMatch(/before the start/)
    expect(benefitProblem(form({ reason: " " }), [])).toMatch(/reason/)
  })
  it("needs a description for other benefits only", () => {
    expect(benefitProblem(form({ kind: "OTHER_FRINGE" }), [])).toMatch(/Describe/)
    expect(benefitProblem(form({ kind: "OTHER_FRINGE", description: "Company car" }), [])).toBe("")
  })
  it("refuses an overlap for medical and retirement, but not for other benefits", () => {
    expect(benefitProblem(form(), [line()])).toMatch(/already has/)
    expect(benefitProblem(form({ kind: "EMPLOYER_RETIREMENT" }), [line()])).toBe("")
    expect(benefitProblem(form(), [line({ effectiveTo: "2026-03-31" })])).toBe("")
    expect(benefitProblem(form({ kind: "OTHER_FRINGE", description: "Car" }), [line({ kind: "OTHER_FRINGE" })])).toBe("")
  })
  it("builds the request body", () => {
    expect(benefitBody(form({ amount: "1500.50", to: "", description: " " }))).toEqual({
      kind: "EMPLOYER_MEDICAL", monthlyAmount: 1500.5, effectiveFrom: "2026-04-01", effectiveTo: null, description: null, reason: "Joined" })
  })
  it("checks the last day when ending", () => {
    expect(endProblem(line(), "")).toMatch(/last day/)
    expect(endProblem(line(), "2026-02-28")).toMatch(/before the benefit started/)
    expect(endProblem(line(), "2026-06-30")).toBe("")
  })
  it("limits editing to people who prepare payroll", () => {
    expect(canEditBenefits(["PAYROLL_PREPARE"])).toBe(true)
    expect(canEditBenefits(["PAYROLL_APPROVE"])).toBe(false)
  })
})

describe("EmployeeBenefitsModal", () => {
  it("lists the benefits", async () => {
    get.mockResolvedValue({ data: { data: [line()] } })
    wrap(<EmployeeBenefitsModal employee={EMP} permissions={["PAYROLL_PREPARE"]} onClose={() => {}} />)
    expect(await screen.findByText(/Reason: Scheme joined/)).toBeTruthy()
    expect(screen.getByText("In force")).toBeTruthy()
    expect(get).toHaveBeenCalledWith("/api/v1/hr/employees/e1/benefits")
  })

  it("saves a new benefit", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    post.mockResolvedValue({ data: {} })
    wrap(<EmployeeBenefitsModal employee={EMP} permissions={["PAYROLL_PREPARE"]} onClose={() => {}} />)
    await screen.findByText(/No employer benefits/)
    fireEvent.change(screen.getByLabelText(/Kind/), { target: { value: "EMPLOYER_RETIREMENT" } })
    fireEvent.change(screen.getByLabelText(/Amount a month/), { target: { value: "2000" } })
    fireEvent.change(screen.getByLabelText(/Starts on/), { target: { value: "2026-04-01" } })
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "Fund joined" } })
    fireEvent.click(screen.getByRole("button", { name: "Save benefit" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/employees/e1/benefits",
      expect.objectContaining({ kind: "EMPLOYER_RETIREMENT", monthlyAmount: 2000, reason: "Fund joined" })))
  })

  it("keeps Save off until the form is complete", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    wrap(<EmployeeBenefitsModal employee={EMP} permissions={["PAYROLL_PREPARE"]} onClose={() => {}} />)
    await screen.findByText(/No employer benefits/)
    expect((screen.getByRole("button", { name: "Save benefit" }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("ends a benefit on the chosen day", async () => {
    get.mockResolvedValue({ data: { data: [line()] } })
    post.mockResolvedValue({ data: {} })
    wrap(<EmployeeBenefitsModal employee={EMP} permissions={["PAYROLL_PREPARE"]} onClose={() => {}} />)
    fireEvent.click(await screen.findByRole("button", { name: /^End Employer medical/ }))
    fireEvent.change(screen.getByLabelText(/Last day of/), { target: { value: "2026-06-30" } })
    fireEvent.click(screen.getByRole("button", { name: "End benefit" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/benefits/b1/end", { lastDay: "2026-06-30" }))
  })

  it("shows the server's message when saving fails", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    post.mockRejectedValue({ response: { data: { message: "Only a payroll preparer can do this." } } })
    wrap(<EmployeeBenefitsModal employee={EMP} permissions={["USER_UPDATE"]} onClose={() => {}} />)
    await screen.findByText(/No employer benefits/)
    fireEvent.change(screen.getByLabelText(/Kind/), { target: { value: "EMPLOYER_MEDICAL" } })
    fireEvent.change(screen.getByLabelText(/Amount a month/), { target: { value: "100" } })
    fireEvent.change(screen.getByLabelText(/Starts on/), { target: { value: "2026-04-01" } })
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "x" } })
    fireEvent.click(screen.getByRole("button", { name: "Save benefit" }))
    expect(await screen.findByText("Only a payroll preparer can do this.")).toBeTruthy()
  })

  it("is read-only without prepare rights", async () => {
    get.mockResolvedValue({ data: { data: [line()] } })
    wrap(<EmployeeBenefitsModal employee={EMP} permissions={["PAYROLL_READ"]} onClose={() => {}} />)
    await screen.findByText(/Reason: Scheme joined/)
    expect(screen.queryByRole("button", { name: "Save benefit" })).toBeNull()
    expect(screen.queryByRole("button", { name: /^End / })).toBeNull()
  })
})
