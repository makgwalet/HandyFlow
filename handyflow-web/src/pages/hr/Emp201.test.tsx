import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import Emp201ActionModal from "./Emp201ActionModal"
import { canRecordEmp201, cleanReference, fileProblem, payProblem, stateOf, type Emp201 } from "./emp201.logic"

const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { post: (...a: unknown[]) => post(...a), get: vi.fn() } }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

const emp = (over: Partial<Emp201> = {}): Emp201 => ({
  id: "x1", payRunId: "r1", periodStart: "2026-10-01", periodEnd: "2026-10-31", dueDate: "2026-11-06", totalPaye: 10000, totalUif: 1000, totalSdl: 345.67,
  totalPayable: 11345.67, status: "SUBMITTED", submittedAt: null, paymentRef: "712345678901", paidOn: null, paidAmount: null, overdue: false, late: false,
  runIssued: true, ...over,
})

beforeEach(() => post.mockReset())
afterEach(cleanup)

describe("EMP201 helpers", () => {
  it("maps the server status to what the person sees", () => {
    expect(stateOf({ status: "DRAFT" })).toBe("NOT_FILED")
    expect(stateOf({ status: "SUBMITTED" })).toBe("FILED")
    expect(stateOf({ status: "PAID" })).toBe("PAID")
  })
  it("needs a payment reference of letters and digits", () => {
    expect(fileProblem(" ")).toMatch(/Enter the payment reference/)
    expect(fileProblem("12-34")).toMatch(/6 to 30/)
    expect(fileProblem("7 1234 5678 9012")).toBe("")
    expect(cleanReference(" 7123 4567 ")).toBe("71234567")
  })
  it("checks the date and the full amount", () => {
    const e = emp()
    expect(payProblem("2026-11-05", "11345.67", e, "2026-11-05")).toBe("")
    expect(payProblem("", "11345.67", e, "2026-11-05")).toMatch(/date/)
    expect(payProblem("2026-11-06", "11345.67", e, "2026-11-05")).toMatch(/future/)
    expect(payProblem("2026-10-30", "11345.67", e, "2026-11-05")).toMatch(/before the period ended/)
    expect(payProblem("2026-11-05", "", e, "2026-11-05")).toMatch(/Enter the amount/)
    expect(payProblem("2026-11-05", "100", e, "2026-11-05")).toMatch(/must equal the total payable/)
    expect(payProblem("2026-11-05", "11345.671", e, "2026-11-05")).toMatch(/two decimals/)
  })
  it("limits recording to people who may finalise", () => {
    expect(canRecordEmp201(["PAYROLL_FINALISE"])).toBe(true)
    expect(canRecordEmp201(["PAYROLL_PREPARE", "PAYROLL_APPROVE"])).toBe(false)
  })
})

describe("Emp201ActionModal", () => {
  it("files with the reference and keeps Save off until it is valid", async () => {
    post.mockResolvedValue({ data: {} })
    wrap(<Emp201ActionModal emp={emp({ status: "DRAFT", paymentRef: null })} mode="file" onClose={() => {}} />)
    const save = screen.getByRole("button", { name: "Record as filed" }) as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Payment reference/), { target: { value: "7123 4567 8901" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/emp201/x1/submit", { paymentRef: "712345678901" }))
  })

  it("records a payment of the full amount, prefilled", async () => {
    post.mockResolvedValue({ data: {} })
    wrap(<Emp201ActionModal emp={emp({ periodEnd: "2025-01-31", dueDate: "2025-02-07" })} mode="pay" onClose={() => {}} />)
    expect((screen.getByLabelText(/Amount paid/) as HTMLInputElement).value).toBe("11345.67")
    fireEvent.click(screen.getByRole("button", { name: "Record as paid" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/emp201/x1/pay", expect.objectContaining({ amount: 11345.67 })))
  })

  it("refuses a short amount before sending anything", () => {
    wrap(<Emp201ActionModal emp={emp({ periodEnd: "2025-01-31", dueDate: "2025-02-07" })} mode="pay" onClose={() => {}} />)
    fireEvent.change(screen.getByLabelText(/Amount paid/), { target: { value: "100" } })
    expect((screen.getByRole("button", { name: "Record as paid" }) as HTMLButtonElement).disabled).toBe(true)
    expect(screen.getByText(/must equal the total payable/)).toBeTruthy()
    expect(post).not.toHaveBeenCalled()
  })

  it("shows the server's message when it refuses", async () => {
    post.mockRejectedValue({ response: { data: { message: "Finalise the pay run before filing its EMP201." } } })
    wrap(<Emp201ActionModal emp={emp({ status: "DRAFT" })} mode="file" onClose={() => {}} />)
    fireEvent.change(screen.getByLabelText(/Payment reference/), { target: { value: "712345678901" } })
    fireEvent.click(screen.getByRole("button", { name: "Record as filed" }))
    expect(await screen.findByText(/Finalise the pay run before filing/)).toBeTruthy()
  })
})
