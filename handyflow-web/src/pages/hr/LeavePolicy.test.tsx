import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import LeavePolicy from "./LeavePolicy"
import { canSetPolicy, kindLabel, policyBody, policyProblem, policySummary } from "./leave.logic"

const get = vi.fn()
const put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), put: (...a: unknown[]) => put(...a) } }))

const ok = (data: unknown) => Promise.resolve({ data: { data } })
const THIS = new Date().getFullYear()
const show = (permissions: string[]) => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><LeavePolicy permissions={permissions} /></QueryClientProvider>)

function serve(policy: object, risk: object = { carryOverCapDays: null, rolloverInto: THIS + 1, employees: [] }) {
  get.mockImplementation((url: string) => url.endsWith("/at-risk") ? ok(risk) : ok(policy))
}
beforeEach(() => { get.mockReset(); put.mockReset() })
afterEach(cleanup)

describe("leave policy rules in the browser", () => {
  it("checks the policy the way the server does", () => {
    expect(policyProblem("", "", "", THIS)).toBeNull()
    expect(policyProblem("5", String(THIS + 1), "Policy 1", THIS)).toBeNull()
    expect(policyProblem("5", String(THIS), "Policy 1", THIS)).toMatch(/next year/)
    expect(policyProblem("5", String(THIS + 1), "  ", THIS)).toMatch(/written agreement/)
    expect(policyProblem("-1", String(THIS + 1), "x", THIS)).toMatch(/negative/)
    expect(policyProblem("101", String(THIS + 1), "x", THIS)).toMatch(/more than 100/)
    expect(policyProblem("5.123", String(THIS + 1), "x", THIS)).toMatch(/two decimals/)
    expect(policyProblem("5", String(THIS + 9), "x", THIS)).toMatch(/too far/)
    expect(policyProblem("", String(THIS + 1), "Policy 1", THIS, "12")).toBeNull()
    expect(policyProblem("", String(THIS + 1), "Policy 1", THIS, "5")).toMatch(/6 to 24 months/)
    expect(policyProblem("", String(THIS + 1), "Policy 1", THIS, "25")).toMatch(/6 to 24 months/)
    expect(policyProblem("", String(THIS + 1), "Policy 1", THIS, "7.5")).toMatch(/6 to 24 months/)
    expect(policyProblem("", String(THIS + 1), "  ", THIS, "12")).toMatch(/written agreement/)
  })
  it("only offers the form to HR managers, and labels forfeited days", () => {
    expect(canSetPolicy(["HR_MANAGE"])).toBe(true)
    expect(canSetPolicy(["LEAVE_ADJUST"])).toBe(false)
    expect(kindLabel("EXPIRY")).toBe("Forfeited under company policy")
  })
})

describe("LeavePolicy panel", () => {
  it("says there is no limit and hides Change from people who cannot set it", async () => {
    serve({ carryOverCapDays: null, effectiveYear: null, agreementRef: null })
    show(["LEAVE_ADJUST"])
    expect((await screen.findByText(/No limit/)).textContent).toMatch(/all unused annual leave/)
    expect(screen.queryByText("Change")).toBeNull()
  })

  it("saves a limit with the agreement named", async () => {
    serve({ carryOverCapDays: null, effectiveYear: null, agreementRef: null })
    put.mockResolvedValue({ data: {} })
    show(["HR_MANAGE"])
    fireEvent.click(await screen.findByText("Change"))
    const save = screen.getByText("Save policy") as HTMLButtonElement
    expect(save.disabled).toBe(false)
    fireEvent.change(screen.getByLabelText("Most days to carry over"), { target: { value: "5" } })
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Agreement"), { target: { value: "Leave policy 2026" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/hr/leave-policy", { carryOverCapDays: 5, carryExpiryMonths: null, effectiveYear: THIS + 1, agreementRef: "Leave policy 2026" }))
  })

  it("shows the server's reason when the policy is refused", async () => {
    serve({ carryOverCapDays: null, effectiveYear: null, agreementRef: null })
    put.mockRejectedValue({ response: { data: { message: "The limit can only start from next year." } } })
    show(["HR_MANAGE"])
    fireEvent.click(await screen.findByText("Change"))
    fireEvent.change(screen.getByLabelText("Most days to carry over"), { target: { value: "5" } })
    fireEvent.change(screen.getByLabelText("Agreement"), { target: { value: "x" } })
    fireEvent.click(screen.getByText("Save policy"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/next year/)
  })

  it("lists who would lose leave at the next rollover", async () => {
    serve({ carryOverCapDays: 5, effectiveYear: THIS + 1, agreementRef: "Leave policy 2026" },
      { carryOverCapDays: 5, rolloverInto: THIS + 1, employees: [{ employeeId: "e1", employeeName: "Jane Dlamini", availableNow: 12, projectedClosing: 14, wouldLose: 9 }] })
    show(["HR_READ"])
    expect(await screen.findByText(/1 person would lose annual leave/)).toBeTruthy()
    expect(screen.getByText("Jane Dlamini")).toBeTruthy()
    expect(screen.getByText("9.0")).toBeTruthy()
    expect(screen.getByText(/Agreement: Leave policy 2026/)).toBeTruthy()
  })

  it("shows nothing when the policy cannot be read", async () => {
    get.mockRejectedValue(new Error("403"))
    const { container } = show(["HR_READ"])
    await waitFor(() => expect(get).toHaveBeenCalled())
    expect(container.textContent).toBe("")
  })
})

describe("carried leave expiry", () => {
  it("sends both settings, or nothing when both are empty", () => {
    expect(policyBody("", "12", "2027", " Policy 1 ")).toEqual({ carryOverCapDays: null, carryExpiryMonths: 12, effectiveYear: 2027, agreementRef: "Policy 1" })
    expect(policyBody("5", "12", "2027", "P")).toEqual({ carryOverCapDays: 5, carryExpiryMonths: 12, effectiveYear: 2027, agreementRef: "P" })
    expect(policyBody(" ", " ", "2027", "P")).toEqual({ carryOverCapDays: null, carryExpiryMonths: null, effectiveYear: null, agreementRef: null })
  })
  it("says in one sentence what the policy does", () => {
    expect(policySummary({ carryOverCapDays: null, effectiveYear: null, agreementRef: null })).toMatch(/No limit/)
    expect(policySummary({ carryOverCapDays: null, carryExpiryMonths: 12, effectiveYear: 2027, agreementRef: "P1" })).toBe("Leave carried into 2027 or later that is not used within 12 months is forfeited. Agreement: P1.")
  })
})
