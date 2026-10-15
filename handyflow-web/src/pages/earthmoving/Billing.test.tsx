import { describe, it, expect, vi, beforeEach, afterEach } from "vitest"
import { render, screen, cleanup, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { MemoryRouter } from "react-router-dom"

const get = vi.fn()
const post = vi.fn()
const put = vi.fn()
let perms: string[] = []
vi.mock("../../api/client", () => ({
  apiClient: { get: (...a: unknown[]) => get(...a), post: (...a: unknown[]) => post(...a), put: (...a: unknown[]) => put(...a), patch: vi.fn(), delete: vi.fn() },
}))
vi.mock("../../store/auth.store", () => ({
  useAuthStore: (sel: (s: unknown) => unknown) => sel({ user: { userId: "u1", permissions: perms } }),
}))

import BillingTab from "./BillingTab"
import { HireContractDialog } from "./HireContractDialog"

afterEach(cleanup)

const line = (over: Record<string, unknown> = {}) => ({
  deploymentId: "d1", assetId: "a1", machine: "D9-001 CAT D9", customerName: "Anglo", siteName: "Mine A", basis: "DAILY",
  periodFrom: "2026-09-01", periodTo: "2026-09-30", state: "READY", message: null, quantity: 30, unitRate: 18500, amount: 555000,
  total: 638250, rateSource: "CONTRACT", ...over,
})
const run = (lines: unknown[], over: Record<string, unknown> = {}) => ({
  month: "2026-09", applied: false, lines, ready: 0, alreadyBilled: 0, exceptions: 0, chargesCreated: 0, invoices: [], ...over,
})
const history = (over: Record<string, unknown> = {}) => ({ autoBillingEnabled: false, runs: [], ...over })
let preview = run([line()])

const mount = (ui: React.ReactElement = <BillingTab />) => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={qc}><MemoryRouter>{ui}</MemoryRouter></QueryClientProvider>)
}

beforeEach(() => {
  vi.clearAllMocks()
  perms = ["EARTHMOVING_READ", "EARTHMOVING_ADMIN"]
  preview = run([line()], { ready: 1 })
  get.mockImplementation((url: string) => Promise.resolve(
    url.includes("/billing/preview") ? { data: { data: preview } }
      : url.includes("/billing/runs") ? { data: { data: history() } }
      : { data: { data: [] } }))
  post.mockResolvedValue({ data: { data: run([line()], { applied: true, ready: 1, chargesCreated: 1, invoices: [{ invoiceId: "i1", invoiceNumber: "INV-0007", subtotal: 1, vatTotal: 0, total: 1, charges: 1 }] }) } })
  put.mockResolvedValue({ data: { data: history({ autoBillingEnabled: true }) } })
})

describe("Monthly billing", () => {
  it("previews the last month and shows what is ready with its rate source", async () => {
    mount()
    expect(await screen.findByText(/D9-001 CAT D9 · Anglo/)).toBeTruthy()
    expect(screen.getByText(/rate from the contract/)).toBeTruthy()
    expect(get.mock.calls.some(c => String(c[0]).includes("/billing/preview?month="))).toBe(true)
  })

  it("shows what needs a person apart from what is ready, with the reason", async () => {
    preview = run([line(), line({ deploymentId: "d2", machine: "Truck", state: "EXCEPTION", message: "Set the machine's daily rate first.", quantity: null, amount: null, total: null }),
      line({ deploymentId: "d3", machine: "Loader", state: "ALREADY_BILLED", message: "Already charged." })], { ready: 1, exceptions: 1, alreadyBilled: 1 })
    mount()
    expect(await screen.findByText(/Needs a person \(1\)/)).toBeTruthy()
    expect(screen.getByText(/Set the machine's daily rate first/)).toBeTruthy()
    expect(screen.getByText(/Ready \(1\)/)).toBeTruthy()
    expect(screen.getByText(/Already billed \(1\)/)).toBeTruthy()
  })

  it("creates charges only, or charges and draft invoices, for the chosen month", async () => {
    mount()
    await screen.findByText(/D9-001 CAT D9 · Anglo/)
    fireEvent.change(screen.getByLabelText("Month"), { target: { value: "2026-08" } })
    await waitFor(() => expect(get.mock.calls.some(c => String(c[0]).includes("month=2026-08"))).toBe(true))
    fireEvent.click(await screen.findByText("Create charges and draft invoices"))
    await waitFor(() => expect(post).toHaveBeenCalled())
    expect(post.mock.calls[0][0]).toContain("/billing/run")
    expect(post.mock.calls[0][1]).toEqual({ month: "2026-08", createDrafts: true })
    expect(await screen.findByText(/1 charge created, 1 draft invoice \(INV-0007\)/)).toBeTruthy()
    fireEvent.click(screen.getByText("Create charges"))
    await waitFor(() => expect(post).toHaveBeenCalledTimes(2))
    expect(post.mock.calls[1][1]).toEqual({ month: "2026-08", createDrafts: false })
  })

  it("says so when nothing is on automatic billing", async () => {
    preview = run([])
    mount()
    expect(await screen.findByText(/Nothing on automatic billing for this month/)).toBeTruthy()
    expect((screen.getByText("Create charges") as HTMLButtonElement).disabled).toBe(true)
  })

  it("a refused month shows the server's reason", async () => {
    get.mockImplementation((url: string) => url.includes("/billing/preview")
      ? Promise.reject({ response: { data: { message: "2026-10 has not ended yet. Bill a month after its last day." } } })
      : Promise.resolve({ data: { data: history() } }))
    mount()
    expect(await screen.findByText(/has not ended yet/)).toBeTruthy()
  })

  it("lets an administrator switch the schedule on", async () => {
    mount()
    fireEvent.click(await screen.findByLabelText("Run automatically on the 2nd of each month"))
    await waitFor(() => expect(put).toHaveBeenCalled())
    expect(put.mock.calls[0][0]).toContain("/billing/scheduled")
    expect(put.mock.calls[0][1]).toEqual({ enabled: true })
  })

  it("gives a non-administrator the past runs but no way to preview, run or switch", async () => {
    perms = ["EARTHMOVING_READ"]
    get.mockImplementation((url: string) => Promise.resolve(url.includes("/billing/runs")
      ? { data: { data: history({ runs: [{ id: "r1", month: "2026-09", trigger: "SCHEDULED", draftsRequested: true, chargesCreated: 4, alreadyBilled: 0, exceptions: 1, invoicesCreated: 2, createdBy: null, createdAt: "2026-10-02T04:30:00Z" }] }) } }
      : { data: { data: [] } }))
    mount()
    expect(await screen.findByText(/Only an administrator can preview or run/)).toBeTruthy()
    expect(await screen.findByText(/4 charges · 2 draft invoices/)).toBeTruthy()
    expect(screen.queryByText("Create charges")).toBeNull()
    expect(screen.queryByLabelText("Run automatically on the 2nd of each month")).toBeNull()
    expect(get.mock.calls.some(c => String(c[0]).includes("/billing/preview"))).toBe(false)
  })
})

describe("Automatic billing on a deployment", () => {
  const asset = { id: "a1", name: "Excavator", fleetNumber: "E-01", assetType: "EXCAVATOR", status: "DEPLOYED", currentHours: 10 } as never
  const open = (basis: string | null) => get.mockImplementation((url: string) => Promise.resolve(
    url.includes("/auto-billing") ? { data: { data: { deploymentId: "d1", basis } } }
      : url.includes("/deployments/d1/contract") ? { data: { data: { deploymentId: "d1", contract: null, addenda: [], expectedValue: 0, threshold: null, expected: false, warning: null } } }
      : url.includes("/assets/a1/deployments") ? { data: { data: { content: [{ id: "d1", returnedAt: null }], last: true } } }
      : { data: { data: [] } }))

  it("lets an administrator put a deployment on the monthly run, and take it off", async () => {
    open(null)
    put.mockResolvedValue({ data: { data: { deploymentId: "d1", basis: "MONTHLY" } } })
    mount(<HireContractDialog asset={asset} onClose={() => {}} />)
    const select = (await screen.findByLabelText("Charge this hire each month")) as HTMLSelectElement
    const save = screen.getByText("Save") as HTMLButtonElement
    expect(save.disabled).toBe(true)
    fireEvent.change(select, { target: { value: "MONTHLY" } })
    fireEvent.click(save)
    await waitFor(() => expect(put).toHaveBeenCalledWith("/api/v1/earthmoving/deployments/d1/auto-billing", { basis: "MONTHLY" }))
  })

  it("shows a reader how the deployment is billed without a way to change it", async () => {
    perms = ["EARTHMOVING_READ"]
    open("WEEKLY")
    mount(<HireContractDialog asset={asset} onClose={() => {}} />)
    expect(await screen.findByText(/Billed automatically each month: per week/)).toBeTruthy()
    expect(screen.queryByLabelText("Charge this hire each month")).toBeNull()
  })
})
