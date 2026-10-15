import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
const post = vi.fn()
const del = vi.fn()
vi.mock("../../api/client", () => ({
  apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), delete: (...a: unknown[]) => del(...a), put: vi.fn(), patch: vi.fn() },
}))

import VatSettingsSection from "./VatSettingsSection"

afterEach(cleanup)

const overview = (over: Record<string, unknown> = {}) => ({
  vatRegistered: true, ratePercent: 15, configured: false, effectiveFrom: null, upcoming: [], history: [], vatNumberOnFile: true, platformRate: 15, ...over,
})
const mount = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><VatSettingsSection /></QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  get.mockResolvedValue({ data: { data: overview() } })
  post.mockResolvedValue({ data: { data: overview({ configured: true }) } })
})

describe("VAT setting", () => {
  it("says a company with no setting is treated as registered at the standard rate", async () => {
    mount()
    expect(await screen.findByText(/VAT registered, 15%/)).toBeTruthy()
    expect(screen.getByText(/not set yet/)).toBeTruthy()
  })

  it("saves a change to not registered from a date, sending no rate", async () => {
    mount()
    await screen.findByText(/VAT registered, 15%/)
    fireEvent.change(screen.getByLabelText("VAT treatment"), { target: { value: "no" } })
    expect(screen.queryByLabelText("VAT rate")).toBeNull()
    fireEvent.change(screen.getByLabelText("VAT reason"), { target: { value: "Deregistered" } })
    fireEvent.click(screen.getByText("Save VAT change"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    const body = post.mock.calls[0][1]
    expect(body.vatRegistered).toBe(false)
    expect(body.standardRate).toBeNull()
    expect(body.reason).toBe("Deregistered")
    expect(body.effectiveFrom).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect(await screen.findByText("VAT change saved.")).toBeTruthy()
  })

  it("will not switch VAT on without a VAT number on the company, or with a rate of zero", async () => {
    get.mockResolvedValue({ data: { data: overview({ vatNumberOnFile: false }) } })
    mount()
    await screen.findByText(/VAT registered, 15%/)
    const save = screen.getByText("Save VAT change") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    expect(screen.getByText(/Add the company's VAT registration number/)).toBeTruthy()
    cleanup()
    get.mockResolvedValue({ data: { data: overview() } })
    mount()
    await screen.findByText(/VAT registered, 15%/)
    const save2 = screen.getByText("Save VAT change") as HTMLButtonElement
    expect(save2.disabled).toBe(false)
    fireEvent.change(screen.getByLabelText("VAT rate"), { target: { value: "0" } })
    expect(save2.disabled).toBe(true)
  })

  it("lists a change still to start and cancels it", async () => {
    const future = { id: "c1", vatRegistered: false, standardRate: 0, effectiveFrom: "2027-01-01", reason: "Deregistering", createdAt: "2026-10-15T08:00:00Z" }
    get.mockResolvedValue({ data: { data: overview({ configured: true, upcoming: [future] }) } })
    del.mockResolvedValue({ data: { data: overview({ configured: true }) } })
    mount()
    expect(await screen.findByText(/From 2027-01-01: Not registered for VAT/)).toBeTruthy()
    fireEvent.click(screen.getByText("Cancel change"))
    await waitFor(() => expect(del).toHaveBeenCalledWith(expect.stringContaining("/vat-settings/c1")))
    expect(await screen.findByText("VAT change cancelled.")).toBeTruthy()
  })

  it("shows the server's reason when a change is refused", async () => {
    post.mockRejectedValue({ response: { data: { message: "There is already a VAT change starting on that date." } } })
    mount()
    await screen.findByText(/VAT registered, 15%/)
    fireEvent.click(screen.getByText("Save VAT change"))
    expect((await screen.findByRole("alert")).textContent).toMatch(/already a VAT change/)
  })
})
