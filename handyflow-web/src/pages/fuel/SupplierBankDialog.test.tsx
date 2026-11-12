import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, fireEvent, waitFor, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import SupplierBankDialog from "./SupplierBankDialog"

const get = vi.fn(); const post = vi.fn()
let perms: string[] = []; let me = "bob"
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermissions: (ps: string[]) => ps.some(p => perms.includes(p)) }))
vi.mock("../../store/auth.store", () => ({ useAuthStore: (sel: (s: any) => unknown) => sel({ user: { userId: me } }) }))

const acct = (o: any = {}) => ({ id: "a1", supplierId: "s1", accountHolder: "Acme Fuels", bankName: "Standard Bank", branchCode: "051001", accountNumberMasked: "****5678", accountType: "CURRENT",
  status: "PENDING_VERIFICATION", reason: null, proposedBy: "alice", proposedAt: "2026-11-12T08:00:00Z", decidedBy: null, decidedAt: null, decisionNote: null, ...o })
let accounts: any[]
function mount() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={qc}><SupplierBankDialog supplierId="s1" supplierName="Acme" onClose={() => {}} /></QueryClientProvider>)
}

describe("SupplierBankDialog", () => {
  beforeEach(() => {
    get.mockReset(); post.mockReset(); perms = ["FUEL_SUPPLIER_BANK_EDIT", "FUEL_SUPPLIER_BANK_VERIFY", "FUEL_SUPPLIER_BANK_READ"]; me = "bob"
    accounts = [acct()]
    get.mockImplementation(async (u: string) => u.endsWith("/reveal") ? { data: { data: { id: "a1", accountNumber: "62012345678" } } } : { data: { data: accounts } })
    post.mockResolvedValue({ data: { data: {} } })
  })
  afterEach(cleanup)

  it("shows only the last four digits and says when nothing is verified", async () => {
    mount()
    expect(await screen.findByTestId("acct-a1")).toHaveProperty("textContent", "****5678")
    expect(screen.getByText("No verified bank details yet.")).toBeTruthy()
  })
  it("shows the full number only on request and hides it again", async () => {
    mount(); await screen.findByTestId("acct-a1")
    fireEvent.click(screen.getByLabelText("Show number ****5678"))
    await waitFor(() => expect(screen.getByTestId("acct-a1").textContent).toBe("62012345678"))
    expect(get).toHaveBeenCalledWith("/api/v1/fuel/suppliers/s1/bank-accounts/a1/reveal")
    fireEvent.click(screen.getByLabelText("Hide number ****5678"))
    await waitFor(() => expect(screen.getByTestId("acct-a1").textContent).toBe("****5678"))
  })
  it("offers no Show number without the read permission", async () => {
    perms = ["FUEL_SUPPLIER_BANK_EDIT"]; mount(); await screen.findByTestId("acct-a1")
    expect(screen.queryByText("Show number")).toBeNull()
  })
  it("someone else verifies, saying how, and the note is sent", async () => {
    mount(); fireEvent.click(await screen.findByLabelText("Verify ****5678"))
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "Called the supplier on the contract number" } })
    fireEvent.click(screen.getByRole("button", { name: "Verify" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/suppliers/s1/bank-accounts/a1/verify", { note: "Called the supplier on the contract number" }))
  })
  it("the proposer cannot verify or reject, only withdraw", async () => {
    me = "alice"; mount(); await screen.findByTestId("acct-a1")
    expect(screen.queryByLabelText("Verify ****5678")).toBeNull(); expect(screen.queryByLabelText("Reject ****5678")).toBeNull()
    expect(screen.getByText(/Waiting for someone else to verify/)).toBeTruthy()
    fireEvent.click(screen.getByLabelText("Withdraw ****5678"))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/suppliers/s1/bank-accounts/a1/withdraw"))
  })
  it("rejecting needs a reason", async () => {
    mount(); fireEvent.click(await screen.findByLabelText("Reject ****5678"))
    expect((screen.getByRole("button", { name: "Reject" }) as HTMLButtonElement).disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "Does not match the contract" } })
    fireEvent.click(screen.getByRole("button", { name: "Reject" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/fuel/suppliers/s1/bank-accounts/a1/reject", { note: "Does not match the contract" }))
  })
  it("proposing checks the details, needs a reason to replace a verified account, then sends digits only", async () => {
    accounts = [acct({ id: "v1", status: "VERIFIED", decidedBy: "carol", decidedAt: "2026-11-01T08:00:00Z" })]
    mount(); fireEvent.click(await screen.findByText("Propose new bank details"))
    fireEvent.click(screen.getByText("Propose for verification")); expect(screen.getByRole("alert").textContent).toMatch(/holder/)
    fireEvent.change(screen.getByLabelText(/Account holder/), { target: { value: "Acme Fuels" } })
    fireEvent.change(screen.getByLabelText("Bank *"), { target: { value: "Nedbank" } })
    fireEvent.change(screen.getByLabelText(/Account number/), { target: { value: "99 98-8877" } })
    fireEvent.click(screen.getByText("Propose for verification")); expect(screen.getByRole("alert").textContent).toMatch(/why/i)
    fireEvent.change(screen.getByLabelText(/Why are the details changing/), { target: { value: "Supplier moved banks" } })
    fireEvent.click(screen.getByText("Propose for verification"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toBe("/api/v1/fuel/suppliers/s1/bank-accounts")
    expect(post.mock.calls[0][1]).toMatchObject({ accountNumber: "99988877", bankName: "Nedbank", reason: "Supplier moved banks" })
  })
  it("will not offer a second proposal while one waits", async () => {
    perms = ["FUEL_SUPPLIER_BANK_EDIT"]; mount(); await screen.findByTestId("acct-a1")
    expect(screen.queryByText("Add bank details")).toBeNull(); expect(screen.getByText(/Another change cannot be proposed/)).toBeTruthy()
  })
  it("says when the details cannot be loaded", async () => {
    get.mockRejectedValue(new Error("x")); mount()
    expect((await screen.findByRole("alert")).textContent).toMatch(/Could not load bank details/)
  })
})
