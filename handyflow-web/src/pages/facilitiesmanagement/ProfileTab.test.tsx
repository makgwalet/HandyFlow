import { describe, it, expect, vi, beforeEach } from "vitest"
import { render, screen, waitFor, fireEvent, cleanup } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import ProfileTab from "./ProfileTab"

const get = vi.fn(), put = vi.fn()
vi.mock("../../api/client", () => ({ apiClient: { get: (...a: unknown[]) => get(...a), put: (...a: unknown[]) => put(...a) } }))
vi.mock("../../hooks/usePermission", () => ({ usePermission: () => true }))
vi.mock("../../store/toast.store", () => ({ toast: { success: vi.fn(), error: vi.fn() } }))

const wrap = () => render(<QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}><ProfileTab /></QueryClientProvider>)
const saved = { companyName: "Zeta", registrationNumber: null, contactEmail: null, contactPhone: null, address: null, notes: null, vatRegistered: false, vatNumber: null, vatRate: 15, paymentTermsDays: 30, arAccountCode: "1100", revenueAccountCode: "4000", vatAccountCode: null, bankAccountCode: null }

describe("ProfileTab", () => {
  beforeEach(() => { cleanup(); get.mockReset(); put.mockReset() })
  it("warns that the bank account is missing and lists ledger accounts", async () => {
    get.mockImplementation(async (u: string) => u.includes("/accounts") ? { data: [{ accountCode: "1000", accountName: "Bank" }] } : { data: saved })
    wrap()
    expect(await screen.findByText(/Ledger accounts missing: bank/)).toBeTruthy()
    expect((await screen.findAllByText("1000 — Bank")).length).toBe(4)
  })
  it("shows a blank create form when no profile exists yet, and blocks saving without a name", async () => {
    get.mockImplementation(async (u: string) => { if (u.includes("/accounts")) throw new Error("no"); throw { response: { status: 404 } } })
    wrap()
    expect(await screen.findByText(/not set up yet/)).toBeTruthy()
    const save = (await screen.findByText("Save settings")) as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText?.("Company name *") ?? document.querySelector("input")!, { target: { value: "Acme" } })
    await waitFor(() => expect((screen.getByText("Save settings") as HTMLButtonElement).disabled).toBe(false))
  })
})
