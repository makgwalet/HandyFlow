import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

// Finance patch F12: reconciliation report, sign-off and matching rules.
let perms = new Set<string>()
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const get = vi.fn(), post = vi.fn(), del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), delete: (...a: any[]) => del(...a) } }))
import BankReconciliationTab from "./BankReconciliationTab"

const report = (over: any = {}) => ({ bankAccountId: "b1", accountName: "Cheque", asAt: "2026-09-30",
  statementBalance: 1000, bookBalance: 1000, statementOnlyNet: 0, bookOnlyNet: 0, difference: 0, balanced: true,
  signedOffThrough: null, signOffBlocker: null, onStatementOnly: [], inBooksOnly: [], bookedFromLines: [], ...over })
const history = [{ id: "r1", periodEnd: "2026-08-31", statementBalance: 900, difference: 0, note: null, status: "SIGNED_OFF", signedAt: "2026-09-02T08:00:00Z", reopenedAt: null, reopenReason: null }]
const rule = { id: "u1", name: "Bank fees", bankAccountId: null, direction: "OUT", matchText: "FEE", matchField: "DESCRIPTION", minAmount: null, maxAmount: 200,
  otherAccountName: "Bank charges", priority: 100, active: true, timesApplied: 3 }

function serve(rep: any, extra: Record<string, any> = {}) {
  get.mockImplementation((url: string) => {
    const w = (d: any) => Promise.resolve({ data: { data: d } })
    if (url.endsWith("/bank-accounts")) return w([{ id: "b1", bankName: "FNB", accountName: "Cheque", active: true }])
    if (url.includes("/reconciliation?")) return w(rep)
    if (url.endsWith("/reconciliations")) return w(history)
    if (url.endsWith("/bank-rules")) return w([rule])
    if (url.endsWith("/rules/preview")) return w(extra.preview ?? [])
    if (url.endsWith("/accounts")) return w([{ id: "a9", accountCode: "6100", accountName: "Bank charges" }])
    return w([])
  })
}
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><BankReconciliationTab /></QueryClientProvider>)
const choose = async () => {
  await screen.findByText(/FNB/)
  fireEvent.change(screen.getByLabelText("Bank account"), { target: { value: "b1" } })
}

beforeEach(() => {
  perms = new Set(["ACCOUNTING_READ", "ACCOUNTING_MANAGE", "ACCOUNTING_RECON_SIGNOFF"])
  get.mockReset(); post.mockReset(); del.mockReset(); post.mockResolvedValue({ data: { data: { applied: 1 } } }); del.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("BankReconciliationTab", () => {
  it("shows the blocker and stops sign-off while something is unmatched", async () => {
    serve(report({ balanced: false, difference: 50, signOffBlocker: "1 statement line is not matched yet.",
      onStatementOnly: [{ transactionId: "t1", date: "2026-09-20", description: "MONTHLY FEE", reference: null, amount: -50, ageDays: 24 }] }))
    show(); await choose()
    expect((await screen.findByTestId("blocker")).textContent).toContain("not matched")
    expect(screen.getByText("MONTHLY FEE")).toBeTruthy()
    expect((screen.getByRole("button", { name: "Sign off" }) as HTMLButtonElement).disabled).toBe(true)
  })

  it("signs off the statement date with a note", async () => {
    serve(report())
    show(); await choose()
    await screen.findByText("Ready to sign off.")
    fireEvent.change(screen.getByLabelText("Sign-off note"), { target: { value: "September done" } })
    fireEvent.click(screen.getByRole("button", { name: "Sign off" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/bank-accounts/b1/reconciliations",
      { periodEnd: expect.stringMatching(/^\d{4}-\d{2}-\d{2}$/), note: "September done" }))
  })

  it("hides sign-off and rule writes without the permissions", async () => {
    perms = new Set(["ACCOUNTING_READ"])
    serve(report())
    show(); await choose()
    await screen.findByText("Ready to sign off.")
    expect(screen.queryByRole("button", { name: "Sign off" })).toBeNull()
    expect(screen.queryByRole("button", { name: "Save rule" })).toBeNull()
    expect(screen.queryByRole("button", { name: "Reopen" })).toBeNull()
  })

  it("reopens the latest sign-off only with a reason", async () => {
    serve(report())
    show(); await choose()
    const reopen = await screen.findByRole("button", { name: "Reopen" }) as HTMLButtonElement
    expect(reopen.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Reason for reopening"), { target: { value: "Missed a fee" } })
    fireEvent.click(reopen)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/bank-reconciliations/r1/reopen", { reason: "Missed a fee" }))
  })

  it("previews rules, then books only the ticked lines", async () => {
    serve(report(), { preview: [
      { transactionId: "t1", date: "2026-09-20", description: "MONTHLY FEE", amount: -50, ruleId: "u1", ruleName: "Bank fees", otherAccountName: "Bank charges" },
      { transactionId: "t2", date: "2026-09-21", description: "CARD FEE", amount: -20, ruleId: "u1", ruleName: "Bank fees", otherAccountName: "Bank charges" }] })
    show(); await choose()
    fireEvent.click(await screen.findByRole("button", { name: "Preview rules on unmatched lines" }))
    const second = await screen.findByLabelText("Apply to CARD FEE")
    fireEvent.click(second) // untick
    fireEvent.click(screen.getByRole("button", { name: "Book 1 line" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/bank-accounts/b1/rules/apply", { transactionIds: ["t1"] }))
  })

  it("saves a rule only when complete, and switches one off", async () => {
    serve(report())
    show(); await choose()
    await screen.findByText("Bank fees")
    expect(screen.getByText(/Money out, description contains “FEE”, up to R/)).toBeTruthy()
    const save = screen.getByRole("button", { name: "Save rule" }) as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Rule name"), { target: { value: "Interest" } })
    fireEvent.change(screen.getByLabelText("Direction"), { target: { value: "IN" } })
    fireEvent.change(screen.getByLabelText("Text to look for"), { target: { value: "INTEREST" } })
    await screen.findByText("6100 Bank charges", { exact: false })
    fireEvent.change(screen.getByLabelText("Book to account"), { target: { value: "a9" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/accounting/bank-rules", expect.objectContaining(
      { name: "Interest", direction: "IN", matchText: "INTEREST", otherAccountId: "a9", bankAccountId: "b1" })))
    fireEvent.click(screen.getByRole("button", { name: "Switch off" }))
    await waitFor(() => expect(del).toHaveBeenCalledWith("/api/v1/accounting/bank-rules/u1"))
  })
})
