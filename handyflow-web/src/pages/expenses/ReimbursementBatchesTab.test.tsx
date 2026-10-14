import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ReimbursementBatchesTab from "./ReimbursementBatchesTab"
import ExpensePoliciesTab from "./ExpensePoliciesTab"

const perms = vi.hoisted(() => ({ held: new Set<string>() }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.held.has(p) }))
const api = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), put: vi.fn(), delete: vi.fn() }))
vi.mock("../../api/client", () => ({ apiClient: api }))

const wrap = (ui: React.ReactElement) => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{ui}</QueryClientProvider>)
const owed = [{ employeeName: "Thandi", total: 150, claims: [
  { claimId: "c1", claimNumber: "EXP-1", claimDate: "2026-10-01", category: "MEALS", amount: 100, inBatch: false },
  { claimId: "c2", claimNumber: "EXP-2", claimDate: "2026-10-02", category: "FUEL", amount: 50, inBatch: true }] }]

function mountBatches(batches: any[] = []) {
  api.get.mockImplementation(async (url: string) => {
    if (url.endsWith("/owed")) return { data: { data: owed } }
    if (url.endsWith("/batches")) return { data: { data: batches } }
    return { data: { data: [{ id: "b1", bankName: "FNB", accountName: "Current" }] } }
  })
  api.post.mockResolvedValue({ data: { data: {} } })
  wrap(<ReimbursementBatchesTab />)
}

describe("ReimbursementBatchesTab", () => {
  beforeEach(() => { vi.clearAllMocks(); perms.held = new Set() })
  afterEach(() => cleanup())

  it("lists what is owed by employee", async () => {
    mountBatches()
    expect(await screen.findByText("Thandi")).toBeTruthy()
    expect(screen.queryByLabelText("Include EXP-1")).toBeNull()
  })

  it("makes a batch of the ticked claims, not those already batched", async () => {
    perms.held = new Set(["EXPENSES_ADMIN"])
    mountBatches()
    const box = await screen.findByLabelText("Include EXP-1")
    expect((screen.getByLabelText("Include EXP-2") as HTMLInputElement).disabled).toBe(true)
    fireEvent.click(box)
    fireEvent.click(await screen.findByText(/Make a batch of 1 claim/))
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/expenses/reimbursements/batches", { claimIds: ["c1"] }))
  })

  it("asks for the bank account before paying", async () => {
    perms.held = new Set(["EXPENSES_ADMIN"])
    mountBatches([{ id: "bt1", batchNumber: "REB-2026-0001", status: "DRAFT", totalAmount: 100, items: [{ claimId: "c1", claimNumber: "EXP-1", employeeName: "Thandi", amount: 100 }] }])
    fireEvent.click(await screen.findByText("Pay batch"))
    const confirm = await screen.findByText("Confirm payment") as HTMLButtonElement
    await screen.findByText("FNB Current")
    expect(confirm.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Paid from"), { target: { value: "b1" } })
    expect(confirm.disabled).toBe(false)
    fireEvent.click(confirm)
    await waitFor(() => expect(api.post).toHaveBeenCalledWith("/api/v1/expenses/reimbursements/batches/bt1/pay", { bankAccountId: "b1", reference: null }))
  })

  it("shows why the server refused", async () => {
    perms.held = new Set(["EXPENSES_ADMIN"])
    mountBatches()
    api.post.mockRejectedValue({ response: { data: { message: "EXP-1 is already in an unpaid batch." } } })
    fireEvent.click(await screen.findByLabelText("Include EXP-1"))
    fireEvent.click(await screen.findByText(/Make a batch/))
    expect((await screen.findByRole("alert")).textContent).toMatch(/already in an unpaid batch/)
  })
})

describe("ExpensePoliciesTab", () => {
  beforeEach(() => { vi.clearAllMocks(); perms.held = new Set(["EXPENSES_ADMIN"]) })
  afterEach(() => cleanup())

  it("shows policies in words and saves a new one", async () => {
    api.get.mockResolvedValue({ data: { data: [{ id: "p1", category: "MEALS", maxPerClaim: 300, limitAction: "BLOCK", receiptAbove: null, maxAgeDays: null }] } })
    api.put.mockResolvedValue({ data: {} })
    wrap(<ExpensePoliciesTab />)
    expect(await screen.findByText(/more is refused/)).toBeTruthy()
    const save = screen.getByText("Save policy") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText("Receipt above"), { target: { value: "50" } })
    expect(save.disabled).toBe(false)
    fireEvent.click(save)
    await waitFor(() => expect(api.put).toHaveBeenCalledWith("/api/v1/expenses/policies",
      { category: "*", maxPerClaim: null, limitAction: null, receiptAbove: 50, maxAgeDays: null }))
  })
})
