import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react"
import { MemoryRouter, Route, Routes } from "react-router-dom"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

// Finance patch F7: drafts, resubmission, withdrawal and payout from a bank account.
let perms = new Set<string>()
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
vi.mock("../../hooks/useAuth", () => ({ useAuth: () => ({ user: { userId: "me" } }) }))
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))
import { ExpensesPage, claimantActions } from "./ExpensesPage"

const claim = (o: any) => ({ id: "c1", claimNumber: "EXP-0001", employeeId: null, employeeName: "Thabo", claimDate: "2026-10-10",
  category: "FUEL", description: "Fuel to site", amount: 250, currency: "ZAR", receiptUrl: null, status: "DRAFT",
  rejectionReason: null, journalEntryId: null, notes: null, approvedAt: null, reimbursedAt: null, createdAt: "2026-10-10T08:00:00Z",
  submittedBy: "me", submissionCount: 0, ...o })

let claims: any[] = []
const banks = [{ id: "b1", bankName: "FNB", accountName: "Cheque", accountNumber: "62001234", active: true, accountId: "gl1" },
               { id: "b2", bankName: "Nedbank", accountName: "Payroll", accountNumber: "1009876", active: true, accountId: "gl2" }]

const show = () => render(
  <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
    <MemoryRouter initialEntries={["/expenses/claims"]}>
      <Routes><Route path="/expenses/:section" element={<ExpensesPage />} /></Routes>
    </MemoryRouter>
  </QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["USER_READ", "USER_CREATE"])
  claims = []
  api.get.mockReset(); api.post.mockReset(); api.put.mockReset()
  api.get.mockImplementation((url: string) => {
    if (url.startsWith("/api/v1/expenses?")) return Promise.resolve({ data: { data: { content: claims } } })
    if (url.includes("/accounting/bank-accounts")) return Promise.resolve({ data: { data: banks } })
    return Promise.resolve({ data: { data: [] } })
  })
  api.post.mockImplementation((url: string) => Promise.resolve({ data: { data: claim({ id: url.split("/")[4] ?? "c1", status: "PENDING" }) } }))
  api.put.mockResolvedValue({ data: { data: claim({}) } })
})
afterEach(cleanup)

describe("claimantActions", () => {
  it("follows the server's lifecycle", () => {
    expect(claimantActions("DRAFT")).toEqual({ edit: true, submit: true, withdraw: true })
    expect(claimantActions("REJECTED")).toEqual({ edit: true, submit: true, withdraw: false })
    expect(claimantActions("PENDING")).toEqual({ edit: false, submit: false, withdraw: true })
    expect(claimantActions("APPROVED")).toEqual({ edit: false, submit: false, withdraw: false })
  })
})

describe("ExpensesPage lifecycle", () => {
  it("saves a new claim as a draft", async () => {
    show()
    fireEvent.click((await screen.findAllByRole("button", { name: /Submit Claim/ }))[0])
    fireEvent.change(screen.getByPlaceholderText("Full name"), { target: { value: "Thabo" } })
    fireEvent.change(screen.getByPlaceholderText(/Team lunch/), { target: { value: "Fuel to site" } })
    fireEvent.change(screen.getByPlaceholderText("0.00"), { target: { value: "250" } })
    fireEvent.click(screen.getByRole("button", { name: "Save draft" }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/expenses?draft=true", expect.objectContaining({ amount: 250, description: "Fuel to site" })))
  })

  it("resubmits a rejected claim of mine and shows why it was rejected", async () => {
    claims = [claim({ status: "REJECTED", rejectionReason: "No receipt attached", submissionCount: 1 })]
    show()
    fireEvent.click(await screen.findByText("Fuel to site"))
    expect(screen.getByText("No receipt attached")).toBeTruthy()
    fireEvent.click(screen.getByRole("button", { name: "Submit again" }))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/expenses/c1/submit"))
  })

  it("does not offer approve or pay on my own claim", async () => {
    perms = new Set(["USER_READ", "USER_CREATE", "EXPENSES_READ", "EXPENSES_MANAGE", "EXPENSES_ADMIN"])
    claims = [claim({ status: "PENDING" }), claim({ id: "c2", claimNumber: "EXP-0002", description: "Lunch", status: "PENDING", submittedBy: "someone" })]
    show()
    await screen.findByText("Lunch")
    expect(screen.getAllByRole("button", { name: "Approve" })).toHaveLength(1)
  })

  it("pays out from the chosen bank account with a reference", async () => {
    perms = new Set(["USER_READ", "EXPENSES_READ", "EXPENSES_ADMIN"])
    claims = [claim({ status: "APPROVED", submittedBy: "someone", journalEntryId: "j1" })]
    show()
    fireEvent.click(await screen.findByRole("button", { name: /Pay/ }))
    const dialog = await screen.findByRole("dialog", { name: "Pay out claim" })
    const button = within(dialog).getByRole("button", { name: "Mark paid out" }) as HTMLButtonElement
    expect(button.disabled).toBe(true)
    fireEvent.change(within(dialog).getByLabelText("Paid from"), { target: { value: "b2" } })
    fireEvent.change(within(dialog).getByLabelText("Payment reference"), { target: { value: "EFT 7781" } })
    fireEvent.click(button)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/expenses/c1/reimburse", { bankAccountId: "b2", reference: "EFT 7781" }))
  })

  it("says when an approved claim is not in the ledger yet", async () => {
    claims = [claim({ status: "APPROVED", submittedBy: "someone", journalEntryId: null })]
    show()
    fireEvent.click(await screen.findByText("Fuel to site"))
    expect(screen.getByText(/Not in the ledger yet/)).toBeTruthy()
  })
})
