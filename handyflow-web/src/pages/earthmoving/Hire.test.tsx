import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"

const get = vi.fn()
const post = vi.fn()
const patch = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({
  apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: vi.fn(), patch: (...a: unknown[]) => patch(...a), delete: vi.fn() },
}))
vi.mock("../../store/auth.store", () => ({
  useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: perms } }),
}))

import HireTab from "./HireTab"

afterEach(cleanup)

const charge = (over: Record<string, unknown> = {}) => ({
  id: "h1", deploymentId: "d1", assetId: "a1", machine: "Excavator 1", customerId: "c1", customerName: "Anglo", siteName: "Mine A",
  periodFrom: "2026-10-01", periodTo: "2026-10-03", basis: "DAILY", quantity: 3, unitRate: 1000, vatRate: 15, amount: 3000,
  vatAmount: 450, total: 3450, description: "Hire", status: "PENDING", invoiceId: null, invoiceNumber: null, note: null, createdAt: null, ...over,
})
const mount = () => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><HireTab /></QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  perms = []
  get.mockImplementation((url: string) => Promise.resolve(
    url.includes("/hire-charges") ? { data: { data: [charge(), charge({ id: "h2", machine: "Loader 2", customerId: "c2", customerName: "Twin" })] } }
      : { data: { data: { content: [], last: true } } }))
  post.mockResolvedValue({ data: { data: { invoiceId: "i1", invoiceNumber: "INV-0001", subtotal: 3000, vatTotal: 450, total: 3450, charges: 1 } } })
  patch.mockResolvedValue({ data: { data: charge({ status: "VOIDED" }) } })
})

describe("Hire charges", () => {
  it("shows charges to a reader without any action buttons", async () => {
    perms = ["EARTHMOVING_READ"]
    mount()
    expect(await screen.findByText(/Excavator 1 · Anglo/)).toBeTruthy()
    expect(screen.queryByText("New hire charge")).toBeNull()
    expect(screen.queryByText("Void")).toBeNull()
    expect(screen.queryByRole("checkbox")).toBeNull()
  })

  it("creates a draft invoice from charges of one customer", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    mount()
    fireEvent.click(await screen.findByLabelText(/Select Excavator 1/))
    fireEvent.click(screen.getByText("Create draft invoice"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toContain("/hire-charges/draft-invoice")
    expect(post.mock.calls[0][1].chargeIds).toEqual(["h1"])
    expect(await screen.findByText(/Draft invoice INV-0001 created/)).toBeTruthy()
  })

  it("refuses to mix customers on one invoice", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    mount()
    const boxes = await screen.findAllByRole("checkbox")
    fireEvent.click(boxes[0]); fireEvent.click(boxes[1])
    expect(screen.getByText(/different customers/)).toBeTruthy()
    expect((screen.getByText("Create draft invoice") as HTMLButtonElement).disabled).toBe(true)
  })

  it("needs a reason of at least five characters to void", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    mount()
    fireEvent.click((await screen.findAllByText("Void"))[0])
    const submit = screen.getByText("Void charge") as HTMLButtonElement
    expect(submit.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Reason/), { target: { value: "Entered twice" } })
    expect(submit.disabled).toBe(false)
    fireEvent.click(submit)
    await waitFor(() => expect(patch).toHaveBeenCalled())
    expect(patch.mock.calls[0][0]).toContain("/hire-charges/h1/void")
    expect(patch.mock.calls[0][1]).toEqual({ reason: "Entered twice" })
  })

  it("asks for a reason before a different rate can be previewed or saved, and sends both", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    get.mockImplementation((url: string) => Promise.resolve(
      url.includes("/hire-charges") ? { data: { data: [] } }
        : url.includes("/deployments") ? { data: { data: { content: [{ id: "d1", assetId: "a1", siteName: "Mine A", clientName: "Anglo", deployedAt: "2026-10-01T06:00:00Z", returnedAt: null }], last: true } } }
        : { data: { data: { content: [{ id: "a1", name: "Excavator", fleetNumber: "E-01", status: "DEPLOYED" }], last: true } } }))
    post.mockResolvedValue({ data: { data: charge({ id: null, status: "PREVIEW", unitRate: 900 }) } })
    mount()
    fireEvent.click(await screen.findByText("New hire charge"))
    await screen.findByRole("option", { name: "Excavator", hidden: true })
    fireEvent.change(screen.getByLabelText(/Machine \*/), { target: { value: "a1" } })
    await screen.findByText(/Mine A · Anglo/)
    fireEvent.change(screen.getByLabelText(/Deployment \*/), { target: { value: "d1" } })
    fireEvent.change(screen.getByLabelText(/From \*/), { target: { value: "2026-10-01" } })
    fireEvent.change(screen.getByLabelText(/To \*/), { target: { value: "2026-10-03" } })
    const preview = screen.getByText("Work out the charge") as HTMLButtonElement
    expect(preview.disabled).toBe(false)
    fireEvent.change(screen.getByLabelText(/Agreed rate/), { target: { value: "900" } })
    expect(preview.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Why this rate/), { target: { value: "Long-term discount" } })
    expect(preview.disabled).toBe(false)
    fireEvent.click(preview)
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1]).toMatchObject({ unitRate: 900, rateReason: "Long-term discount", deploymentId: "d1" })
  })

  it("charges by kilometre, and a rate typed here needs a reason, and sends the included allowance", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    get.mockImplementation((url: string) => Promise.resolve(
      url.includes("/hire-charges") ? { data: { data: [] } }
        : url.includes("/deployments") ? { data: { data: { content: [{ id: "d1", assetId: "a1", siteName: "Mine A", clientName: "Anglo", deployedAt: "2026-10-01T06:00:00Z", returnedAt: null }], last: true } } }
        : { data: { data: { content: [{ id: "a1", name: "Truck", fleetNumber: "T-01", status: "DEPLOYED" }], last: true } } }))
    post.mockResolvedValue({ data: { data: charge({ id: null, status: "PREVIEW", basis: "KILOMETRE", quantity: 350, unitRate: 12.5 }) } })
    mount()
    fireEvent.click(await screen.findByText("New hire charge"))
    await screen.findByRole("option", { name: "Truck", hidden: true })
    fireEvent.change(screen.getByLabelText(/Machine \*/), { target: { value: "a1" } })
    await screen.findByText(/Mine A · Anglo/)
    fireEvent.change(screen.getByLabelText(/Deployment \*/), { target: { value: "d1" } })
    fireEvent.change(screen.getByLabelText(/From \*/), { target: { value: "2026-10-01" } })
    fireEvent.change(screen.getByLabelText(/To \*/), { target: { value: "2026-10-10" } })
    fireEvent.change(screen.getByLabelText("Charge by"), { target: { value: "KILOMETRE" } })
    const preview = screen.getByText("Work out the charge") as HTMLButtonElement
    expect(preview.disabled).toBe(false) // with no rate typed, the contract's rate is used
    fireEvent.change(screen.getByLabelText(/Agreed rate per kilometre/), { target: { value: "12.5" } })
    expect(preview.disabled).toBe(true)
    fireEvent.change(screen.getByLabelText(/Why this rate/), { target: { value: "Rate in signed contract" } })
    fireEvent.change(screen.getByLabelText(/Kilometres included/), { target: { value: "500" } })
    expect(preview.disabled).toBe(false)
    fireEvent.click(preview)
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1]).toMatchObject({ basis: "KILOMETRE", unitRate: 12.5, includedKm: 500 })
    expect(await screen.findByText(/350 km at/)).toBeTruthy()
  })

  const openForm = async (contract: unknown) => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    get.mockImplementation((url: string) => Promise.resolve(
      url.includes("/hire-charges") ? { data: { data: [] } }
        : url.includes("/contract-rates") ? { data: { data: contract } }
        : url.includes("/deployments") ? { data: { data: { content: [{ id: "d1", assetId: "a1", siteName: "Mine A", clientName: "Anglo", deployedAt: "2026-10-01T06:00:00Z", returnedAt: null }], last: true } } }
        : { data: { data: { content: [{ id: "a1", name: "Truck", fleetNumber: "T-01", status: "DEPLOYED" }], last: true } } }))
    mount()
    fireEvent.click(await screen.findByText("New hire charge"))
    await screen.findByRole("option", { name: "Truck", hidden: true })
    fireEvent.change(screen.getByLabelText(/Machine \*/), { target: { value: "a1" } })
    await screen.findByText(/Mine A · Anglo/)
    fireEvent.change(screen.getByLabelText(/Deployment \*/), { target: { value: "d1" } })
    fireEvent.change(screen.getByLabelText(/From \*/), { target: { value: "2026-10-01" } })
    fireEvent.change(screen.getByLabelText(/To \*/), { target: { value: "2026-10-10" } })
  }

  it("charges by the week or month without typing a rate, and shows the contract's rate for it", async () => {
    await openForm({ deploymentId: "d1", contractId: "k1", contractNumber: "CON-0001", rates: [
      { basis: "WEEKLY", unitRate: 9000, includedKm: null, updatedAt: null }, { basis: "MONTHLY", unitRate: 30000, includedKm: null, updatedAt: null }] })
    post.mockResolvedValue({ data: { data: charge({ id: null, status: "PREVIEW", basis: "WEEKLY", quantity: 2, unitRate: 9000, rateSource: "CONTRACT" }) } })
    fireEvent.change(screen.getByLabelText("Charge by"), { target: { value: "WEEKLY" } })
    expect(await screen.findByText(/The contract's rate: .*per week/)).toBeTruthy()
    const preview = screen.getByText("Work out the charge") as HTMLButtonElement
    expect(preview.disabled).toBe(false)
    fireEvent.click(preview)
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][1]).toMatchObject({ basis: "WEEKLY", unitRate: null, rateReason: null })
    expect(await screen.findByText(/2 weeks at/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Charge by"), { target: { value: "MONTHLY" } })
    expect(await screen.findByText(/The contract's rate: .*per month/)).toBeTruthy()
  })

  it("says so when the contract has no rate for the chosen basis", async () => {
    await openForm({ deploymentId: "d1", contractId: "k1", contractNumber: "CON-0001", rates: [] })
    expect(await screen.findByText(/machine's own rate is used/)).toBeTruthy()
    fireEvent.change(screen.getByLabelText("Charge by"), { target: { value: "MONTHLY" } })
    expect(await screen.findByText(/no month rate\. Agree one here, or add it to the contract/)).toBeTruthy()
  })

  it("shows where a charge's rate came from, and holds back a charge that is on hold", async () => {
    perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
    get.mockImplementation((url: string) => Promise.resolve(
      url.includes("/hire-charges") ? { data: { data: [
        charge({ rateSource: "CONTRACT" }),
        charge({ id: "h2", machine: "Truck 2", basis: "KILOMETRE", quantity: 900, rateSource: "AGREED", recalculatedAt: "2026-10-12T08:00:00Z",
          attention: "An odometer reading is disputed: Driver says 10850" })] } }
        : { data: { data: { content: [], last: true } } }))
    mount()
    expect(await screen.findByText(/rate from the contract/)).toBeTruthy()
    expect(screen.getByText(/rate agreed for this charge/)).toBeTruthy()
    expect(screen.getByText(/recalculated .* after a reading changed/)).toBeTruthy()
    expect(screen.getByText(/On hold, cannot be invoiced yet: An odometer reading is disputed/)).toBeTruthy()
    expect((screen.getByLabelText(/Select Truck 2/) as HTMLInputElement).disabled).toBe(true)
    expect((screen.getByLabelText(/Select Excavator 1/) as HTMLInputElement).disabled).toBe(false)
  })
})
