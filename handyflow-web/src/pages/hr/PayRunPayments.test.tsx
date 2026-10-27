import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import PayRunPaymentsPanel from "./PayRunPaymentsPanel"
import { canPayRun, nextStep, returnProblem, type PaymentsSummary, type RunPayment } from "./payRunPayments.logic"

const get = vi.fn()
const post = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))

const wrap = (ui: React.ReactElement) =>
  render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)

const pay = (over: Partial<RunPayment> = {}): RunPayment => ({
  id: "p1", employeeId: "e1", employeeNumber: "E001", beneficiaryName: "Thandi Nkosi", bankName: "FNB", branchCode: "250655",
  accountMasked: "••••7890", amount: 21000.5, reference: "SAL PR00005 E001", status: "PENDING", downloaded: false,
  paidAt: null, returnedAt: null, returnReason: null, canReturn: true, ...over,
})
const summary = (over: Partial<PaymentsSummary> = {}): PaymentsSummary => ({
  payRunId: "r1", payRunNumber: "PR00005", runStatus: "FINALISED", count: 1, total: 21000.5, pending: 1, paid: 0, returned: 0, returnedTotal: 0,
  downloaded: false, canMakeUp: true, skipped: [], payments: [pay()], ...over,
})
const loaded = (s: PaymentsSummary) => get.mockResolvedValue({ data: { data: s } })

beforeEach(() => { get.mockReset(); post.mockReset() })
afterEach(cleanup)

describe("bank payment helpers", () => {
  it("only issued runs can be paid", () => {
    expect(canPayRun("FINALISED")).toBe(true)
    expect(canPayRun("COMPLETED")).toBe(true)
    expect(canPayRun("APPROVED")).toBe(false)
    expect(canPayRun("DRAFT")).toBe(false)
  })
  it("needs a reason when the bank returned a payment", () => {
    expect(returnProblem(" ")).toMatch(/why/i)
    expect(returnProblem("x".repeat(301))).toMatch(/too long/)
    expect(returnProblem("Account closed")).toBe("")
  })
  it("says what to do next", () => {
    expect(nextStep(summary({ count: 0, pending: 0, payments: [] }))).toMatch(/Make them up/)
    expect(nextStep(summary())).toMatch(/Download the bank file/)
    expect(nextStep(summary({ downloaded: true }))).toMatch(/confirm that it was paid/)
    expect(nextStep(summary({ pending: 0, paid: 1 }))).toBe("All payments are paid.")
    expect(nextStep(summary({ pending: 0, paid: 0, returned: 2 }))).toMatch(/2 payments were returned/)
  })
})

describe("PayRunPaymentsPanel", () => {
  it("shows the payments with the account number masked", async () => {
    loaded(summary())
    wrap(<PayRunPaymentsPanel runId="r1" canAct />)
    expect(await screen.findByText("Thandi Nkosi")).toBeTruthy()
    expect(screen.getByText(/••••7890/)).toBeTruthy()
    expect(get).toHaveBeenCalledWith("/api/v1/hr/pay-runs/r1/payments")
  })

  it("makes payments up and lists who was left out", async () => {
    loaded(summary({ count: 0, pending: 0, payments: [] }))
    post.mockResolvedValue({ data: { data: summary({ skipped: [{ employeeId: "e2", employeeNumber: "E002", employeeName: "Sipho Dlamini", amount: 15000, reason: "Bank details: no bank account number" }] }) } })
    wrap(<PayRunPaymentsPanel runId="r1" canAct />)
    fireEvent.click(await screen.findByRole("button", { name: "Make up payments" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/pay-runs/r1/payments/make-up"))
    expect(await screen.findByText(/Sipho Dlamini/)).toBeTruthy()
    expect(screen.getByText(/no bank account number/)).toBeTruthy()
  })

  it("offers Confirm paid only after the file was downloaded", async () => {
    loaded(summary())
    wrap(<PayRunPaymentsPanel runId="r1" canAct />)
    await screen.findByText("Thandi Nkosi")
    expect(screen.queryByRole("button", { name: "Confirm paid" })).toBeNull()
    cleanup()
    loaded(summary({ downloaded: true, payments: [pay({ downloaded: true })] }))
    wrap(<PayRunPaymentsPanel runId="r1" canAct />)
    expect(await screen.findByRole("button", { name: "Confirm paid" })).toBeTruthy()
  })

  it("confirms that the bank paid, with a note", async () => {
    loaded(summary({ downloaded: true, payments: [pay({ downloaded: true })] }))
    post.mockResolvedValue({ data: { data: summary({ pending: 0, paid: 1, payments: [pay({ status: "PAID" })] }) } })
    wrap(<PayRunPaymentsPanel runId="r1" canAct />)
    fireEvent.click(await screen.findByRole("button", { name: "Confirm paid" }))
    fireEvent.change(screen.getByLabelText(/Note/), { target: { value: "Uploaded to the bank" } })
    fireEvent.click(screen.getByRole("button", { name: "Yes, it was paid" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/pay-runs/r1/payments/confirm-paid", { note: "Uploaded to the bank" }))
    expect(await screen.findByText("All payments are paid.")).toBeTruthy()
  })

  it("records a returned payment with its reason", async () => {
    loaded(summary({ pending: 0, paid: 1, payments: [pay({ status: "PAID" })] }))
    post.mockResolvedValue({ data: { data: summary({ pending: 0, paid: 0, returned: 1, payments: [pay({ status: "RETURNED", returnReason: "Account closed", canReturn: false })] }) } })
    wrap(<PayRunPaymentsPanel runId="r1" canAct />)
    fireEvent.click(await screen.findByRole("button", { name: "Bank returned Thandi Nkosi" }))
    const submit = screen.getByRole("button", { name: "Mark as returned" }) as HTMLButtonElement
    expect(submit.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Why did the bank return it/), { target: { value: "Account closed" } })
    fireEvent.click(submit)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/hr/pay-run-payments/p1/return", { reason: "Account closed" }))
    expect(await screen.findByText("Account closed")).toBeTruthy()
  })

  it("shows the server's message when a step is refused", async () => {
    loaded(summary({ count: 0, pending: 0, payments: [] }))
    post.mockRejectedValue({ response: { data: { message: "Payments can be made up once the pay run has been finalised." } } })
    wrap(<PayRunPaymentsPanel runId="r1" canAct />)
    fireEvent.click(await screen.findByRole("button", { name: "Make up payments" }))
    expect(await screen.findByText(/once the pay run has been finalised/)).toBeTruthy()
  })

  it("is read-only without the right to finalise", async () => {
    loaded(summary({ downloaded: true, payments: [pay({ downloaded: true })] }))
    wrap(<PayRunPaymentsPanel runId="r1" canAct={false} />)
    await screen.findByText("Thandi Nkosi")
    for (const name of [/Make up/, /Download bank file/, "Confirm paid", /Bank returned/]) expect(screen.queryByRole("button", { name })).toBeNull()
  })
})
