import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import PayAdjustmentsModal from "./PayAdjustmentsModal"
import { EMPTY_PAY_FORM, EMPTY_SEVERANCE_FORM, severanceBody, severanceNet, severanceProblem, severanceShown, type Severance, noticeBody, noticeProblem, noticeShown, type NoticePay, advanceBody, advanceProblem, advanceShown, cancelProblem, canEnterPay, payBody, payProblem, type PayAdjustment, type PayForm } from "./payAdjustments.logic"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

const TODAY = "2026-10-28"
const item = (over: Partial<PayAdjustment> = {}): PayAdjustment => ({
  id: "p1", employeeId: "e1", employeeNumber: "E001", employeeName: "Thandi Nkosi", kind: "BONUS", kindLabel: "Bonus", amount: 20000,
  payFrom: "2026-10-01", description: "Annual bonus", relatesFrom: null, relatesTo: null, reason: "Board decision", status: "OPEN",
  payRunId: null, payRunNumber: null, canCancel: true, cancelReason: null, createdAt: "2026-10-20T08:00:00Z", ...over,
})
const form = (over: Partial<PayForm> = {}): PayForm => ({ ...EMPTY_PAY_FORM, kind: "BONUS", amount: "20000", payFrom: "2026-10-01", description: "Annual bonus", reason: "Board decision", ...over })
const EMP = { id: "e1", fullName: "Thandi Nkosi", employeeNumber: "E001", startDate: "2024-01-15" }

beforeEach(() => { get.mockReset(); post.mockReset() })
afterEach(cleanup)

describe("pay item rules", () => {
  it("accepts a complete bonus and rejects each missing part", () => {
    expect(payProblem(form(), TODAY)).toBe("")
    expect(payProblem(form({ kind: "" }), TODAY)).toMatch(/bonus, back pay or recovery/)
    expect(payProblem(form({ amount: "0" }), TODAY)).toMatch(/amount/)
    expect(payProblem(form({ amount: "10.123" }), TODAY)).toMatch(/amount/)
    expect(payProblem(form({ amount: "10000000.01" }), TODAY)).toMatch(/too large/)
    expect(payProblem(form({ payFrom: "" }), TODAY)).toMatch(/pay month/)
    expect(payProblem(form({ description: " " }), TODAY)).toMatch(/Describe/)
    expect(payProblem(form({ reason: " " }), TODAY)).toMatch(/reason/)
  })
  it("refuses a date more than a year away but accepts one in the past", () => {
    expect(payProblem(form({ payFrom: "2028-01-01" }), TODAY)).toMatch(/more than a year/)
    expect(payProblem(form({ payFrom: "2026-08-01" }), TODAY)).toBe("")
  })
  it("makes back pay say which period it covers, and that period must have passed", () => {
    const back = (over: Partial<PayForm>) => form({ kind: "BACK_PAY", relatesFrom: "2026-07-01", relatesTo: "2026-09-30", ...over })
    expect(payProblem(back({}), TODAY, "2024-01-15")).toBe("")
    expect(payProblem(back({ relatesFrom: "" }), TODAY)).toMatch(/which period/)
    expect(payProblem(back({ relatesFrom: "2026-09-30", relatesTo: "2026-07-01" }), TODAY)).toMatch(/cannot end before/)
    expect(payProblem(back({ relatesTo: "2026-11-30" }), TODAY)).toMatch(/already passed/)
    expect(payProblem(back({ relatesFrom: "2023-12-01" }), TODAY, "2024-01-15")).toMatch(/before the employee did/)
  })
  it("does not let a bonus carry a period", () => {
    expect(payProblem(form({ relatesFrom: "2026-07-01" }), TODAY)).toMatch(/Only back pay covers a period/)
  })
  it("accepts a recovery with no period, and refuses one that names a period", () => {
    expect(payProblem(form({ kind: "RECOVERY", description: "Overpaid in September", reason: "Paid twice" }), TODAY)).toBe("")
    expect(payProblem(form({ kind: "RECOVERY", relatesTo: "2026-07-01" }), TODAY)).toMatch(/Only back pay covers a period/)
  })
  it("builds the request body, leaving the period out for a bonus", () => {
    expect(payBody(form({ amount: "1500.50", description: " Annual bonus ", relatesFrom: "2026-07-01" }))).toEqual({
      kind: "BONUS", amount: 1500.5, payFrom: "2026-10-01", description: "Annual bonus", relatesFrom: null, relatesTo: null, reason: "Board decision" })
    expect(payBody(form({ kind: "BACK_PAY", relatesFrom: "2026-07-01", relatesTo: "2026-09-30" }))).toMatchObject({ relatesFrom: "2026-07-01", relatesTo: "2026-09-30" })
  })
  it("needs a reason to cancel", () => {
    expect(cancelProblem(" ")).toMatch(/reason/)
    expect(cancelProblem("x".repeat(301))).toMatch(/too long/)
    expect(cancelProblem("Entered twice")).toBe("")
  })
  it("limits entering to people who prepare payroll", () => {
    expect(canEnterPay(["PAYROLL_PREPARE"])).toBe(true)
    expect(canEnterPay(["PAYROLL_APPROVE"])).toBe(false)
  })
})

describe("PayAdjustmentsModal", () => {
  it("lists the payments with their status", async () => {
    get.mockResolvedValue({ data: { data: [item(), item({ id: "p2", payRunNumber: "PR00005", payRunId: "r1", canCancel: false, description: "Raise" })] } })
    wrap(<PayAdjustmentsModal employee={EMP} permissions={["PAYROLL_PREPARE"]} onClose={() => {}} />)
    expect(await screen.findByText("Waiting for a pay run")).toBeTruthy()
    expect(screen.getByText("In PR00005")).toBeTruthy()
    expect(get).toHaveBeenCalledWith("/api/v1/hr/employees/e1/pay-adjustments")
    expect(screen.getAllByRole("button", { name: /^Cancel Bonus/ }).length).toBe(1)
  })

  it("saves a bonus", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    post.mockResolvedValue({ data: {} })
    wrap(<PayAdjustmentsModal employee={EMP} permissions={["PAYROLL_PREPARE"]} onClose={() => {}} />)
    await screen.findByText(/No bonuses, back pay or recoveries/)
    fireEvent.change(screen.getByLabelText(/^Kind/), { target: { value: "BONUS" } })
    fireEvent.change(screen.getByLabelText(/^Amount/), { target: { value: "5000" } })
    fireEvent.change(screen.getByLabelText(/^Pay from/), { target: { value: "2026-11-01" } })
    fireEvent.change(screen.getByLabelText(/^Description/), { target: { value: "December bonus" } })
    fireEvent.change(screen.getByLabelText(/^Reason/), { target: { value: "Company results" } })
    fireEvent.click(screen.getByRole("button", { name: "Save payment" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/employees/e1/pay-adjustments",
      expect.objectContaining({ kind: "BONUS", amount: 5000, payFrom: "2026-11-01", relatesFrom: null })))
  })

  it("asks for the period only when the payment is back pay", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    wrap(<PayAdjustmentsModal employee={EMP} permissions={["PAYROLL_PREPARE"]} onClose={() => {}} />)
    await screen.findByText(/No bonuses, back pay or recoveries/)
    expect(screen.queryByLabelText(/Period covered: from/)).toBeNull()
    fireEvent.change(screen.getByLabelText(/^Kind/), { target: { value: "BACK_PAY" } })
    expect(screen.getByLabelText(/Period covered: from/)).toBeTruthy()
  })

  it("keeps Save off until the form is complete", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    wrap(<PayAdjustmentsModal employee={EMP} permissions={["PAYROLL_PREPARE"]} onClose={() => {}} />)
    await screen.findByText(/No bonuses, back pay or recoveries/)
    expect((screen.getByRole("button", { name: "Save payment" }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("cancels a payment with a reason", async () => {
    get.mockResolvedValue({ data: { data: [item()] } })
    post.mockResolvedValue({ data: {} })
    wrap(<PayAdjustmentsModal employee={EMP} permissions={["PAYROLL_PREPARE"]} onClose={() => {}} />)
    fireEvent.click(await screen.findByRole("button", { name: /^Cancel Bonus/ }))
    fireEvent.change(screen.getByLabelText(/^Why cancel/), { target: { value: "Entered twice" } })
    fireEvent.click(screen.getByRole("button", { name: "Cancel payment" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/pay-adjustments/p1/cancel", { reason: "Entered twice" }))
  })

  it("shows the server's message when saving fails, such as entering a payment for yourself", async () => {
    get.mockResolvedValue({ data: { data: [] } })
    post.mockRejectedValue({ response: { data: { message: "You cannot enter a payment for yourself. Ask another payroll user." } } })
    wrap(<PayAdjustmentsModal employee={EMP} permissions={["PAYROLL_PREPARE"]} onClose={() => {}} />)
    await screen.findByText(/No bonuses, back pay or recoveries/)
    fireEvent.change(screen.getByLabelText(/^Kind/), { target: { value: "BONUS" } })
    fireEvent.change(screen.getByLabelText(/^Amount/), { target: { value: "100" } })
    fireEvent.change(screen.getByLabelText(/^Pay from/), { target: { value: "2026-11-01" } })
    fireEvent.change(screen.getByLabelText(/^Description/), { target: { value: "x" } })
    fireEvent.change(screen.getByLabelText(/^Reason/), { target: { value: "y" } })
    fireEvent.click(screen.getByRole("button", { name: "Save payment" }))
    expect(await screen.findByText(/cannot enter a payment for yourself/)).toBeTruthy()
  })

  it("is read-only without prepare rights", async () => {
    get.mockResolvedValue({ data: { data: [item()] } })
    wrap(<PayAdjustmentsModal employee={EMP} permissions={["PAYROLL_READ"]} onClose={() => {}} />)
    await screen.findByText(/Reason: Board decision/)
    expect(screen.queryByRole("button", { name: "Save payment" })).toBeNull()
    expect(screen.queryByRole("button", { name: /^Cancel Bonus/ })).toBeNull()
  })
})

describe("leave taken in advance", () => {
  const ADV = { terminated: true, days: 2.5, amount: 3571.43, alreadyEntered: false }
  const GONE = { ...EMP, status: "TERMINATED" }
  it("needs a named, dated agreement that is not in the future", () => {
    expect(advanceProblem({ ref: "", date: "2026-10-01" }, TODAY)).toMatch(/written agreement/)
    expect(advanceProblem({ ref: "x".repeat(101), date: "2026-10-01" }, TODAY)).toMatch(/too long/)
    expect(advanceProblem({ ref: "Letter", date: "" }, TODAY)).toMatch(/date/)
    expect(advanceProblem({ ref: "Letter", date: "2026-11-01" }, TODAY)).toMatch(/future/)
    expect(advanceProblem({ ref: "Letter", date: "2026-10-01" }, TODAY)).toBeNull()
    expect(advanceBody({ ref: " Letter ", date: "2026-10-01" })).toEqual({ agreementRef: "Letter", agreementDate: "2026-10-01" })
  })
  it("shows the panel only for a terminated employee with days taken", () => {
    expect(advanceShown(undefined)).toBe(false)
    expect(advanceShown({ ...ADV, terminated: false })).toBe(false)
    expect(advanceShown({ ...ADV, days: 0 })).toBe(false)
    expect(advanceShown(ADV)).toBe(true)
  })
  it("takes the leave back with the agreement", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.endsWith("leave-advance") ? ADV : [] } }))
    post.mockResolvedValue({ data: {} })
    wrap(<PayAdjustmentsModal employee={GONE} permissions={["PAYROLL_PREPARE"]} onClose={() => {}} />)
    await screen.findByText(/2.5 day\(s\) of annual leave were taken before/)
    const btn = screen.getByRole("button", { name: "Take back from final pay" }) as HTMLButtonElement
    expect(btn.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/^Written agreement/), { target: { value: "Leave letter" } })
    fireEvent.change(screen.getByLabelText(/^Dated/), { target: { value: "2026-09-01" } })
    fireEvent.click(btn)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/employees/e1/leave-advance/recover", { agreementRef: "Leave letter", agreementDate: "2026-09-01" }))
  })
  it("does not offer a second recovery, and is not asked for an active employee", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.endsWith("leave-advance") ? { ...ADV, alreadyEntered: true } : [] } }))
    wrap(<PayAdjustmentsModal employee={GONE} permissions={["PAYROLL_PREPARE"]} onClose={() => {}} />)
    await screen.findByText(/A recovery is already entered/)
    expect(screen.queryByRole("button", { name: "Take back from final pay" })).toBeNull()
    cleanup(); get.mockReset(); get.mockResolvedValue({ data: { data: [] } })
    wrap(<PayAdjustmentsModal employee={EMP} permissions={["PAYROLL_PREPARE"]} onClose={() => {}} />)
    await screen.findByText(/No bonuses, back pay or recoveries/)
    expect(get).not.toHaveBeenCalledWith("/api/v1/hr/employees/e1/leave-advance")
  })
})

describe("pay in place of notice", () => {
  const NP: NoticePay = { terminated: true, endType: "EMPLOYER_NOTICE", endLabel: "ended by the employer, with notice owed", requiredDays: 28, givenDays: 14, shortfallDays: 14, owed: 12000, cap: 24000, alreadyEntered: false }
  const GONE = { ...EMP, status: "TERMINATED" }
  it("is shown only when the employer ended employment with notice owed", () => {
    expect(noticeShown(undefined)).toBe(false)
    expect(noticeShown({ ...NP, endType: "RESIGNATION" })).toBe(false)
    expect(noticeShown({ ...NP, terminated: false })).toBe(false)
    expect(noticeShown(NP)).toBe(true)
    expect(noticeShown({ ...NP, endType: "RETRENCHMENT" })).toBe(true)
  })
  it("takes the pay owed when the amount is blank, allows more up to the whole period, and needs a reason", () => {
    expect(noticeProblem({ amount: "", reason: "" }, NP)).toMatch(/reason/)
    expect(noticeProblem({ amount: "", reason: "Two weeks short" }, NP)).toBeNull()
    expect(noticeProblem({ amount: "24000", reason: "Paid in full" }, NP)).toBeNull()
    expect(noticeProblem({ amount: "24000.01", reason: "x" }, NP)).toMatch(/whole notice period/)
    expect(noticeProblem({ amount: "0", reason: "x" }, NP)).toMatch(/amount/)
    expect(noticeProblem({ amount: "10.123", reason: "x" }, NP)).toMatch(/two decimals/)
    expect(noticeProblem({ amount: "", reason: "x" }, { ...NP, owed: 0 })).toMatch(/whole notice period was given/)
    expect(noticeProblem({ amount: "", reason: "x" }, { ...NP, alreadyEntered: true })).toMatch(/already entered/)
    expect(noticeBody({ amount: "", reason: " Two weeks short " })).toEqual({ amount: null, reason: "Two weeks short" })
    expect(noticeBody({ amount: "12000.5", reason: "r" })).toEqual({ amount: 12000.5, reason: "r" })
  })
  it("enters the notice pay", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.endsWith("notice-pay") ? NP : url.endsWith("leave-advance") ? { terminated: true, days: 0, amount: 0, alreadyEntered: false } : [] } }))
    post.mockResolvedValue({ data: {} })
    wrap(<PayAdjustmentsModal employee={GONE} permissions={["PAYROLL_PREPARE"]} onClose={() => {}} />)
    await screen.findByText(/Notice required: 28 days\. Given: 14 days\. Short: 14 days/)
    const btn = screen.getByRole("button", { name: "Enter notice pay" }) as HTMLButtonElement
    expect(btn.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/^Reason/), { target: { value: "Two weeks short" } })
    fireEvent.click(btn)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/employees/e1/notice-pay/enter", { amount: null, reason: "Two weeks short" }))
  })
})

describe("severance on retrenchment", () => {
  const SV: Severance = { retrenched: true, endType: "RETRENCHMENT", completedYears: 2, weeklyPay: 6923.08, minimum: 13846.16, summary: "Severance owed at least: 13846.16.", alreadyEntered: false }
  const OK = { amount: "20000", directive: "IRP3-1", tax: "1500.50", refused: false, reason: "Position made redundant" }
  it("is shown only for someone who was retrenched", () => {
    expect(severanceShown(undefined)).toBe(false)
    expect(severanceShown({ ...SV, retrenched: false })).toBe(false)
    expect(severanceShown(SV)).toBe(true)
  })
  it("needs the amount, a directive, its tax and a reason", () => {
    expect(severanceProblem(OK, SV)).toBeNull()
    expect(severanceProblem({ ...OK, amount: "" }, SV)).toMatch(/severance amount/)
    expect(severanceProblem({ ...OK, amount: "10.123" }, SV)).toMatch(/two decimals/)
    expect(severanceProblem({ ...OK, directive: " " }, SV)).toMatch(/directive number/)
    expect(severanceProblem({ ...OK, directive: "a$b" }, SV)).toMatch(/digits/)
    expect(severanceProblem({ ...OK, tax: "" }, SV)).toMatch(/tax SARS directed/)
    expect(severanceProblem({ ...OK, tax: "20000.01" }, SV)).toMatch(/more than the severance/)
    expect(severanceProblem({ ...OK, tax: "20000" }, SV)).toBeNull()
    expect(severanceProblem({ ...OK, reason: "" }, SV)).toMatch(/reason/)
    expect(severanceProblem(OK, { ...SV, alreadyEntered: true })).toMatch(/already entered/)
  })
  it("holds to the BCEA minimum unless other work was refused", () => {
    expect(severanceProblem({ ...OK, amount: "13846.15", tax: "0" }, SV)).toMatch(/BCEA minimum/)
    expect(severanceProblem({ ...OK, amount: "13846.16", tax: "0" }, SV)).toBeNull()
    expect(severanceProblem({ ...OK, amount: "100", tax: "0", refused: true }, SV)).toBeNull()
  })
  it("sends the figures and works out what the employee receives", () => {
    expect(severanceBody({ ...OK, directive: " IRP3-1 ", reason: " r " })).toEqual({ amount: 20000, directiveNumber: "IRP3-1", directiveTax: 1500.5, refusedAlternative: false, reason: "r" })
    expect(severanceNet(OK)).toBeCloseTo(18499.5, 2)
    expect(severanceNet(EMPTY_SEVERANCE_FORM)).toBe(0)
  })
  it("enters the severance", async () => {
    get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.endsWith("severance") ? SV : url.endsWith("leave-advance") ? { terminated: true, days: 0, amount: 0, alreadyEntered: false } : [] } }))
    post.mockResolvedValue({ data: {} })
    wrap(<PayAdjustmentsModal employee={{ ...EMP, status: "TERMINATED" }} permissions={["PAYROLL_PREPARE"]} onClose={() => {}} />)
    await screen.findByText(/the BCEA minimum is/)
    const btn = screen.getByRole("button", { name: "Enter severance" }) as HTMLButtonElement
    expect(btn.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/^Severance amount/), { target: { value: "20000" } })
    fireEvent.change(screen.getByLabelText(/^SARS directive number/), { target: { value: "IRP3-1" } })
    fireEvent.change(screen.getByLabelText(/^Tax the directive asks for/), { target: { value: "1500.50" } })
    fireEvent.change(screen.getByLabelText(/^Reason/), { target: { value: "Position made redundant" } })
    expect(btn.disabled).toBe(false)
    fireEvent.click(btn)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/employees/" + EMP.id + "/severance/enter",
      { amount: 20000, directiveNumber: "IRP3-1", directiveTax: 1500.5, refusedAlternative: false, reason: "Position made redundant" }))
  })
})
