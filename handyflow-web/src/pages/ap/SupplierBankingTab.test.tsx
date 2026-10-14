import { afterEach, beforeEach, describe, it, expect, vi } from "vitest"
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

// Finance patch F6: changed supplier bank details wait for a second person's check.
let perms = new Set<string>()
vi.mock("../../hooks/usePermission", () => ({ usePermission: (p: string) => perms.has(p) }))
const get = vi.fn(), post = vi.fn(), put = vi.fn(), del = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: {
  get: (...a: any[]) => get(...a), post: (...a: any[]) => post(...a), put: (...a: any[]) => put(...a), delete: (...a: any[]) => del(...a) } }))
import { SupplierBankingTab } from "./SupplierBankingTab"

const row = (o: any) => ({ id: "s1", supplierName: "Acme", bankName: "FNB", accountHolder: "Acme Ltd", accountNumber: "62001234",
  branchCode: "250655", vatNumber: null, notes: null, createdAt: "2026-10-01T00:00:00Z", verified: true, ...o })
const show = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><SupplierBankingTab /></QueryClientProvider>)

beforeEach(() => {
  perms = new Set(["AP_READ", "AP_SUPPLIER_BANKING"])
  get.mockReset(); post.mockReset()
  get.mockImplementation((url: string) => Promise.resolve({ data: { data: url.includes("known-names") ? [] :
    [row({}), row({ id: "s2", supplierName: "Sasol", verified: false })] } }))
  post.mockResolvedValue({ data: {} })
})
afterEach(cleanup)

describe("SupplierBankingTab checks", () => {
  it("shows which details wait for a check and checks them after confirmation", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true)
    show()
    expect(await screen.findByText("Waiting for a second person")).toBeTruthy()
    expect(screen.getAllByText("Checked").length).toBeGreaterThan(0)
    fireEvent.click(screen.getByRole("button", { name: "Mark Sasol's bank details as checked" }))
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/ap/suppliers/banking/s2/verify", {}))
  })

  it("shows the server's refusal when you entered the details yourself", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(true)
    post.mockRejectedValueOnce({ response: { data: { message: "You entered these bank details, so someone else must check them (one other person can)" } } })
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Mark Sasol's bank details as checked" }))
    expect((await screen.findByRole("alert")).textContent).toMatch(/someone else must check/)
  })

  it("does not check when the confirmation is cancelled", async () => {
    vi.spyOn(window, "confirm").mockReturnValue(false)
    show()
    fireEvent.click(await screen.findByRole("button", { name: "Mark Sasol's bank details as checked" }))
    expect(post).not.toHaveBeenCalled()
  })

  it("is read-only without AP_SUPPLIER_BANKING", async () => {
    perms = new Set(["AP_READ"])
    show()
    await screen.findByText("Waiting for a second person")
    expect(screen.queryByRole("button", { name: /Add Supplier/ })).toBeNull()
    expect(screen.queryByRole("button", { name: /checked/ })).toBeNull()
    expect(screen.queryByRole("button", { name: "Edit Acme" })).toBeNull()
  })
})
